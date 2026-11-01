package dev.ciryva.core.turn

/**
 * A small single-generation arbiter. Late events from a superseded, cancelled or
 * failed request cannot change the current text or its terminal state.
 *
 * Thread-safe for a future Android networking/UI split; no Android dependency.
 */
enum class TurnStatus { IDLE, STREAMING, COMPLETED, CANCELLED, FAILED }

data class TurnSnapshot(
    val requestId: String?,
    val status: TurnStatus,
    val text: String,
    val error: String? = null,
)

class TurnCoordinator(private val maxCharacters: Int = 200_000) {
    init { require(maxCharacters > 0) }

    private var currentId: String? = null
    private var status: TurnStatus = TurnStatus.IDLE
    private val text = StringBuilder()
    private var error: String? = null

    @Synchronized fun start(requestId: String): TurnSnapshot {
        require(requestId.isNotBlank())
        currentId = requestId
        status = TurnStatus.STREAMING
        text.setLength(0)
        error = null
        return snapshot()
    }

    @Synchronized fun append(requestId: String, delta: String): Boolean {
        if (currentId != requestId || status != TurnStatus.STREAMING) return false
        require(text.length + delta.length <= maxCharacters) { "Turn output limit exceeded" }
        text.append(delta)
        return true
    }

    @Synchronized fun complete(requestId: String): Boolean =
        transition(requestId, TurnStatus.COMPLETED)

    @Synchronized fun cancel(requestId: String): Boolean =
        transition(requestId, TurnStatus.CANCELLED)

    @Synchronized fun fail(requestId: String, reason: String): Boolean {
        if (!transition(requestId, TurnStatus.FAILED)) return false
        error = reason.take(500)
        return true
    }

    private fun transition(requestId: String, destination: TurnStatus): Boolean {
        if (currentId != requestId || status != TurnStatus.STREAMING) return false
        status = destination
        return true
    }

    @Synchronized fun snapshot(): TurnSnapshot =
        TurnSnapshot(currentId, status, text.toString(), error)
}
