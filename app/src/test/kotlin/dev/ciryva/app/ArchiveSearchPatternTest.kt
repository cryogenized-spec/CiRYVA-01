package dev.ciryva.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM tests; no database, emulator, network or API key. */
class ArchiveSearchPatternTest {
    @Test fun literalWildcardsAndEscapeMarkerRemainSearchable() {
        assertEquals("%100!%!_!!%", archiveLikePattern("100%_!"))
        assertEquals("%one two%", archiveLikePattern("  one two  "))
        assertEquals("%regular%", archiveLikePattern("regular"))
    }

    @Test fun searchLengthIsBoundedBeforeSqlitePatternConstruction() {
        assertEquals(
            LocalConversationArchive.MAX_SEARCH_LENGTH + 2,
            archiveLikePattern("a".repeat(500)).length,
        )
    }
}
