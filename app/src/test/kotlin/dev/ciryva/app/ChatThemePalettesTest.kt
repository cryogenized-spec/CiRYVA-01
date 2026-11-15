package dev.ciryva.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatThemePalettesTest {
    @Test fun tenDistinctPresetsHaveExactlyFourCompleteColours() {
        val presets = ChatThemePalettes.presets
        assertEquals(10, presets.size)
        assertEquals(10, presets.map { it.id }.toSet().size)
        presets.forEach { preset ->
            val t = preset.tokens
            listOf(t.background, t.panel, t.accent, t.text).forEach {
                assertEquals(it, HexThemeInput.complete(it))
            }
        }
        assertEquals("carbon", ChatThemePalettes.DEFAULT)
    }

    @Test fun sixDigitHexPasteStripsRichMarkupSpacesAndUnicodeFormatting() {
        assertEquals("#A1B2C3", HexThemeInput.input(" #a1 b2 c3 \n"))
        assertEquals("#7FA09C", HexThemeInput.input("<b>#7fa09c</b>"))
        assertEquals("#77AABB",
            HexThemeInput.input("<span style=\"font-weight:bold\">#77aabb</span>"))
        assertEquals("#00FF44", HexThemeInput.input("\u200B#00\u00A0FF 44\uFEFF"))
        assertEquals("#11AB5C", HexThemeInput.input("color: #11ab5c;"))
        assertEquals("#0CA3FF", HexThemeInput.input("0x0ca3ff"))
        assertEquals("#123", HexThemeInput.input("#1 2 3"))
        assertNull(HexThemeInput.complete("#123"))
    }

    @Test fun ambiguousAndMalformedPastesNeverReachStoredPalette() {
        assertNull(HexThemeInput.input("<b>#123456</b> and #AABBCC"))
        assertNull(HexThemeInput.input("#11223344"))
        assertNull(HexThemeInput.input("rgb(12, 34, 56)"))
        assertNull(HexThemeInput.input("javascript:alert(1)"))
        assertNull(HexThemeInput.input("a".repeat(4097)))
        assertNull(HexThemeInput.input("\u0000#123456"))
        assertEquals("#FFFFFF", HexThemeInput.complete("#FFFFFF; onload=alert(1)"))
        assertFalse(HexThemeInput.isComplete("#AAB"))
        assertTrue(HexThemeInput.isComplete("#AABBCC"))
    }

    @Test fun corruptedPersistedFieldsAreIndividuallyReplacedBySafeDefaults() {
        val custom = ChatThemeTokens("#0A0B0C", "<html>", "#12345678", "#F2F2F2")
        val safe = custom.validated()
        assertEquals("#0A0B0C", safe.background)
        assertEquals(ChatThemePalettes.carbon.tokens.panel, safe.panel)
        assertEquals(ChatThemePalettes.carbon.tokens.accent, safe.accent)
        assertEquals("#F2F2F2", safe.text)
    }

    @Test fun previousPurpleIDsUpgradeDeterministically() {
        assertEquals("carbon", ChatThemePalettes.normalizeId("burgundy"))
        assertEquals("coastal", ChatThemePalettes.normalizeId("midnight"))
        assertEquals("signal", ChatThemePalettes.normalizeId("graphite"))
        assertEquals("carbon", ChatThemePalettes.normalizeId("unknown"))
        assertEquals(ChatThemePalettes.CUSTOM,
            ChatThemePalettes.normalizeId(ChatThemePalettes.CUSTOM))
    }
}
