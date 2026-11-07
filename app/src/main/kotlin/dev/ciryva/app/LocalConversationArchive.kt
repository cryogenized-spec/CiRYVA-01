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
/**
 * Preserve the previous drawer's Unicode-aware case-insensitive matching,
 * including accented letters. Punctuation is literal rather than a wildcard.
 */
internal fun archiveTextMatches(candidate: String, query: String): Boolean =
    query.isNotBlank() && candidate.contains(query.trim(), ignoreCase = true)

data class LocalThread(
    val id: String,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val isPinned: Boolean = false,
)

/** Only provider-confirmed token receipts. Not a bill or price estimate. */
data class LocalUsageTotals(
    val recordedTurns: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
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
    val media: LocalStoredMedia? = null,
    // Stable private identifier for this user-initiated Send. No provider data.
    val submissionId: String? = null,
    /** Every private original in explicit selection order; media is the legacy first. */
    val attachments: List<LocalStoredMedia> = listOfNotNull(media),
)

class LocalConversationArchive(
    context: Context,
    databaseName: String = "ciryva_conversations.db",
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, 5) {
    private val mediaStore = LocalAttachmentStore(
        java.io.File(context.applicationContext.filesDir, "ciryva_media")
    )
    companion object {
        const val MAX_VISIBLE_TURNS = 100
        const val MAX_LIST_THREADS = 100
        const val MAX_SEARCH_LENGTH = 128
        const val MAX_SEARCH_RESULTS = 100
        const val MAX_ATTACHMENTS_PER_TURN = 12
        const val MAX_CONTEXT_PAIRS = 6
        const val MAX_CONTEXT_CHARACTERS = 12_000
        // Match the validated outgoing TextResponseRequest prompt ceiling so that a
        // locally accepted essay is never rejected by the archive journal.
        private const val MAX_STORED_PROMPT = 32_768
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

    private fun createTurnMediaTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE turn_media (
                turn_id TEXT NOT NULL REFERENCES turns(id) ON DELETE CASCADE,
                position INTEGER NOT NULL CHECK(position >= 0),
                attachment_id TEXT NOT NULL UNIQUE,
                attachment_name TEXT NOT NULL,
                attachment_mime TEXT NOT NULL,
                attachment_bytes INTEGER NOT NULL CHECK(attachment_bytes > 0),
                PRIMARY KEY (turn_id, position)
            )""",
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE threads (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                created_ms INTEGER NOT NULL,
                updated_ms INTEGER NOT NULL,
                is_pinned INTEGER NOT NULL DEFAULT 0 CHECK(is_pinned IN (0,1))
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
                total_tokens INTEGER,
                attachment_id TEXT,
                attachment_name TEXT,
                attachment_mime TEXT,
                attachment_bytes INTEGER,
                client_submission_id TEXT
            )""",
        )
        db.execSQL("CREATE INDEX turns_thread_order ON turns(thread_id, seq)")
        db.execSQL("CREATE UNIQUE INDEX turns_submission_id ON turns(client_submission_id)")
        db.execSQL("CREATE INDEX threads_recent ON threads(updated_ms DESC)")
        db.execSQL("CREATE INDEX threads_pinned_recent ON threads(is_pinned DESC, updated_ms DESC, created_ms DESC)")
        createTurnMediaTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Sequential non-destructive migrations. Existing archive text and
        // attachment metadata remain untouched, even on a v1 -> v5 upgrade.
        require(oldVersion in 1..4 && newVersion == 5) {
            "Archive migration required: $oldVersion -> $newVersion"
        }
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE turns ADD COLUMN attachment_id TEXT")
            db.execSQL("ALTER TABLE turns ADD COLUMN attachment_name TEXT")
            db.execSQL("ALTER TABLE turns ADD COLUMN attachment_mime TEXT")
            db.execSQL("ALTER TABLE turns ADD COLUMN attachment_bytes INTEGER")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE turns ADD COLUMN client_submission_id TEXT")
            db.execSQL("CREATE UNIQUE INDEX turns_submission_id ON turns(client_submission_id)")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE threads ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX threads_pinned_recent ON threads(is_pinned DESC, updated_ms DESC, created_ms DESC)")
        }
        if (oldVersion < 5) {
            createTurnMediaTable(db)
            // Backfill existing originals without changing any legacy turn
            // columns. Migration runs transactionally under SQLiteOpenHelper.
            db.execSQL(
                """INSERT INTO turn_media
                    (turn_id, position, attachment_id, attachment_name,
                     attachment_mime, attachment_bytes)
                    SELECT id, 0, attachment_id, attachment_name,
                           attachment_mime, attachment_bytes
                    FROM turns WHERE attachment_id IS NOT NULL"""
            )
        }
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

    /** Pin changes presentation ordering, never activity timestamps or message text. */
    @Synchronized
    fun setThreadPinned(threadId: String, pinned: Boolean): Boolean =
        writableDatabase.update(
            "threads", ContentValues().apply { put("is_pinned", if (pinned) 1 else 0) },
            "id=?", arrayOf(threadId),
        ) == 1

    private fun threadFromCursor(c: Cursor): LocalThread = LocalThread(
        c.getString(0), c.getString(1), c.getLong(2), c.getLong(3),
        c.getInt(4) != 0,
    )

    /**
     * Keep the current/new conversation visible even when the pinned portion
     * fills the bounded history list. This must not silently select another
     * thread after New conversation or after restoring an older active thread.
     * At most one selected row is appended outside the normal display cap.
     */
    @Synchronized
    fun listThreads(ensureVisibleThreadId: String? = null): List<LocalThread> {
        val columns = arrayOf("id", "title", "created_ms", "updated_ms", "is_pinned")
        val order = "is_pinned DESC, updated_ms DESC, created_ms DESC"
        val threads = readableDatabase.query(
            "threads", columns, null, null, null, null, order,
            MAX_LIST_THREADS.toString(),
        ).use { c ->
            buildList { while (c.moveToNext()) add(threadFromCursor(c)) }
        }
        if (ensureVisibleThreadId == null ||
            threads.any { it.id == ensureVisibleThreadId }) return threads
        val selected = readableDatabase.query(
            "threads", columns, "id=?", arrayOf(ensureVisibleThreadId),
            null, null, null, "1",
        ).use { c -> if (c.moveToFirst()) threadFromCursor(c) else null }
            ?: return threads
        return (threads + selected).sortedWith(
            compareByDescending<LocalThread> { it.isPinned }
                .thenByDescending { it.updatedAtMs }
                .thenByDescending { it.createdAtMs },
        )
    }

    /**
     * Device-private lexical search over all archived conversation titles and
     * original user/assistant text (not a model memory or context injection).
     *
     * Android SQLite's LIKE folds only ASCII case by default. Scan the text
     * columns using Kotlin's Unicode-aware ignoreCase semantics instead of
     * dropping previously searchable titles such as "École". Cursor iteration
     * keeps large archives off the Compose/UI thread and avoids loading every
     * message into a List. A future indexed search can optimize large archives.
     */
    @Synchronized
    fun searchThreads(query: String): List<LocalThread> {
        val literal = query.trim().take(MAX_SEARCH_LENGTH)
        if (literal.isEmpty()) return emptyList()
        val columns = arrayOf("id", "title", "created_ms", "updated_ms", "is_pinned")
        val orderedThreads = readableDatabase.query(
            "threads", columns, null, null, null, null,
            "is_pinned DESC, updated_ms DESC, created_ms DESC",
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(threadFromCursor(cursor)) }
        }
        val matchedIds = orderedThreads.asSequence()
            .filter { archiveTextMatches(it.title, literal) }
            .mapTo(mutableSetOf()) { it.id }
        readableDatabase.query(
            "turns", arrayOf("thread_id", "user_text", "assistant_text"),
            null, null, null, null, null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val threadId = cursor.getString(0)
                if (threadId !in matchedIds &&
                    (archiveTextMatches(cursor.getString(1), literal) ||
                        archiveTextMatches(cursor.getString(2), literal))) {
                    matchedIds.add(threadId)
                }
            }
        }
        return orderedThreads.asSequence()
            .filter { it.id in matchedIds }
            .take(MAX_SEARCH_RESULTS)
            .toList()
    }

    /**
     * Honest on-device totals for retained, provider-reported usage only.
     * Deleted turns are no longer counted. Missing provider receipts do not
     * produce made-up token numbers or monetary estimates.
     */
    @Synchronized
    fun usageTotals(): LocalUsageTotals = readableDatabase.rawQuery(
        "SELECT COUNT(*), COALESCE(SUM(input_tokens),0), " +
            "COALESCE(SUM(output_tokens),0), COALESCE(SUM(total_tokens),0) " +
            "FROM turns WHERE total_tokens IS NOT NULL AND " +
            "state IN ('completed','incomplete')",
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) LocalUsageTotals()
        else LocalUsageTotals(
            cursor.getLong(0), cursor.getLong(1),
            cursor.getLong(2), cursor.getLong(3),
        )
    }

    /** Snapshot the only private original IDs that must be kept after restart. */
    @Synchronized
    fun retainedMediaIds(): Set<String> = readableDatabase.rawQuery(
        "SELECT attachment_id FROM turn_media UNION " +
            "SELECT attachment_id FROM turns WHERE attachment_id IS NOT NULL",
        null,
    ).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
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
        val turns = readableDatabase.query(
            "turns",
            arrayOf("id", "thread_id", "user_text", "assistant_text",
                "reasoning_summary", "model_id", "state", "created_ms",
                "input_tokens", "output_tokens", "total_tokens", "status_detail",
                "attachment_id", "attachment_name", "attachment_mime", "attachment_bytes",
                "client_submission_id"),
            where, args, null, null, "seq DESC", limit.toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(turnFromCursor(cursor))
            }.reversed()
        }
        if (turns.isEmpty()) return turns
        // Load all media for this page in one query; never issue one SQLite
        // query per transcript row while opening a large conversation.
        val mediaByTurn = loadMediaForTurns(turns)
        return turns.map { turn ->
            turn.copy(attachments = mediaByTurn[turn.id] ?: listOfNotNull(turn.media))
        }
    }

    private fun loadMediaForTurns(turns: List<LocalTurn>): Map<String, List<LocalStoredMedia>> {
        val placeholders = turns.joinToString(",") { "?" }
        return readableDatabase.query(
            "turn_media",
            arrayOf("turn_id", "attachment_id", "attachment_name",
                "attachment_mime", "attachment_bytes"),
            "turn_id IN ($placeholders)", turns.map { it.id }.toTypedArray(),
            null, null, "turn_id, position",
        ).use { cursor ->
            val grouped = mutableMapOf<String, MutableList<LocalStoredMedia>>()
            while (cursor.moveToNext()) {
                grouped.getOrPut(cursor.getString(0)) { mutableListOf() }.add(
                    LocalStoredMedia(cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getInt(4))
                )
            }
            grouped
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
        media = if (c.isNull(12)) null else LocalStoredMedia(
            c.getString(12), c.getString(13), c.getString(14), c.getInt(15)
        ),
        submissionId = if (c.isNull(16)) null else c.getString(16),
    )

    @Synchronized
    fun beginTurn(threadId: String, prompt: String, modelId: String,
        media: LocalStoredMedia? = null, submissionId: String? = null,
        attachments: List<LocalStoredMedia> = listOfNotNull(media)): String {
        require(prompt.isNotBlank() && prompt.length <= MAX_STORED_PROMPT)
        require(modelId.length in 1..128)
        require(media == null || attachments.firstOrNull() == media) {
            "Legacy first attachment must match the ordered attachment list"
        }
        require(attachments.size <= MAX_ATTACHMENTS_PER_TURN &&
            attachments.map { it.id }.toSet().size == attachments.size &&
            attachments.sumOf { it.sizeBytes.toLong() } <=
                dev.ciryva.core.stream.ResponseAttachment.MAX_BYTES.toLong() &&
            attachments.all { it.sizeBytes in 1..
                dev.ciryva.core.stream.ResponseAttachment.MAX_BYTES &&
                it.mimeType in dev.ciryva.core.stream.ResponseAttachment.TYPES &&
                runCatching { UUID.fromString(it.id) }.isSuccess &&
                it.id.length == 36 &&
                it.filename.length in 1..100 &&
                it.filename.none { ch -> ch == '/' || ch == '\\' || ch.code < 32 }
            }) { "Invalid local attachment manifest" }
        require(submissionId == null ||
            (submissionId.length in 1..128 &&
                submissionId.all { it.isLetterOrDigit() || it == '-' })) {
            "Invalid local submission identifier"
        }
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
                if (submissionId != null) put("client_submission_id", submissionId)
                media?.let {
                    require(it.sizeBytes in 1..dev.ciryva.core.stream.ResponseAttachment.MAX_BYTES)
                    require(it.mimeType in dev.ciryva.core.stream.ResponseAttachment.TYPES)
                    put("attachment_id", it.id)
                    put("attachment_name", it.filename)
                    put("attachment_mime", it.mimeType)
                    put("attachment_bytes", it.sizeBytes)
                }
            }
            db.insertOrThrow("turns", null, values)
            attachments.forEachIndexed { position, item ->
                db.insertOrThrow("turn_media", null, ContentValues().apply {
                    put("turn_id", id)
                    put("position", position)
                    put("attachment_id", item.id)
                    put("attachment_name", item.filename)
                    put("attachment_mime", item.mimeType)
                    put("attachment_bytes", item.sizeBytes)
                })
            }
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
            // A rotated Activity may retry while the old instance's disk
            // worker has just completed the *same* terminal write. Treat
            // identical final content as idempotent; never overwrite a
            // conflicting already-terminal row.
            val alreadySaved = if (count == 0) {
                db.query(
                    "turns",
                    arrayOf("state", "assistant_text", "reasoning_summary", "status_detail"),
                    "id=?", arrayOf(turnId), null, null, null,
                ).use { cursor ->
                    cursor.moveToFirst() &&
                        cursor.getString(0) == state &&
                        cursor.getString(1) == answer &&
                        cursor.getString(2) == summary &&
                        cursor.getString(3) == statusDetail.take(300)
                }
            } else false
            db.setTransactionSuccessful()
            return count == 1 || alreadySaved
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Run once on cold process launch, before reading any thread. No Android
     * generation can survive process death: a leftover pending row is not a
     * live provider request. Keep its original prompt and mark unknown usage.
     * Never touch already-terminal responses or assume completion/billing.
     */
    @Synchronized
    fun recoverStrandedPending(): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val count = db.update(
                "turns",
                ContentValues().apply {
                    put("state", "interrupted")
                    put("status_detail",
                        "Interrupted when the app stopped; provider usage may be unknown.")
                },
                "state='pending'", null,
            )
            db.setTransactionSuccessful()
            return count
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun isTerminalSaved(turnId: String): Boolean = readableDatabase.query(
        "turns", arrayOf("state"), "id=?", arrayOf(turnId),
        null, null, null,
    ).use { cursor ->
        cursor.moveToFirst() && cursor.getString(0) in TERMINAL
    }

    @Synchronized
    fun discardUnstarted(turnId: String): Boolean =
        writableDatabase.delete("turns", "id=? AND state='pending'",
            arrayOf(turnId)) == 1

    @Synchronized
    fun deleteThread(threadId: String): Boolean {
        // Delete originals only after the parent archive thread has been deleted.
        val ids = readableDatabase.rawQuery(
            "SELECT tm.attachment_id FROM turn_media tm " +
                "JOIN turns t ON t.id=tm.turn_id WHERE t.thread_id=? " +
                "UNION SELECT attachment_id FROM turns " +
                "WHERE thread_id=? AND attachment_id IS NOT NULL",
            arrayOf(threadId, threadId),
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val deleted = writableDatabase.delete("threads", "id=?", arrayOf(threadId)) == 1
        if (deleted) ids.forEach { id -> mediaStore.delete(id) }
        return deleted
    }

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
