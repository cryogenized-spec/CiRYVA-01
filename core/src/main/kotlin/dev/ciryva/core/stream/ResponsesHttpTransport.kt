package dev.ciryva.core.stream

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Single-use, explicit text request. Model eligibility is not inferred here.
 * Prompt and output budgets are provisional hard caps, not a token cost quote.
 */
data class TextResponseRequest(
    val model: String,
    val prompt: String,
    val maxOutputTokens: Int = 1_024,
) {
    init {
        require(model.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))) { "Invalid model identifier" }
        require(prompt.isNotEmpty() && prompt.length <= 32_768) { "Prompt exceeds text budget" }
        require(maxOutputTokens in 1..4_096) { "Output token cap exceeds the configured ceiling" }
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
        cancelled.set(true)
        active.getAndSet(null)?.disconnect()
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
        val payload = buildJsonObject {
            put("model", JsonPrimitive(request.model))
            put("input", JsonPrimitive(request.prompt))
            put("stream", JsonPrimitive(true))
            put("store", JsonPrimitive(false))
            put("background", JsonPrimitive(false))
            put("max_output_tokens", JsonPrimitive(request.maxOutputTokens))
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

        fun accept(event: SseEvent): HttpStreamResult? {
            if (cancel.isCancelled) return HttpStreamResult.Cancelled
            eventCount++
            if (eventCount > 2_000) return HttpStreamResult.ProtocolError("event_limit")
            val parsed = parser.parse(event)
            if (parsed is ResponsesStreamEvent.TextDelta) {
                visibleChars += parsed.delta.length
                if (visibleChars > 131_072) return HttpStreamResult.ProtocolError("output_limit")
            }
            if (cancel.isCancelled) return HttpStreamResult.Cancelled
            onEvent(parsed)
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
