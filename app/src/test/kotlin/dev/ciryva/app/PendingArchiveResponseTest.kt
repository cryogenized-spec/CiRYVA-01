package dev.ciryva.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Process-local retention protects an unsaved answer across Activity rotation. */
class PendingArchiveResponseTest {
    @Test fun replacementActivityMustAwaitColdRecoveryCommitBeforeDiskAccess() {
        assertTrue(PendingArchiveResponse.claimColdProcessStartup())
        val started = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val work = executor.submit<Boolean> {
                started.countDown()
                PendingArchiveResponse.awaitColdProcessRecovery()
                true
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertFalse("Replacement disk work must wait for recovery", work.isDone)
            PendingArchiveResponse.finishColdProcessRecovery()
            assertTrue(work.get(2, TimeUnit.SECONDS))
            // Release can safely be called more than once.
            PendingArchiveResponse.finishColdProcessRecovery()
        } finally {
            PendingArchiveResponse.finishColdProcessRecovery()
            executor.shutdownNow()
        }
    }

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
