package dev.ciryva.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalChatPreviewTest {
    @Test fun acceptedTextProducesOnlyUserAuthoredMessage() {
        assertEquals(listOf("Hello 🌍"),
            LocalChatPreview.appendUserMessage(emptyList(), "  Hello 🌍  "))
    }

    @Test fun blanksAndOverlongTextsAreRejected() {
        assertNull(LocalChatPreview.appendUserMessage(emptyList(), "   "))
        assertNull(LocalChatPreview.appendUserMessage(emptyList(),
            "x".repeat(LocalChatPreview.MAX_MESSAGE_CHARS + 1)))
    }

    @Test fun messageCapIsEnforcedWithoutCorruptingHistory() {
        val original = List(LocalChatPreview.MAX_MESSAGES) { "user-$it" }
        assertNull(LocalChatPreview.appendUserMessage(original, "new"))
        assertEquals(original, original)
    }
}
