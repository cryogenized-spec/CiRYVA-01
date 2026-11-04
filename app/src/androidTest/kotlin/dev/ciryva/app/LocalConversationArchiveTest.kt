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

    @Test fun pinnedThreadsSurviveReopenWithoutRewritingHistoryOrRecency() {
        var pinnedId = ""
        var ordinaryId = ""
        var pinnedUpdatedAt = 0L
        LocalConversationArchive(context, dbName).use { archive ->
            pinnedId = archive.createThread("Keep on top").id
            ordinaryId = archive.createThread("Other work").id
            archive.beginTurn(pinnedId, "Saved user original", "test-model")
            // beginTurn may legitimately advance recency; pinning must not.
            pinnedUpdatedAt = archive.listThreads().first { it.id == pinnedId }.updatedAtMs
            assertTrue(archive.setThreadPinned(pinnedId, true))
            assertFalse(archive.setThreadPinned("does-not-exist", true))
            assertEquals(pinnedId, archive.listThreads().first().id)
            assertTrue(archive.listThreads().first().isPinned)
            assertEquals(pinnedUpdatedAt,
                archive.listThreads().first { it.id == pinnedId }.updatedAtMs)
        }
        LocalConversationArchive(context, dbName).use { archive ->
            assertEquals(pinnedId, archive.listThreads().first().id)
            assertTrue(archive.listThreads().first().isPinned)
            assertEquals("Saved user original", archive.listTurns(pinnedId).single().userText)
            assertTrue(archive.setThreadPinned(pinnedId, false))
            assertFalse(archive.listThreads().first { it.id == pinnedId }.isPinned)
            assertEquals(2, archive.listThreads().size)
            assertTrue(archive.listThreads().any { it.id == ordinaryId })
        }
    }

    @Test fun repeatedIdenticalPromptsRetainDistinctDurableSubmissionIds() {
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        var thread = ""
        LocalConversationArchive(context, dbName).use { archive ->
            thread = archive.createThread().id
            archive.beginTurn(thread, "Continue", "gpt-test", submissionId = first)
            archive.beginTurn(thread, "Continue", "gpt-test", submissionId = second)
        }
        LocalConversationArchive(context, dbName).use { archive ->
            val turns = archive.listTurns(thread)
            assertEquals(2, turns.size)
            assertEquals(listOf("Continue", "Continue"), turns.map { it.userText })
            assertEquals(listOf(first, second), turns.map { it.submissionId })
        }
    }

    @Test fun existingV2ArchiveMigratesWithoutLosingOriginalMessages() {
        // Construct a pre-upgrade v2 file, then open it through the v3 helper.
        // No real user archive is modified by this isolated test database.
        context.openOrCreateDatabase(dbName, 0, null).use { old ->
            old.execSQL("CREATE TABLE threads (id TEXT PRIMARY KEY NOT NULL, " +
                "title TEXT NOT NULL, created_ms INTEGER NOT NULL, " +
                "updated_ms INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE turns (" +
                "seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL UNIQUE, " +
                "thread_id TEXT NOT NULL REFERENCES threads(id) ON DELETE CASCADE, " +
                "user_text TEXT NOT NULL, assistant_text TEXT NOT NULL DEFAULT '', " +
                "reasoning_summary TEXT NOT NULL DEFAULT '', model_id TEXT NOT NULL, " +
                "state TEXT NOT NULL, created_ms INTEGER NOT NULL, " +
                "status_detail TEXT NOT NULL DEFAULT '', input_tokens INTEGER, " +
                "output_tokens INTEGER, total_tokens INTEGER, attachment_id TEXT, " +
                "attachment_name TEXT, attachment_mime TEXT, attachment_bytes INTEGER)")
            old.execSQL("INSERT INTO threads(id,title,created_ms,updated_ms) " +
                "VALUES ('legacy','Old conversation',100,100)")
            old.execSQL("INSERT INTO turns(id,thread_id,user_text,assistant_text," +
                "model_id,state,created_ms) " +
                "VALUES ('old-turn','legacy','Original question','Original answer'," +
                "'legacy-model','completed',100)")
            old.version = 2
        }
        val id = UUID.randomUUID().toString()
        LocalConversationArchive(context, dbName).use { upgraded ->
            val old = upgraded.listTurns("legacy").single()
            assertEquals("Original question", old.userText)
            assertEquals("Original answer", old.assistantText)
            assertEquals(null, old.submissionId)
            assertFalse(upgraded.listThreads().single().isPinned)
            assertTrue(upgraded.setThreadPinned("legacy", true))
            upgraded.beginTurn("legacy", "Continue", "new-model", submissionId = id)
        }
        LocalConversationArchive(context, dbName).use { reopened ->
            val turns = reopened.listTurns("legacy")
            assertEquals(2, turns.size)
            assertEquals("Original question", turns.first().userText)
            assertEquals(id, turns.last().submissionId)
            assertTrue(reopened.listThreads().single().isPinned)
        }
    }

    @Test fun fullLengthEssayPromptPersistsAcrossReopenWithoutTruncation() {
        val essay = "E".repeat(32_768)
        var thread = ""
        LocalConversationArchive(context, dbName).use { archive ->
            thread = archive.createThread().id
            archive.beginTurn(thread, essay, "test-model")
        }
        LocalConversationArchive(context, dbName).use { archive ->
            assertEquals(essay, archive.listTurns(thread).single().userText)
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
