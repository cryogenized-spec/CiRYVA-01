package dev.ciryva.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Private, explicit on-device Latin OCR. The picked image isn't transmitted,
 * and the extracted text stays editable until the owner taps Insert.
 *
 * A thumbnail-size source is decoded instead of an arbitrary-size original.
 */
internal object LocalImageOcr {
    private const val MAX_EDGE = 2_048
    private const val MAX_TEXT_CHARS = 32_000

    fun extract(
        context: Context,
        uri: Uri,
        onResult: (Result<String>) -> Unit,
    ) {
        val bitmap = try { decodeBounded(context, uri) }
            catch (_: Exception) { null }
        if (bitmap == null) {
            onResult(Result.failure(IllegalArgumentException("Could not decode selected image.")))
            return
        }
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            client.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val recognized = result.text.trim()
                    if (recognized.length > MAX_TEXT_CHARS) {
                        onResult(Result.failure(IllegalArgumentException(
                            "OCR result exceeds the 32,000-character editor limit.")))
                    } else if (recognized.isBlank()) {
                        onResult(Result.failure(IllegalArgumentException(
                            "No recognizable printed text was detected.")))
                    } else onResult(Result.success(recognized))
                }
                .addOnFailureListener {
                    onResult(Result.failure(IllegalStateException(
                        "On-device OCR could not read this image.")))
                }
                .addOnCompleteListener {
                    bitmap.recycle()
                    client.close()
                }
        } catch (_: Exception) {
            bitmap.recycle()
            client.close()
            onResult(Result.failure(IllegalStateException("OCR could not start.")))
        }
    }

    private fun decodeBounded(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) {
                decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val scale = (MAX_EDGE.toDouble() /
                    maxOf(width, height).coerceAtLeast(1)).coerceAtMost(1.0)
                decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options()
            while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > MAX_EDGE) {
                options.inSampleSize *= 2
            }
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }
    }
}
