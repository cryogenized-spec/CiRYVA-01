package dev.ciryva.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.util.concurrent.Executors

/**
 * Owns a selected local original and in-flight ContentResolver import for the
 * whole Android app *process*, not for a short-lived Activity. Rotation does
 * not drop a provider read or strand its successfully imported private copy.
 *
 * The singleton does NOT survive process death. Android's savedInstanceState
 * separately restores the already imported draft's UUID and safe metadata.
 */
internal object PendingMediaImport {
    val selected = mutableStateOf<LocalMediaSelection?>(null)
    val loading = mutableStateOf(false)
    val notice = mutableStateOf<String?>(null)
    // A reserved Send owns the staged file until the serialized SQLite
    // worker commits a turn or declares that no turn was created.
    val journalPending = mutableStateOf(false)

    fun finishJournal(media: LocalMediaSelection?, archived: Boolean) {
        ui.post {
            if (!archived && media != null && selected.value == null) {
                selected.value = media
            }
            journalPending.value = false
        }
    }


    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ciryva-media-import").apply { isDaemon = true }
    }

    fun importFromPicker(context: Context, uri: Uri) {
        val app = context.applicationContext
        importWith(app) {
            LocalAttachmentStore(File(app.filesDir, "ciryva_media"))
                .import(app.contentResolver, uri)
        }
    }

    // Test hook uses the SAME process-retained worker; lets instrumentation
    // hold a provider read across ActivityScenario.recreate without real
    // external document providers or network traffic.
    internal fun importWith(context: Context, importer: () -> LocalMediaSelection) {
        if (loading.value || journalPending.value) return
        loading.value = true
        notice.value = null
        val app = context.applicationContext
        worker.execute {
            val result = runCatching(importer)
            ui.post {
                loading.value = false
                result.onSuccess { imported ->
                    val old = selected.value
                    selected.value = imported
                    if (old != null && old.media.id != imported.media.id) {
                        worker.execute {
                            LocalAttachmentStore(File(app.filesDir, "ciryva_media"))
                                .delete(old.media.id)
                        }
                    }
                }.onFailure {
                    // Never expose untrusted provider names or exception messages.
                    notice.value = if (it is AttachmentSizeLimitException)
                        "File is larger than the current 16 MiB Android memory-safe limit."
                    else "Couldn't attach this file. Select a valid JPEG, PNG or PDF."
                }
            }
        }
    }

    /**
     * Remove an unsent original only after the caller has stopped owning it.
     * Never delete a file already transferred to a persisted archive turn.
     */
    fun clearUnsent(context: Context) {
        if (loading.value) return
        val old = selected.value ?: return
        selected.value = null
        notice.value = null
        val app = context.applicationContext
        worker.execute {
            LocalAttachmentStore(File(app.filesDir, "ciryva_media")).delete(old.media.id)
        }
    }
}
