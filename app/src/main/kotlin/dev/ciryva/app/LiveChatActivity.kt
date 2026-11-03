package dev.ciryva.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.ciryva.core.stream.ResponsesHttpTransport
import dev.ciryva.core.stream.TextResponseRequest
import dev.ciryva.core.turn.ResponseTurnOutcome

/**
 * P05 Android BYOK chat and device-private history. The app never opens the
 * provider connection merely by loading a previous conversation or the key.
 *
 * A pending user prompt is committed locally *before* any chargeable Send.
 * A terminal provider result updates that same row exactly once, and only
 * completed pairs are eligible for bounded follow-up context.
 */
class LiveChatActivity : ComponentActivity() {
    private val snapshot = mutableStateOf(LiveChatSnapshot())
    private val keyPresent = mutableStateOf(false)
    private val threadList = mutableStateOf<List<LocalThread>>(emptyList())
    private val visibleTurns = mutableStateOf<List<LocalTurn>>(emptyList())
    private val selectedThreadId = mutableStateOf<String?>(null)
    private val archiveNotice = mutableStateOf<String?>(null)

    private lateinit var archive: LocalConversationArchive
    private var controller: LiveChatController? = null
    private var pendingTurnId: String? = null

    private fun refreshArchive() {
        val threads = archive.listThreads()
        threadList.value = threads
        selectedThreadId.value?.let { selected ->
            if (threads.none { it.id == selected }) {
                selectedThreadId.value = threads.firstOrNull()?.id
            }
        }
        visibleTurns.value = selectedThreadId.value?.let(archive::listTurns)
            ?: emptyList()
    }

    private fun turnState(outcome: ResponseTurnOutcome?): String = when (outcome) {
        ResponseTurnOutcome.COMPLETED -> "completed"
        ResponseTurnOutcome.CANCELLED -> "cancelled"
        ResponseTurnOutcome.INCOMPLETE -> "incomplete"
        null -> "interrupted"
        else -> "failed"
    }

    private fun onStreamUpdate(value: LiveChatSnapshot) {
        if (isFinishing || isDestroyed) return
        snapshot.value = value
        val pendingId = pendingTurnId
        if (!value.busy && pendingId != null) {
            // Clear the marker before updating the UI: no duplicate commit if
            // the controller's reset notification is immediately delivered.
            pendingTurnId = null
            try {
                archive.settleTurn(
                    turnId = pendingId,
                    state = turnState(value.outcome),
                    answer = value.assistantText,
                    summary = value.reasoningSummary,
                    usage = value.usage,
                )
                archiveNotice.value = null
                refreshArchive()
                controller?.clearView()
            } catch (_: RuntimeException) {
                // Never claim successful archival when SQLite rejected a write.
                archiveNotice.value = "Could not save the response locally. Check device storage."
            }
        }
    }

    private fun sendWithArchive(request: TextResponseRequest): Boolean {
        val runner = controller ?: return false
        if (runner.snapshot().busy || pendingTurnId != null) return false
        var createdId: String? = null
        return try {
            val id = selectedThreadId.value ?: archive.createThread(request.prompt).also {
                selectedThreadId.value = it.id
            }.id
            val history = archive.boundedContext(id)
            val expandedRequest = request.copy(history = history)
            createdId = archive.beginTurn(id, request.prompt, request.model)
            pendingTurnId = createdId
            if (runner.send(expandedRequest)) {
                archiveNotice.value = null
                // Show the current request through streaming snapshot rather
                // than rendering a second copy of the pending database row.
                refreshArchive()
                true
            } else {
                archive.discardUnstarted(createdId!!)
                pendingTurnId = null
                refreshArchive()
                false
            }
        } catch (_: RuntimeException) {
            createdId?.let { archive.discardUnstarted(it) }
            pendingTurnId = null
            archiveNotice.value = "Unable to save conversation. No request was started."
            false
        }
    }

    private fun newConversation() {
        if (controller?.snapshot()?.busy == true) return
        try {
            selectedThreadId.value = archive.createThread().id
            controller?.clearView()
            archiveNotice.value = null
            refreshArchive()
        } catch (_: RuntimeException) {
            archiveNotice.value = "Unable to create a local conversation."
        }
    }

    private fun selectConversation(id: String) {
        if (controller?.snapshot()?.busy == true || pendingTurnId != null) return
        if (threadList.value.none { it.id == id }) return
        selectedThreadId.value = id
        controller?.clearView()
        archiveNotice.value = null
        refreshArchive()
    }

    private fun deleteConversation(id: String) {
        if (controller?.snapshot()?.busy == true || pendingTurnId != null) return
        try {
            if (archive.deleteThread(id)) {
                if (selectedThreadId.value == id) selectedThreadId.value = null
                controller?.clearView()
                refreshArchive()
                if (selectedThreadId.value == null) {
                    selectedThreadId.value = threadList.value.firstOrNull()?.id
                    refreshArchive()
                }
            }
            archiveNotice.value = null
        } catch (_: RuntimeException) {
            archiveNotice.value = "Could not delete the selected conversation."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(22, 17, 24)),
        )
        archive = LocalConversationArchive(applicationContext)
        selectedThreadId.value = archive.listThreads().firstOrNull()?.id
        refreshArchive()

        val keyStore = AndroidByokKeyStore(applicationContext)
        controller = LiveChatController(
            client = ChatStreamClient { request, key, cancel, onEvent ->
                ResponsesHttpTransport().stream(request, key, cancel, onEvent)
            },
            credentialProvider = { keyStore.load() },
            onUpdate = { value ->
                runOnUiThread { onStreamUpdate(value) }
            },
        )
        setContent {
            LiveChatScreen(
                snapshot = snapshot.value,
                hasKey = keyPresent.value,
                onBack = { startActivity(Intent(this, MainActivity::class.java)) },
                onKeys = { startActivity(Intent(this, ByokSettingsActivity::class.java)) },
                onSend = ::sendWithArchive,
                onCancel = { controller?.cancel() ?: false },
                archiveEnabled = true,
                archiveThreads = threadList.value,
                archiveTurns = visibleTurns.value.filterNot {
                    it.id == pendingTurnId
                },
                currentThreadId = selectedThreadId.value,
                archiveNotice = archiveNotice.value,
                onNewThread = ::newConversation,
                onOpenThread = ::selectConversation,
                onDeleteThread = ::deleteConversation,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        keyPresent.value = AndroidByokKeyStore(applicationContext).hasKey()
    }

    override fun onStop() {
        // Leaving foreground cancels generation. Preserve partial visible
        // text as *interrupted*, never as a provider-confirmed completed turn.
        controller?.cancel()
        pendingTurnId?.let { turnId ->
            pendingTurnId = null
            try {
                val current = controller?.snapshot()
                archive.settleTurn(
                    turnId, "interrupted",
                    current?.assistantText.orEmpty(),
                    current?.reasoningSummary.orEmpty(),
                )
                refreshArchive()
            } catch (_: RuntimeException) {
                archiveNotice.value = "Interrupted response could not be saved."
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        controller?.close()
        controller = null
        archive.close()
        super.onDestroy()
    }
}
