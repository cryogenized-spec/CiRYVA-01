package dev.ciryva.core.agent

/**
 * Thread-safe, synchronous cancellation fan-out with a monotonic stop epoch.
 * Every subsystem (chat, agent worker, API/MCP) registers its own cancel action.
 * Callbacks are invoked outside the lock to avoid deadlocks and are best-effort;
 * the generation is invalidated FIRST, so stale work cannot claim authority.
 */
class EmergencyStopHub {
    data class StopReceipt(val epoch: Long, val notified: Int, val failed: Int)

    private val callbacks = LinkedHashMap<Long, () -> Unit>()
    private var nextId = 0L
    private var epoch = 0L

    @Synchronized fun generation(): Long = epoch

    @Synchronized fun isCurrent(generation: Long): Boolean = epoch == generation

    @Synchronized fun register(cancel: () -> Unit): AutoCloseable {
        val id = ++nextId
        callbacks[id] = cancel
        return AutoCloseable { synchronized(this) { callbacks.remove(id) } }
    }

    fun stopAll(): StopReceipt {
        val (currentEpoch, targets) = synchronized(this) {
            epoch++
            epoch to callbacks.values.toList()
        }
        var failures = 0
        targets.forEach { callback ->
            try { callback() }
            catch (_: Exception) { failures++ }
        }
        return StopReceipt(currentEpoch, targets.size, failures)
    }
}
