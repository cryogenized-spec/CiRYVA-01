package dev.ciryva.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import dev.ciryva.core.agent.AgentAuthorizationKernel
import dev.ciryva.core.agent.AgentOperation
import dev.ciryva.core.agent.AgentPermit
import dev.ciryva.core.agent.EmergencyStopHub
import dev.ciryva.core.agent.AgentExecutionLoop
import dev.ciryva.core.agent.AgentStepReceipt
import dev.ciryva.core.agent.AgentStepStatus
import dev.ciryva.core.agent.AgentWindowObservation

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
    private val execution = AgentExecutionLoop(AgentStopSwitch::generation) {
        android.os.SystemClock.elapsedRealtime()
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun needsWindowEvent(): Boolean = kernel.pending() != null || execution.hasPending()

    private fun observation(root: AccessibilityNodeInfo): AgentWindowObservation? {
        val pkg = root.packageName?.toString() ?: return null
        if (!AgentAuthorizationKernel.isPackageValid(pkg)) return null
        return AgentWindowObservation(pkg, root.windowId, readLabels(root))
    }

    private fun publishReceipt(receipt: AgentStepReceipt) {
        val description = when (receipt.status) {
            AgentStepStatus.WAITING_FOR_CHANGE -> return
            AgentStepStatus.UI_CHANGED -> "UI changed after the tap; remote task outcome NOT verified."
            AgentStepStatus.UNCHANGED -> "Tap dispatched; no UI change verified before deadline."
            AgentStepStatus.EXPIRED -> "Tap verification timed out; outcome unknown."
            AgentStepStatus.APP_LEFT_FOREGROUND -> "Target app left foreground; outcome unknown."
            AgentStepStatus.INTERRUPTED -> "Tap verification interrupted by STOP ALL."
        }
        note("Step #" + receipt.actionId + ": " + description)
    }

    /** Owner taps one of the visible actions; UI never grants itself Accessibility. */
    fun arm(packageName: String, operation: AgentOperation, label: String? = null): Boolean {
        val permit = try { kernel.arm(packageName.trim(), operation, label) }
            catch (error: IllegalArgumentException) {
                note(error.message ?: "Invalid permission request.")
                return false
            }
        val authorizationEvent: String =
            "Owner authorized one " + operation.name + " on " + permit.packageName
        state.value = AgentDeviceSnapshot(
            status = "Armed " + operation.name.lowercase() + " for " + permit.packageName +
                "; expires in 30 seconds.",
            targetPackage = permit.packageName,
            operation = operation.name,
            events = (state.value.events + authorizationEvent).takeLast(16),
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
        val currentRoot = service.rootInActiveWindow
        // A completed one-shot permit can still be awaiting actual UI evidence.
        if (execution.hasPending()) {
            val expected = execution.expectedPackage()
            val seen = currentRoot?.packageName?.toString()
            // Cross-package context is metadata only; do not traverse labels.
            val receipt = if (currentRoot != null && seen != expected) {
                execution.foregroundDeparted()
            } else {
                execution.observe(currentRoot?.let(::observation))
            }
            receipt?.let(::publishReceipt)
            return
        }
        val permit = kernel.pending() ?: return
        val root = currentRoot ?: return
        // Only process a visible window of the exact one-time permitted package.
        if (root.packageName?.toString() != permit.packageName) return
        val epoch = AgentStopSwitch.generation()
        when (permit.operation) {
            AgentOperation.OBSERVE_WINDOW -> {
                if (!kernel.consume(permit.id, permit.packageName, permit.operation)) return
                val labels = readLabels(root)
                val observationEvent: String =
                    "Observed " + permit.packageName + ": " + labels.size +
                        " visible labels; no network upload."
                state.value = state.value.copy(
                    status = "Observed " + permit.packageName +
                        " locally. No tap was performed.",
                    labels = labels,
                    events = (state.value.events + observationEvent).takeLast(16),
                )
            }
            AgentOperation.TAP_EXACT_LABEL -> {
                val label = permit.exactLabel ?: return
                val target = findClickableLabel(root, label) ?: return
                // A node can be invalidated by app re-rendering or IME movement.
                // Refresh and recheck its OWN semantics immediately before action.
                if (!runCatching { target.refresh() }.getOrDefault(false) ||
                    !AgentAuthorizationKernel.isExactActionableLabel(
                        target.text?.toString(), target.contentDescription?.toString(),
                        label, target.isClickable,
                    ) || target.packageName?.toString() != permit.packageName) {
                    note("Target navigation node became stale; no tap sent.")
                    return
                }
                // Repeat the privilege and stop checks immediately before dispatch.
                if (!AgentStopSwitch.isCurrent(epoch)) return
                if (!kernel.consume(permit.id, permit.packageName, permit.operation, label)) return
                if (!AgentStopSwitch.isCurrent(epoch)) return
                val before = observation(root) ?: return
                val issued = runCatching {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
                if (!AgentStopSwitch.isCurrent(epoch)) return
                if (issued) {
                    val start = execution.start(epoch, permit.packageName, label, before)
                    if (start.status == AgentStepStatus.WAITING_FOR_CHANGE) {
                        // This is a one-shot timeout, not polling or a background agent.
                        mainHandler.postDelayed({
                            execution.expire(start.actionId)?.let(::publishReceipt)
                        }, AgentExecutionLoop.MAX_SETTLE_MILLIS)
                    } else publishReceipt(start)
                }
                // ACTION_CLICK=true only confirms dispatch, not a saved app result.
                val receiptLine: String = if (issued) {
                    "Tap dispatched in " + permit.packageName + ": " + label +
                        " (verification pending)"
                } else {
                    "Tap rejected by Android: " + label
                }
                state.value = state.value.copy(
                    status = if (issued)
                        "Tap dispatched for '" + label + "'; awaiting a fresh UI observation."
                        else "Android rejected the tap for '" + label + "'.",
                    labels = emptyList(),
                    events = (state.value.events + receiptLine).takeLast(16),
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
        var uniqueMatch: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty() && seen++ < 120) {
            val (node, depth) = queue.removeFirst()
            if (!node.isVisibleToUser || node.isPassword || node.isEditable) continue
            // Only direct clickable node semantics can satisfy this grant.
            // A harmless child in a destructive parent is not authorized.
            if (AgentAuthorizationKernel.isExactActionableLabel(
                    node.text?.toString(), node.contentDescription?.toString(),
                    targetLabel, node.isClickable,
                ) && node.packageName?.toString() == root.packageName?.toString()) {
                // Never guess between multiple controls with the same name.
                if (uniqueMatch != null) return null
                uniqueMatch = node
            }
            if (depth < 7) {
                for (i in 0 until node.childCount.coerceAtMost(25)) {
                    node.getChild(i)?.let { queue.add(it to depth + 1) }
                }
            }
        }
        // An incomplete tree is not enough evidence of a unique target.
        return if (queue.isEmpty()) uniqueMatch else null
    }

    fun stop() {
        kernel.stop()
        execution.stop()
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
    private val hub = EmergencyStopHub()

    fun register(stopStream: () -> Unit): AutoCloseable = hub.register(stopStream)
    fun generation(): Long = hub.generation()
    fun isCurrent(epoch: Long): Boolean = hub.isCurrent(epoch)

    fun stopAll() {
        // Block future Accessibility events before invoking any stream callbacks.
        AgentRuntime.stop()
        val receipt = hub.stopAll()
        if (receipt.failed > 0) {
            AgentRuntime.note(
                "STOP ALL issued, but " + receipt.failed +
                    " cancellation handler(s) reported an error. Verify the activity stopped.")
        }
    }
}
