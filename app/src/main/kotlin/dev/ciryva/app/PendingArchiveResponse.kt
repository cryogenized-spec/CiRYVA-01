package dev.ciryva.app

/**
 * Process-local emergency owner for an uncommitted final answer. It outlives
 * Activity rotation while the same app process is alive. No API credentials
 * or secret storage handles are retained here.
 *
 * This is a last resort for a database write that fails (e.g. full disk), not
 * a durable substitute for SQLite. A killed process cannot recover an answer
 * that the operating system was unable to write to any persistent storage.
 */
internal data class RetainedArchiveResponse(
    val threadId: String,
    val turnId: String,
    val snapshot: LiveChatSnapshot,
)

internal object PendingArchiveResponse {
    private val unsaved = LinkedHashMap<String, RetainedArchiveResponse>()

    // Only the first LiveChatActivity in a newly created app process performs
    // stale-pending recovery. Ordinary Activity rotation must not mark the
    // outgoing Activity's still-settling turn as an old interrupted request.
    private var didClaimProcessStartup = false

    @Synchronized fun claimColdProcessStartup(): Boolean {
        if (didClaimProcessStartup) return false
        didClaimProcessStartup = true
        return true
    }

    @Synchronized fun retain(value: RetainedArchiveResponse) {
        unsaved[value.turnId] = value
    }

    @Synchronized fun latest(): RetainedArchiveResponse? =
        unsaved.values.lastOrNull()

    @Synchronized fun clear(turnId: String) {
        unsaved.remove(turnId)
    }
}
