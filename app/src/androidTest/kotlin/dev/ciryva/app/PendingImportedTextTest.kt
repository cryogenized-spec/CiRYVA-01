package dev.ciryva.app

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Fake-only tests; no transcription, API keys, charge, storage or network. */
@RunWith(AndroidJUnit4::class)
class PendingImportedTextTest {
    @Test fun completedTranscriptSurvivesSavedStateAndNeedsExplicitDismiss() {
        PendingImportedText.dismiss()
        try {
            PendingImportedText.text.value = "One paid transcription result"
            PendingImportedText.source.value = "Voice note transcript"
            val saved = Bundle()
            PendingImportedText.save(saved)
            // Simulate loss of process-scoped memory on cold process restart.
            PendingImportedText.dismiss()
            PendingImportedText.restoreCold(saved)
            assertEquals("One paid transcription result", PendingImportedText.text.value)
            assertEquals("Voice note transcript", PendingImportedText.source.value)
            PendingImportedText.dismiss()
            assertNull(PendingImportedText.text.value)
        } finally {
            PendingImportedText.dismiss()
        }
    }

    @Test fun restoredStaleBundleCannotOverwriteNewerProcessResult() {
        PendingImportedText.dismiss()
        try {
            PendingImportedText.text.value = "Old transcript"
            val saved = Bundle()
            PendingImportedText.save(saved)
            PendingImportedText.text.value = "Newer transcript"
            PendingImportedText.restoreCold(saved)
            assertEquals("Newer transcript", PendingImportedText.text.value)
        } finally {
            PendingImportedText.dismiss()
        }
    }

    @Test fun oversizedUntrustedSavedStateNeverBecomesPendingText() {
        PendingImportedText.dismiss()
        try {
            val saved = Bundle().apply {
                putString("ciryva.intake.review.text", "x".repeat(32_769))
            }
            PendingImportedText.restoreCold(saved)
            assertNull(PendingImportedText.text.value)
        } finally {
            PendingImportedText.dismiss()
        }
    }
}
