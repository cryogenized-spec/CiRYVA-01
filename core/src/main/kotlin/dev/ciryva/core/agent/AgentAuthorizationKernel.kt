package dev.ciryva.core.agent

/** A0: one-shot, exact-app authorization. The LLM never creates or extends grants. */
enum class AgentOperation { OBSERVE_WINDOW, TAP_EXACT_LABEL }

data class AgentPermit(
    val id: Long,
    val packageName: String,
    val operation: AgentOperation,
    val exactLabel: String?,
    val expiresAtMillis: Long,
)

class AgentAuthorizationKernel(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val MAX_GRANT_MILLIS = 30_000L
        private val PACKAGE = Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+")
        private val HIGH_IMPACT = Regex(
            "\\b(delete|remove|pay|purchase|buy|send|post|publish|submit|confirm|" +
                "install|grant|allow|reset|factory|erase|wipe|transfer|checkout|" +
                "unsubscribe|archive|sign out|log out|disable security)\\b",
            RegexOption.IGNORE_CASE,
        )
        fun isPackageValid(value: String): Boolean =
            value.length in 3..150 && PACKAGE.matches(value)
        fun isSafeLabel(value: String): Boolean {
            val trimmed = value.trim()
            return trimmed.length in 2..80 && '\n' !in trimmed &&
                '\r' !in trimmed && !HIGH_IMPACT.containsMatchIn(trimmed)
        }
    }

    private var nextId = 0L
    private var active: AgentPermit? = null

    /** An explicit local user gesture creates a single-use grant with a hard expiry. */
    @Synchronized fun arm(
        packageName: String,
        operation: AgentOperation,
        exactLabel: String? = null,
        ttlMillis: Long = MAX_GRANT_MILLIS,
    ): AgentPermit {
        require(isPackageValid(packageName)) { "Enter a valid Android package name." }
        require(ttlMillis in 1..MAX_GRANT_MILLIS) { "Grant duration is invalid." }
        if (operation == AgentOperation.TAP_EXACT_LABEL) {
            require(exactLabel != null && isSafeLabel(exactLabel)) {
                "This action needs a safe exact label. Consequential actions require a later approval workflow."
            }
        } else require(exactLabel == null) { "Observe cannot include a tap target." }
        val permit = AgentPermit(++nextId, packageName, operation,
            exactLabel?.trim(), nowMillis() + ttlMillis)
        active = permit
        return permit
    }

    @Synchronized fun pending(): AgentPermit? {
        val value = active ?: return null
        if (value.expiresAtMillis <= nowMillis()) {
            active = null
            return null
        }
        return value
    }

    /** Never consume a grant merely because an unrelated app displayed a window. */
    @Synchronized fun allowed(packageName: String, operation: AgentOperation,
                              exactLabel: String? = null): Boolean {
        val permit = pending() ?: return false
        return permit.packageName == packageName && permit.operation == operation &&
            permit.exactLabel == exactLabel
    }

    /** Consumes BEFORE any external action. Replayed events cannot dispatch duplicates. */
    @Synchronized fun consume(
        permitId: Long,
        packageName: String,
        operation: AgentOperation,
        exactLabel: String? = null,
    ): Boolean {
        if (!allowed(packageName, operation, exactLabel)) return false
        if (active?.id != permitId) return false
        active = null
        return true
    }

    /** Emergency-stop revokes all rights immediately; the app must explicitly arm anew. */
    @Synchronized fun stop(): Boolean {
        val hadWork = active != null
        active = null
        nextId++
        return hadWork
    }
}
