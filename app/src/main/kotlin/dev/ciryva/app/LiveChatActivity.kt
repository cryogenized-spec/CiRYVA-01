package dev.ciryva.app

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
        // Only a random local ID, never prompt or credential data. A process-
        // wide Compose state publishes successful journal ownership even if
        // the originating Activity rotates before its UI callback executes.
        val committedJournalId = mutableStateOf<String?>(null)
        // Guards queued SQLite journals across Activity rotation in the
        // surviving process; reset on a cold process launch (no worker lives).
        val inFlightJournalId = mutableStateOf<String?>(null)
    }

    private data class ReservedSend(
        val submissionId: String,
        val request: TextResponseRequest,
        val previousThreadId: String?,
        val selections: List<LocalMediaSelection>,
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
    private val archiveSearchResults = mutableStateOf<List<LocalThread>>(emptyList())
    private val archiveSearchResolvedQuery = mutableStateOf("")
    private val archiveSearchBusy = mutableStateOf(false)
    private val archiveSearchError = mutableStateOf<String?>(null)
    private val visibleTurns = mutableStateOf<List<LocalTurn>>(emptyList())
    private val localUsageTotals = mutableStateOf<LocalUsageTotals?>(null)
    private val selectedThreadId = mutableStateOf<String?>(null)
    private val archiveNotice = mutableStateOf<String?>(null)
    private val archiveBusy = mutableStateOf(true)
    // A failed archive query is NOT proof that an accepted Send was unsaved.
    private val archiveReadSucceeded = mutableStateOf(false)
    private val archiveSaveRetryable = mutableStateOf(false)
    // One process owner survives Activity rotation while ContentResolver reads.
    private val selectedMedia = PendingMediaImport.selected
    private val mediaLoading = PendingMediaImport.loading
    private val mediaNotice = PendingMediaImport.notice

    private val disk: ExecutorService = Executors.newSingleThreadExecutor { command ->
        Thread(command, "ciryva-archive-disk").apply { isDaemon = true }
    }
    private lateinit var archive: LocalConversationArchive
    private lateinit var mediaStore: LocalAttachmentStore
    private val documentPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) importPickedDocuments(uris)
    }
    private lateinit var chatPreferencesStore: ChatPreferencesStore
    private var controller: LiveChatController? = null

    // Main-thread ownership: callbacks are marshalled with runOnUiThread.
    private var archiveEpoch = 0L
    private var searchEpoch = 0L
    private var activeArchiveSearch = ""
    private var reservedSend: ReservedSend? = null
    private var networkTurnId: String? = null
    private var awaitingSave: AwaitingSave? = null

    private fun importPickedDocuments(uris: List<Uri>) {
        if (snapshot.value.busy || archiveBusy.value) return
        PendingMediaImport.importFromPicker(applicationContext, uris)
    }

    private fun viewArchivedMedia(media: LocalStoredMedia) {
        try {
            val original = mediaStore.fileForView(media)
            val uri = FileProvider.getUriForFile(
                this, "$packageName.media", original,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, media.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            mediaNotice.value =
                "This device has no compatible viewer, or the local file is unavailable."
        }
    }

    private fun removeSelectedMedia() {
        if (archiveBusy.value || snapshot.value.busy) return
        PendingMediaImport.clearUnsent(applicationContext)
    }

    private fun removeSelectedMediaById(id: String) {
        if (archiveBusy.value || snapshot.value.busy) return
        PendingMediaImport.removeUnsent(applicationContext, id)
    }

    private fun clearMediaOnThreadChange() {
        PendingMediaImport.clearUnsent(applicationContext)
    }

    private fun postToUi(block: () -> Unit) {
        runOnUiThread {
            if (!isDestroyed && !isFinishing) block()
        }
    }

    /**
     * Bounded, local title/message search. Each input supersedes any previous
     * disk result; it never affects Send eligibility or archive ownership.
     */
    private fun searchConversations(query: String) {
        val term = query.trim().take(LocalConversationArchive.MAX_SEARCH_LENGTH)
        activeArchiveSearch = term
        val epoch = ++searchEpoch
        if (term.isEmpty()) {
            archiveSearchResolvedQuery.value = ""
            archiveSearchResults.value = emptyList()
            archiveSearchBusy.value = false
            archiveSearchError.value = null
            return
        }
        archiveSearchBusy.value = true
        archiveSearchError.value = null
        disk.execute {
            val results = runCatching { archive.searchThreads(term) }
            postToUi {
                if (searchEpoch != epoch) return@postToUi
                archiveSearchResolvedQuery.value = term
                archiveSearchResults.value = results.getOrElse { emptyList() }
                archiveSearchBusy.value = false
                archiveSearchError.value = if (results.isFailure)
                    "Unable to search this device's conversation archive." else null
            }
        }
    }

    /**
     * Snapshot queries run in order on the disk executor. A stale load is never
     * allowed to replace the currently selected thread after navigation.
     */
    private fun reloadArchive() {
        archiveBusy.value = true
        archiveReadSucceeded.value = false
        val wanted = selectedThreadId.value
        val retryId = awaitingSave?.turnId
        val epoch = ++archiveEpoch
        disk.execute {
            try {
                // Replacement Activities wait for first-process orphan recovery
                // before seeing the archive or enabling a new provider request.
                PendingArchiveResponse.awaitColdProcessRecovery()
                val threads = archive.listThreads(wanted)
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
                    // A load that raced a still-in-flight journal is not
                    // authoritative for reconciling a restored submission.
                    archiveReadSucceeded.value = inFlightJournalId.value == null
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
                    if (activeArchiveSearch.isNotEmpty()) {
                        searchConversations(activeArchiveSearch)
                    }
                }
            } catch (_: RuntimeException) {
                postToUi {
                    if (archiveEpoch != epoch) return@postToUi
                    archiveBusy.value = false
                    archiveReadSucceeded.value = false
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
    private fun sendWithArchive(request: TextResponseRequest, submissionId: String): Boolean {
        if (!archiveReadSucceeded.value || inFlightJournalId.value != null ||
            archiveBusy.value || mediaLoading.value ||
            PendingMediaImport.journalPending.value || reservedSend != null || networkTurnId != null ||
            awaitingSave != null || controller?.snapshot()?.busy == true) return false
        if (mediaLoading.value) return false
        val reservation = ReservedSend(submissionId, request, selectedThreadId.value,
            PendingMediaImport.currentSelections())
        // All selected private originals now belong to one pending journal.
        // Neither a second Send nor a new picker batch can steal them.
        if (reservation.selections.isNotEmpty()) {
            PendingMediaImport.journalPending.value = true
        }
        PendingMediaImport.setSelections(emptyList())
        reservedSend = reservation
        archiveReadSucceeded.value = false
        inFlightJournalId.value = submissionId
        // Journal reconciliation must finish before an accepted draft can
        // be restored after cancellation. Keep Send and UI recovery gated
        // until either beginTurn settles or reloadArchive publishes its state.
        archiveBusy.value = true
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
                val attachmentParts = reservation.selections.map {
                    mediaStore.forRequest(it.media)
                }
                if (reservation.cancelled.get()) return@execute
                val input = request.copy(
                    history = archive.boundedContext(threadId),
                    attachment = null,
                    attachments = attachmentParts,
                )
                val createdTurnId = archive.beginTurn(
                    threadId, request.prompt, request.model,
                    media = reservation.selections.firstOrNull()?.media,
                    submissionId = reservation.submissionId,
                    attachments = reservation.selections.map { it.media },
                )
                savedTurnId = createdTurnId
                reservation.persistedTurnId = createdTurnId
                // The exact accepted submission now has a durable SQLite row.
                // Publish the ID regardless of whether this Activity survives:
                // it is the only authority for clearing a composer draft.
                runOnUiThread { committedJournalId.value = reservation.submissionId }
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
            } catch (_: Exception) {
                // Includes IOException from a vanished/unreadable local media
                // original. No provider call is attempted after failed preflight.
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
                        // The disk-worker finally block determines ownership;
                        // a callback from an Activity being destroyed may vanish.
                        snapshot.value = LiveChatSnapshot(
                            status = "Unable to save a local request. No API call was started.",
                        )
                        archiveBusy.value = false
                        archiveNotice.value =
                            "Local storage is unavailable. No provider request was started."
                        reloadArchive()
                    }
                }
            } finally {
                if (reservation.selections.isNotEmpty()) {
                    PendingMediaImport.finishJournal(
                        reservation.selections, archived = savedTurnId != null,
                    )
                }
                runOnUiThread {
                    if (inFlightJournalId.value == reservation.submissionId)
                        inFlightJournalId.value = null
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
            // Do NOT restore here: beginTurn may have committed while the
            // disk worker has not published persistedTurnId. Its finally
            // block makes the one authoritative transfer decision.
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
        if (archiveBusy.value || mediaLoading.value ||
            PendingMediaImport.journalPending.value || reservedSend != null || networkTurnId != null ||
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
        if (archiveBusy.value || mediaLoading.value ||
            PendingMediaImport.journalPending.value || reservedSend != null || networkTurnId != null ||
            awaitingSave != null || controller?.snapshot()?.busy == true) return
        if (threadList.value.none { it.id == threadId } &&
            archiveSearchResults.value.none { it.id == threadId }) return
        clearMediaOnThreadChange()
        selectedThreadId.value = threadId
        controller?.clearView()
        reloadArchive()
    }

    /** Local-only pin action. Never changes the selected thread or starts an API request. */
    private fun setConversationPinned(threadId: String, pinned: Boolean) {
        if (archiveBusy.value || mediaLoading.value ||
            PendingMediaImport.journalPending.value || reservedSend != null || networkTurnId != null ||
            awaitingSave != null || controller?.snapshot()?.busy == true) return
        if (threadList.value.none { it.id == threadId } &&
            archiveSearchResults.value.none { it.id == threadId }) return
        archiveBusy.value = true
        disk.execute {
            try {
                val updated = archive.setThreadPinned(threadId, pinned)
                postToUi {
                    if (updated) {
                        reloadArchive()
                    } else {
                        archiveBusy.value = false
                        archiveNotice.value = "Conversation no longer exists."
                    }
                }
            } catch (_: RuntimeException) {
                postToUi {
                    archiveBusy.value = false
                    archiveNotice.value = "Could not update conversation pin."
                }
            }
        }
    }

    private fun deleteConversation(threadId: String) {
        if (archiveBusy.value || mediaLoading.value ||
            PendingMediaImport.journalPending.value || reservedSend != null || networkTurnId != null ||
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
        // In a surviving process, the singleton is authoritative. A Bundle
        // captured before a disk journal committed may be stale by recreation.
        // Restore metadata from Bundle only on cold process startup, where
        // retainedMediaIds() below reconciles committed ownership before
        // the archive is unlocked.
        if (firstProcessLaunch && PendingMediaImport.currentSelections().isEmpty() &&
            !mediaLoading.value && !PendingMediaImport.journalPending.value) {
            savedInstanceState?.let { bundle ->
                val ids = bundle.getStringArrayList("media_ids")
                val names = bundle.getStringArrayList("media_names")
                val types = bundle.getStringArrayList("media_types")
                val sizes = bundle.getIntegerArrayList("media_sizes")
                val saved = if (ids != null && names != null && types != null &&
                    sizes != null && ids.size in 1..PendingMediaImport.MAX_SELECTED_FILES &&
                    ids.size == names.size && ids.size == types.size &&
                    ids.size == sizes.size) {
                    ids.indices.map { i ->
                        LocalMediaSelection(LocalStoredMedia(
                            ids[i], names[i], types[i], sizes[i]
                        ))
                    }
                } else {
                    // Old Android savedInstanceState bundle from one-file builds.
                    val id = bundle.getString("media_id")
                    val name = bundle.getString("media_name")
                    val mime = bundle.getString("media_mime")
                    val size = bundle.getInt("media_size")
                    if (id != null && name != null && mime != null && size > 0)
                        listOf(LocalMediaSelection(LocalStoredMedia(id, name, mime, size)))
                    else emptyList()
                }
                val recoverable = mediaStore.availableDrafts(saved)
                if (recoverable.isNotEmpty()) {
                    PendingMediaImport.setSelections(recoverable)
                }
                if (recoverable.size < saved.size) {
                    mediaNotice.value =
                        "Some draft attachments are unavailable; remaining files were kept."
                }
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
            val journalNow = inFlightJournalId.value
            val observedInFlight = remember { mutableStateOf(journalNow != null) }
            LaunchedEffect(journalNow) {
                if (observedInFlight.value && journalNow == null) reloadArchive()
                observedInFlight.value = journalNow != null
            }
            LiveChatScreen(
                snapshot = snapshot.value,
                hasKey = keyPresent.value,
                onBack = { startActivity(Intent(this, MainActivity::class.java)) },
                onKeys = { startActivity(Intent(this, ByokSettingsActivity::class.java)) },
                onSend = { request ->
                    sendWithArchive(request, java.util.UUID.randomUUID().toString())
                },
                onJournaledSend = ::sendWithArchive,
                journalCommittedId = committedJournalId.value,
                journalInFlight = inFlightJournalId.value != null,
                archiveVerified = archiveReadSucceeded.value,
                onCancel = ::cancelCurrentRequest,
                mediaEnabled = true,
                selectedMedia = selectedMedia.value,
                selectedMediaItems = PendingMediaImport.currentSelections(),
                mediaImportProgress = PendingMediaImport.progress.value,
                onCancelMediaImport = PendingMediaImport::cancelCurrentImport,
                onOpenStoredMedia = ::viewArchivedMedia,
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
                onRemoveMediaById = ::removeSelectedMediaById,
                localUsageTotals = localUsageTotals.value,
                preferences = savedChatPreferences,
                onPreferencesChange = { updated ->
                    chatPreferencesStore.save(updated)
                },
                archiveEnabled = true,
                archiveThreads = threadList.value,
                archiveSearchResults = archiveSearchResults.value,
                archiveSearchResolvedQuery = archiveSearchResolvedQuery.value,
                archiveSearchBusy = archiveSearchBusy.value,
                archiveSearchError = archiveSearchError.value,
                onArchiveSearch = ::searchConversations,
                archiveTurns = visibleTurns.value.filterNot {
                    it.id == networkTurnId || it.id == awaitingSave?.turnId
                },
                currentThreadId = selectedThreadId.value,
                archiveNotice = archiveNotice.value,
                archiveBusy = archiveBusy.value || mediaLoading.value ||
                    inFlightJournalId.value != null ||
                    PendingMediaImport.journalPending.value,
                archiveSaveRetryable = archiveSaveRetryable.value,
                onRetryArchive = {
                    val pending = awaitingSave
                    if (pending != null) persistFinal(pending) else reloadArchive()
                },
                onNewThread = ::newConversation,
                onOpenThread = ::selectConversation,
                onSetThreadPinned = ::setConversationPinned,
                onDeleteThread = ::deleteConversation,
            )
        }
        if (firstProcessLaunch) {
            archiveBusy.value = true
            disk.execute {
                try {
                    archive.recoverStrandedPending()
                    val retainedIds = archive.retainedMediaIds()
                    val rememberedDrafts = PendingMediaImport.currentSelections()
                    if (rememberedDrafts.any { it.media.id in retainedIds }) {
                        // The SQLite journal wins over stale restored bundles.
                        runOnUiThread {
                            PendingMediaImport.setSelections(
                                PendingMediaImport.currentSelections().filterNot {
                                    it.media.id in retainedIds
                                }
                            )
                        }
                    }
                    mediaStore.pruneStaleUnreferenced(
                        retainedIds, rememberedDrafts.map { it.media.id }.toSet(),
                    )
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
        val drafts = PendingMediaImport.currentSelections().ifEmpty {
            reservedSend?.takeIf { it.persistedTurnId == null }?.selections.orEmpty()
        }.map { it.media }
        if (drafts.isNotEmpty()) {
            outState.putStringArrayList("media_ids",
                ArrayList(drafts.map { it.id }))
            outState.putStringArrayList("media_names",
                ArrayList(drafts.map { it.filename }))
            outState.putStringArrayList("media_types",
                ArrayList(drafts.map { it.mimeType }))
            outState.putIntegerArrayList("media_sizes",
                ArrayList(drafts.map { it.sizeBytes }))
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
