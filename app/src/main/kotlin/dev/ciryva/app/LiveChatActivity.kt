package dev.ciryva.app

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.ciryva.core.stream.ResponsesHttpTransport
import dev.ciryva.core.stream.TextResponseRequest
import dev.ciryva.core.turn.ResponseTurnOutcome
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * P05: local-first conversation state with a serialized disk worker.
 *
 * Chargeable inference NEVER starts until the user's prompt is committed
 * locally. SQLite queries/writes never execute on the Android UI thread.
 * Only completed pairs of messages may be included in future context.
 */
class LiveChatActivity : ComponentActivity() {
    private companion object {
        const val SELECTED_THREAD_KEY = "ciryva.selected_thread"
    }

    private data class ReservedSend(
        val request: TextResponseRequest,
        val previousThreadId: String?,
        val selection: LocalMediaSelection?,
        val cancelled: AtomicBoolean = AtomicBoolean(false),
        @Volatile var persistedTurnId: String? = null,
    )

    private data class AwaitingSave(
        val turnId: String,
        val final: LiveChatSnapshot,
        val threadId: String,
        var saving: Boolean = false,
    )

    private val snapshot = mutableStateOf(LiveChatSnapshot())
    private val keyPresent = mutableStateOf(false)
    private val threadList = mutableStateOf<List<LocalThread>>(emptyList())
    private val visibleTurns = mutableStateOf<List<LocalTurn>>(emptyList())
    private val localUsageTotals = mutableStateOf<LocalUsageTotals?>(null)
    private val selectedThreadId = mutableStateOf<String?>(null)
    private val archiveNotice = mutableStateOf<String?>(null)
    private val archiveBusy = mutableStateOf(true)
    private val archiveSaveRetryable = mutableStateOf(false)
    private val selectedMedia = mutableStateOf<LocalMediaSelection?>(null)
    private val mediaLoading = mutableStateOf(false)
    private val mediaNotice = mutableStateOf<String?>(null)

