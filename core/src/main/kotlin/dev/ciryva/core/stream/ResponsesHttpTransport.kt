package dev.ciryva.core.stream

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Single-use, explicit text request. Model eligibility is not inferred here.
 * Prompt and output budgets are provisional hard caps, not a token cost quote.
 */
data class PriorChatMessage(val role: String, val content: String) {
    init {
        require(role == "user" || role == "assistant") { "Invalid prior message role" }
        require(content.isNotBlank() && content.length <= 8_192) { "Invalid prior message length" }
    }
}

/**
 * One locally bounded, user-selected image/PDF transferred only during an approved
 * Responses API request. The bytes never appear in history, logs or prompts.
 * No implicit provider file upload or remote storage resource is created.
 */
data class ResponseAttachment(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    companion object {
        // The Responses API has a larger provider limit. This local ceiling is
        // a conservative safety guard until the data-URL transport is streamed:
        // base64 and JSON temporarily duplicate payloads in Android memory.
        const val MAX_BYTES = 16 * 1024 * 1024
        val TYPES = setOf("image/jpeg", "image/png", "application/pdf")
    }
    init {
        require(filename.length in 1..100 &&
            filename.none { it == '/' || it == '\\' || it.code < 32 } &&
            filename != "." && filename != "..") { "Invalid attachment name" }
        require(mimeType in TYPES) { "Unsupported attachment type" }
        require(bytes.size in 1..MAX_BYTES) { "Attachment exceeds the 16 MiB in-memory transport ceiling" }
        val signatureMatches = when (mimeType) {
            "image/jpeg" -> bytes.size >= 3 &&
                (bytes[0].toInt() and 255) == 0xff &&
                (bytes[1].toInt() and 255) == 0xd8 &&
                (bytes[2].toInt() and 255) == 0xff
            "image/png" -> bytes.size >= 8 &&
                bytes.copyOfRange(0, 8).contentEquals(
                    byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
                )
            "application/pdf" -> bytes.size >= 5 &&
                bytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())
            else -> false
        }
        require(signatureMatches) { "Attachment contents do not match the selected format" }
    }
}

data class TextResponseRequest(
    val model: String,
    val prompt: String,
    val maxOutputTokens: Int = 1_024,
    val settings: GenerationSettings? = null,
    val history: List<PriorChatMessage> = emptyList(),
    val instructions: String? = null,
    val attachment: ResponseAttachment? = null,
    // Preferred multi-file API; legacy attachment remains compatible with
    // already-merged Android callers while the picker/archive are upgraded.
    val attachments: List<ResponseAttachment> = emptyList(),
) {
    val selectedAttachments: List<ResponseAttachment>
        get() = if (attachment == null) attachments else listOf(attachment) + attachments

    init {
        require(model.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))) { "Invalid model identifier" }
        require(prompt.isNotEmpty() && prompt.length <= 32_768) { "Prompt exceeds text budget" }
        require(maxOutputTokens in 1..4_096) { "Output token cap exceeds the configured ceiling" }
        // Only completed local turns may become context, never hidden system
        // messages or uncontrolled transcript replay.
        require(history.size <= 12 && history.size % 2 == 0) { "History turn cap exceeded" }
        require(history.sumOf { it.content.length } <= 12_000) { "History character cap exceeded" }
        require(history.withIndex().all { (index, item) ->
            item.role == (if (index % 2 == 0) "user" else "assistant")
        }) { "History must contain complete user/assistant pairs" }
        require(instructions == null || (
            instructions.isNotBlank() && instructions.length <= 2_048 &&
                '\u0000' !in instructions && '\r' !in instructions
        )) { "Invalid user-authored instructions" }
        require(settings == null || settings.modelId == model) {
            "Settings model must match request model"
        }
        // The provider's published combined-file allowance is higher.
        // Current JSON/data-URL encoding expands all files in Android memory,
        // so bound the number of objects and TOTAL original bytes locally.
        require(selectedAttachments.size <= 12) {
            "This local transport supports at most 12 file parts per request"
        }
        require(selectedAttachments.sumOf { it.bytes.size.toLong() } <=
            ResponseAttachment.MAX_BYTES.toLong()) {
            "Combined attachments exceed the 16 MiB Android transport budget"
        }
    }
}

/**
 * The provider's terminal receipt is necessary for success; EOF is not success.
 * All failure categories contain sanitized metadata, NEVER response bodies or secrets.
 */
sealed interface HttpStreamResult {
    data class Completed(val responseId: String, val usage: TokenUsage?, val requestId: String?) : HttpStreamResult
    data class Rejected(val httpStatus: Int, val requestId: String?) : HttpStreamResult
    data class Failed(val responseId: String, val code: String?) : HttpStreamResult
    data class Incomplete(val responseId: String, val reason: String?, val usage: TokenUsage? = null) : HttpStreamResult
    data class ProviderError(val code: String?) : HttpStreamResult
    data class ProtocolError(val reason: String) : HttpStreamResult
    data object UnexpectedEnd : HttpStreamResult
    data object NetworkError : HttpStreamResult
    data object Cancelled : HttpStreamResult
}

