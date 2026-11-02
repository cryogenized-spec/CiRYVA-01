package dev.ciryva.core.stream

import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Utf8SseEventDecoderTest {
    private val payload =
        "event: response.output_text.delta\ndata: {\"delta\":\"café 🌍 日本語\"}\n\n"

    @Test fun `all individual byte boundaries preserve multibyte text`() {
        val original = payload.toByteArray(StandardCharsets.UTF_8)
        val expected = SseEvent("response.output_text.delta", "{\"delta\":\"café 🌍 日本語\"}", null)

        for (cut in 0..original.size) {
            val decoder = Utf8SseEventDecoder()
            val received = decoder.feed(original.copyOfRange(0, cut)) +
                decoder.feed(original.copyOfRange(cut, original.size)) +
                decoder.finish()
            assertEquals(listOf(expected), received, "split at byte ${cut}")
        }
    }

    @Test fun `one byte at a time joins a four byte Unicode scalar`() {
        val decoder = Utf8SseEventDecoder()
        val events = payload.toByteArray(StandardCharsets.UTF_8)
            .flatMap { byte -> decoder.feed(byteArrayOf(byte)) } + decoder.finish()
        assertEquals(1, events.size)
        assertTrue(events.single().data.contains("🌍"))
    }

    @Test fun `BOM and CRLF split between reads is recognized`() {
        val decoder = Utf8SseEventDecoder()
        val bytes = "\uFEFF: ping\r\nevent: ready\r\ndata: yes\r\n\r\n"
            .toByteArray(StandardCharsets.UTF_8)
        val events = bytes.flatMap { decoder.feed(byteArrayOf(it)) } + decoder.finish()
        assertEquals(listOf(SseEvent("ready", "yes", null)), events)
    }

    @Test fun `data lines and event limits are still enforced`() {
        val decoder = Utf8SseEventDecoder(maxLineChars = 20, maxEventChars = 3)
        assertThrows(IllegalArgumentException::class.java) {
            decoder.feed("data: over\n\n".toByteArray(StandardCharsets.UTF_8))
        }
    }

    @Test fun `malformed input fails rather than silently emitting replacement symbols`() {
        val decoder = Utf8SseEventDecoder()
        assertThrows(MalformedInputException::class.java) {
            decoder.feed(byteArrayOf(0xC3.toByte(), 0x28))
        }
        assertThrows(IllegalStateException::class.java) {
            decoder.feed("data: test\n\n".toByteArray(StandardCharsets.UTF_8))
        }
    }

    @Test fun `truncated multibyte value fails on clean EOF`() {
        val decoder = Utf8SseEventDecoder()
        assertTrue(decoder.feed(byteArrayOf(0xF0.toByte(), 0x9F.toByte())).isEmpty())
        assertThrows(MalformedInputException::class.java) { decoder.finish() }
    }

    @Test fun `EOF flushes pending valid event once and rejects reuse`() {
        val decoder = Utf8SseEventDecoder()
        assertTrue(decoder.feed("data: final".toByteArray(StandardCharsets.UTF_8)).isEmpty())
        assertEquals(listOf(SseEvent("message", "final", null)), decoder.finish())
        assertThrows(IllegalStateException::class.java) { decoder.finish() }
        assertThrows(IllegalStateException::class.java) { decoder.feed(byteArrayOf(65)) }
    }

    @Test fun `large input is incrementally buffered without losing event sequence`() {
        val decoder = Utf8SseEventDecoder(maxLineChars = 16, maxEventChars = 16)
        val body = buildString {
            repeat(3500) { append("data: ok\n\n") }
        }
        val events = decoder.feed(body.toByteArray(StandardCharsets.UTF_8)) + decoder.finish()
        assertEquals(3500, events.size)
        assertFalse(events.any { it.data != "ok" })
    }
}
