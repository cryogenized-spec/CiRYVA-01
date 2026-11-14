package dev.ciryva.core.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentAuthorizationKernelTest {
    private var time = 1_000L
    private val kernel = AgentAuthorizationKernel { time }

    @Test fun noGrantCanReadOrTap() {
        assertFalse(kernel.allowed("com.example.reader", AgentOperation.OBSERVE_WINDOW))
        assertFalse(kernel.allowed("com.example.reader", AgentOperation.TAP_EXACT_LABEL, "Settings"))
    }

    @Test fun exactPackageAndSingleUseAreRequired() {
        val permit = kernel.arm("com.example.reader", AgentOperation.OBSERVE_WINDOW)
        assertFalse(kernel.allowed("com.other.app", AgentOperation.OBSERVE_WINDOW))
        assertFalse(kernel.allowed("com.example.reader", AgentOperation.TAP_EXACT_LABEL, "Settings"))
        assertTrue(kernel.consume(permit.id,"com.example.reader",AgentOperation.OBSERVE_WINDOW))
        assertFalse(kernel.consume(permit.id,"com.example.reader",AgentOperation.OBSERVE_WINDOW))
        assertFalse(kernel.allowed("com.example.reader", AgentOperation.OBSERVE_WINDOW))
    }

    @Test fun tapMustBeExplicitAndExactAndCannotReplay() {
        val permit = kernel.arm("com.example.reader", AgentOperation.TAP_EXACT_LABEL, "Settings")
        assertFalse(kernel.consume(permit.id,"com.example.reader",AgentOperation.TAP_EXACT_LABEL,"Settings!"))
        assertTrue(kernel.consume(permit.id,"com.example.reader",AgentOperation.TAP_EXACT_LABEL,"Settings"))
        assertFalse(kernel.consume(permit.id,"com.example.reader",AgentOperation.TAP_EXACT_LABEL,"Settings"))
    }

    @Test fun expirationRevokesGrantAndNewEpochInvalidatesOldReceipt() {
        val old = kernel.arm("com.example.reader",AgentOperation.OBSERVE_WINDOW,ttlMillis=10)
        time += 11
        assertEquals(null,kernel.pending())
        val next = kernel.arm("com.example.reader",AgentOperation.OBSERVE_WINDOW)
        assertNotNull(next)
        assertFalse(kernel.consume(old.id,"com.example.reader",AgentOperation.OBSERVE_WINDOW))
        assertTrue(kernel.consume(next.id,"com.example.reader",AgentOperation.OBSERVE_WINDOW))
    }

    @Test fun emergencyStopRevokesAndDoesNotAutomaticallyRearm() {
        val permit=kernel.arm("com.example.reader",AgentOperation.TAP_EXACT_LABEL,"Open")
        assertTrue(kernel.stop())
        assertFalse(kernel.consume(permit.id,"com.example.reader",AgentOperation.TAP_EXACT_LABEL,"Open"))
        assertFalse(kernel.stop())
    }

    @Test fun dangerousOrMalformedRequestsFailClosed() {
        for (label in listOf("Delete", "Post", "Send", "Pay now", "Factory reset",
                "Grant permission", "Publish comment", "Remove member")) {
            assertThrows(IllegalArgumentException::class.java) {
                kernel.arm("com.example.reader",AgentOperation.TAP_EXACT_LABEL,label)
            }
        }
        for (pkg in listOf("", "android", "com example", "com..reader", "123.bad")) {
            assertThrows(IllegalArgumentException::class.java) {
                kernel.arm(pkg,AgentOperation.OBSERVE_WINDOW)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kernel.arm("com.example.reader",AgentOperation.OBSERVE_WINDOW,ttlMillis=60000)
        }
    }
}
