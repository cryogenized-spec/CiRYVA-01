package dev.ciryva.core.stream

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Real loopback HTTP connections; NO outgoing OpenAI requests and NO API keys.
 * The server intentionally inspects the request wire format and SSE responses.
 */
class ResponsesHttpTransportTest {
    private val delta = """{"type":"response.output_text.delta","delta":"café 🌍","item_id":"msg1","output_index":0,"content_index":0}"""
    private val completed = """{"type":"response.completed","response":{"id":"resp1","status":"completed","usage":{"input_tokens":9,"output_tokens":5,"total_tokens":14}}}"""
    private val sseBody =
        sse("response.output_text.delta", delta) + sse("response.completed", completed)

    private fun sse(type: String, json: String) = "event: $type\ndata: $json\n\n"

    private fun testServer(handler: (HttpExchange) -> Unit, test: (URL) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/responses") { exchange ->
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            test(URL("http://127.0.0.1:${server.address.port}/v1/responses"))
        } finally {
            server.stop(0)
        }
    }

    private fun transport(url: URL) = ResponsesHttpTransport(
        connectionFactory = { url.openConnection() as HttpURLConnection },
        readTimeoutMillis = 3_000,
        maxDurationMillis = 10_000,
    )

    private fun respond(
        exchange: HttpExchange,
        status: Int = 200,
        body: String = sseBody,
        contentType: String = "text/event-stream; charset=utf-8",
    ) {
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.responseHeaders.add("x-request-id", "req_unit_test_123")
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    @Test fun `authorized POST streams text and only a terminal receipt proves success`() {
        var requestVerified = false
        testServer({ exchange ->
            assertEquals("POST", exchange.requestMethod)
            assertEquals("Bearer sk-test-only", exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("text/event-stream", exchange.requestHeaders.getFirst("Accept"))
            val payload = Json.parseToJsonElement(
                exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            ) as JsonObject
            assertEquals("private-model-id", payload["model"]?.jsonPrimitive?.content)
            assertEquals("Hello, give me a quote 🌍", payload["input"]?.jsonPrimitive?.content)
            assertEquals(true, payload["stream"]?.jsonPrimitive?.boolean)
            assertEquals(false, payload["store"]?.jsonPrimitive?.boolean)
            assertEquals(false, payload["background"]?.jsonPrimitive?.boolean)
            assertEquals(123, payload["max_output_tokens"]?.jsonPrimitive?.int)
            requestVerified = true
            respond(exchange)
        }) { url ->
            val seen = mutableListOf<ResponsesStreamEvent>()
            val outcome = transport(url).stream(
                TextResponseRequest("private-model-id", "Hello, give me a quote 🌍", 123),
                "sk-test-only",
                onEvent = { seen.add(it) },
            )
            assertEquals(HttpStreamResult.Completed("resp1", TokenUsage(9, 5, 14), "req_unit_test_123"), outcome)
            assertEquals(2, seen.size)
            assertEquals("café 🌍", (seen[0] as ResponsesStreamEvent.TextDelta).delta)
        }
        assertTrue(requestVerified)
    }

    @Test fun `HTTP authentication and throttling failures are sanitized and not retried`() {
        for (code in listOf(401, 403, 429, 503, 302)) {
            val calls = AtomicInteger()
            testServer({ exchange ->
                calls.incrementAndGet()
                respond(exchange, status = code, body = """{"error":{"message":"do not log secret"}}""",
                    contentType = "application/json")
            }) { url ->
                val observed = mutableListOf<ResponsesStreamEvent>()
                val result = transport(url).stream(
                    TextResponseRequest("m", "test"), "sk-unit-key",
                    onEvent = { observed.add(it) },
                )
                assertEquals(HttpStreamResult.Rejected(code, "req_unit_test_123"), result)
                assertTrue(observed.isEmpty())
            }
            assertEquals(1, calls.get(), "No retries or redirects")
        }
    }

    @Test fun `no success without completed receipt even on clean EOF`() {
        testServer({ exchange -> respond(exchange, body = sse("response.output_text.delta", delta)) }) { url ->
            val events = mutableListOf<ResponsesStreamEvent>()
            val outcome = transport(url).stream(TextResponseRequest("m", "test"), "sk-unit-key",
                onEvent = { events.add(it) })
            assertEquals(HttpStreamResult.UnexpectedEnd, outcome)
            assertEquals(1, events.size)
        }
    }

    @Test fun `provider failure and incomplete status are not success`() {
        val cases = listOf(
            sse("response.failed", """{"type":"response.failed","response":{"id":"r1","status":"failed","error":{"code":"model_error","message":"failed"}}}""") to
                HttpStreamResult.Failed("r1", "model_error"),
            sse("response.incomplete", """{"type":"response.incomplete","response":{"id":"r2","status":"incomplete","incomplete_details":{"reason":"max_output_tokens"}}}""") to
                HttpStreamResult.Incomplete("r2", "max_output_tokens"),
            sse("error", """{"type":"error","code":"rate_limit","message":"unavailable"}""") to
                HttpStreamResult.ProviderError("rate_limit"),
        )
        for ((body, expected) in cases) {
            testServer({ exchange -> respond(exchange, body = body) }) { url ->
                assertEquals(expected, transport(url).stream(TextResponseRequest("m", "test"), "sk-unit-key", onEvent = {}))
            }
        }
    }

    @Test fun `cancel during first delta stops delivery before completed event`() {
        testServer({ exchange -> respond(exchange) }) { url ->
            val cancellation = StreamCancellation()
            val seen = mutableListOf<ResponsesStreamEvent>()
            val result = transport(url).stream(
                TextResponseRequest("m", "test"), "sk-unit-key", cancellation,
                onEvent = { event ->
                    seen.add(event)
                    if (event is ResponsesStreamEvent.TextDelta) cancellation.cancel()
                },
            )
            assertEquals(HttpStreamResult.Cancelled, result)
            assertEquals(1, seen.size)
            assertInstanceOf(ResponsesStreamEvent.TextDelta::class.java, seen.single())
        }
    }

    @Test fun `pre-cancelled request never opens a connection`() {
        val connections = AtomicInteger()
        val transport = ResponsesHttpTransport({
            connections.incrementAndGet()
            throw java.io.IOException("no connection should be attempted")
        })
        val cancellation = StreamCancellation()
        cancellation.cancel()
        assertEquals(HttpStreamResult.Cancelled,
            transport.stream(TextResponseRequest("m", "test"), "sk-unit-key", cancellation, onEvent = {}))
        assertEquals(0, connections.get())
    }

    @Test fun `malformed event JSON and bad UTF8 are explicit protocol failures`() {
        val cases = listOf(
            sse("response.completed", """{"type":"response.completed","response":BROKEN}""")
                .toByteArray(StandardCharsets.UTF_8) to "sse_or_json",
            ("event: response.output_text.delta\ndata: ".toByteArray(StandardCharsets.UTF_8) +
                byteArrayOf(0xC3.toByte(), 0x28, 0x0A, 0x0A)) to "utf8",
        )
        for ((payload, expected) in cases) {
            testServer({ exchange ->
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.write(payload)
            }) { url ->
                val result = transport(url).stream(TextResponseRequest("m", "test"), "sk-unit-key", onEvent = {})
                assertEquals(HttpStreamResult.ProtocolError(expected), result)
            }
        }
    }

    @Test fun `unexpected HTML content is not parsed or displayed`() {
        testServer({ exchange -> respond(exchange, body = "<html>login</html>", contentType = "text/html") }) { url ->
            assertEquals(HttpStreamResult.ProtocolError("content_type"),
                transport(url).stream(TextResponseRequest("m", "test"), "sk-unit-key", onEvent = {}))
        }
    }

    @Test fun `invalid request is rejected before network or credential usage`() {
        assertThrows(IllegalArgumentException::class.java) { TextResponseRequest("bad\nmodel", "prompt") }
        assertThrows(IllegalArgumentException::class.java) { TextResponseRequest("m", "") }
        assertThrows(IllegalArgumentException::class.java) { TextResponseRequest("m", "text", 4097) }
        val t = ResponsesHttpTransport({
            throw java.io.IOException("must not connect")
        })
        assertThrows(IllegalArgumentException::class.java) {
            t.stream(TextResponseRequest("m", "test"), "sk-invalid\nheader", onEvent = {})
        }
    }

    @Test fun `HTTP incomplete status includes billable usage but never reports success`() {
        val eventBody = sse(
            "response.incomplete",
            """{"type":"response.incomplete","response":{"id":"r_billed","status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"usage":{"input_tokens":25,"output_tokens":200,"total_tokens":225}}}"""
        )
        testServer({ exchange -> respond(exchange, body = eventBody) }) { url ->
            val terminal = transport(url).stream(
                TextResponseRequest("m", "test"), "sk-unit-key", onEvent = {},
            )
            assertEquals(
                HttpStreamResult.Incomplete("r_billed", "max_output_tokens", TokenUsage(25, 200, 225)),
                terminal,
            )
        }
    }
}
