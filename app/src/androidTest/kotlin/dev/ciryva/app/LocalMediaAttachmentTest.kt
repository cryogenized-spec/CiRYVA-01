package dev.ciryva.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real ContentResolver/FileProvider + SQLite lifecycle. No paid network calls. */
@RunWith(AndroidJUnit4::class)
class LocalMediaAttachmentTest {
    private val context get() =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "p06-media-" + UUID.randomUUID() + ".db"
    private val folder get() = File(context.filesDir, "ciryva_media")
    private val fixtures = mutableListOf<File>()

    private fun source(bytes: ByteArray): android.net.Uri {
        folder.mkdirs()
        val file = File(folder, UUID.randomUUID().toString())
        file.writeBytes(bytes)
        fixtures += file
        return FileProvider.getUriForFile(
            context, context.packageName + ".media", file,
        )
    }

    @After fun cleanup() {
        fixtures.forEach { it.delete() }
        context.deleteDatabase(dbName)
    }

    @Test fun selectedPdfIsCopiedKeptWithThreadAndDeletedWithItsArchive() {
        val original = "%PDF-1.4\n% test fixture\n".toByteArray()
        val uri = source(original)
        val mediaStore = LocalAttachmentStore(folder)
        val selection = mediaStore.import(context.contentResolver, uri)
        assertEquals("application/pdf", selection.media.mimeType)
        assertTrue(selection.media.filename.endsWith(".pdf"))
        assertEquals(original.size, selection.media.sizeBytes)
        assertTrue(mediaStore.forRequest(selection.media).bytes.contentEquals(original))
        assertNull(selection.thumbnail)

        val threadId: String
        LocalConversationArchive(context, dbName).use { archive ->
            threadId = archive.createThread("Read this PDF").id
            val turnId = archive.beginTurn(
                threadId, "Summarize PDF", "test-model",
                media = selection.media
            )
            assertTrue(archive.settleTurn(turnId, "completed", "Original preserved"))
        }
        val stored = mediaStore.fileForView(selection.media)
        assertTrue(stored.isFile)
        LocalConversationArchive(context, dbName).use { archive ->
            val saved = archive.listTurns(threadId).single()
            assertEquals(selection.media, saved.media)
            assertEquals(2, archive.boundedContext(threadId).size)
            assertTrue(archive.deleteThread(threadId))
        }
        assertFalse(stored.exists())
    }

