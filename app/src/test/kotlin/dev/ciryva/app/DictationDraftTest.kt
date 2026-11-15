package dev.ciryva.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DictationDraftTest {
    @Test fun finalizedSpeechAppendsWithoutDiscardingTypedText() {
        assertEquals("Initial typed sentence. Spoken addition.",
            appendDictatedText("Initial typed sentence.", " Spoken addition. "))
        assertEquals("Already spaced spoken text",
            appendDictatedText("Already spaced ", " spoken text "))
    }

    @Test fun emptyHypothesisIsNeverAddedAndExistingFormattingSurvives() {
        assertEquals("First line\nSecond line", appendDictatedText("First line\nSecond line", " \n "))
        assertEquals("First line\n- item spoken words",
            appendDictatedText("First line\n- item ", " spoken words "))
        assertEquals("Words", appendDictatedText("", "  Words  "))
    }
}
