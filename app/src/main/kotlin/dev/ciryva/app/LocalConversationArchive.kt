package dev.ciryva.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.ciryva.core.stream.PriorChatMessage
import dev.ciryva.core.stream.TokenUsage
import java.util.UUID

/**
 * Device-private archive of the original user and assistant text. No provider
 * calls, remote database, credentials or external dependencies.
 *
 * This first P05 schema deliberately stores every turn, including interrupted
 * and incomplete ones, while *only completed pairs* can become API context.
 * Android app backup is disabled in the manifest. SQLite itself is not an
 * encryption-at-rest guarantee: protect this device and its screen lock.
 */
data class LocalThread(
    val id: String,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

data class LocalTurn(
    val id: String,
    val threadId: String,
    val userText: String,
    val assistantText: String,
    val reasoningSummary: String,
    val modelId: String,
    val state: String,
    val createdAtMs: Long,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val totalTokens: Int?,
    val statusDetail: String = "",
)

class LocalConversationArchive(
    context: Context,
    databaseName: String = "ciryva_conversations.db",
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, 1) {
    companion object {
        const val MAX_VISIBLE_TURNS = 100
        const val MAX_LIST_THREADS = 100
        const val MAX_CONTEXT_PAIRS = 6
        const val MAX_CONTEXT_CHARACTERS = 12_000
        private const val MAX_STORED_PROMPT = 4_000
        private const val MAX_STORED_ANSWER = 131_072
        private const val MAX_STORED_SUMMARY = 8_192
        private val TERMINAL = setOf(
            "completed", "cancelled", "incomplete", "failed", "interrupted",
        )
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE threads (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                created_ms INTEGER NOT NULL,
                updated_ms INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE turns (
                seq INTEGER PRIMARY KEY AUTOINCREMENT,
                id TEXT NOT NULL UNIQUE,
                thread_id TEXT NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
                user_text TEXT NOT NULL,
                assistant_text TEXT NOT NULL DEFAULT '',
                reasoning_summary TEXT NOT NULL DEFAULT '',
                model_id TEXT NOT NULL,
                state TEXT NOT NULL CHECK(state IN (
                    'pending', 'completed', 'cancelled', 'incomplete', 'failed', 'interrupted')),
                created_ms INTEGER NOT NULL,
                status_detail TEXT NOT NULL DEFAULT '',
                input_tokens INTEGER,
                output_tokens INTEGER,
                total_tokens INTEGER
            )""",
        )
        db.execSQL("CREATE INDEX turns_thread_order ON turns(thread_id, seq)")
        db.execSQL("CREATE INDEX threads_recent ON threads(updated_ms DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never drop users' local archive as a shortcut during future schema upgrades.
        throw IllegalStateException("Archive migration required: $oldVersion -> $newVersion")
    }

    @Synchronized
    fun createThread(title: String = "New conversation"): LocalThread {
        val now = System.currentTimeMillis()
        val safeTitle = title.trim().replace(Regex("\\s+"), " ").take(72)
            .ifBlank { "New conversation" }
        val thread = LocalThread(UUID.randomUUID().toString(), safeTitle, now, now)
        val values = ContentValues().apply {
            put("id", thread.id)
            put("title", thread.title)
            put("created_ms", now)
            put("updated_ms", now)
        }
        check(writableDatabase.insertOrThrow("threads", null, values) > 0)
        return thread
    }

    @Synchronized
    fun listThreads(): List<LocalThread> = readableDatabase.query(
        "threads", arrayOf("id", "title", "created_ms", "updated_ms"),
        null, null, null, null, "updated_ms DESC, created_ms DESC",
        MAX_LIST_THREADS.toString(),
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                LocalThread(
                    c.getString(0), c.getString(1), c.getLong(2), c.getLong(3)
                )
            )
        }
    }

    @Synchronized
    fun listTurns(threadId: String): List<LocalTurn> {
        // Read the most recent page without deleting older data. Pages are a
        // planned follow-up; the database retains every record.
        return loadTurns(threadId, MAX_VISIBLE_TURNS, null)
    }

    private fun loadTurns(
        threadId: String,
        limit: Int,
        stateFilter: String?,
    ): List<LocalTurn> {
        val where = if (stateFilter == null) "thread_id=?" else "thread_id=? AND state=?"
        val args = if (stateFilter == null) arrayOf(threadId) else arrayOf(threadId, stateFilter)
        return readableDatabase.query(
            "turns",
            arrayOf("id", "thread_id", "user_text", "assistant_text",
                "reasoning_summary", "model_id", "state", "created_ms",
                "input_tokens", "output_tokens", "total_tokens", "status_detail"),
            where, args, null, null, "seq DESC", limit.toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(turnFromCursor(cursor))
            }.reversed()
        }
    }

    private fun turnFromCursor(c: Cursor) = LocalTurn(
        id = c.getString(0),
        threadId = c.getString(1),
        userText = c.getString(2),
        assistantText = c.getString(3),
        reasoningSummary = c.getString(4),
        modelId = c.getString(5),
        state = c.getString(6),
        createdAtMs = c.getLong(7),
        inputTokens = if (c.isNull(8)) null else c.getInt(8),
        outputTokens = if (c.isNull(9)) null else c.getInt(9),
        totalTokens = if (c.isNull(10)) null else c.getInt(10),
        statusDetail = c.getString(11),
    )

    @Synchronized
    fun beginTurn(threadId: String, prompt: String, modelId: String): String {
        require(prompt.isNotBlank() && prompt.length <= MAX_STORED_PROMPT)
        require(modelId.length in 1..128)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            check(db.rawQuery(
                "SELECT id FROM threads WHERE id=?", arrayOf(threadId)
            ).use { it.moveToFirst() }) { "Thread no longer exists" }
            val values = ContentValues().apply {
                put("id", id)
                put("thread_id", threadId)
                put("user_text", prompt)
                put("model_id", modelId)
                put("state", "pending")
                put("created_ms", now)
            }
            db.insertOrThrow("turns", null, values)
            val firstPromptTitle = prompt.trim().replace(Regex("\\s+"), " ")
                .take(72)
            db.execSQL(
                "UPDATE threads SET updated_ms=?, " +
                    "title=CASE WHEN title='New conversation' THEN ? ELSE title END " +
                    "WHERE id=?",
                arrayOf(now, firstPromptTitle, threadId),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return id
    }

    /**
     * One terminal transition only. Late async callbacks cannot transform
     * interrupted or previously settled messages into a false success.
     */
    @Synchronized
    fun settleTurn(
        turnId: String,
        state: String,
        answer: String,
        summary: String = "",
        usage: TokenUsage? = null,
        statusDetail: String = "",
    ): Boolean {
        require(state in TERMINAL)
        require(answer.length <= MAX_STORED_ANSWER)
        require(summary.length <= MAX_STORED_SUMMARY)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("state", state)
                put("assistant_text", answer)
                put("reasoning_summary", summary)
                put("status_detail", statusDetail.take(300))
                // Incomplete Responses may carry a genuine provider usage receipt.
                if (usage != null && (state == "completed" || state == "incomplete")) {
                    put("input_tokens", usage.inputTokens)
                    put("output_tokens", usage.outputTokens)
                    put("total_tokens", usage.totalTokens)
                }
            }
            val count = db.update("turns", values, "id=? AND state='pending'",
                arrayOf(turnId))
            if (count == 1) {
                db.execSQL(
                    "UPDATE threads SET updated_ms=? WHERE id=(" +
                        "SELECT thread_id FROM turns WHERE id=?)",
                    arrayOf(System.currentTimeMillis(), turnId),
                )
            }
            db.setTransactionSuccessful()
            return count == 1
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun discardUnstarted(turnId: String): Boolean =
        writableDatabase.delete("turns", "id=? AND state='pending'",
            arrayOf(turnId)) == 1

    @Synchronized
    fun deleteThread(threadId: String): Boolean =
        writableDatabase.delete("threads", "id=?", arrayOf(threadId)) == 1

    /**
     * Only complete, locally stored user/assistant pairs qualify as context.
     * Six most recent eligible pairs at most; skip an oversized pair rather
     * than silently truncating its meaning or sending a partial assistant turn.
     */
    @Synchronized
    fun boundedContext(threadId: String): List<PriorChatMessage> {
        val finished = loadTurns(threadId, 40, "completed")
            .asReversed()
        var budget = MAX_CONTEXT_CHARACTERS
        val retained = mutableListOf<LocalTurn>()
        for (turn in finished) {
            if (retained.size >= MAX_CONTEXT_PAIRS) break
            val needed = turn.userText.length + turn.assistantText.length
            if (turn.assistantText.isBlank() || turn.userText.isBlank() ||
                turn.assistantText.length > 8_192 || needed > budget) continue
            retained += turn
            budget -= needed
        }
        return retained.asReversed().flatMap { turn ->
            listOf(
                PriorChatMessage("user", turn.userText),
                PriorChatMessage("assistant", turn.assistantText),
            )
        }
    }
}