    @Test fun pngContentPickerCopiesTheOriginalAndGeneratesBoundedThumbnail() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        try { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) }
        finally { bitmap.recycle() }
        val selection = LocalAttachmentStore(folder).import(
            context.contentResolver, source(out.toByteArray())
        )
        assertEquals("image/png", selection.media.mimeType)
        assertNotNull(selection.thumbnail)
        assertTrue(selection.thumbnail!!.width <= 256)
        assertTrue(LocalAttachmentStore(folder)
            .forRequest(selection.media).bytes.contentEquals(out.toByteArray()))
        LocalAttachmentStore(folder).delete(selection.media.id)
    }

    @Test fun pdfLargerThanTwoMegabytesImportsWithoutTruncatingOriginal() {
        val original = ByteArray(3_200_000) { 0x20 }
        "%PDF-1.7".toByteArray().copyInto(original)
        val store = LocalAttachmentStore(folder)
        val imported = store.import(context.contentResolver, source(original))
        try {
            assertEquals(original.size, imported.media.sizeBytes)
            assertEquals("application/pdf", imported.media.mimeType)
            assertTrue(store.forRequest(imported.media).bytes.contentEquals(original))
        } finally {
            store.delete(imported.media.id)
        }
    }

    @Test fun oversizeImportIsRejectedWithoutLeakingPartialOriginal() {
        val over = ByteArray(dev.ciryva.core.stream.ResponseAttachment.MAX_BYTES + 1)
        "%PDF-1.7".toByteArray().copyInto(over)
        val store = LocalAttachmentStore(folder)
        val uri = source(over)
        val before = folder.listFiles()?.map { it.name }?.toSet().orEmpty()
        org.junit.Assert.assertThrows(AttachmentSizeLimitException::class.java) {
            store.import(context.contentResolver, uri)
        }
        // source() created a fixture inside this same directory, but an
        // unsuccessful import must not leave a new .part or UUID original.
        val additional = folder.listFiles()?.map { it.name }?.toSet().orEmpty() - before
        assertEquals(emptySet<String>(), additional)
    }

    @Test fun multipleSelectedOriginalsSurviveReopenAndAreDeletedWithTheirTurn() {
        val store = LocalAttachmentStore(folder)
        val pdf = store.import(context.contentResolver,
            source("%PDF-1.4\\nfirst original".toByteArray())).media
        val image = store.import(context.contentResolver,
            source(byteArrayOf(-1,-40,-1,-32,4,3,2,1))).media
        val secondPdf = store.import(context.contentResolver,
            source("%PDF-1.7\\nthird original".toByteArray())).media
        val items = listOf(pdf, image, secondPdf)
        val threadId: String
        LocalConversationArchive(context, dbName).use { archive ->
            threadId = archive.createThread("Three local originals").id
            archive.beginTurn(threadId, "Inspect all", "test-model",
                media = pdf, attachments = items)
            assertEquals(items.map { it.id }.toSet(), archive.retainedMediaIds())
        }
        LocalConversationArchive(context, dbName).use { archive ->
            val saved = archive.listTurns(threadId).single()
            assertEquals(pdf, saved.media)
            assertEquals(items, saved.attachments)
            assertTrue(items.all { store.fileForView(it).isFile })
            assertTrue(archive.deleteThread(threadId))
            assertTrue(archive.retainedMediaIds().isEmpty())
        }
        assertTrue(items.none {
            File(folder, it.id).exists()
        })
    }

    @Test fun migratingVersionFourPreservesLegacyOriginalAndPinnedConversation() {
        val thread = UUID.randomUUID().toString()
        val turn = UUID.randomUUID().toString()
        val original = UUID.randomUUID().toString()
        val old = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        old.execSQL("""CREATE TABLE threads (
            id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL,
            created_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL,
            is_pinned INTEGER NOT NULL DEFAULT 0)""")
        old.execSQL("""CREATE TABLE turns (
            seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL UNIQUE,
            thread_id TEXT NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
            user_text TEXT NOT NULL, assistant_text TEXT NOT NULL DEFAULT '',
            reasoning_summary TEXT NOT NULL DEFAULT '', model_id TEXT NOT NULL,
            state TEXT NOT NULL, created_ms INTEGER NOT NULL,
            status_detail TEXT NOT NULL DEFAULT '', input_tokens INTEGER,
            output_tokens INTEGER, total_tokens INTEGER, attachment_id TEXT,
            attachment_name TEXT, attachment_mime TEXT, attachment_bytes INTEGER,
            client_submission_id TEXT)""")
        old.execSQL("INSERT INTO threads VALUES (?,?,?,?,?)",
            arrayOf(thread, "Earlier thread", 10, 11, 1))
        old.execSQL("""INSERT INTO turns
            (id,thread_id,user_text,assistant_text,model_id,state,created_ms,
             attachment_id,attachment_name,attachment_mime,attachment_bytes)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
            arrayOf(turn, thread, "Earlier photo", "Seen", "test", "completed",
                12, original, "before.pdf", "application/pdf", 50))
        old.version = 4
        old.close()
        LocalConversationArchive(context, dbName).use { archive ->
            assertTrue(archive.listThreads().single().isPinned)
            val saved = archive.listTurns(thread).single()
            assertEquals("Earlier photo", saved.userText)
            assertEquals(original, saved.media?.id)
            assertEquals(listOf(saved.media), saved.attachments)
            assertTrue(original in archive.retainedMediaIds())
            assertTrue(archive.deleteThread(thread))
        }
    }

    @Test fun vanishedPrivateOriginalFailsClosedBeforeAnyNetworkEncoding() {
        val missing = LocalStoredMedia(UUID.randomUUID().toString(),
            "missing.pdf", "application/pdf", 55)
        val store = LocalAttachmentStore(folder)
        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            store.forRequest(missing)
        }
    }

    @Test fun staleUnlinkedMediaIsPrunedButArchivedAndSelectedDraftsRemain() {
        val privateDir = File(context.filesDir, "ciryva_media_cleanup_" + UUID.randomUUID())
        privateDir.mkdirs()
        val archived = File(privateDir, UUID.randomUUID().toString())
        val selected = File(privateDir, UUID.randomUUID().toString())
        val abandoned = File(privateDir, UUID.randomUUID().toString())
        val recent = File(privateDir, UUID.randomUUID().toString())
        try {
            listOf(archived, selected, abandoned, recent).forEach {
                it.writeBytes("%PDF-1.4".toByteArray())
            }
            val old = System.currentTimeMillis() - 48L * 60 * 60 * 1000
            listOf(archived, selected, abandoned).forEach { it.setLastModified(old) }
            val removed = LocalAttachmentStore(privateDir)
                .pruneStaleUnreferenced(setOf(archived.name), selected.name)
            assertEquals(1, removed)
            assertTrue(archived.exists())
            assertTrue(selected.exists())
            assertTrue(recent.exists())
            assertFalse(abandoned.exists())
        } finally {
            privateDir.deleteRecursively()
        }
    }

    @Test fun sqliteV1ToV2MigrationPreservesExistingTurnsWithoutMedia() {
        val threadId = UUID.randomUUID().toString()
        val turnId = UUID.randomUUID().toString()
        // Construct only the two v1 tables and one real legacy row, then open
        // through the app's v2 SQLiteOpenHelper. No delete/recreate workaround.
        val old = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        old.execSQL("""CREATE TABLE threads (
            id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL,
            created_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL)""")
        old.execSQL("""CREATE TABLE turns (
            seq INTEGER PRIMARY KEY AUTOINCREMENT,
            id TEXT NOT NULL UNIQUE,
            thread_id TEXT NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
            user_text TEXT NOT NULL, assistant_text TEXT NOT NULL DEFAULT '',
            reasoning_summary TEXT NOT NULL DEFAULT '', model_id TEXT NOT NULL,
            state TEXT NOT NULL, created_ms INTEGER NOT NULL,
            status_detail TEXT NOT NULL DEFAULT '', input_tokens INTEGER,
            output_tokens INTEGER, total_tokens INTEGER)""")
        old.execSQL("INSERT INTO threads VALUES (?, ?, ?, ?)",
            arrayOf(threadId, "Pre-attachment note", 1, 1))
        old.execSQL(
            """INSERT INTO turns
               (id,thread_id,user_text,assistant_text,model_id,state,created_ms)
               VALUES (?,?,?,?,?,?,?)""",
            arrayOf(turnId, threadId, "Older prompt", "Older response",
                "test-model", "completed", 2),
        )
        old.version = 1
        old.close()

        LocalConversationArchive(context, dbName).use { archive ->
            val saved = archive.listTurns(threadId).single()
            assertEquals(turnId, saved.id)
            assertEquals("Older response", saved.assistantText)
            assertNull(saved.media)
            assertEquals(2, archive.boundedContext(threadId).size)
            val next = archive.beginTurn(threadId, "After migration", "test-model")
            assertTrue(archive.settleTurn(next, "completed", "Working"))
            assertEquals(2, archive.listTurns(threadId).size)
        }
    }
}
