package dev.ciryva.core.context

import dev.ciryva.core.stream.HttpStreamResult
import dev.ciryva.core.stream.TextResponseRequest
import dev.ciryva.core.stream.TokenUsage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionOutputAllowanceTest {
    private fun req(n: Int) = TextResponseRequest("test-model", "Prompt", n)

    @Test fun `reservations are admitted only within both fixed limits`() {
        val gate = SessionOutputAllowance(800, 3)
        assertEquals(OutputReservationResult.Accepted, gate.reserve("one", req(400)))
        assertEquals(OutputReservationResult.Accepted, gate.reserve("two", req(400)))
        assertEquals(
            OutputReservationResult.Rejected(OutputReservationRejection.OUTPUT_ALLOWANCE_EXCEEDED),
            gate.reserve("three", req(1)),
        )
        assertEquals(
            OutputReservationResult.Rejected(OutputReservationRejection.DUPLICATE_REQUEST),
            gate.reserve("one", req(1)),
        )
        assertEquals(800L, gate.snapshot().reservedOutputTokens)
        assertEquals(0L, gate.snapshot().remainingOutputTokens)
    }

    @Test fun `request count cap rejects even when output tokens remain`() {
        val gate = SessionOutputAllowance(8_000, 1)
        assertEquals(OutputReservationResult.Accepted, gate.reserve("one", req(1)))
        assertEquals(
            OutputReservationResult.Rejected(OutputReservationRejection.REQUEST_COUNT_EXCEEDED),
            gate.reserve("two", req(1)),
        )
        assertEquals(1, gate.snapshot().acceptedRequests)
    }

    @Test fun `cancellations and rejected HTTP calls do not refund reservations`() {
        val gate = SessionOutputAllowance(100, 2)
        gate.reserve("a", req(50))
        gate.reserve("b", req(50))
        assertTrue(gate.record("a", HttpStreamResult.Cancelled))
        assertTrue(gate.record("b", HttpStreamResult.Rejected(401, null)))
        assertEquals(100L, gate.snapshot().reservedOutputTokens)
        assertEquals(0L, gate.snapshot().remainingOutputTokens)
        assertEquals(2, gate.snapshot().receiptsWithoutUsage)
        assertEquals(0L, gate.snapshot().confirmedUsage.totalTokens)
        assertEquals(2, gate.snapshot().settledRequests)
    }

    @Test fun `real completed and incomplete usage are counted separately from reservations`() {
        val gate = SessionOutputAllowance(700, 3)
        gate.reserve("a", req(200))
        gate.reserve("b", req(200))
        assertTrue(gate.record("a", HttpStreamResult.Completed("resp", TokenUsage(23, 40, 63), "req")))
        assertTrue(gate.record("b", HttpStreamResult.Incomplete("resp2", "max_output_tokens", TokenUsage(12, 200, 212))))
        assertEquals(TokenUsage(35, 240, 275), gate.snapshot().confirmedUsage)
        assertEquals(400L, gate.snapshot().reservedOutputTokens)
        assertEquals(300L, gate.snapshot().remainingOutputTokens)
        assertEquals(0, gate.snapshot().receiptsWithoutUsage)
    }

    @Test fun `missing provider usage remains explicitly unknown rather than fabricated`() {
        val gate = SessionOutputAllowance(100, 2)
        gate.reserve("a", req(30))
        gate.record("a", HttpStreamResult.Completed("r", null, "req"))
        assertEquals(1, gate.snapshot().receiptsWithoutUsage)
        assertEquals(TokenUsage(0, 0, 0), gate.snapshot().confirmedUsage)
    }

    @Test fun `unknown and duplicate receipts cannot modify accepted totals`() {
        val gate = SessionOutputAllowance(100, 2)
        assertFalse(gate.record("absent", HttpStreamResult.Completed("r", TokenUsage(1, 2, 3), null)))
        gate.reserve("a", req(50))
        assertTrue(gate.record("a", HttpStreamResult.Completed("r", TokenUsage(1, 2, 3), null)))
        assertFalse(gate.record("a", HttpStreamResult.Completed("r", TokenUsage(100, 200, 300), null)))
        assertEquals(TokenUsage(1, 2, 3), gate.snapshot().confirmedUsage)
    }

    @Test fun `synchronized concurrent reservations cannot exceed allowance`() {
        val quota = SessionOutputAllowance(1_000, 100)
        val start = CountDownLatch(1)
        val admitted = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = (1..60).map { id ->
                pool.submit {
                    start.await()
                    if (quota.reserve("id_$id", req(100)) == OutputReservationResult.Accepted) {
                        admitted.incrementAndGet()
                    }
                }
            }
            start.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(10, admitted.get())
            assertEquals(1_000L, quota.snapshot().reservedOutputTokens)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun `invalid identifiers and allowance configuration fail before admission`() {
        assertThrows(IllegalArgumentException::class.java) { SessionOutputAllowance(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { SessionOutputAllowance(10, 0) }
        val quota = SessionOutputAllowance(100, 2)
        assertThrows(IllegalArgumentException::class.java) { quota.reserve(" ", req(1)) }
    }
}
