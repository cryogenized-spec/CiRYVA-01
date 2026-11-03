package dev.ciryva.app

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import dev.ciryva.core.stream.ResponseAttachment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * An imported original, copied to private internal device storage before any
 * network access. Only opaque UUIDs enter the SQLite archive; the original
 * source URI and file-system path never enter the OpenAI prompt.
 */
data class LocalStoredMedia(
    val id: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Int,
)

data class LocalMediaSelection(
    val media: LocalStoredMedia,
    val thumbnail: Bitmap? = null,
)

internal class LocalAttachmentStore(private val directory: File) {
    companion object {
        fun sniffMime(bytes: ByteArray): String = when {
            bytes.size >= 8 &&
                bytes.copyOfRange(0, 8).contentEquals(
                    byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
                ) -> "image/png"
            bytes.size >= 3 &&
                (bytes[0].toInt() and 255) == 0xff &&
                (bytes[1].toInt() and 255) == 0xd8 &&
                (bytes[2].toInt() and 255) == 0xff -> "image/jpeg"
            bytes.size >= 5 &&
                bytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray()) ->
                "application/pdf"
            else -> throw IllegalArgumentException(
                "Only genuine JPEG, PNG and PDF documents are supported."
            )
        }

        private fun extension(mime: String) = when (mime) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "application/pdf" -> ".pdf"
            else -> throw IllegalArgumentException("Unsupported format")
        }

        private fun sanitizedFileName(original: String, mime: String): String {
            val base = original.substringAfterLast('/').substringAfterLast('\\')
                .substringBeforeLast('.').filter { it.code in 32..126 && it != '/' }
                .trim().take(74).ifBlank { "attachment" }
            return base + extension(mime)
        }
    }

    private fun fileForId(id: String): File {
        require(runCatching { UUID.fromString(id) }.isSuccess &&
            id.length == 36) { "Invalid local attachment ID" }
        return File(directory, id)
    }

    /** Never call from the UI thread. Source provider size can be a lie. */
    fun import(resolver: ContentResolver, uri: Uri): LocalMediaSelection {
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null)?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "attachment"
        val claimedType = resolver.getType(uri)
        // Bound the content while reading; do not trust OpenableColumns.SIZE.
        val bytes = ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                if (bytes.size() + n > ResponseAttachment.MAX_BYTES) {
                    throw IllegalArgumentException("Attachment exceeds 2 MB.")
                }
                bytes.write(buf, 0, n)
            }
        } ?: throw IOException("Document provider could not open this file.")
        val original = bytes.toByteArray()
        val mime = sniffMime(original)
        if (claimedType != null && claimedType != "application/octet-stream" &&
            claimedType != "*/*" && claimedType != mime &&
            !(mime == "image/jpeg" && claimedType == "image/jpg")) {
            throw IllegalArgumentException("File contents do not match the declared type.")
        }
        val safeName = sanitizedFileName(displayName, mime)
        // Validate BOTH magic bytes and request-level filename before saving.
        ResponseAttachment(safeName, mime, original)
        val image = if (mime.startsWith("image/")) preview(original) else null
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create private media directory.")
        }
        val id = UUID.randomUUID().toString()
        val temp = File(directory, "$id.part")
        val target = fileForId(id)
        try {
            temp.outputStream().use { it.write(original) }
            if (!temp.renameTo(target)) throw IOException("Unable to save private original.")
            return LocalMediaSelection(LocalStoredMedia(
                id, safeName, mime, original.size
            ), image)
        } finally {
            temp.delete()
        }
    }

    fun forRequest(media: LocalStoredMedia): ResponseAttachment {
        val saved = fileForId(media.id)
        if (!saved.isFile || saved.length() !in 1..ResponseAttachment.MAX_BYTES.toLong()) {
            throw IOException("Local attachment is unavailable.")
        }
        val original = saved.readBytes()
        require(original.size == media.sizeBytes) { "Local attachment size changed" }
        return ResponseAttachment(media.filename, media.mimeType, original)
    }

    /** Expose only a validated app-private original to Android's FileProvider. */
    fun fileForView(media: LocalStoredMedia): File {
        val original = fileForId(media.id)
        if (!original.isFile || original.length() != media.sizeBytes.toLong()) {
            throw IOException("Archived original is unavailable.")
        }
        return original
    }

    fun delete(id: String) {
        fileForId(id).delete()
    }

    private fun preview(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..8_192 || bounds.outHeight !in 1..8_192) {
            throw IllegalArgumentException("Image dimensions are unsupported.")
        }
        var sample = 1
        while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        ) ?: throw IllegalArgumentException("Image preview could not be decoded.")
    }
}
