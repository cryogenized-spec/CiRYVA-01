package dev.ciryva.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android SharedPreferences, not a mock: proves global user settings
 * survive store reopening without persisting API credentials or charge consent.
 */
@RunWith(AndroidJUnit4::class)
class ChatPreferencesStoreTest {
    private val context get() =
        InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var name: String

    @Before fun setUp() { name = "p06-preferences-test-" + UUID.randomUUID() }

    @After fun tearDown() {
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun globalModelAndUserPersonaSurviveStoreReopen() {
        ChatPreferencesStore(context, name).save(
            ChatPreferences(
                modelId = "gpt-5.4-mini-2026-03-17",
                summaryEnabled = false,
                outputLimit = "2048",
                personaMode = ChatPreferences.MODE_CUSTOM,
                customInstructions = "Prefer concise, precise engineering explanations.",
            )
        )
        val loaded = ChatPreferencesStore(context, name).load()
        assertEquals("2048", loaded.outputLimit)
        assertFalse(loaded.summaryEnabled)
        assertEquals(ChatPreferences.MODE_CUSTOM, loaded.personaMode)
        assertEquals(
            "Prefer concise, precise engineering explanations.",
            loaded.requestInstructions()
        )
        assertEquals("gpt-5.4-mini-2026-03-17", loaded.modelId)
        assertEquals(null, loaded.copy(personaMode = ChatPreferences.MODE_STANDARD)
            .requestInstructions())
    }

    @Test fun roleplayAndStandardModesNeverConflateTheirUserText() {
        val prefs = ChatPreferences(
            personaMode = ChatPreferences.MODE_ROLEPLAY,
            roleplayInstructions = "Use an original fictional character.",
            customInstructions = "Explain facts plainly.",
        )
        assertTrue(prefs.requestInstructions()!!.contains("fictional character"))
        assertFalse(prefs.requestInstructions()!!.contains("Explain facts plainly"))
        assertEquals("Explain facts plainly",
            prefs.copy(personaMode = ChatPreferences.MODE_CUSTOM).requestInstructions())
        assertEquals(null,
            prefs.copy(personaMode = ChatPreferences.MODE_STANDARD).requestInstructions())
    }

    @Test fun staleOrCorruptChoicesFallBackSafelyWithoutIncreasingOutputBudget() {
        val stored = ChatPreferences(
            modelId = "nonexistent-model",
            outputLimit = "999999",
            personaMode = "unknown-mode",
            customInstructions = "x".repeat(4_000),
        )
        ChatPreferencesStore(context, name).save(stored)
        val loaded = ChatPreferencesStore(context, name).load()
        assertEquals("1024", loaded.outputLimit)
        assertEquals(ChatPreferences.MODE_STANDARD, loaded.personaMode)
        assertTrue(loaded.customInstructions.length <=
            ChatPreferences.MAX_PERSONA_CHARS)
        assertEquals(null, loaded.requestInstructions())
    }
}
