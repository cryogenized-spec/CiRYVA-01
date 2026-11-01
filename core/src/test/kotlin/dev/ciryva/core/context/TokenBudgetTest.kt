package dev.ciryva.core.context

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TokenBudgetTest {
    @Test fun `reserve leaves only safe input room`() {
        val budget = TokenBudget(
            contextLimit = 10_000,
            outputReservation = 2_000,
            instructionTokens = 500,
            toolSchemaTokens = 500,
            safetyMargin = 1_000,
            maxToolCalls = 4,
        )
        assertEquals(6_000, budget.availableInputTokens)
        assertTrue(budget.accepts(6_000, 4))
        assertFalse(budget.accepts(6_001, 4))
        assertFalse(budget.accepts(100, 5))
    }

    @Test fun `invalid reservations fail before calling a provider`() {
        assertThrows(IllegalArgumentException::class.java) {
            TokenBudget(1_000, 900, 200, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TokenBudget(1_000, -1, 100, 100)
        }
    }

    @Test fun `requires actual nonnegative token counts`() {
        val budget = TokenBudget(8_000, 1_000, 500, 0)
        assertThrows(IllegalArgumentException::class.java) {
            budget.accepts(-1, 0)
        }
    }
}
