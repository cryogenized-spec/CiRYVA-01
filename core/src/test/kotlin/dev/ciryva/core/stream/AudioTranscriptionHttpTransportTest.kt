package dev.ciryva.core.stream

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Loopback-only: no paid OpenAI requests, secrets, microphone or emulator. */
class AudioTranscriptionHttpTransportTest {
    private fun withServer(
        handler: (HttpExchange) -> Unit,
        verify: (AudioTranscriptionHttpTransport) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/audio/transcriptions") { exchange ->
            try { handler(exchange) } finally { exchange.close() }
        }
        server.start()
        try {
            val url = URL("http://127.0.0.1:" + server.address.port + "/v1/audio/transcriptions")
            verify(AudioTranscriptionHttpTransport {
                url.openConnection() as HttpURLConnection
            })
        } finally { server.stop(0) }
    }

    private fun recording(): File = File.createTempFile("voice-fixture-", ".m4a").apply {
        writeBytes("illustrative-aac-bytes-no-real-voice".toByteArray(StandardCharsets.UTF_8))
    }

    private fun respond(exchange: HttpExchange, status: Int, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    @Test fun multipartRequestContainsModelAndRecordingOnlyAfterExplicitCall() {
        val calls = AtomicInteger(0)
        withServer({ exchange ->
            calls.incrementAndGet()
            assertEquals("POST", exchange.requestMethod)
            assertEquals("Bearer test-byo-key", exchange.requestHeaders.getFirst("Authorization"))
            val type = exchange.requestHeaders.getFirst("Content-Type")
            assertTrue(type.startsWith("multipart/form-data; boundary=ciryva-"))
            val content = exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            assertTrue(content.contains("name=\"model\"\r\n\r\ngpt-transcribe"))
            assertTrue(content.contains("filename=\"dictation.m4a\""))
            assertTrue(content.contains("name=\"response_format\"\r\n\r\njson"))
            assertTrue(content.contains("illustrative-aac-bytes-no-real-voice"))
            respond(exchange, 200, """{"text":"Hi, CiRYVA. Café!"}""")
        }) { transport ->
            val file = recording()
            try {
                assertEquals(0, calls.get())
                val result = transport.transcribe(file, "test-byo-key")
                assertEquals("Hi, CiRYVA. Café!",
                    (assertInstanceOf(AudioTranscriptionResult.Success::class.java, result)).text)
                assertEquals(1, calls.get())
            } finally { file.delete() }
        }
    }

    @Test fun errorsNeverExposeProviderBodyOrMakeAutomaticRetries() {
        val calls = AtomicInteger(0)
        withServer({ exchange ->
            calls.incrementAndGet()
            exchange.requestBody.readAllBytes()
            respond(exchange, 401, """{"error":{"message":"secret-content-must-not-surface"}}""")
        }) { transport ->
            val file = recording()
            try {
                val result = transport.transcribe(file, "wrong-key")
                val failure = assertInstanceOf(AudioTranscriptionResult.Failure::class.java, result)
                assertTrue(failure.message.contains("key rejected"))
                assertTrue(!failure.message.contains("secret-content"))
                assertEquals(1, calls.get())
            } finally { file.delete() }
        }
    }

    @Test fun cancellationOrInvalidInputNeverStartsUpload() {
        val file = recording()
        try {
            val cancel = AudioTranscriptionCancellation()
            cancel.cancel()
            val transport = AudioTranscriptionHttpTransport {
                error("No connection should be opened")
            }
            assertEquals(AudioTranscriptionResult.Cancelled,
                transport.transcribe(file, "test-key", cancel))
            assertThrows(IllegalArgumentException::class.java) {
                transport.transcribe(file, "unsafe\nkey")
            }
            val empty = File.createTempFile("empty-", ".m4a")
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    transport.transcribe(empty, "test-key")
                }
            } finally { empty.delete() }
        } finally { file.delete() }
    }

    @Test fun malformedJsonIsReportedRatherThanInsertedIntoChat() {
        withServer({ exchange ->
            exchange.requestBody.readAllBytes()
            respond(exchange, 200, "not-a-json-response")
        }) { transport ->
            val file = recording()
            try {
                val result = transport.transcribe(file, "test-key")
                assertInstanceOf(AudioTranscriptionResult.Failure::class.java, result)
            } finally { file.delete() }
        }
    }
}
