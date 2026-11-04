package dev.ciryva.app

import dev.ciryva.core.stream.HttpStreamResult
import dev.ciryva.core.stream.ResponsesStreamEvent
import dev.ciryva.core.stream.StreamCancellation
import dev.ciryva.core.stream.TextResponseRequest
import dev.ciryva.core.stream.TokenUsage
import dev.ciryva.core.turn.ResponseTurnOutcome
import dev.ciryva.core.turn.ResponseTurnSession
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** An injectable boundary: tests never need network access or real credentials. */
fun interface ChatStreamClient {
    fun stream(
        request: TextResponseRequest,
        key: String,
        cancellation: StreamCancellation,
        onEvent: (ResponsesStreamEvent) -> Unit,
    ): HttpStreamResult
}

/** A bounded display record, never an inference about private chain-of-thought. */
data class LiveActivityStep(
    val label: String,
    val source: String,
    val elapsedMs: Long,
)

data class LiveChatSnapshot(
    val userPrompt: String = "",
    val assistantText: String = "",
    /** Provider-authored summary only; does not expose private raw reasoning. */
    val reasoningSummary: String = "",
    val modelId: String? = null,
    val busy: Boolean = false,
    val status: String = "Ready. Completed local turns may be included in bounded context.",
    val outcome: ResponseTurnOutcome? = null,
    val usage: TokenUsage? = null,
    // Monotonic process-local timestamp, only meaningful for the active turn.
    val activityStartedNanos: Long? = null,
    val activityDurationMs: Long? = null,
    val activitySteps: List<LiveActivityStep> = emptyList(),
)

/**
 * One explicit, bounded, single-flight BYOK request at a time.
 * Never prefetches a key or starts inference until send() is called.
 * Cancellation keeps Send blocked until the previous worker exits so there
 * cannot be overlapping chargeable generations from this controller.
 */
