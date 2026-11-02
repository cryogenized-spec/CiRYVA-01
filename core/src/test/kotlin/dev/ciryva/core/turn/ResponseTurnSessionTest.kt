package dev.ciryva.core.turn

import dev.ciryva.core.stream.HttpStreamResult
import dev.ciryva.core.stream.ResponsesStreamEvent
import dev.ciryva.core.stream.StreamCancellation
import dev.ciryva.core.stream.TokenUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResponseTurnSessionTest {
    private fun delta(text: String) =
        ResponsesStreamEvent.TextDelta(text, "message_1", 0, 0)

    @Test fun `only confirmed terminal result can complete a streamed turn`() {
        val session = ResponseTurnSession()
        val token = StreamCancellation()
        session.begin("t1", token)
        assertTrue(session.onEvent("t1", token, delta("Hello ")))
        assertTrue(session.onEvent("t1", token, delta("🌍")))
        assertTrue(session.onEvent("t1", token, ResponsesStreamEvent.Completed("r1", TokenUsage(7, 3, 10))))
        assertEquals(TurnStatus.STREAMING, session.snapshot().turn.status)
        assertNull(session.snapshot().usage)
        assertTrue(session.settle("t1", token, HttpStreamResult.Completed("r1", TokenUsage(7, 3, 10), "req1")))
        val snapshot = session.snapshot()
        assertEquals(TurnStatus.COMPLETED, snapshot.turn.status)
        assertEquals("Hello 🌍", snapshot.turn.text)
        assertEquals(TokenUsage(7, 3, 10), snapshot.usage)
        assertEquals("r1", snapshot.providerResponseId)
        assertEquals(ResponseTurnOutcome.COMPLETED, snapshot.outcome)
    }

    @Test fun `incomplete response keeps partial text and reported billable usage`() {
        val s = ResponseTurnSession()
        val token = StreamCancellation()
        s.begin("id", token)
        s.onEvent("id", token, delta("partial"))
        assertTrue(s.settle("id", token, HttpStreamResult.Incomplete("response1", "max_output_tokens", TokenUsage(5, 90, 95))))
        assertEquals(TurnStatus.FAILED, s.snapshot().turn.status)
        assertEquals(ResponseTurnOutcome.INCOMPLETE, s.snapshot().outcome)
        assertEquals("partial", s.snapshot().turn.text)
        assertEquals(TokenUsage(5, 90, 95), s.snapshot().usage)
        assertEquals("response1", s.snapshot().providerResponseId)
    }

    @Test fun `cancel preserves partial text and refuses late event and success`() {
        val token = StreamCancellation()
        val s = ResponseTurnSession()
        s.begin("id", token)
        s.onEvent("id", token, delta("kept"))
        assertTrue(s.cancel("id", token))
        assertTrue(token.isCancelled)
        assertFalse(s.onEvent("id", token, delta("bad")))
        assertFalse(s.settle("id", token, HttpStreamResult.Completed("response", null, null)))
        assertEquals("kept", s.snapshot().turn.text)
        assertNull(s.snapshot().usage)
        assertEquals(ResponseTurnOutcome.CANCELLED, s.snapshot().outcome)
    }

    @Test fun `newer turn cancels old token and blocks stale completion`() {
        val s = ResponseTurnSession()
        val old = StreamCancellation()
        s.begin("old", old)
        s.onEvent("old", old, delta("old"))
        val fresh = StreamCancellation()
        s.begin("fresh", fresh)
        assertTrue(old.isCancelled)
        assertFalse(fresh.isCancelled)
        assertFalse(s.onEvent("old", old, delta("stale")))
        assertFalse(s.settle("old", old, HttpStreamResult.Completed("r1", null, null)))
        assertTrue(s.onEvent("fresh", fresh, delta("new")))
        assertTrue(s.settle("fresh", fresh, HttpStreamResult.Completed("r2", null, "req2")))
        assertEquals("new", s.snapshot().turn.text)
        assertEquals("r2", s.snapshot().providerResponseId)
    }

    @Test fun `unknown tool and reasoning events never become displayed text`() {
        val s = ResponseTurnSession()
        val token = StreamCancellation()
        s.begin("id", token)
        assertTrue(s.onEvent("id", token, ResponsesStreamEvent.Other("response.reasoning_summary_text.delta")))
        assertTrue(s.onEvent("id", token, ResponsesStreamEvent.Other("response.output_item.added")))
        assertTrue(s.onEvent("id", token, ResponsesStreamEvent.Error("provider", "non-user-facing failure")))
        assertEquals("", s.snapshot().turn.text)
    }

    @Test fun `duplicate terminal result cannot rewrite usage or text`() {
        val s = ResponseTurnSession()
        val token = StreamCancellation()
        s.begin("id", token)
        assertTrue(s.settle("id", token, HttpStreamResult.Completed("r", TokenUsage(1, 2, 3), null)))
        assertFalse(s.settle("id", token, HttpStreamResult.Incomplete("r", "limit", TokenUsage(10, 20, 30))))
        assertFalse(s.onEvent("id", token, delta("too late")))
        assertEquals(TokenUsage(1, 2, 3), s.snapshot().usage)
        assertEquals(ResponseTurnOutcome.COMPLETED, s.snapshot().outcome)
    }

    @Test fun `sanitized HTTP rejection does not fabricate a usage receipt`() {
        val s = ResponseTurnSession()
        val token = StreamCancellation()
        s.begin("id", token)
        assertTrue(s.settle("id", token, HttpStreamResult.Rejected(429, "req")))
        assertEquals(TurnStatus.FAILED, s.snapshot().turn.status)
        assertEquals("http_429", s.snapshot().turn.error)
        assertEquals(ResponseTurnOutcome.REJECTED, s.snapshot().outcome)
        assertNull(s.snapshot().usage)
    }

    @Test fun `session rejects reused or pre-cancelled tokens and oversized output`() {
        val s = ResponseTurnSession(maxCharacters = 3)
        val token = StreamCancellation()
        s.begin("id", token)
        assertThrows(IllegalArgumentException::class.java) { s.begin("again", token) }
        assertTrue(s.onEvent("id", token, delta("abc")))
        assertThrows(IllegalArgumentException::class.java) { s.onEvent("id", token, delta("d")) }
        assertEquals("abc", s.snapshot().turn.text)
        val preCancelled = StreamCancellation()
        preCancelled.cancel()
        assertThrows(IllegalArgumentException::class.java) { s.begin("new", preCancelled) }
    }

    @Test fun `late callbacks with reused ID cannot modify a newer generation`() {
        val s = ResponseTurnSession()
        val old = StreamCancellation()
        s.begin("same-id", old)
        assertTrue(s.onEvent("same-id", old, delta("old")))
        val fresh = StreamCancellation()
        s.begin("same-id", fresh)
        assertTrue(old.isCancelled)
        assertFalse(s.onEvent("same-id", old, delta("late")))
        assertFalse(s.settle("same-id", old, HttpStreamResult.Cancelled))
        assertFalse(s.cancel("same-id", old))
        assertTrue(s.onEvent("same-id", fresh, delta("correct")))
        assertTrue(s.settle("same-id", fresh, HttpStreamResult.Completed("resp", null, null)))
        assertEquals("correct", s.snapshot().turn.text)
        assertEquals(TurnStatus.COMPLETED, s.snapshot().turn.status)
    }
}
