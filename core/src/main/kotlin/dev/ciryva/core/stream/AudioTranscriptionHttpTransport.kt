package dev.ciryva.core.stream

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface AudioTranscriptionResult {
    data class Success(val text: String) : AudioTranscriptionResult
    data class Failure(val message: String) : AudioTranscriptionResult
    data object Cancelled : AudioTranscriptionResult
}

/** Cancellation also disconnects the upload; a provider may still bill an already received request. */
class AudioTranscriptionCancellation {
    private val stopped = AtomicBoolean(false)
    private val active = AtomicReference<HttpURLConnection?>(null)
    val isCancelled: Boolean get() = stopped.get()

    fun cancel() {
        stopped.set(true)
        active.getAndSet(null)?.disconnect()
    }

    internal fun attach(connection: HttpURLConnection): Boolean {
        if (stopped.get()) return false
        active.set(connection)
        if (stopped.get()) {
            active.compareAndSet(connection, null)
            return false
        }
        return true
    }

    internal fun release(connection: HttpURLConnection) {
        active.compareAndSet(connection, null)
    }
}

/**
 * A single, explicit, bounded gpt-transcribe POST. Uses the user's existing BYOK
 * credential, multipart streaming (not base64), and no new remote file resource.
 * No automatic retry, proxy, background upload, transcript persistence or logging.
 */
class AudioTranscriptionHttpTransport internal constructor(
    private val connectionFactory: () -> HttpURLConnection,
) {
    constructor() : this({
        URL("https://api.openai.com/v1/audio/transcriptions").openConnection() as HttpURLConnection
    })

    companion object {
        const val MODEL = "gpt-transcribe"
        const val MAX_AUDIO_BYTES = 10 * 1024 * 1024
        const val MAX_TRANSCRIPT_CHARS = 16_000
        private const val MAX_RESPONSE_BYTES = 128 * 1024
    }

    fun transcribe(
        audio: File,
        bearerToken: String,
        cancel: AudioTranscriptionCancellation = AudioTranscriptionCancellation(),
    ): AudioTranscriptionResult {
        require(bearerToken.isNotBlank() && bearerToken.length <= 512 &&
            '\n' !in bearerToken && '\r' !in bearerToken) { "Invalid OpenAI credential" }
        require(audio.isFile && audio.extension.lowercase() == "m4a" &&
            audio.length() in 1..MAX_AUDIO_BYTES.toLong()) { "Invalid or oversized audio file" }
        if (cancel.isCancelled) return AudioTranscriptionResult.Cancelled

        val boundary = "ciryva-" + UUID.randomUUID().toString().replace("-", "")
        val start = (
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"model\"\r\n\r\n$MODEL\r\n" +
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"response_format\"\r\n\r\njson\r\n" +
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"dictation.m4a\"\r\n" +
            "Content-Type: audio/mp4\r\n\r\n"
        ).toByteArray(StandardCharsets.UTF_8)
        val end = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
        val requestSize = start.size.toLong() + audio.length() + end.size

        val connection = try { connectionFactory() }
            catch (_: IOException) { return AudioTranscriptionResult.Failure("Network unavailable.") }
        if (!cancel.attach(connection)) {
            connection.disconnect()
            return AudioTranscriptionResult.Cancelled
        }
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept", "application/json")
            connection.setFixedLengthStreamingMode(requestSize)
            if (cancel.isCancelled) return AudioTranscriptionResult.Cancelled

            connection.outputStream.use { output ->
                output.write(start)
                audio.inputStream().use { source ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (cancel.isCancelled) return AudioTranscriptionResult.Cancelled
                        val n = source.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                    }
                }
                output.write(end)
            }
            if (cancel.isCancelled) return AudioTranscriptionResult.Cancelled
            val status = connection.responseCode
            if (status != 200) {
                return AudioTranscriptionResult.Failure(when (status) {
                    401, 403 -> "OpenAI API key rejected or transcription access unavailable."
                    413 -> "Recording exceeds the provider's file limit."
                    429 -> "OpenAI rate or usage limit reached."
                    in 500..599 -> "OpenAI transcription temporarily unavailable."
                    else -> "Transcription request failed (HTTP $status)."
                })
            }
            val bytes = connection.inputStream.use { stream ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    if (cancel.isCancelled) return AudioTranscriptionResult.Cancelled
                    val n = stream.read(buf)
                    if (n < 0) break
                    if (out.size() + n > MAX_RESPONSE_BYTES)
                        return AudioTranscriptionResult.Failure("Transcription response too large.")
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
            if (cancel.isCancelled) return AudioTranscriptionResult.Cancelled
            val text = try {
                Json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8))
                    .jsonObject["text"]?.jsonPrimitive?.content?.trim()
            } catch (_: Exception) { null }
            return when {
                text.isNullOrBlank() -> AudioTranscriptionResult.Failure("No transcription returned.")
                text.length > MAX_TRANSCRIPT_CHARS ->
                    AudioTranscriptionResult.Failure("Transcript exceeds the editor's voice-input limit.")
                else -> AudioTranscriptionResult.Success(text)
            }
        } catch (_: IOException) {
            return if (cancel.isCancelled) AudioTranscriptionResult.Cancelled
                else AudioTranscriptionResult.Failure("Transcription network connection failed.")
        } finally {
            cancel.release(connection)
            connection.disconnect()
        }
    }
}
