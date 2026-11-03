package dev.ciryva.app

import android.content.Context
import dev.ciryva.core.stream.CiRYVAModelCatalog

/**
 * P06 globally configured model/generation/personalization preferences.
 *
 * This uses Android's built-in app-private SharedPreferences; no third-party
 * dependency, no network use and no OpenAI API credentials. Persona prose
 * is stored as plain app-private settings, NOT encrypted like the API key.
 */
data class ChatPreferences(
    val modelId: String = DEFAULT_MODEL,
    val effort: String = DEFAULT_OPTION,
    val verbosity: String = DEFAULT_OPTION,
    val sampling: String = DEFAULT_OPTION,
    val temperature: String = "1.0",
    val topP: String = "1.0",
    val outputLimit: String = "1024",
    val summaryEnabled: Boolean = true,
    val personaMode: String = MODE_STANDARD,
    val customInstructions: String = "",
    val roleplayInstructions: String = "",
    val backgroundPreset: String = BACKGROUND_BURGUNDY,
    val readingTypeface: String = TYPEFACE_MODERN,
    val decorativeWaves: Boolean = true,
) {
    companion object {
        const val DEFAULT_MODEL = "gpt-5.4-mini-2026-03-17"
        const val DEFAULT_OPTION = "Provider default"
        const val MODE_STANDARD = "standard"
        const val MODE_CUSTOM = "custom"
        const val MODE_ROLEPLAY = "roleplay"
        const val MAX_PERSONA_CHARS = 1_600
        const val BACKGROUND_BURGUNDY = "burgundy"
        const val BACKGROUND_MIDNIGHT = "midnight"
        const val BACKGROUND_GRAPHITE = "graphite"
        const val TYPEFACE_MODERN = "modern"
        const val TYPEFACE_LITERARY = "literary"
    }

    fun validated(): ChatPreferences {
        val chosenModel = CiRYVAModelCatalog.selectable
            .firstOrNull { it.id == modelId }
            ?: CiRYVAModelCatalog.selectable.first()
        val allowSampling = chosenModel.supportsSampling &&
            (!chosenModel.samplingOnlyWhenNoReasoning || effort == "none")
        return copy(
            modelId = chosenModel.id,
            effort = effort.takeIf { it == DEFAULT_OPTION ||
                it in chosenModel.reasoningEfforts } ?: DEFAULT_OPTION,
            verbosity = verbosity.takeIf { it == DEFAULT_OPTION ||
                (chosenModel.supportsVerbosity && it in listOf("low", "medium", "high")) }
                ?: DEFAULT_OPTION,
            sampling = sampling.takeIf {
                allowSampling && it in listOf("temperature", "top_p")
            } ?: DEFAULT_OPTION,
            temperature = temperature.takeIf {
                it.toDoubleOrNull()?.let { n -> n.isFinite() && n in 0.0..2.0 } == true
            } ?: "1.0",
            topP = topP.takeIf {
                it.toDoubleOrNull()?.let { n -> n.isFinite() && n > 0.0 && n <= 1.0 } == true
            } ?: "1.0",
            outputLimit = outputLimit.takeIf { value ->
                value.toIntOrNull()?.let { it in 1..4096 } == true
            } ?: "1024",
            personaMode = personaMode.takeIf {
                it in listOf(MODE_STANDARD, MODE_CUSTOM, MODE_ROLEPLAY)
            } ?: MODE_STANDARD,
            customInstructions = customInstructions.take(MAX_PERSONA_CHARS),
            roleplayInstructions = roleplayInstructions.take(MAX_PERSONA_CHARS),
            backgroundPreset = backgroundPreset.takeIf {
                it in listOf(BACKGROUND_BURGUNDY, BACKGROUND_MIDNIGHT, BACKGROUND_GRAPHITE)
            } ?: BACKGROUND_BURGUNDY,
            readingTypeface = readingTypeface.takeIf {
                it in listOf(TYPEFACE_MODERN, TYPEFACE_LITERARY)
            } ?: TYPEFACE_MODERN,
        )
    }

    /**
     * The provider instructions are supplied only when a user explicitly
     * authorizes a Send, never on startup, switching threads or editing them.
     * They are global preferences, not fake memories or system-internal CoT.
     */
    fun requestInstructions(): String? = when (personaMode) {
        MODE_CUSTOM -> customInstructions.trim().takeIf { it.isNotEmpty() }
        MODE_ROLEPLAY -> buildString {
            append("This request is fictional roleplay. Respond in the requested ")
            append("character or scenario while keeping fiction distinguishable from facts.")
            roleplayInstructions.trim().takeIf { it.isNotEmpty() }?.let {
                append("\nUser-authored roleplay instructions:\n")
                append(it)
            }
        }
        else -> null
    }
}

internal class ChatPreferencesStore(
    context: Context,
    name: String = "ciryva_chat_preferences",
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        name, Context.MODE_PRIVATE
    )

    fun load(): ChatPreferences = ChatPreferences(
        modelId = prefs.getString("model", ChatPreferences.DEFAULT_MODEL)
            ?: ChatPreferences.DEFAULT_MODEL,
        effort = prefs.getString("effort", ChatPreferences.DEFAULT_OPTION)
            ?: ChatPreferences.DEFAULT_OPTION,
        verbosity = prefs.getString("verbosity", ChatPreferences.DEFAULT_OPTION)
            ?: ChatPreferences.DEFAULT_OPTION,
        sampling = prefs.getString("sampling", ChatPreferences.DEFAULT_OPTION)
            ?: ChatPreferences.DEFAULT_OPTION,
        temperature = prefs.getString("temperature", "1.0") ?: "1.0",
        topP = prefs.getString("top_p", "1.0") ?: "1.0",
        outputLimit = prefs.getString("output_limit", "1024") ?: "1024",
        summaryEnabled = prefs.getBoolean("summary_enabled", true),
        personaMode = prefs.getString("persona_mode", ChatPreferences.MODE_STANDARD)
            ?: ChatPreferences.MODE_STANDARD,
        customInstructions = prefs.getString("custom_instructions", "") ?: "",
        roleplayInstructions = prefs.getString("roleplay_instructions", "") ?: "",
        backgroundPreset = prefs.getString("background_preset",
            ChatPreferences.BACKGROUND_BURGUNDY) ?: ChatPreferences.BACKGROUND_BURGUNDY,
        readingTypeface = prefs.getString("reading_typeface",
            ChatPreferences.TYPEFACE_MODERN) ?: ChatPreferences.TYPEFACE_MODERN,
        decorativeWaves = prefs.getBoolean("decorative_waves", true),
    ).validated()

    fun save(settings: ChatPreferences) {
        val safe = settings.validated()
        prefs.edit()
            .putString("model", safe.modelId)
            .putString("effort", safe.effort)
            .putString("verbosity", safe.verbosity)
            .putString("sampling", safe.sampling)
            .putString("temperature", safe.temperature)
            .putString("top_p", safe.topP)
            .putString("output_limit", safe.outputLimit)
            .putBoolean("summary_enabled", safe.summaryEnabled)
            .putString("persona_mode", safe.personaMode)
            .putString("custom_instructions", safe.customInstructions)
            .putString("roleplay_instructions", safe.roleplayInstructions)
            .putString("background_preset", safe.backgroundPreset)
            .putString("reading_typeface", safe.readingTypeface)
            .putBoolean("decorative_waves", safe.decorativeWaves)
            .apply()
    }
}
