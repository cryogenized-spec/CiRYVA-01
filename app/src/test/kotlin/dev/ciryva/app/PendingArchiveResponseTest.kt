package dev.ciryva.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Process-local retention protects an unsaved answer across Activity rotation. */
class PendingArchiveResponseTest {
    @Test fun unsavedFinalRemainsAvailableToReplacementActivityUntilCommitted() {
        val entry = RetainedArchiveResponse(
            threadId = "test-thread",
            turnId = "test-turn",
            snapshot = LiveChatSnapshot(
                userPrompt = "Remember this response",
                assistantText = "Actual provider content kept for retry",
                busy = false,
                status = "Completed with provider confirmation.",
            ),
        )
        try {
            PendingArchiveResponse.retain(entry)
            assertEquals(entry, PendingArchiveResponse.latest())
            assertEquals("Actual provider content kept for retry",
                PendingArchiveResponse.latest()?.snapshot?.assistantText)
        } finally {
            PendingArchiveResponse.clear(entry.turnId)
        }
        assertNull(PendingArchiveResponse.latest())
    }
}
