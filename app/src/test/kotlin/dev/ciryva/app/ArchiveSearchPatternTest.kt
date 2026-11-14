package dev.ciryva.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM lexical matching regressions; no database or API calls. */
class ArchiveSearchPatternTest {
    @Test fun punctuationIsMatchedLiterallyRatherThanAsSqlWildcards() {
        assertTrue(archiveTextMatches("Model SR_100%!", "SR_100%!"))
        assertTrue(archiveTextMatches("100% of parts", "100%"))
        assertFalse(archiveTextMatches("SR-100", "SR_100"))
        assertFalse(archiveTextMatches("Model 100", "100%"))
        assertFalse(archiveTextMatches("Any saved text", "   "))
    }

    @Test fun unicodeCaseFoldingMatchesPreviouslyDiscoverableTitlesAndMessages() {
        assertTrue(archiveTextMatches("École de Paris", "école"))
        assertTrue(archiveTextMatches("CAFÉ comparison", "café"))
        assertTrue(archiveTextMatches("A note about Σ", "σ"))
        assertFalse(archiveTextMatches("Cafe", "café"))
    }
}