    private val disk: ExecutorService = Executors.newSingleThreadExecutor { command ->
        Thread(command, "ciryva-archive-disk").apply { isDaemon = true }
    }
    private lateinit var archive: LocalConversationArchive
    private lateinit var mediaStore: LocalAttachmentStore
    private val documentPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) importPickedDocument(uri)
    }
    private lateinit var chatPreferencesStore: ChatPreferencesStore
    private var controller: LiveChatController? = null

    // Main-thread ownership: callbacks are marshalled with runOnUiThread.
    private var archiveEpoch = 0L
    private var reservedSend: ReservedSend? = null
    private var networkTurnId: String? = null
    private var awaitingSave: AwaitingSave? = null

    private fun importPickedDocument(uri: Uri) {
        if (mediaLoading.value || snapshot.value.busy || archiveBusy.value) return
        mediaLoading.value = true
        mediaNotice.value = null
        disk.execute {
            try {
                val selection = mediaStore.import(contentResolver, uri)
                postToUi {
                    val previous = selectedMedia.value
                    selectedMedia.value = selection
                    mediaLoading.value = false
                    previous?.let { old ->
                        disk.execute { mediaStore.delete(old.media.id) }
                    }
                }
            } catch (_: Exception) {
                postToUi {
                    mediaLoading.value = false
                    mediaNotice.value =
                        "Couldn't attach the file. Choose a JPEG, PNG or PDF up to 2 MB."
                }
            }
        }
    }

    private fun removeSelectedMedia() {
        if (archiveBusy.value || snapshot.value.busy) return
        val media = selectedMedia.value ?: return
        selectedMedia.value = null
        mediaNotice.value = null
        disk.execute { mediaStore.delete(media.media.id) }
    }

    private fun clearMediaOnThreadChange() {
        val old = selectedMedia.value ?: return
        selectedMedia.value = null
        mediaNotice.value = null
        disk.execute { mediaStore.delete(old.media.id) }
    }

    private fun postToUi(block: () -> Unit) {
        runOnUiThread {
            if (!isDestroyed && !isFinishing) block()
        }
    }

    /**
     * Snapshot queries run in order on the disk executor. A stale load is never
     * allowed to replace the currently selected thread after navigation.
     */
    private fun reloadArchive() {
        archiveBusy.value = true
        val wanted = selectedThreadId.value
        val retryId = awaitingSave?.turnId
        val epoch = ++archiveEpoch
        disk.execute {
            try {
                // Replacement Activities wait for first-process orphan recovery
                // before seeing the archive or enabling a new provider request.
                PendingArchiveResponse.awaitColdProcessRecovery()
                val threads = archive.listThreads()
                val selected = wanted?.takeIf { id -> threads.any { it.id == id } }
                    ?: threads.firstOrNull()?.id
                // Check terminal completion before loading turns on the same
                // serialized disk worker. This avoids showing a stale pending
                // row when the previous Activity settled while rotating.
                val savedInOtherInstance = retryId != null &&
                    archive.isTerminalSaved(retryId)
                val turns = selected?.let(archive::listTurns) ?: emptyList()
                val usage = archive.usageTotals()
                postToUi {
                    if (archiveEpoch != epoch) return@postToUi
                    selectedThreadId.value = selected
                    threadList.value = threads
                    visibleTurns.value = turns
                    localUsageTotals.value = usage
                    if (savedInOtherInstance && awaitingSave?.turnId == retryId &&
                        awaitingSave?.saving != true) {
                        PendingArchiveResponse.clear(retryId!!)
                        awaitingSave = null
                        archiveSaveRetryable.value = false
                        archiveNotice.value = null
                        controller?.clearView()
                    }
                    if (awaitingSave?.saving != true && reservedSend == null) {
                        archiveBusy.value = false
                    }
                }
            } catch (_: RuntimeException) {
                postToUi {
                    if (archiveEpoch != epoch) return@postToUi
                    archiveBusy.value = false
                    archiveNotice.value =
                        "Unable to read local conversations. Check device storage."
                }
            }
        }
    }

    private fun outcomeState(outcome: ResponseTurnOutcome?): String = when (outcome) {
        ResponseTurnOutcome.COMPLETED -> "completed"
        ResponseTurnOutcome.CANCELLED -> "cancelled"
        ResponseTurnOutcome.INCOMPLETE -> "incomplete"
        null -> "interrupted"
        else -> "failed"
    }

    /** Persist a final snapshot on the disk worker; leave it retryable on error. */
    private fun persistFinal(record: AwaitingSave) {
        if (record.saving) return
        PendingArchiveResponse.retain(RetainedArchiveResponse(
            record.threadId, record.turnId, record.final,
        ))
        record.saving = true
        archiveSaveRetryable.value = false
        archiveBusy.value = true
        disk.execute {
            val saved = try {
                archive.settleTurn(
                    turnId = record.turnId,
                    state = outcomeState(record.final.outcome),
                    answer = record.final.assistantText,
                    summary = record.final.reasoningSummary,
                    usage = record.final.usage,
                    statusDetail = record.final.status,
                )
            } catch (_: RuntimeException) {
                false
            }
            if (saved) PendingArchiveResponse.clear(record.turnId)
            postToUi {
                if (awaitingSave !== record) return@postToUi
                record.saving = false
                if (saved) {
                    awaitingSave = null
                    archiveSaveRetryable.value = false
                    archiveNotice.value = null
                    controller?.clearView()
                    reloadArchive()
                } else {
                    // The response stays on screen; Send and navigation are
                    // blocked until the user retries rather than discarding it.
                    archiveBusy.value = false
                    archiveSaveRetryable.value = true
                    archiveNotice.value =
                        "The response could not be saved. Free storage and retry."
                    snapshot.value = record.final
                }
            }
        }
    }

    private fun onStreamUpdate(value: LiveChatSnapshot) {
        if (isDestroyed || isFinishing) return
        snapshot.value = value
        val pendingId = networkTurnId
        if (!value.busy && pendingId != null && awaitingSave == null) {
            networkTurnId = null
            val record = AwaitingSave(
                pendingId, value, selectedThreadId.value.orEmpty()
            )
            awaitingSave = record
            persistFinal(record)
        }
    }

    /** Every explicit Send reserves the slot before a background disk write. */
    private fun sendWithArchive(request: TextResponseRequest): Boolean {
        if (archiveBusy.value || reservedSend != null || networkTurnId != null ||
            awaitingSave != null || controller?.snapshot()?.busy == true) return false
        if (mediaLoading.value) return false
        val reservation = ReservedSend(request, selectedThreadId.value,
            selectedMedia.value)
        // One file belongs to one accepted Send. Its private original now
        // belongs to that turn's pending archive journal, not the next draft.
        selectedMedia.value = null
        reservedSend = reservation
        snapshot.value = LiveChatSnapshot(
            userPrompt = request.prompt,
            modelId = request.model,
            busy = true,
            status = "Saving the message locally before the request…",
        )
        archiveNotice.value = null

        disk.execute {
            var savedTurnId: String? = null
            try {
                if (reservation.cancelled.get()) {
                    postToUi {
                        if (reservedSend === reservation) {
                            reservedSend = null
                            snapshot.value = LiveChatSnapshot(
                                status = "Cancelled before contacting OpenAI.",
                            )
                            archiveBusy.value = false
                        }
                    }
                    return@execute
                }
                val threadId = reservation.previousThreadId
                    ?: archive.createThread(request.prompt).id
                val attachment = reservation.selection?.media?.let(mediaStore::forRequest)
                if (reservation.cancelled.get()) return@execute
                val input = request.copy(
                    history = archive.boundedContext(threadId),
                    attachment = attachment,
                )
                val createdTurnId = archive.beginTurn(
                    threadId, request.prompt, request.model,
                    media = reservation.selection?.media,
                )
                savedTurnId = createdTurnId
                reservation.persistedTurnId = createdTurnId
                if (reservation.cancelled.get()) {
                    archive.settleTurn(
                        createdTurnId, "interrupted", "",
                        statusDetail = "Cancelled before contacting OpenAI.",
                    )
                    return@execute
                }

                postToUi {
                    // onStop / Cancel may have invalidated this reservation
                    // after the database write but before UI dispatch.
                    if (reservation.cancelled.get() || reservedSend !== reservation) {
                        return@postToUi
                    }
                    selectedThreadId.value = threadId
                    reservedSend = null
                    networkTurnId = createdTurnId
                    val started = controller?.send(input) ?: false
                    if (!started) {
                        networkTurnId = null
                        archiveNotice.value = "Unable to start the provider request."
                        snapshot.value = LiveChatSnapshot(
                            status = "Request did not start; no API call was made.",
                        )
                        disk.execute {
                            archive.settleTurn(
                                createdTurnId, "failed", "",
                                statusDetail = "Request did not start.",
                            )
                        }
                    }
                    reloadArchive()
                }
            } catch (_: RuntimeException) {
                savedTurnId?.let { row ->
                    try {
                        archive.settleTurn(
                            row, "failed", "", statusDetail = "Unable to start request.",
                        )
                    } catch (_: RuntimeException) { /* Preserve pending row on storage failure. */ }
                }
                postToUi {
                    if (reservedSend === reservation) {
                        reservedSend = null
                        // Failed preflight is not silently discarding the user's
                        // chosen original; they can repair storage and retry.
                        selectedMedia.value = reservation.selection
                        snapshot.value = LiveChatSnapshot(
                            status = "Unable to save a local request. No API call was started.",
                        )
                        archiveBusy.value = false
                        archiveNotice.value =
                            "Local storage is unavailable. No provider request was started."
                        reloadArchive()
                    }
                }
            }
        }
        return true
    }

    private fun cancelCurrentRequest(): Boolean {
        val reservation = reservedSend
        if (reservation != null) {
            reservation.cancelled.set(true)
            reservedSend = null
            snapshot.value = LiveChatSnapshot(
                status = "Cancelled before the request could start.",
            )
            // Serialize this after any already-running beginTurn transaction.
            disk.execute {
                reservation.persistedTurnId?.let { id ->
                    archive.settleTurn(
                        id, "interrupted", "",
                        statusDetail = "Cancelled before contacting OpenAI.",
                    )
                }
                postToUi { reloadArchive() }
            }
            return true
        }
        return controller?.cancel() ?: false
    }

    private fun newConversation() {
        if (archiveBusy.value || reservedSend != null || networkTurnId != null ||
            awaitingSave != null || controller?.snapshot()?.busy == true) return
        archiveBusy.value = true
        disk.execute {
            try {
                val thread = archive.createThread()
                postToUi {
                    clearMediaOnThreadChange()
                    selectedThreadId.value = thread.id
                    archiveNotice.value = null
                    controller?.clearView()
                    reloadArchive()
                }
            } catch (_: RuntimeException) {
                postToUi {
                    archiveBusy.value = false
                    archiveNotice.value = "Could not create a local conversation."
                }
            }
        }
    }

    private fun selectConversation(threadId: String) {
        if (archiveBusy.value || reservedSend != null || networkTurnId != null ||
            awaitingSave != null || controller?.snapshot()?.busy == true) return
        if (threadList.value.none { it.id == threadId }) return
        clearMediaOnThreadChange()
        selectedThreadId.value = threadId
        controller?.clearView()
        reloadArchive()
    }

    private fun deleteConversation(threadId: String) {
        if (archiveBusy.value || reservedSend != null || networkTurnId != null ||
            awaitingSave != null || controller?.snapshot()?.busy == true) return
        archiveBusy.value = true
        disk.execute {
            try {
                val deleted = archive.deleteThread(threadId)
                postToUi {
                    if (deleted) {
                        if (selectedThreadId.value == threadId) {
                            clearMediaOnThreadChange()
                            selectedThreadId.value = null
                            controller?.clearView()
                        }
                        reloadArchive()
                    } else {
                        archiveBusy.value = false
                        archiveNotice.value = "Conversation no longer exists."
                    }
                }
            } catch (_: RuntimeException) {
                postToUi {
                    archiveBusy.value = false
                    archiveNotice.value = "Could not delete the conversation."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(22, 17, 24)),
        )
        val firstProcessLaunch = PendingArchiveResponse.claimColdProcessStartup()
        val retained = PendingArchiveResponse.latest()
        selectedThreadId.value = retained?.threadId
            ?: savedInstanceState?.getString(SELECTED_THREAD_KEY)
        retained?.let {
            awaitingSave = AwaitingSave(it.turnId, it.snapshot, it.threadId)
            snapshot.value = it.snapshot
            archiveSaveRetryable.value = true
            archiveNotice.value = "Response is not saved yet. Retry after freeing storage."
        }
        archive = LocalConversationArchive(applicationContext)
        mediaStore = LocalAttachmentStore(
            java.io.File(applicationContext.filesDir, "ciryva_media")
        )
        savedInstanceState?.let { bundle ->
            val id = bundle.getString("media_id")
            val name = bundle.getString("media_name")
            val mime = bundle.getString("media_mime")
            val size = bundle.getInt("media_size")
            if (id != null && name != null && mime != null && size > 0) {
                selectedMedia.value = LocalMediaSelection(
                    LocalStoredMedia(id, name, mime, size)
                )
            }
        }
        chatPreferencesStore = ChatPreferencesStore(applicationContext)
        val savedChatPreferences = chatPreferencesStore.load()

        val keyStore = AndroidByokKeyStore(applicationContext)
        controller = LiveChatController(
            client = ChatStreamClient { request, key, cancel, onEvent ->
                ResponsesHttpTransport().stream(request, key, cancel, onEvent)
            },
            credentialProvider = { keyStore.load() },
            onUpdate = { value -> runOnUiThread { onStreamUpdate(value) } },
        )
        setContent {
            LiveChatScreen(
                snapshot = snapshot.value,
                hasKey = keyPresent.value,
                onBack = { startActivity(Intent(this, MainActivity::class.java)) },
                onKeys = { startActivity(Intent(this, ByokSettingsActivity::class.java)) },
                onSend = ::sendWithArchive,
                onCancel = ::cancelCurrentRequest,
                selectedMedia = selectedMedia.value,
                mediaLoading = mediaLoading.value,
                mediaNotice = mediaNotice.value,
                onChooseMedia = {
                    if (!mediaLoading.value && !snapshot.value.busy && !archiveBusy.value) {
                        documentPicker.launch(arrayOf(
                            "image/jpeg", "image/png", "application/pdf"
                        ))
                    }
                },
                onRemoveMedia = ::removeSelectedMedia,
                localUsageTotals = localUsageTotals.value,
                preferences = savedChatPreferences,
                onPreferencesChange = { updated ->
                    chatPreferencesStore.save(updated)
                },
                archiveEnabled = true,
                archiveThreads = threadList.value,
                archiveTurns = visibleTurns.value.filterNot {
                    it.id == networkTurnId || it.id == awaitingSave?.turnId
                },
                currentThreadId = selectedThreadId.value,
                archiveNotice = archiveNotice.value,
                archiveBusy = archiveBusy.value,
                archiveSaveRetryable = archiveSaveRetryable.value,
                onRetryArchive = { awaitingSave?.let(::persistFinal) },
                onNewThread = ::newConversation,
                onOpenThread = ::selectConversation,
                onDeleteThread = ::deleteConversation,
            )
        }
        if (firstProcessLaunch) {
            archiveBusy.value = true
            disk.execute {
                try {
                    archive.recoverStrandedPending()
                } catch (_: RuntimeException) {
                    postToUi {
                        archiveNotice.value =
                            "Could not recover interrupted requests. Check local storage."
                    }
                } finally {
                    // Always unblock replacement Activities, even if SQLite
                    // cannot be written. A disk worker may wait; UI never does.
                    PendingArchiveResponse.finishColdProcessRecovery()
                }
                postToUi { reloadArchive() }
            }
        } else {
            reloadArchive()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        selectedMedia.value?.media?.let {
            outState.putString("media_id", it.id)
            outState.putString("media_name", it.filename)
            outState.putString("media_mime", it.mimeType)
            outState.putInt("media_size", it.sizeBytes)
        }
        selectedThreadId.value?.let {
            outState.putString(SELECTED_THREAD_KEY, it)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        keyPresent.value = AndroidByokKeyStore(applicationContext).hasKey()
    }

    override fun onStop() {
        val reservation = reservedSend
        if (reservation != null) cancelCurrentRequest()

        val authoritativeBefore = controller?.snapshot()
        controller?.cancel()
        val authoritativeAfter = controller?.snapshot() ?: authoritativeBefore

        val turnId = networkTurnId
        if (turnId != null && awaitingSave == null) {
            networkTurnId = null
            // The provider may have finished before a queued UI callback.
            // Preserve that receipt instead of falsely calling it interrupted.
            val final = if (authoritativeAfter?.busy == false) authoritativeAfter
                else (authoritativeAfter ?: LiveChatSnapshot()).copy(
                    busy = false,
                    outcome = ResponseTurnOutcome.CANCELLED,
                    usage = null,
                    status = "Interrupted on background; provider usage may be unknown.",
                )
            val record = AwaitingSave(
                turnId, final, selectedThreadId.value.orEmpty()
            )
            awaitingSave = record
            persistFinal(record)
        }
        super.onStop()
    }

    override fun onDestroy() {
        controller?.close()
        controller = null
        // Drain queued journal writes before closing the SQLite helper. Never
        // discard accepted pending work with shutdownNow().
        disk.execute { archive.close() }
        disk.shutdown()
        super.onDestroy()
    }
}
