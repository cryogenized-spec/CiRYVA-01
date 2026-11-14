package dev.ciryva.core.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentExecutionLoopTest {
    private val stop = EmergencyStopHub()
    private var clock = 42_000L
    private val loop = AgentExecutionLoop(stop::generation, { clock })
    private val original = AgentWindowObservation(
        "com.example.notes", 7, listOf("Home", "Tasks", "Overview"),
    )

    @Test fun stateDiffIsRealUiEvidenceButNeverClaimsRemoteWrite() {
        val started = loop.start(stop.generation(), "com.example.notes", "Tasks", original)
        assertEquals(AgentStepStatus.WAITING_FOR_CHANGE, started.status)
        assertTrue(loop.hasPending())
        assertEquals(AgentStepStatus.WAITING_FOR_CHANGE, loop.observe(original)?.status)
        clock += 100L
        val changed = loop.observe(original.copy(labels=listOf("Tasks", "My tasks")))
        assertEquals(AgentStepStatus.UI_CHANGED, changed?.status)
        assertFalse(changed!!.remoteEffectVerified)
        assertFalse(loop.hasPending())
        assertNull(loop.observe(original))
    }

    @Test fun stopInvalidatesReceiptAndPendingWorkImmediately() {
        loop.start(stop.generation(), "com.example.notes", "Tasks", original)
        stop.stopAll()
        assertEquals(AgentStepStatus.INTERRUPTED,
            loop.observe(original.copy(labels = listOf("Saved")))?.status)
        assertFalse(loop.hasPending())
        assertNull(loop.observe(original))
    }

    @Test fun staleCapturedEpochCannotStartAJobAfterStop() {
        val stale = stop.generation()
        stop.stopAll()
        val result = loop.start(stale, "com.example.notes", "Tasks", original)
        assertEquals(AgentStepStatus.INTERRUPTED,result.status)
        assertFalse(loop.hasPending())
    }

    @Test fun differentForegroundAppDoesNotCountAsTaskSuccess() {
        loop.start(stop.generation(), "com.example.notes", "Tasks", original)
        val result = loop.observe(AgentWindowObservation(
            "com.example.browser", 8, listOf("Homepage"),
        ))
        assertEquals(AgentStepStatus.APP_LEFT_FOREGROUND,result?.status)
        assertEquals(false,result?.remoteEffectVerified)
    }

    @Test fun timeoutNeverReturnsSuccessEvenIfUiWasSilent() {
        loop.start(stop.generation(), "com.example.notes", "Tasks", original)
        clock += 2600L
        val timeout = loop.observe(null)
        assertEquals(AgentStepStatus.EXPIRED,timeout?.status)
        assertFalse(loop.hasPending())
    }

    @Test fun manualDeadlineReportsUnknownAndRevokesFurtherObservation() {
        loop.start(stop.generation(), "com.example.notes", "Tasks", original)
        clock += 500L
        val r = loop.expire()
        assertNotNull(r)
        assertEquals(AgentStepStatus.UNCHANGED, r?.status)
        assertFalse(loop.hasPending())
    }

    @Test fun rapidStopReturnsInterruptedWithoutWaitingForAnyAppEvent() {
        loop.start(stop.generation(), "com.example.notes", "Tasks", original)
        val interrupted = loop.stop()
        assertEquals(AgentStepStatus.INTERRUPTED, interrupted?.status)
        assertNull(loop.expire())
    }

    @Test fun independentNewTurnCannotAccidentallyReuseOldReceipt() {
        val old = loop.start(stop.generation(), "com.example.notes", "Tasks", original)
        loop.expire()
        val fresh = loop.start(stop.generation(), "com.example.notes", "Home", original)
        assertTrue(fresh.actionId > old.actionId)
    }
}
