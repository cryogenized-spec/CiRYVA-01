package dev.ciryva.app

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import dev.ciryva.core.stream.ResponseAttachment
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

internal class AttachmentSizeLimitException : IllegalArgumentException(
    "File exceeds the current 16 MiB device-safe attachment limit."
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
        // Stream the original straight into app-private storage. The external
        // provider's claimed size is not reliable, so enforce the limit on
        // actual bytes copied. Only a short header is needed to identify MIME.
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create private media directory.")
        }
        val id = UUID.randomUUID().toString()
        val temp = File(directory, "$id.part")
        val target = fileForId(id)
        try {
            var count = 0
            resolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (n == 0) continue
                        if (count.toLong() + n > ResponseAttachment.MAX_BYTES.toLong()) {
                            throw AttachmentSizeLimitException()
                        }
                        output.write(buf, 0, n)
                        count += n
                    }
                }
            } ?: throw IOException("Document provider could not open this file.")
            if (count == 0) throw IllegalArgumentException("The selected file is empty.")
            val signature = ByteArray(12)
            val signatureCount = temp.inputStream().use { it.read(signature) }
            val mime = sniffMime(signature.copyOf(signatureCount.coerceAtLeast(0)))
            if (claimedType != null && claimedType != "application/octet-stream" &&
                claimedType != "*/*" && claimedType != mime &&
                !(mime == "image/jpeg" && claimedType == "image/jpg")) {
                throw IllegalArgumentException("File contents do not match the declared type.")
            }
            val safeName = sanitizedFileName(displayName, mime)
            val image = if (mime.startsWith("image/")) preview(temp) else null
            if (!temp.renameTo(target)) throw IOException("Unable to save private original.")
            return LocalMediaSelection(
                LocalStoredMedia(id, safeName, mime, count), image
            )
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

    /**
     * Cold-start-only cleanup. Never delete a media UUID linked to the archive
     * or an unsent saved draft. Keep recent orphan imports for at least 24h to
     * avoid treating an Activity recreation as a process-level abandon.
     * This acts only inside our dedicated private media directory.
     */
    fun pruneStaleUnreferenced(retained: Set<String>, unsentId: String?,
        minAgeMillis: Long = 24L * 60 * 60 * 1000): Int {
        val now = System.currentTimeMillis()
        var count = 0
        directory.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val uuidName = file.name.removeSuffix(".part")
            if (runCatching { UUID.fromString(uuidName) }.isFailure ||
                uuidName.length != 36 || uuidName in retained ||
                uuidName == unsentId || now - file.lastModified() < minAgeMillis) {
                return@forEach
            }
            if (file.delete()) count++
        }
        return count
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

    private fun preview(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth !in 1..8_192 || bounds.outHeight !in 1..8_192) {
            throw IllegalArgumentException("Image dimensions are unsupported.")
        }
        var sample = 1
        while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        ) ?: throw IllegalArgumentException("Image preview could not be decoded.")
    }
}
