package dev.ciryva.core.turn

import dev.ciryva.core.stream.HttpStreamResult
import dev.ciryva.core.stream.ResponsesStreamEvent
import dev.ciryva.core.stream.StreamCancellation
import dev.ciryva.core.stream.TokenUsage

/**
 * Credential-free bridge between P03 streaming events and P04 turn state.
 *
 * The caller must run network work off Android's main thread. The same logical
 * request ID must be supplied to onEvent/settle/cancel. StreamCancellation is
 * created by the caller and attached to the corresponding HTTP request.
 *
 * Network callbacks cannot mutate a superseded or cancelled turn. The
 * provider's raw completed event is NOT a UI-success state: only the final
 * HttpStreamResult.Completed can settle a turn successfully.
 */
data class ResponseTurnSnapshot(
    val turn: TurnSnapshot,
    val outcome: ResponseTurnOutcome?,
    val usage: TokenUsage?,
    val providerResponseId: String?,
)

/** A separate incomplete outcome preserves partial text and billable usage. */
enum class ResponseTurnOutcome {
    COMPLETED, CANCELLED, INCOMPLETE, REJECTED, PROVIDER_FAILED,
    PROVIDER_ERROR, PROTOCOL_ERROR, NETWORK_ERROR, UNEXPECTED_END,
}

class ResponseTurnSession(maxCharacters: Int = 131_072) {
    private val turns = TurnCoordinator(maxCharacters)
    private var cancellation: StreamCancellation? = null
    private var outcome: ResponseTurnOutcome? = null
    private var usage: TokenUsage? = null
    private var providerResponseId: String? = null

    /**
     * Starting a new turn cancels the old transport without blocking on its
     * socket read; callers must not reuse a cancellation token across turns.
     */
    @Synchronized fun begin(id: String, token: StreamCancellation): ResponseTurnSnapshot {
        require(id.isNotBlank())
        require(token !== cancellation) { "Do not reuse a stream cancellation token" }
        require(!token.isCancelled) { "Cannot begin a pre-cancelled request" }
        cancellation?.cancel()
        turns.start(id)
        cancellation = token
        outcome = null
        usage = null
        providerResponseId = null
        return snapshot()
    }

    /**
     * Only assistant text changes the visible text. Reasoning, tool and
     * lifecycle envelopes are never presented as fabricated assistant output.
     */
    @Synchronized fun onEvent(id: String, event: ResponsesStreamEvent): Boolean {
        if (turns.snapshot().requestId != id || turns.snapshot().status != TurnStatus.STREAMING) {
            return false
        }
        if (cancellation?.isCancelled == true) return false
        if (event is ResponsesStreamEvent.TextDelta) {
            turns.append(id, event.delta)
        }
        return true
    }

    /**
     * Terminal state is selected from the actual transport result, not SSE
     * frame presence. Late or duplicate completions cannot replace a newer
     * request or alter a settled result.
     */
    @Synchronized fun settle(id: String, result: HttpStreamResult): Boolean {
        val prior = turns.snapshot()
        if (prior.requestId != id || prior.status != TurnStatus.STREAMING) return false
        if (cancellation?.isCancelled == true || result is HttpStreamResult.Cancelled) {
            return cancel(id)
        }

        outcome = when (result) {
            is HttpStreamResult.Completed -> ResponseTurnOutcome.COMPLETED
            is HttpStreamResult.Incomplete -> ResponseTurnOutcome.INCOMPLETE
            is HttpStreamResult.Rejected -> ResponseTurnOutcome.REJECTED
            is HttpStreamResult.Failed -> ResponseTurnOutcome.PROVIDER_FAILED
            is HttpStreamResult.ProviderError -> ResponseTurnOutcome.PROVIDER_ERROR
            is HttpStreamResult.ProtocolError -> ResponseTurnOutcome.PROTOCOL_ERROR
            HttpStreamResult.NetworkError -> ResponseTurnOutcome.NETWORK_ERROR
            HttpStreamResult.UnexpectedEnd -> ResponseTurnOutcome.UNEXPECTED_END
            HttpStreamResult.Cancelled -> ResponseTurnOutcome.CANCELLED
        }
        when (result) {
            is HttpStreamResult.Completed -> {
                usage = result.usage
                providerResponseId = result.responseId
                turns.complete(id)
            }
            is HttpStreamResult.Incomplete -> {
                usage = result.usage
                providerResponseId = result.responseId
                turns.fail(id, "incomplete")
            }
            is HttpStreamResult.Failed -> {
                providerResponseId = result.responseId
                turns.fail(id, "provider_failed")
            }
            is HttpStreamResult.Rejected -> turns.fail(id, "http_${result.httpStatus}")
            is HttpStreamResult.ProviderError -> turns.fail(id, "provider_error")
            is HttpStreamResult.ProtocolError -> turns.fail(id, "protocol_error")
            HttpStreamResult.UnexpectedEnd -> turns.fail(id, "unexpected_end")
            HttpStreamResult.NetworkError -> turns.fail(id, "network_error")
            HttpStreamResult.Cancelled -> turns.cancel(id)
        }
        cancellation = null
        return true
    }

    /** Explicit cancellation never erases already rendered partial text. */
    @Synchronized fun cancel(id: String): Boolean {
        if (turns.snapshot().requestId != id || turns.snapshot().status != TurnStatus.STREAMING) return false
        cancellation?.cancel()
        cancellation = null
        outcome = ResponseTurnOutcome.CANCELLED
        usage = null // Unknown unless a provider-confirmed receipt already settled.
        providerResponseId = null
        return turns.cancel(id)
    }

    @Synchronized fun snapshot(): ResponseTurnSnapshot =
        ResponseTurnSnapshot(turns.snapshot(), outcome, usage, providerResponseId)
}
