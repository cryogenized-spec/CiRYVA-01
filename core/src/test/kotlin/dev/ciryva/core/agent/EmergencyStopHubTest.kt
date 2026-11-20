package dev.ciryva.core.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmergencyStopHubTest {
    @Test fun stopInvalidatesGenerationBeforeInvokingCancellation() {
        val hub = EmergencyStopHub()
        val old = hub.generation()
        var stopped = false
        val handle = hub.register {
            assertFalse(hub.isCurrent(old))
            stopped = true
        }
        val receipt = hub.stopAll()
        assertTrue(stopped)
        assertEquals(1, receipt.notified)
        assertEquals(0, receipt.failed)
        handle.close()
    }

    @Test fun stoppingOneStreamDoesNotBlockOtherRegisteredStreamCallbacks() {
        val hub = EmergencyStopHub()
        var secondCalled = 0
        hub.register { error("Simulated transport cancellation failure") }
        val other = hub.register { secondCalled++ }
        val result = hub.stopAll()
        assertEquals(2, result.notified)
        assertEquals(1, result.failed)
        assertEquals(1, secondCalled)
        other.close()
        hub.stopAll()
        assertEquals(1, secondCalled)
    }

    @Test fun stopWithNoRegistrationsStillInvalidatesQueuedWork() {
        val hub = EmergencyStopHub()
        val old = hub.generation()
        assertEquals(0, hub.stopAll().notified)
        assertFalse(hub.isCurrent(old))
        assertEquals(old + 1, hub.generation())
    }
}