/**
 * Explicit cancellation object; disconnects an active read and prevents even
 * starting a request that was cancelled before transport setup.
 */
class StreamCancellation {
    private val cancelled = AtomicBoolean(false)
    private val active = AtomicReference<HttpURLConnection?>(null)

    fun cancel() {
        // Avoid blocking the UI thread while Java's HttpURLConnection is in a
        // stalled read: JDK 17 disconnect() may wait for the read timeout.
        // A token can be cancelled only once, so at most one daemon is started.
        if (!cancelled.compareAndSet(false, true)) return
        active.getAndSet(null)?.let { connection ->
            Thread({
                try {
                    connection.disconnect()
                } catch (_: Exception) {
                    // The stream thread independently checks the cancelled flag.
                }
            }, "ciryva-stream-cancel").apply {
                isDaemon = true
                start()
            }
        }
    }

    val isCancelled: Boolean get() = cancelled.get()

    internal fun attach(connection: HttpURLConnection): Boolean {
        if (cancelled.get()) return false
        active.set(connection)
        if (cancelled.get()) {
            active.compareAndSet(connection, null)
            connection.disconnect()
            return false
        }
        return true
    }

    internal fun release(connection: HttpURLConnection) {
        active.compareAndSet(connection, null)
    }
}

/**
 * Android/JVM compatible streaming HTTP transport. No new networking dependency,
 * background threads, credential storage, retries or side effects beyond the
 * explicitly invoked POST. Caller MUST run stream() on a background thread.
 *
 * Default connection factory is fixed to OpenAI's official HTTPS endpoint;
 * internal factory injection exists solely for offline loopback tests.
 *
 * Credential lifecycle and ownership are deliberately external to this class:
 * no key is read from build config, disk, environment, logs or a UI here.
 */
private class ListenerFailure(val original: Exception) : RuntimeException(original)

