package dev.ciryva.core.agent

/**
 * A1.1: deterministic single-step UI execution verifier, with no Android
 * dependency, LLM privilege, network traffic or unbounded retries.
 *
 * A changed accessibility tree proves an observed UI transition; it never
 * proves an external app committed a task or posted a message.
 */
data class AgentWindowObservation(
    val packageName: String,
    val windowId: Int,
    val labels: List<String>,
) {
    init {
        require(AgentAuthorizationKernel.isPackageValid(packageName))
        require(labels.size <= 30 && labels.all { it.length <= 100 })
    }
}

enum class AgentStepStatus {
    WAITING_FOR_CHANGE,
    UI_CHANGED,
    UNCHANGED,
    APP_LEFT_FOREGROUND,
    INTERRUPTED,
    EXPIRED,
}

data class AgentStepReceipt(
    val actionId: Long,
    val packageName: String,
    val targetLabel: String,
    val status: AgentStepStatus,
    val beforeCount: Int,
    val afterCount: Int?,
    val elapsedMillis: Long,
) {
    /** Never imply a remote service or server persisted the UI interaction. */
    val remoteEffectVerified: Boolean = false
}

class AgentExecutionLoop(
    private val stopEpoch: () -> Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val MAX_SETTLE_MILLIS = 2_500L
    }

    private data class Pending(
        val id: Long,
        val expectedEpoch: Long,
        val packageName: String,
        val label: String,
        val before: AgentWindowObservation,
        val started: Long,
    )

    private var pending: Pending? = null
    private var nextId = 0L

    /**
     * Called only AFTER a valid one-use permit was consumed and Android's
     * performAction(ACTION_CLICK) returned true. Start observation promptly.
     */
    @Synchronized fun start(
        epoch: Long,
        packageName: String,
        targetLabel: String,
        before: AgentWindowObservation,
    ): AgentStepReceipt {
        require(before.packageName == packageName)
        require(AgentAuthorizationKernel.isSafeLabel(targetLabel))
        pending = null
        val id = ++nextId
        if (stopEpoch() != epoch) return receipt(
            id, packageName, targetLabel, AgentStepStatus.INTERRUPTED,
            before.labels.size, null, 0,
        )
        pending = Pending(id, epoch, packageName, targetLabel, before, nowMillis())
        return receipt(
            id, packageName, targetLabel, AgentStepStatus.WAITING_FOR_CHANGE,
            before.labels.size, null, 0,
        )
    }

    @Synchronized fun hasPending(): Boolean = pending != null

    /**
     * Called on the next real Accessibility event (or a timeout).
     * Waiting does not consume any more permissions or issue new taps.
     */
    @Synchronized fun observe(after: AgentWindowObservation?): AgentStepReceipt? {
        val step = pending ?: return null
        val elapsed = (nowMillis() - step.started).coerceAtLeast(0L)
        val status = when {
            stopEpoch() != step.expectedEpoch -> AgentStepStatus.INTERRUPTED
            elapsed >= MAX_SETTLE_MILLIS -> AgentStepStatus.EXPIRED
            after == null -> AgentStepStatus.WAITING_FOR_CHANGE
            after.packageName != step.packageName -> AgentStepStatus.APP_LEFT_FOREGROUND
            after.windowId != step.before.windowId || after.labels != step.before.labels ->
                AgentStepStatus.UI_CHANGED
            else -> AgentStepStatus.WAITING_FOR_CHANGE
        }
        val outcome = receipt(step.id, step.packageName, step.label, status,
            step.before.labels.size, after?.labels?.size, elapsed)
        if (status != AgentStepStatus.WAITING_FOR_CHANGE) pending = null
        return outcome
    }

    /**
     * A delayed timeout may only settle the exact step that scheduled it.
     * A timer from an earlier run must NEVER expire a newer approved action.
     */
    @Synchronized fun expire(expectedActionId: Long): AgentStepReceipt? {
        val step = pending ?: return null
        if (step.id != expectedActionId) return null
        pending = null
        return receipt(step.id, step.packageName, step.label,
            if (stopEpoch() != step.expectedEpoch) AgentStepStatus.INTERRUPTED
            else AgentStepStatus.UNCHANGED, step.before.labels.size, null,
            (nowMillis() - step.started).coerceAtLeast(0L))
    }

    @Synchronized fun stop(): AgentStepReceipt? {
        val step = pending ?: return null
        pending = null
        return receipt(step.id, step.packageName, step.label, AgentStepStatus.INTERRUPTED,
            step.before.labels.size, null, (nowMillis() - step.started).coerceAtLeast(0L))
    }

    private fun receipt(
        id: Long, pkg: String, label: String, status: AgentStepStatus,
        before: Int, after: Int?, elapsed: Long,
    ) = AgentStepReceipt(id, pkg, label, status, before, after, elapsed)
}
