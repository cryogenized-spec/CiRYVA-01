package dev.ciryva.core.stream

import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ResponsesEventParserTest {
    private val parser = ResponsesEventParser()

    @Test fun `realistic text delta is decoded without treating it as a complete response`() {
        val event = SseEvent(
            "response.output_text.delta",
            """{"type":"response.output_text.delta","item_id":"msg_123","output_index":0,"content_index":0,"delta":"Hello 🌍","sequence_number":2}""",
            null,
        )
        assertEquals(
            ResponsesStreamEvent.TextDelta("Hello 🌍", "msg_123", 0, 0),
            parser.parse(event),
        )
    }

    @Test fun `provider usage is sourced only from completed receipt`() {
        val event = SseEvent(
            "response.completed",
            """{"type":"response.completed","response":{"id":"resp_123","status":"completed","usage":{"input_tokens":15,"output_tokens":7,"total_tokens":22}}}""",
            null,
        )
        assertEquals(
            ResponsesStreamEvent.Completed("resp_123", TokenUsage(15, 7, 22)),
            parser.parse(event),
        )
    }

    @Test fun `completion without usage keeps unknown rather than fabricating zero`() {
        val e = SseEvent("response.completed",
            """{"type":"response.completed","response":{"id":"resp_123","status":"completed","usage":null}}""", null)
        assertEquals(ResponsesStreamEvent.Completed("resp_123", null), parser.parse(e))
    }

    @Test fun `failed and incomplete are never treated as completed`() {
        val failed = SseEvent("response.failed",
            """{"type":"response.failed","response":{"id":"r1","status":"failed","error":{"code":"server_error","message":"Unavailable"}}}""",null)
        val incomplete = SseEvent("response.incomplete",
            """{"type":"response.incomplete","response":{"id":"r2","status":"incomplete","incomplete_details":{"reason":"max_output_tokens"}}}""",null)
        assertEquals(ResponsesStreamEvent.Failed("r1", "server_error", "Unavailable"), parser.parse(failed))
        assertEquals(ResponsesStreamEvent.Incomplete("r2", "max_output_tokens"), parser.parse(incomplete))
    }

    @Test fun `transport error event stays distinct from provider failure`() {
        val event = SseEvent("error", """{"type":"error","code":"rate_limit","message":"Try later"}""", null)
        assertEquals(ResponsesStreamEvent.Error("rate_limit", "Try later"), parser.parse(event))
    }

    @Test fun `unrecognized tool or reasoning events are not displayed as assistant text`() {
        val event = SseEvent("response.output_item.added",
            """{"type":"response.output_item.added","item":{"type":"function_call","arguments":"secret"}}""",null)
        assertEquals(ResponsesStreamEvent.Other("response.output_item.added"), parser.parse(event))
    }

    @Test fun `mismatched SSE and JSON event types are rejected`() {
        val event = SseEvent("response.completed",
            """{"type":"response.output_text.delta","delta":"fake","item_id":"id","output_index":0,"content_index":0}""",null)
        assertThrows(IllegalArgumentException::class.java) { parser.parse(event) }
    }

    @Test fun `forged terminal status does not create a success receipt`() {
        val event = SseEvent("response.completed",
            """{"type":"response.completed","response":{"id":"r","status":"in_progress","usage":null}}""",null)
        assertThrows(IllegalArgumentException::class.java) { parser.parse(event) }
    }

    @Test fun `negative or nonnumeric usage values are rejected`() {
        val event = SseEvent("response.completed",
            """{"type":"response.completed","response":{"id":"r","status":"completed","usage":{"input_tokens":-2,"output_tokens":3,"total_tokens":1}}}""",null)
        assertThrows(IllegalArgumentException::class.java) { parser.parse(event) }
    }

    @Test fun `missing delta and negative output index cannot enter displayed text`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(SseEvent("response.output_text.delta",
                """{"type":"response.output_text.delta","item_id":"id","output_index":0,"content_index":0}""",null))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(SseEvent("response.output_text.delta",
                """{"type":"response.output_text.delta","delta":"x","item_id":"id","output_index":-1,"content_index":0}""",null))
        }
    }

    @Test fun `oversize JSON and corrupt JSON fail without success event`() {
        assertThrows(IllegalArgumentException::class.java) {
            ResponsesEventParser(maxJsonChars = 12).parse(
                SseEvent("message", """{"type":"response.completed"}""",null),
            )
        }
        assertThrows(SerializationException::class.java) {
            parser.parse(SseEvent("message", """{"type": "response.completed" """,null))
        }
    }

    @Test fun `schema valid empty delta is a no-op not a parse error`() {
        val event = SseEvent(
            "response.output_text.delta",
            """{"type":"response.output_text.delta","item_id":"msg_1","output_index":0,"content_index":0,"delta":""}""",
            null,
        )
        assertEquals(ResponsesStreamEvent.TextDelta("", "msg_1", 0, 0), parser.parse(event))
    }

    @Test fun `empty response IDs still fail validation`() {
        val event = SseEvent(
            "response.completed",
            """{"type":"response.completed","response":{"id":"","status":"completed","usage":null}}""",
            null,
        )
        assertThrows(IllegalArgumentException::class.java) { parser.parse(event) }
    }

    @Test fun `quoted numeric indexes are not JSON integers`() {
        for (field in listOf("output_index", "content_index")) {
            val event = SseEvent(
                "response.output_text.delta",
                """{"type":"response.output_text.delta","item_id":"m","delta":"hi","output_index":0,"content_index":0}"""
                    .replace("\"${field}\":0", "\"${field}\":\"0\""),
                null,
            )
            assertThrows(IllegalArgumentException::class.java, field) { parser.parse(event) }
        }
    }

    @Test fun `quoted numeric token usage cannot become a completed receipt`() {
        for (field in listOf("input_tokens", "output_tokens", "total_tokens")) {
            val event = SseEvent(
                "response.completed",
                """{"type":"response.completed","response":{"id":"r","status":"completed","usage":{"input_tokens":15,"output_tokens":7,"total_tokens":22}}}"""
                    .replace("\"${field}\":", "\"${field}\":\"")
                    .replace(Regex("\"${field}\":\"(\\d+)"), "\"${field}\":\"$1\""),
                null,
            )
            assertThrows(IllegalArgumentException::class.java, field) { parser.parse(event) }
        }
    }
}
