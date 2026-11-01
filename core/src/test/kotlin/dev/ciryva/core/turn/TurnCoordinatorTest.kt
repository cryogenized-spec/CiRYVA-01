package dev.ciryva.core.turn

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TurnCoordinatorTest {
    @Test fun `superseded generations cannot mutate the current turn`() {
        val turns = TurnCoordinator()
        turns.start("older")
        assertTrue(turns.append("older", "unused"))
        turns.start("new")
        assertFalse(turns.append("older", "late"))
        assertFalse(turns.complete("older"))
        assertTrue(turns.append("new", "real"))
        assertTrue(turns.complete("new"))
        assertEquals(TurnSnapshot("new", TurnStatus.COMPLETED, "real"), turns.snapshot())
    }

    @Test fun `cancellation and failure are terminal`() {
        val turns = TurnCoordinator()
        turns.start("one")
        assertTrue(turns.append("one", "partial"))
        assertTrue(turns.cancel("one"))
        assertFalse(turns.append("one", "late"))
        assertFalse(turns.complete("one"))
        assertEquals(TurnStatus.CANCELLED, turns.snapshot().status)
        turns.start("two")
        assertTrue(turns.fail("two", "network error"))
        assertEquals("network error", turns.snapshot().error)
        assertFalse(turns.append("two", "late"))
    }

    @Test fun `bounds large streaming output`() {
        val turns = TurnCoordinator(maxCharacters = 3)
        turns.start("one")
        assertTrue(turns.append("one", "abc"))
        assertThrows(IllegalArgumentException::class.java) {
            turns.append("one", "d")
        }
        assertEquals("abc", turns.snapshot().text)
    }
}