/** No UI or storage exception is silently recategorized as a provider error. */
class ResponsesHttpTransport internal constructor(
    private val connectionFactory: () -> HttpURLConnection,
    private val readTimeoutMillis: Int = 15_000,
    private val maxDurationMillis: Long = 120_000,
) {
    constructor() : this({
        URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection
    })

    init {
        require(readTimeoutMillis in 1..60_000)
        require(maxDurationMillis in 1..600_000)
    }

    fun stream(
        request: TextResponseRequest,
        bearerToken: String,
        cancel: StreamCancellation = StreamCancellation(),
        onEvent: (ResponsesStreamEvent) -> Unit,
    ): HttpStreamResult {
        require(bearerToken.isNotBlank() && bearerToken.length <= 512 &&
            '\r' !in bearerToken && '\n' !in bearerToken) { "Invalid credential" }
        if (cancel.isCancelled) return HttpStreamResult.Cancelled

        // Explicit privacy default: do not store Responses API output server-side.
        val selectedFiles = request.selectedAttachments
        val payload = buildJsonObject {
            put("model", JsonPrimitive(request.model))
            request.instructions?.let { put("instructions", JsonPrimitive(it)) }
            if (request.history.isEmpty() && selectedFiles.isEmpty()) {
                put("input", JsonPrimitive(request.prompt))
            } else {
                put("input", buildJsonArray {
                    request.history.forEach { item ->
                        add(buildJsonObject {
                            put("role", JsonPrimitive(item.role))
                            put("content", JsonPrimitive(item.content))
                        })
                    }
                    add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        if (selectedFiles.isEmpty()) {
                            put("content", JsonPrimitive(request.prompt))
                        } else {
                            // User-visible Send is the only point at which
                            // base64 encoding happens. Never pre-upload files.
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", JsonPrimitive("input_text"))
                                    put("text", JsonPrimitive(request.prompt))
                                })
                                selectedFiles.forEach { file ->
                                    val dataUrl = "data:" + file.mimeType + ";base64," +
                                        Base64.getEncoder().encodeToString(file.bytes)
                                    add(buildJsonObject {
                                        if (file.mimeType == "application/pdf") {
                                            put("type", JsonPrimitive("input_file"))
                                            put("filename", JsonPrimitive(file.filename))
                                            put("file_data", JsonPrimitive(dataUrl))
                                        } else {
                                            put("type", JsonPrimitive("input_image"))
                                            put("image_url", JsonPrimitive(dataUrl))
                                            put("detail", JsonPrimitive("low"))
                                        }
                                    })
                                }
                            })
                        }
                    })
                })
            }
            put("stream", JsonPrimitive(true))
            put("store", JsonPrimitive(false))
            put("background", JsonPrimitive(false))
            put("max_output_tokens", JsonPrimitive(request.maxOutputTokens))
            request.settings?.let { settings ->
                if (settings.reasoningEffort != null || settings.reasoningSummary != null) {
                    put("reasoning", buildJsonObject {
                        settings.reasoningEffort?.let { put("effort", JsonPrimitive(it)) }
                        settings.reasoningSummary?.let { put("summary", JsonPrimitive(it)) }
                    })
                }
                settings.verbosity?.let { detail ->
                    put("text", buildJsonObject { put("verbosity", JsonPrimitive(detail)) })
                }
                settings.temperature?.let { put("temperature", JsonPrimitive(it)) }
                settings.topP?.let { put("top_p", JsonPrimitive(it)) }
            }
        }.toString().toByteArray(StandardCharsets.UTF_8)

        val connection = try {
            connectionFactory()
        } catch (_: IOException) {
            return if (cancel.isCancelled) HttpStreamResult.Cancelled else HttpStreamResult.NetworkError
        }
        if (!cancel.attach(connection)) {
            connection.disconnect()
            return HttpStreamResult.Cancelled
        }

        val start = System.nanoTime()
        val decoder = Utf8SseEventDecoder()
        val parser = ResponsesEventParser()
        var totalBytes = 0
        var eventCount = 0
        var visibleChars = 0
        var summaryChars = 0

        fun accept(event: SseEvent): HttpStreamResult? {
            if (cancel.isCancelled) return HttpStreamResult.Cancelled
            eventCount++
            if (eventCount > 2_000) return HttpStreamResult.ProtocolError("event_limit")
            val parsed = parser.parse(event)
            if (parsed is ResponsesStreamEvent.TextDelta) {
                visibleChars += parsed.delta.length
                if (visibleChars > 131_072) return HttpStreamResult.ProtocolError("output_limit")
            }
            if (parsed is ResponsesStreamEvent.ReasoningSummaryDelta) {
                summaryChars += parsed.delta.length
                if (summaryChars > 8_192) return HttpStreamResult.ProtocolError("summary_limit")
            }
            if (cancel.isCancelled) return HttpStreamResult.Cancelled
            try {
                onEvent(parsed)
            } catch (error: Exception) {
                throw ListenerFailure(error)
            }
            if (cancel.isCancelled) return HttpStreamResult.Cancelled
            return when (parsed) {
                is ResponsesStreamEvent.Completed -> HttpStreamResult.Completed(
                    parsed.responseId, parsed.usage, safeRequestId(connection.getHeaderField("x-request-id"))
                )
                is ResponsesStreamEvent.Failed -> HttpStreamResult.Failed(parsed.responseId, parsed.code)
                is ResponsesStreamEvent.Incomplete -> HttpStreamResult.Incomplete(parsed.responseId, parsed.reason, parsed.usage)
                is ResponsesStreamEvent.Error -> HttpStreamResult.ProviderError(parsed.code)
                else -> null
            }
        }

        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = readTimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.setFixedLengthStreamingMode(payload.size)
            if (cancel.isCancelled) return HttpStreamResult.Cancelled
            connection.outputStream.use { stream ->
                stream.write(payload)
            }
            if (cancel.isCancelled) return HttpStreamResult.Cancelled

            val status = connection.responseCode
            if (cancel.isCancelled) return HttpStreamResult.Cancelled
            if (status != 200) return HttpStreamResult.Rejected(
                status, safeRequestId(connection.getHeaderField("x-request-id"))
            )
            // In particular, reject an HTML login page/proxy error as a valid SSE stream.
            val contentType = connection.contentType?.substringBefore(';')?.trim()
            if (!"text/event-stream".equals(contentType, ignoreCase = true)) {
                return HttpStreamResult.ProtocolError("content_type")
            }
            connection.inputStream.use { input ->
                val buffer = ByteArray(8_192)
                while (true) {
                    if (cancel.isCancelled) return HttpStreamResult.Cancelled
                    if ((System.nanoTime() - start) / 1_000_000 > maxDurationMillis) {
                        return HttpStreamResult.ProtocolError("deadline")
                    }
                    val count = input.read(buffer)
                    if (cancel.isCancelled) return HttpStreamResult.Cancelled
                    if (count == -1) break
                    if (count == 0) continue
                    totalBytes += count
                    if (totalBytes > 4_194_304) return HttpStreamResult.ProtocolError("byte_limit")
                    for (event in decoder.feed(buffer.copyOfRange(0, count))) {
                        accept(event)?.let { return it }
                    }
                }
            }
            if (cancel.isCancelled) return HttpStreamResult.Cancelled
            // Only genuinely clean EOF can flush an unterminated *event*.
            for (event in decoder.finish()) {
                accept(event)?.let { return it }
            }
            return HttpStreamResult.UnexpectedEnd
        } catch (error: ListenerFailure) {
            throw error.original
        } catch (_: java.nio.charset.CharacterCodingException) {
            return if (cancel.isCancelled) HttpStreamResult.Cancelled
                else HttpStreamResult.ProtocolError("utf8")
        } catch (_: IllegalArgumentException) {
            return if (cancel.isCancelled) HttpStreamResult.Cancelled
                else HttpStreamResult.ProtocolError("sse_or_json")
        } catch (_: IOException) {
            return if (cancel.isCancelled) HttpStreamResult.Cancelled else HttpStreamResult.NetworkError
        } finally {
            cancel.release(connection)
            connection.disconnect()
        }
    }

    private fun safeRequestId(input: String?): String? =
        input?.takeIf { id ->
            id.length in 1..128 && id.all { it.code in 33..126 }
        }
}
