package dev.ciryva.core.stream

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SseEventDecoderTest {
    @Test fun `joins event chunks across boundaries`() {
        val parser = SseEventDecoder()
        assertTrue(parser.feed("event: response.output_text.de").isEmpty())
        assertTrue(parser.feed("lta\r\ndata: {\\"delta\\":\\"Hel").isEmpty())
        val event = parser.feed("lo\\"}\r\n\r\n").single()
        assertEquals("response.output_text.delta", event.type)
        assertEquals("{\\"delta\\":\\"Hello\\"}", event.data)
    }

    @Test fun `multiline data comments and event ids`() {
        val parser = SseEventDecoder()
        val result = parser.feed(": keepalive\ndata: first\ndata: second\nid: 17\n\n")
        assertEquals(listOf(SseEvent("message", "first\nsecond", "17")), result)
    }

    @Test fun `finish returns incomplete final event once`() {
        val parser = SseEventDecoder()
        parser.feed("event: done\ndata: [DONE]")
        assertEquals(SseEvent("done", "[DONE]", null), parser.finish().single())
        assertTrue(parser.finish().isEmpty())
    }

    @Test fun `rejects unbounded lines and events`() {
        assertThrows(IllegalArgumentException::class.java) {
            SseEventDecoder(maxLineChars = 5).feed("data: too much")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SseEventDecoder(maxEventChars = 3).feed("data: larger\n")
        }
    }
}