class LiveChatController(
    private val client: ChatStreamClient,
    private val credentialProvider: () -> String?,
    private val onUpdate: (LiveChatSnapshot) -> Unit,
    private val executor: Executor = Executors.newSingleThreadExecutor(),
    private val monotonicNanos: () -> Long = System::nanoTime,
) : AutoCloseable {
    private data class Active(val id: String, val token: StreamCancellation)
    private val session = ResponseTurnSession()
    private var active: Active? = null
    private var sequence = 0L
    private var closed = false
    private var state = LiveChatSnapshot()

    @Synchronized fun snapshot(): LiveChatSnapshot = state

    private fun elapsedMs(): Long {
        val started = state.activityStartedNanos ?: return 0
        return ((monotonicNanos() - started).coerceAtLeast(0L) / 1_000_000L)
    }

    /** Deduplicate consecutive phase updates and bound timeline growth. */
    private fun markActivity(label: String, source: String) {
        if (state.activityStartedNanos == null) return
        if (state.activitySteps.lastOrNull()?.let {
                it.label == label && it.source == source
            } == true) return
        state = state.copy(activitySteps =
            (state.activitySteps + LiveActivityStep(label, source, elapsedMs()))
                .takeLast(12))
    }

    /** View-only reset when selecting another local conversation. It cannot
     * reset or supersede an active billable request. The archived state lives
     * in SQLite, separate from this request-scoped streaming snapshot. */
    @Synchronized fun clearView(): Boolean {
        if (closed || active != null) return false
        state = LiveChatSnapshot()
        onUpdate(state)
        return true
    }

    /** Caller must explicitly confirm one chargeable request in the UI. */
    @Synchronized fun send(request: TextResponseRequest): Boolean {
        if (closed || active != null) return false
        val token = StreamCancellation()
        val id = "turn-${++sequence}"
        session.begin(id, token)
        active = Active(id, token)
        state = LiveChatSnapshot(
            userPrompt = request.prompt,
            modelId = request.model,
            busy = true,
            status = "Connecting to OpenAI using your own API key…",
            activityStartedNanos = monotonicNanos(),
        )
        markActivity("Request queued", "Local app")
        onUpdate(state)
        try {
            executor.execute {
                val key = try { credentialProvider() } catch (_: Exception) { null }
                if (key.isNullOrBlank()) {
                    finishMissingKey(id, token)
                    return@execute
                }
                val outcome = try {
                    if (token.isCancelled) HttpStreamResult.Cancelled
                    else {
                        synchronized(this) {
                            if (!closed && active?.id == id && active?.token === token) {
                                markActivity("Opening provider connection", "Local app")
                                onUpdate(state)
                            }
                        }
                        if (token.isCancelled) HttpStreamResult.Cancelled
                        else client.stream(request, key, token) {
                            event -> accept(id, token, event)
                        }
                    }
                } catch (_: Exception) {
                    // Never surface exception messages: provider/OS messages can contain secrets.
                    if (token.isCancelled) HttpStreamResult.Cancelled else HttpStreamResult.NetworkError
                }
                finish(id, token, outcome)
            }
        } catch (_: RuntimeException) {
            session.cancel(id, token)
            active = null
            state = state.copy(busy = false, status = "Unable to start network worker.")
            onUpdate(state)
            return false
        }
        return true
    }

    @Synchronized private fun accept(
        id: String, token: StreamCancellation, event: ResponsesStreamEvent,
    ) {
        if (closed || active?.id != id || active?.token !== token) return
        if (!session.onEvent(id, token, event)) return
        val current = session.snapshot()
        // Only provider-authored summaries appear; unknown reasoning envelopes
        // never become assistant text or fabricated reasoning content.
        when (event) {
            is ResponsesStreamEvent.ReasoningSummaryDelta -> {
                if (event.delta.isEmpty()) return
                state = state.copy(
                    reasoningSummary = (state.reasoningSummary + event.delta).take(8_192),
                    status = "Receiving provider reasoning summary…",
                )
                markActivity("Reasoning summary received", "OpenAI")
            }
            is ResponsesStreamEvent.TextDelta -> {
                if (event.delta.isEmpty()) return
                state = state.copy(
                    assistantText = current.turn.text,
                    status = "Receiving response…",
                )
                markActivity("Writing answer", "OpenAI")
            }
            is ResponsesStreamEvent.Other -> {
                // A provider lifecycle envelope is evidence of provider
                // activity; a proposed function/tool call is NOT execution.
                if (event.type == "response.created" ||
                    event.type == "response.in_progress") {
                    markActivity("Provider acknowledged request", "OpenAI")
                } else return
            }
            else -> return
        }
        onUpdate(state)
    }

    @Synchronized private fun finishMissingKey(id: String, token: StreamCancellation) {
        if (closed || active?.id != id || active?.token !== token) return
        if (token.isCancelled) session.cancel(id, token)
        else session.settle(id, token, HttpStreamResult.Rejected(401, null))
        active = null
        val snapshot = session.snapshot()
        state = state.copy(
            busy = false,
            outcome = snapshot.outcome,
            status = if (token.isCancelled) "Cancelled; provider usage may be unknown."
                else "Stored API key unavailable. Open key settings to replace it.",
        )
        markActivity(if (token.isCancelled) "Cancelled" else "Credential unavailable",
            "Local app")
        state = state.copy(activityDurationMs = elapsedMs())
        onUpdate(state)
    }

    @Synchronized private fun finish(
        id: String, token: StreamCancellation, result: HttpStreamResult,
    ) {
        if (closed || active?.id != id || active?.token !== token) return
        session.settle(id, token, result)
        val final = session.snapshot()
        active = null
        state = state.copy(
            assistantText = final.turn.text,
            busy = false,
            outcome = final.outcome,
            usage = final.usage,
            // The controller may be cancelled after the provider returns but
            // before this thread settles. Session outcome is authoritative.
            status = if (final.outcome == ResponseTurnOutcome.CANCELLED)
                "Cancelled; provider usage may be unknown."
            else when (result) {
                is HttpStreamResult.Completed -> "Completed with provider confirmation."
                is HttpStreamResult.Incomplete -> "Incomplete response; partial text may be retained."
                is HttpStreamResult.Rejected -> when (result.httpStatus) {
                    400 -> "OpenAI rejected the request (HTTP 400). Check model and settings."
                    401 -> "OpenAI rejected the API key (HTTP 401)."
                    403 -> "Access denied (HTTP 403). Check your model/project permissions."
                    429 -> "Rate limited (HTTP 429). Check your usage allowance."
                    else -> "OpenAI returned HTTP ${result.httpStatus}."
                }
                is HttpStreamResult.Failed -> "OpenAI reported a failed response."
                is HttpStreamResult.ProviderError -> "OpenAI reported a provider error."
                is HttpStreamResult.ProtocolError -> "Unable to validate the response stream."
                HttpStreamResult.NetworkError -> "Connection failed; completion and charge are unknown."
                HttpStreamResult.UnexpectedEnd -> "Response stream ended without confirmation."
                HttpStreamResult.Cancelled -> "Cancelled; provider usage may be unknown."
            },
        )
        markActivity(when (final.outcome) {
            ResponseTurnOutcome.COMPLETED -> "Completed"
            ResponseTurnOutcome.CANCELLED -> "Cancelled"
            ResponseTurnOutcome.INCOMPLETE -> "Incomplete"
            else -> "Failed or interrupted"
        }, "Local app")
        state = state.copy(activityDurationMs = elapsedMs())
        onUpdate(state)
    }

    /** Does not assume disconnect guarantees non-billing. */
    @Synchronized fun cancel(): Boolean {
        if (closed) return false
        val request = active ?: return false
        if (request.token.isCancelled) return false
        session.cancel(request.id, request.token)
        state = state.copy(status = "Cancelling… provider usage may be unknown.")
        markActivity("Cancellation requested", "Local app")
        onUpdate(state)
        return true
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        active?.let { session.cancel(it.id, it.token) }
        active = null
        (executor as? ExecutorService)?.shutdownNow()
        // No UI updates after lifecycle teardown; a late worker cannot re-open chat.
    }
}
