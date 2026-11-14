package dev.ciryva.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.mutableStateOf
import dev.ciryva.core.agent.AgentAuthorizationKernel
import dev.ciryva.core.agent.AgentOperation
import dev.ciryva.core.agent.AgentPermit
import java.util.concurrent.atomic.AtomicLong

/** No model instructions, background polling, account secrets or raw screen logs. */
internal data class AgentDeviceSnapshot(
    val status: String = "Idle. No device action is authorized.",
    val targetPackage: String? = null,
    val operation: String? = null,
    val labels: List<String> = emptyList(),
    val events: List<String> = emptyList(),
)

internal object AgentRuntime {
    private val kernel = AgentAuthorizationKernel { android.os.SystemClock.elapsedRealtime() }
    val state = mutableStateOf(AgentDeviceSnapshot())

    /** Owner taps one of the visible actions; UI never grants itself Accessibility. */
    fun arm(packageName: String, operation: AgentOperation, label: String? = null): Boolean {
        val permit = try { kernel.arm(packageName.trim(), operation, label) }
            catch (error: IllegalArgumentException) {
                note(error.message ?: "Invalid permission request.")
                return false
            }
        state.value = AgentDeviceSnapshot(
            status = "Armed " + operation.name.lowercase() + " for " + permit.packageName +
                "; expires in 30 seconds.",
            targetPackage = permit.packageName,
            operation = operation.name,
            events = (state.value.events +
                "Owner authorized one " + operation.name + " on " + permit.packageName)
                .takeLast(16),
        )
        return true
    }

    fun pending(): AgentPermit? = kernel.pending()

    fun note(message: String) {
        state.value = state.value.copy(
            status = message,
            events = (state.value.events + message.take(160)).takeLast(16),
        )
    }

    fun onAccessibilityWindow(service: AccessibilityService) {
        val permit = kernel.pending() ?: return
        // Only process a visible window of the exact one-time permitted package.
        val root = service.rootInActiveWindow ?: return
        if (root.packageName?.toString() != permit.packageName) return
        when (permit.operation) {
            AgentOperation.OBSERVE_WINDOW -> {
                if (!kernel.consume(permit.id, permit.packageName, permit.operation)) return
                val labels = readLabels(root)
                state.value = state.value.copy(
                    status = "Observed " + permit.packageName +
                        " locally. No tap was performed.",
                    labels = labels,
                    events = (state.value.events +
                        "Observed " + permit.packageName + ": " + labels.size +
                            " visible labels; no network upload.").takeLast(16),
                )
            }
            AgentOperation.TAP_EXACT_LABEL -> {
                val label = permit.exactLabel ?: return
                val target = findClickableLabel(root, label) ?: return
                if (!kernel.consume(permit.id, permit.packageName, permit.operation, label)) return
                val issued = runCatching {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
                // ACTION_CLICK=true acknowledges dispatch, NOT server-side effect.
                state.value = state.value.copy(
                    status = if (issued)
                        "Tap dispatched for '" + label + "'. App outcome not verified."
                        else "Android rejected the tap for '" + label + "'.",
                    labels = emptyList(),
                    events = (state.value.events +
                        if (issued) "Tap dispatched in " + permit.packageName +
                            ": " + label + " (unverified effect)"
                        else "Tap rejected by Android: " + label).takeLast(16),
                )
            }
        }
    }

    private fun readLabels(root: AccessibilityNodeInfo): List<String> {
        val output = mutableListOf<String>()
        val nodes = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        nodes.add(root to 0)
        var visited = 0
        while (nodes.isNotEmpty() && visited < 120 && output.size < 30) {
            val (node, depth) = nodes.removeFirst()
            visited++
            if (!node.isVisibleToUser || node.isPassword) continue
            if (!node.isEditable) {
                val text = (node.text ?: node.contentDescription)?.toString()
                    ?.replace('\n', ' ')?.trim()
                if (!text.isNullOrBlank() && text.length <= 100 &&
                    !output.contains(text)) output.add(text)
            }
            if (depth < 7) {
                for (i in 0 until node.childCount.coerceAtMost(25)) {
                    node.getChild(i)?.let { nodes.add(it to depth + 1) }
                }
            }
        }
        return output
    }

    private fun findClickableLabel(
        root: AccessibilityNodeInfo,
        targetLabel: String,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var seen = 0
        while (queue.isNotEmpty() && seen++ < 120) {
            val (node, depth) = queue.removeFirst()
            if (!node.isVisibleToUser || node.isPassword || node.isEditable) continue
            val labels = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            if (labels.any { it == targetLabel }) {
                var ancestor: AccessibilityNodeInfo? = node
                repeat(5) {
                    if (ancestor?.isClickable == true &&
                        ancestor?.packageName?.toString() == root.packageName?.toString()) {
                        return ancestor
                    }
                    ancestor = ancestor?.parent
                }
                return null
            }
            if (depth < 7) {
                for (i in 0 until node.childCount.coerceAtMost(25)) {
                    node.getChild(i)?.let { queue.add(it to depth + 1) }
                }
            }
        }
        return null
    }

    fun stop() {
        kernel.stop()
        state.value = AgentDeviceSnapshot(
            status = "EMERGENCY STOP: all current agent permits revoked.",
            events = (state.value.events + "STOP ALL pressed; one-time grants revoked.")
                .takeLast(16),
        )
    }
}

/**
 * Process-local shared stop. Every registered live stream gets a cancellation
 * request and every current agent authorization is revoked. App process death
 * also terminates all of its in-process agents/workers.
 */
internal object AgentStopSwitch {
    private val seq = AtomicLong(0)
    private val callbacks = LinkedHashMap<Long, () -> Unit>()

    @Synchronized fun register(stopStream: () -> Unit): AutoCloseable {
        val id = seq.incrementAndGet()
        callbacks[id] = stopStream
        return AutoCloseable { synchronized(this) { callbacks.remove(id) } }
    }

    fun stopAll() {
        AgentRuntime.stop()
        val targets = synchronized(this) { callbacks.values.toList() }
        targets.forEach { runCatching { it() } }
    }
}
