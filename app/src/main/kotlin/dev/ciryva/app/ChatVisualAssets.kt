package dev.ciryva.app

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min

/**
 * Explicit SAF-driven local wallpaper/font cache; no remote requests or broad
 * device storage permissions. Use fixed internal filenames only (never an
 * untrusted display name or path) and a bounded copy before image decoding.
 */
internal class ChatVisualAssets(private val context: Context) {
    companion object {
        private const val MAX_SOURCE_BYTES = 12 * 1024 * 1024
        private const val MAX_FONT_BYTES = 2 * 1024 * 1024
        private const val TARGET_WIDTH = 1080
        private const val TARGET_HEIGHT = 1920
        private const val MAX_WALLPAPER_BYTES = 2 * 1024 * 1024
    }

    private val directory = File(context.filesDir, "chat_visual")
    private val wallpaper = File(directory, "chat_wallpaper.jpg")
    private val fontFile = File(directory, "reading_font")
    private fun ensure() {
        if (!directory.exists() && !directory.mkdirs())
            throw IOException("Could not initialize private display assets.")
    }

    private fun copyBounded(
        resolver: ContentResolver, source: Uri, target: File, maxBytes: Int,
    ) {
        resolver.openInputStream(source)?.use { input ->
            FileOutputStream(target).use { output ->
                val bytes = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = input.read(bytes)
                    if (n < 0) break
                    total += n
                    require(total <= maxBytes) { "Selected file exceeds the local size limit." }
                    output.write(bytes, 0, n)
                }
                require(total > 0) { "Selected file is empty." }
            }
        } ?: throw IOException("Cannot open selected document.")
    }

    /**
     * Crop to exact 9:16, downsample before decode, and save an optimized,
     * app-private JPEG under a constant path.
     */
    fun importWallpaper(uri: Uri) {
        ensure()
        val source = File(directory, "wallpaper_source.tmp")
        val encoded = File(directory, "wallpaper_output.tmp")
        try {
            copyBounded(context.contentResolver, uri, source, MAX_SOURCE_BYTES)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, options)
            val w = options.outWidth
            val h = options.outHeight
            require(w in 64..16_384 && h in 64..16_384) {
                "Select a valid image at least 64 pixels wide and high."
            }
            var sample = 1
            while (w / sample > 2048 || h / sample > 3072) sample *= 2
            val bitmap = BitmapFactory.decodeFile(source.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: throw IOException("Could not decode selected image.")
            val cut: Bitmap
            try {
                val targetAspect = 9f / 16f
                val aspect = bitmap.width.toFloat() / bitmap.height
                val cropWidth: Int
                val cropHeight: Int
                if (aspect > targetAspect) {
                    cropHeight = bitmap.height
                    cropWidth = (cropHeight * targetAspect).toInt().coerceAtLeast(1)
                } else {
                    cropWidth = bitmap.width
                    cropHeight = (cropWidth / targetAspect).toInt().coerceAtLeast(1)
                }
                cut = Bitmap.createBitmap(bitmap,
                    (bitmap.width - cropWidth) / 2,
                    (bitmap.height - cropHeight) / 2, cropWidth, cropHeight)
            } finally {
                // Cropped bitmap can share memory with the parent; do not recycle it early.
            }
            val scaled = Bitmap.createScaledBitmap(cut, TARGET_WIDTH, TARGET_HEIGHT, true)
            FileOutputStream(encoded).use { output ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 75, output))
                    throw IOException("Could not encode the wallpaper.")
            }
            if (scaled !== cut) scaled.recycle()
            if (cut !== bitmap) cut.recycle()
            bitmap.recycle()
            require(encoded.length() in 1..MAX_WALLPAPER_BYTES.toLong()) {
                "Wallpaper exceeds the 2 MiB local image limit."
            }
            if (!encoded.renameTo(wallpaper)) throw IOException("Could not save the wallpaper.")
        } finally {
            source.delete()
            encoded.delete()
        }
    }

    fun wallpaperImage(): ImageBitmap? {
        if (!wallpaper.isFile) return null
        val bitmap = BitmapFactory.decodeFile(wallpaper.absolutePath) ?: return null
        return bitmap.asImageBitmap()
    }
    fun hasWallpaper(): Boolean = wallpaper.isFile
    fun removeWallpaper() { wallpaper.delete() }

    fun importTypeface(uri: Uri) {
        ensure()
        val temp = File(directory, "font.tmp")
        try {
            copyBounded(context.contentResolver, uri, temp, MAX_FONT_BYTES)
            val prefix = ByteArray(4)
            temp.inputStream().use { check(it.read(prefix) == 4) }
            val type = prefix.map { it.toInt() and 0xff }
            require(
                type == listOf(0, 1, 0, 0) || type == listOf(0x4f, 0x54, 0x54, 0x4f) ||
                    type == listOf(0x74, 0x72, 0x75, 0x65)
            ) { "Select a genuine TTF or OTF font." }
            // createFromFile silently substitutes Typeface.DEFAULT for corrupt data.
            // Builder(File).build() instead returns null for invalid font tables (API 26+).
            require(Typeface.Builder(temp).build() != null) {
                "Font tables are invalid or unsupported; existing font preserved."
            }
            if (!temp.renameTo(fontFile)) throw IOException("Unable to cache this font.")
        } finally {
            temp.delete()
        }
    }

    fun importedTypeface(): FontFamily? = runCatching {
        if (fontFile.isFile && fontFile.length() in 1..MAX_FONT_BYTES.toLong()) {
            Typeface.Builder(fontFile).build()?.let { FontFamily(it) }
        } else null
    }.getOrNull()

    fun removeTypeface() { fontFile.delete() }
}
