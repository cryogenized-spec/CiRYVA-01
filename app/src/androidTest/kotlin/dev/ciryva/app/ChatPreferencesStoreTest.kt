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
        assertEquals("Explain facts plainly.",
            prefs.copy(personaMode = ChatPreferences.MODE_CUSTOM).requestInstructions())
        assertEquals(null,
            prefs.copy(personaMode = ChatPreferences.MODE_STANDARD).requestInstructions())
    }

    @Test fun appearanceAndTypographySurviveStoreReopenAndRestoreValidDefaults() {
        val chosen = ChatPreferences(
            backgroundPreset = ChatPreferences.BACKGROUND_GRAPHITE,
            readingTypeface = ChatPreferences.TYPEFACE_LITERARY,
            decorativeWaves = false,
        )
        ChatPreferencesStore(context, name).save(chosen)
        val reopened = ChatPreferencesStore(context, name).load()
        assertEquals(ChatPreferences.BACKGROUND_GRAPHITE, reopened.backgroundPreset)
        assertEquals(ChatPreferences.TYPEFACE_LITERARY, reopened.readingTypeface)
        assertFalse(reopened.decorativeWaves)
        // These three preferences never authorize paid requests or change personas.
        assertEquals(null, reopened.requestInstructions())

        ChatPreferencesStore(context, name).save(
            reopened.copy(backgroundPreset = "unrecognized-gradient",
                readingTypeface = "untrusted-font-name"),
        )
        val corrected = ChatPreferencesStore(context, name).load()
        assertEquals(ChatPreferences.BACKGROUND_BURGUNDY, corrected.backgroundPreset)
        assertEquals(ChatPreferences.TYPEFACE_MODERN, corrected.readingTypeface)
        assertFalse(corrected.decorativeWaves)
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
    @Test fun newSystemPromptRoleplayAndReaderSizeSurviveRestart() {
        val store = ChatPreferencesStore(context, name)
        store.save(ChatPreferences(
            systemPrompt = "Answer precisely and distinguish speculation.",
            roleplayEnabled = true,
            roleplayInstructions = "The librarian speaks in first person.",
            readingSizeSp = 20,
            readingTypeface = ChatPreferences.TYPEFACE_MONOSPACE,
        ))
        val recovered = ChatPreferencesStore(context, name).load()
        assertEquals(20, recovered.readingSizeSp)
        assertEquals(ChatPreferences.TYPEFACE_MONOSPACE, recovered.readingTypeface)
        assertTrue(recovered.roleplayEnabled)
        val composed = recovered.requestInstructions()!!
        assertTrue(composed.contains("Answer precisely"))
        assertTrue(composed.contains("Roleplay formatting"))
        assertTrue(composed.contains("first person"))
        val disabled = recovered.copy(roleplayEnabled = false)
        assertFalse(disabled.requestInstructions()!!.contains("Roleplay formatting"))
        assertTrue(disabled.requestInstructions()!!.contains("Answer precisely"))
        assertEquals(ChatPreferences.MAX_READING_SP,
            recovered.copy(readingSizeSp = 200).validated().readingSizeSp)
    }

    @Test fun intentionallyClearedSystemPromptIsNotReinitializedAfterRestart() {
        val store = ChatPreferencesStore(context, name)
        val fresh = store.load()
        assertFalse(fresh.systemPromptInitialized)
        store.save(ChatPreferences(systemPrompt = ""))
        val reopened = ChatPreferencesStore(context, name).load()
        assertTrue(reopened.systemPromptInitialized)
        assertEquals("", reopened.systemPrompt)
        assertEquals(null, reopened.requestInstructions())
    }

}
