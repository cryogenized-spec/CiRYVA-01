package dev.ciryva.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import dev.ciryva.core.stream.ResponseAttachment
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-retained, app-private draft originals. UI and journal mutations are
 * main-thread operations; ContentResolver imports and deletions use one worker.
 * Selected originals never leave this process for a provider before Send.
 */
internal object PendingMediaImport {
    const val MAX_SELECTED_FILES = 12
    // Keep the original single-item state for existing clients and rotation
    // instrumentation. Additional items are a separate observable list.
    val selected = mutableStateOf<LocalMediaSelection?>(null)
    val additional = mutableStateOf<List<LocalMediaSelection>>(emptyList())
    val loading = mutableStateOf(false)
    val progress = mutableStateOf<String?>(null)
    val notice = mutableStateOf<String?>(null)
    val journalPending = mutableStateOf(false)

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ciryva-media-import").apply { isDaemon = true }
    }
    @Volatile private var activeCancellation: AtomicBoolean? = null

    fun currentSelections(): List<LocalMediaSelection> =
        listOfNotNull(selected.value) + additional.value

    /** Called only on the UI thread, except during initial cold recovery. */
    fun setSelections(items: List<LocalMediaSelection>) {
        selected.value = items.firstOrNull()
        additional.value = items.drop(1)
    }

    /** The journal owns ALL originals or NONE, with a single SQLite commit. */
    fun finishJournal(items: List<LocalMediaSelection>, archived: Boolean) {
        ui.post {
            if (!archived && items.isNotEmpty() && currentSelections().isEmpty()) {
                setSelections(items)
            }
            journalPending.value = false
        }
    }

    /** Appends distinct user-picked URIs; never contacts OpenAI. */
    fun importFromPicker(context: Context, uris: List<Uri>) {
        val chosen = uris.distinct()
        if (chosen.isEmpty()) return
        val already = currentSelections()
        if (chosen.size + already.size > MAX_SELECTED_FILES) {
            notice.value = "Select up to $MAX_SELECTED_FILES attachments per message."
            return
        }
        val app = context.applicationContext
        importManyWith(app, replaceExisting = false) { token, report ->
            val store = LocalAttachmentStore(File(app.filesDir, "ciryva_media"))
            val imported = mutableListOf<LocalMediaSelection>()
            var used = already.sumOf { it.media.sizeBytes.toLong() }
            try {
                chosen.forEachIndexed { i, uri ->
                    if (token.get()) throw CancellationException("Import cancelled")
                    report("Importing file ${i + 1} of ${chosen.size}…")
                    val next = store.import(app.contentResolver, uri) { token.get() }
                    imported.add(next)
                    used += next.media.sizeBytes.toLong()
                    if (used > ResponseAttachment.MAX_BYTES.toLong()) {
                        throw AttachmentSizeLimitException()
                    }
                }
                imported.toList()
            } catch (error: Exception) {
                // A failed batch must not erase the prior draft, and must not
                // leak successfully imported files from this failed batch.
                imported.forEach { store.delete(it.media.id) }
                throw error
            }
        }
    }

    /** Existing test hook: one replacement, same process-retained worker. */
    internal fun importWith(context: Context, importer: () -> LocalMediaSelection) {
        importManyWith(context, replaceExisting = true) { _, _ -> listOf(importer()) }
    }

    internal fun importManyWith(
        context: Context,
        replaceExisting: Boolean = false,
        importer: (AtomicBoolean, (String) -> Unit) -> List<LocalMediaSelection>,
    ) {
        if (loading.value || journalPending.value) return
        val old = currentSelections()
        loading.value = true
        notice.value = null
        progress.value = "Preparing local import…"
        val app = context.applicationContext
        val cancel = AtomicBoolean(false)
        activeCancellation = cancel
        worker.execute {
            val result = runCatching { importer(cancel) { message ->
                ui.post { if (loading.value) progress.value = message }
            } }
            ui.post {
                val imported = result.getOrNull().orEmpty()
                val combined = if (replaceExisting) imported else old + imported
                val valid = imported.isNotEmpty() &&
                    combined.size <= MAX_SELECTED_FILES &&
                    combined.map { it.media.id }.toSet().size == combined.size &&
                    combined.sumOf { it.media.sizeBytes.toLong() } <=
                        ResponseAttachment.MAX_BYTES.toLong()
                if (result.isSuccess && !cancel.get() && valid) {
                    setSelections(combined)
                    if (replaceExisting) {
                        val retained = combined.map { it.media.id }.toSet()
                        val toDelete = old.filterNot { it.media.id in retained }
                        worker.execute {
                            val store = LocalAttachmentStore(
                                File(app.filesDir, "ciryva_media")
                            )
                            toDelete.forEach { store.delete(it.media.id) }
                        }
                    }
                } else {
                    // If a batch returned originals but validation/cancel
                    // rejected it, these were never bound to the archive.
                    val retained = old.map { it.media.id }.toSet()
                    worker.execute {
                        val store = LocalAttachmentStore(File(app.filesDir, "ciryva_media"))
                        imported.filterNot { it.media.id in retained }.forEach {
                            store.delete(it.media.id)
                        }
                    }
                    notice.value = when {
                        cancel.get() || result.exceptionOrNull() is CancellationException ->
                            "Import cancelled; existing attachments were kept."
                        result.exceptionOrNull() is AttachmentSizeLimitException ||
                            !valid && combined.sumOf { it.media.sizeBytes.toLong() } >
                                ResponseAttachment.MAX_BYTES.toLong() ->
                            "Combined attachments exceed the current 16 MiB Android memory-safe limit."
                        !valid && combined.size > MAX_SELECTED_FILES ->
                            "Select up to $MAX_SELECTED_FILES attachments per message."
                        else -> "Couldn't attach these files. Use valid JPEG, PNG or PDF originals."
                    }
                }
                loading.value = false
                progress.value = null
                activeCancellation = null
            }
        }
    }

    fun cancelCurrentImport() {
        activeCancellation?.set(true)
        if (loading.value) progress.value = "Cancelling import…"
    }

    /** Removing one draft original cannot delete any archived or journaled file. */
    fun removeUnsent(context: Context, id: String) {
        if (loading.value || journalPending.value) return
        val old = currentSelections()
        if (old.none { it.media.id == id }) return
        setSelections(old.filterNot { it.media.id == id })
        notice.value = null
        val app = context.applicationContext
        worker.execute {
            LocalAttachmentStore(File(app.filesDir, "ciryva_media")).delete(id)
        }
    }

    fun clearUnsent(context: Context) {
        if (loading.value || journalPending.value) return
        val old = currentSelections()
        if (old.isEmpty()) return
        setSelections(emptyList())
        notice.value = null
        val app = context.applicationContext
        worker.execute {
            val store = LocalAttachmentStore(File(app.filesDir, "ciryva_media"))
            old.forEach { store.delete(it.media.id) }
        }
    }
}
