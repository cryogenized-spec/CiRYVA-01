package dev.ciryva.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerActionRulesTest {
    @Test fun blankOrWhitespaceComposerOffersMic() {
        assertTrue(ComposerActionRules.showsMicrophone(""))
        assertTrue(ComposerActionRules.showsMicrophone(" \n\t "))
        assertFalse(ComposerActionRules.showsMicrophone("Hello"))
        assertFalse(ComposerActionRules.showsMicrophone("  A "))
    }

    @Test fun returningSwipeMustNeverBecomePaidSendTap() {
        // Pointer goes 80 px up then comes back: everLeftTapSlop stays true.
        assertFalse(ComposerActionRules.permitsTap(true, 160L))
        assertFalse(ComposerActionRules.permitsTap(true, 500L))
        assertTrue(ComposerActionRules.permitsTap(false, 120L))
        assertFalse(ComposerActionRules.permitsTap(false, 700L))
    }

    @Test fun onlyDeliberateUpwardHoldStartsVoice() {
        assertTrue(ComposerActionRules.qualifiesVoiceSwipe(-60f, 250L))
        assertFalse(ComposerActionRules.qualifiesVoiceSwipe(-20f, 250L))
        assertFalse(ComposerActionRules.qualifiesVoiceSwipe(-60f, 100L))
        assertFalse(ComposerActionRules.qualifiesVoiceSwipe(60f, 300L))
    }
}
