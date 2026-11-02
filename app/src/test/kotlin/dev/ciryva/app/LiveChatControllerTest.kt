package dev.ciryva.app

import dev.ciryva.core.stream.HttpStreamResult
import dev.ciryva.core.stream.ResponsesStreamEvent
import dev.ciryva.core.stream.TextResponseRequest
import dev.ciryva.core.stream.TokenUsage
import dev.ciryva.core.turn.ResponseTurnOutcome
import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChatControllerTest {
    private val request = TextResponseRequest(
        model = "gpt-4o-mini-2024-07-18", prompt = "hello", maxOutputTokens = 128,
    )

    @Test fun constructionNeverFetchesKeyOrStartsARequest() {
        var keys = 0
        var sends = 0
        val controller = LiveChatController(
            client = ChatStreamClient { _, _, _, _ -> sends++; HttpStreamResult.UnexpectedEnd },
            credentialProvider = { keys++; "synthetic-key-for-tests" },
            onUpdate = {},
            executor = Executor { it.run() },
        )
        assertEquals(0, keys)
        assertEquals(0, sends)
        assertEquals(false, controller.snapshot().busy)
        controller.close()
    }

    @Test fun providerConfirmedTerminalCompletesAndPreservesReceipt() {
        val states = mutableListOf<LiveChatSnapshot>()
        val controller = LiveChatController(
            client = ChatStreamClient { _, key, _, event ->
                assertEquals("synthetic-key-for-tests", key)
                event(ResponsesStreamEvent.TextDelta("Hel", "msg1", 0, 0))
                event(ResponsesStreamEvent.TextDelta("lo", "msg1", 0, 0))
                event(ResponsesStreamEvent.Other("response.reasoning.delta"))
                HttpStreamResult.Completed("response1", TokenUsage(2, 3, 5), null)
            },
            credentialProvider = { "synthetic-key-for-tests" },
            onUpdate = { states += it },
            executor = Executor { it.run() },
        )
        assertTrue(controller.send(request))
        assertEquals("Hello", controller.snapshot().assistantText)
        assertEquals(ResponseTurnOutcome.COMPLETED, controller.snapshot().outcome)
        assertEquals(TokenUsage(2, 3, 5), controller.snapshot().usage)
        assertTrue(states.any { it.busy && it.assistantText == "Hello" })
        assertTrue(states.last().status.contains("provider confirmation"))
        controller.close()
    }

    @Test fun aMissingCredentialNeverOpensTransport() {
        var calls = 0
        val controller = LiveChatController(
            client = ChatStreamClient { _, _, _, _ -> calls++; HttpStreamResult.UnexpectedEnd },
            credentialProvider = { null },
            onUpdate = {},
            executor = Executor { it.run() },
        )
        assertTrue(controller.send(request))
        assertEquals(0, calls)
        assertFalse(controller.snapshot().busy)
        assertTrue(controller.snapshot().status.contains("unavailable"))
        controller.close()
    }

    @Test fun cancelRejectsLateTextAndBlocksOverlappingRequests() {
        val tasks = ArrayDeque<Runnable>()
        var providerCalls = 0
        val controller = LiveChatController(
            client = ChatStreamClient { _, _, cancellation, event ->
                providerCalls++
                event(ResponsesStreamEvent.TextDelta("late", "msg", 0, 0))
                if (cancellation.isCancelled) HttpStreamResult.Cancelled
                else HttpStreamResult.UnexpectedEnd
            },
            credentialProvider = { "synthetic-key-for-tests" },
            onUpdate = {},
            executor = Executor { tasks.addLast(it) },
        )
        assertTrue(controller.send(request))
        assertFalse(controller.send(request))
        assertTrue(controller.cancel())
        assertFalse(controller.send(request)) // flight still draining
        tasks.removeFirst().run()
        assertEquals(0, providerCalls) // pre-cancelled before network
        assertEquals("", controller.snapshot().assistantText)
        assertFalse(controller.snapshot().busy)
        assertEquals(ResponseTurnOutcome.CANCELLED, controller.snapshot().outcome)
        controller.close()
    }

    @Test fun cancelBetweenTransportCompletionAndSettlementNeverShowsSuccess() {
        lateinit var controller: LiveChatController
        controller = LiveChatController(
            client = ChatStreamClient { _, _, _, event ->
                event(ResponsesStreamEvent.TextDelta("delivered", "msg", 0, 0))
                // Deterministic race: cancel when provider has text but before
                // its terminal HttpStreamResult.Completed reaches finish().
                assertTrue(controller.cancel())
                HttpStreamResult.Completed("provider-id", TokenUsage(2, 4, 6), null)
            },
            credentialProvider = { "synthetic-key-for-tests" },
            onUpdate = {},
            executor = Executor { it.run() },
        )
        assertTrue(controller.send(request))
        assertEquals("delivered", controller.snapshot().assistantText)
        assertEquals(ResponseTurnOutcome.CANCELLED, controller.snapshot().outcome)
        assertNull(controller.snapshot().usage)
        assertFalse(controller.snapshot().status.contains("Completed"))
        assertTrue(controller.snapshot().status.contains("Cancelled"))
        controller.close()
    }

    @Test fun errorsAreSanitizedAndCloseStopsQueuedWork() {
        val controller = LiveChatController(
            client = ChatStreamClient { _, _, _, _ ->
                HttpStreamResult.Rejected(429, "request-fake")
            },
            credentialProvider = { "synthetic-key-for-tests" },
            onUpdate = {},
            executor = Executor { it.run() },
        )
        controller.send(request)
        assertTrue(controller.snapshot().status.contains("429"))
        assertNull(controller.snapshot().usage)
        controller.close()
        assertFalse(controller.send(request))
    }

    @Test fun incompleteStreamDoesNotReportSuccessOrManufactureUsage() {
        val controller = LiveChatController(
            client = ChatStreamClient { _, _, _, event ->
                event(ResponsesStreamEvent.TextDelta("partial", "msg", 0, 0))
                HttpStreamResult.UnexpectedEnd
            },
            credentialProvider = { "synthetic-key-for-tests" },
            onUpdate = {},
            executor = Executor { it.run() },
        )
        controller.send(request)
        assertEquals("partial", controller.snapshot().assistantText)
        assertEquals(ResponseTurnOutcome.UNEXPECTED_END, controller.snapshot().outcome)
        assertNull(controller.snapshot().usage)
        controller.close()
    }
}
