package dev.ciryva.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftEditorTest {
    private class MemoryStore(
        var value: String = "",
        var writesAllowed: Boolean = true,
    ) : DraftStore {
        override fun read(): String = value
        override fun write(text: String): Boolean {
            if (!writesAllowed) return false
            value = text
            return true
        }
        override fun clear(): Boolean {
            if (!writesAllowed) return false
            value = ""
            return true
        }
    }

    @Test fun restoresSavedDraftAcrossEditorInstances() {
        val store = MemoryStore()
        assertTrue(DraftEditor(store).save("Remember the wording"))
        assertEquals("Remember the wording", DraftEditor(store).load())
    }

    @Test fun clearsSavedDraft() {
        val store = MemoryStore("Old text")
        assertTrue(DraftEditor(store).clear())
        assertEquals("", DraftEditor(store).load())
    }

    @Test fun enforcesMaximumSizeWithoutWriting() {
        val store = MemoryStore("original")
        assertThrows(IllegalArgumentException::class.java) {
            DraftEditor(store).save("x".repeat(DraftEditor.MAX_LENGTH + 1))
        }
        assertEquals("original", store.value)
    }

    @Test fun surfacesStorageFailureWithoutClaimingSuccess() {
        val store = MemoryStore("original", writesAllowed = false)
        assertFalse(DraftEditor(store).save("change"))
        assertFalse(DraftEditor(store).clear())
        assertEquals("original", DraftEditor(store).load())
    }

    @Test fun boundsCorruptOrLegacyPersistedValueOnRead() {
        val store = MemoryStore("x".repeat(DraftEditor.MAX_LENGTH + 100))
        assertEquals(DraftEditor.MAX_LENGTH, DraftEditor(store).load().length)
    }
}
