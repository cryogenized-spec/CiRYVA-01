package dev.ciryva.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ciryva.core.stream.TokenUsage
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-backed SQLite tests, with no credentials or provider network calls.
 * Closing and reopening the database is intentional: an in-memory fake cannot
 * establish that real conversations survive an Android app restart.
 */
@RunWith(AndroidJUnit4::class)
class LocalConversationArchiveTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dbName: String

    @Before fun setUp() {
        dbName = "ciryva-p05-test-" + UUID.randomUUID() + ".db"
    }

    @After fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test fun completedConversationSurvivesDatabaseReopenWithOriginalTextAndUsage() {
        var conversationId = ""
        var turnId = ""
        LocalConversationArchive(context, dbName).use { archive ->
            conversationId = archive.createThread().id
            turnId = archive.beginTurn(conversationId, "A small detail about tea.", "gpt-test")
            assertTrue(archive.settleTurn(
                turnId, "completed", "You mentioned kukicha tea.",
                summary = "A genuine provider-authored sample summary.",
                usage = TokenUsage(9, 7, 16),
            ))
            // New Activity after rotation can repeat the same final write.
            assertTrue(archive.settleTurn(
                turnId, "completed", "You mentioned kukicha tea.",
                summary = "A genuine provider-authored sample summary.",
                usage = TokenUsage(9, 7, 16),
            ))
        }
        LocalConversationArchive(context, dbName).use { archive ->
            val threads = archive.listThreads()
            assertEquals(1, threads.size)
            assertEquals(conversationId, threads.single().id)
            assertEquals("A small detail about tea.", threads.single().title)
            val saved = archive.listTurns(conversationId).single()
            assertEquals(turnId, saved.id)
            assertEquals("A small detail about tea.", saved.userText)
            assertEquals("You mentioned kukicha tea.", saved.assistantText)
            assertEquals("A genuine provider-authored sample summary.", saved.reasoningSummary)
            assertEquals("gpt-test", saved.modelId)
            assertEquals("completed", saved.state)
            assertEquals(16, saved.totalTokens)
            assertEquals(2, archive.boundedContext(conversationId).size)
        }
    }

    @Test fun interruptedOrFailedResponsesNeverBecomeContinuationContext() {
        LocalConversationArchive(context, dbName).use { archive ->
            val id = archive.createThread("Context exclusion").id
            val completed = archive.beginTurn(id, "My name is Alex.", "test-model")
            assertTrue(archive.settleTurn(completed, "completed", "Hello Alex."))
            val interrupted = archive.beginTurn(id, "Hidden work draft", "test-model")
            assertTrue(archive.settleTurn(interrupted, "interrupted", "partial"))
            val failed = archive.beginTurn(id, "Failed request", "test-model")
            assertTrue(archive.settleTurn(failed, "failed", "error"))
            val pending = archive.beginTurn(id, "Ongoing request", "test-model")
            val context = archive.boundedContext(id)
            assertEquals(listOf("My name is Alex.", "Hello Alex."),
                context.map { it.content })
            assertEquals(listOf("user", "assistant"), context.map { it.role })
            assertEquals(4, archive.listTurns(id).size)
            // A late success cannot rewrite a settled interrupted turn.
            assertFalse(archive.settleTurn(interrupted, "completed", "incorrectly claimed"))
            assertEquals("interrupted",
                archive.listTurns(id).first { it.id == interrupted }.state)
            assertTrue(archive.discardUnstarted(pending))
        }
    }

    @Test fun contextCapsAtSixRecentCompletePairsAndSkipsOversizedAnswers() {
        LocalConversationArchive(context, dbName).use { archive ->
            val id = archive.createThread("Context budget").id
            repeat(8) { index ->
                val turn = archive.beginTurn(id, "question $index", "test-model")
                archive.settleTurn(turn, "completed", "reply $index")
            }
            val context = archive.boundedContext(id)
            assertEquals(12, context.size)
            assertEquals("question 2", context.first().content)
            assertEquals("reply 7", context.last().content)
            val longTurn = archive.beginTurn(id, "oversized", "test-model")
            archive.settleTurn(longTurn, "completed", "x".repeat(15_000))
            val limited = archive.boundedContext(id)
            assertEquals(12, limited.size)
            assertFalse(limited.any { it.content.contains("oversized") })
            assertTrue(limited.sumOf { it.content.length } <=
                LocalConversationArchive.MAX_CONTEXT_CHARACTERS)
        }
    }

    @Test fun coldProcessRecoveryMarksOnlyOrphanedPendingTurnsInterrupted() {
        var threadId = ""
        var pendingId = ""
        var completeId = ""
        LocalConversationArchive(context, dbName).use { archive ->
            threadId = archive.createThread("Cold recovery").id
            pendingId = archive.beginTurn(
                threadId, "Never completed because Android exited", "m"
            )
            completeId = archive.beginTurn(
                threadId, "Already finished and billed", "m"
            )
            assertTrue(archive.settleTurn(
                completeId, "completed", "Saved answer",
                usage = TokenUsage(6, 3, 9),
            ))
        }
        LocalConversationArchive(context, dbName).use { archive ->
            assertEquals(1, archive.recoverStrandedPending())
            // Repeating recovery is idempotent and never corrupts prior data.
            assertEquals(0, archive.recoverStrandedPending())
            val turns = archive.listTurns(threadId)
            val orphan = turns.first { it.id == pendingId }
            val done = turns.first { it.id == completeId }
            assertEquals("interrupted", orphan.state)
            assertEquals("Never completed because Android exited", orphan.userText)
            assertTrue(orphan.statusDetail.contains("usage may be unknown"))
            assertEquals(null, orphan.totalTokens)
            assertEquals("completed", done.state)
            assertEquals(9, done.totalTokens)
            assertEquals(listOf("Already finished and billed", "Saved answer"),
                archive.boundedContext(threadId).map { it.content })
        }
    }

    @Test fun onlyGenuineLocalReceiptsContributeToUsageTotals() {
        LocalConversationArchive(context, dbName).use { archive ->
            val first = archive.createThread("Recorded usage").id
            val second = archive.createThread("Usage deleted later").id
            val completed = archive.beginTurn(first, "Count input and output", "m")
            assertTrue(archive.settleTurn(
                completed, "completed", "Answer", usage = TokenUsage(14, 7, 21),
            ))
            val incomplete = archive.beginTurn(first, "Unfinished but billed", "m")
            assertTrue(archive.settleTurn(
                incomplete, "incomplete", "Partial", usage = TokenUsage(10, 4, 14),
            ))
            val unknown = archive.beginTurn(first, "No provider receipt", "m")
            assertTrue(archive.settleTurn(unknown, "cancelled", ""))
            val other = archive.beginTurn(second, "Temporary receipt", "m")
            assertTrue(archive.settleTurn(
                other, "completed", "Later removed", usage = TokenUsage(5, 2, 7),
            ))
            val before = archive.usageTotals()
            assertEquals(3L, before.recordedTurns)
            assertEquals(29L, before.inputTokens)
            assertEquals(13L, before.outputTokens)
            assertEquals(42L, before.totalTokens)
            assertTrue(archive.deleteThread(second))
            val after = archive.usageTotals()
            assertEquals(2L, after.recordedTurns)
            assertEquals(24L, after.inputTokens)
            assertEquals(11L, after.outputTokens)
            assertEquals(35L, after.totalTokens)
            assertEquals(2, archive.boundedContext(first).size)
        }
    }

    @Test fun deletionRequiresAnActualSelectedThreadAndCascadesToTurns() {
        LocalConversationArchive(context, dbName).use { archive ->
            val keep = archive.createThread("Retain me").id
            val removed = archive.createThread("Remove me").id
            val pending = archive.beginTurn(removed, "Private local note", "test-model")
            archive.settleTurn(pending, "completed", "Stored answer")
            assertFalse(archive.deleteThread("not-a-real-thread"))
            assertTrue(archive.deleteThread(removed))
            assertEquals(emptyList<LocalTurn>(), archive.listTurns(removed))
            assertEquals(listOf(keep), archive.listThreads().map { it.id })
            assertEquals(emptyList<dev.ciryva.core.stream.PriorChatMessage>(),
                archive.boundedContext(removed))
        }
    }
}
