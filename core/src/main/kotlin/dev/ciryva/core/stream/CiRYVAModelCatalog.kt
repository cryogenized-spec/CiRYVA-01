package dev.ciryva.core.stream

/**
 * Public model feature registry, verified against the OpenAI API model catalog,
 * model pages, data-sharing incentive list, and deprecation notices on 2026-11-02.
 *
 * Membership in an incentive group is NOT a promise of complimentary usage:
 * OpenAI controls organization eligibility, opt-in, remaining quota and billing.
 * Historical disabled entries are never included in the selectable model list.
 */
enum class IncentiveGroup { STANDARD, SMALL, NONE }

data class CiRYVAModel(
    val id: String,
    val label: String,
    val incentiveGroup: IncentiveGroup,
    val reasoningEfforts: Set<String> = emptySet(),
    val supportsVerbosity: Boolean = false,
    val supportsSampling: Boolean = false,
    val samplingOnlyWhenNoReasoning: Boolean = false,
    val retired: Boolean = false,
    val retirementNote: String? = null,
)

object CiRYVAModelCatalog {
    private val modernEffort = setOf("none", "low", "medium", "high", "xhigh", "max")
    private val fullEffort = setOf("low", "medium", "high", "xhigh", "max")
    private val generation5Effort = setOf("none", "low", "medium", "high", "xhigh")

    val all: List<CiRYVAModel> = listOf(
        CiRYVAModel("gpt-6-luna", "GPT-6 Luna", IncentiveGroup.STANDARD,
            modernEffort, supportsVerbosity = true, supportsSampling = true,
            samplingOnlyWhenNoReasoning = true),
        CiRYVAModel("gpt-6.1-sol", "GPT-6.1 Sol", IncentiveGroup.STANDARD,
            fullEffort, supportsVerbosity = true),
        CiRYVAModel("gpt-6-astra", "GPT-6 Astra", IncentiveGroup.STANDARD,
            fullEffort, supportsVerbosity = true),
        CiRYVAModel("gpt-5.4-2026-03-05", "GPT-5.4", IncentiveGroup.STANDARD,
            generation5Effort, supportsVerbosity = true, supportsSampling = true,
            samplingOnlyWhenNoReasoning = true),
        CiRYVAModel("gpt-5.4-mini-2026-03-17", "GPT-5.4 Mini", IncentiveGroup.SMALL,
            generation5Effort, supportsVerbosity = true, supportsSampling = true,
            samplingOnlyWhenNoReasoning = true),
        CiRYVAModel("gpt-5.6-luna", "GPT-5.6 Luna", IncentiveGroup.SMALL,
            modernEffort, supportsVerbosity = true, supportsSampling = true,
            samplingOnlyWhenNoReasoning = true),
        CiRYVAModel("gpt-4o-2024-11-20", "GPT-4o (legacy, retires Jan 2027)",
            IncentiveGroup.STANDARD, supportsSampling = true,
            retirementNote = "Scheduled API shutdown 2027-01-14"),
        CiRYVAModel("gpt-4o-mini-2024-07-18", "GPT-4o Mini (legacy, retires Jan 2027)",
            IncentiveGroup.SMALL, supportsSampling = true,
            retirementNote = "Scheduled API shutdown 2027-01-14"),
        CiRYVAModel("o3-2025-04-16", "o3 (legacy reasoning)", IncentiveGroup.STANDARD,
            reasoningEfforts = setOf("low", "medium", "high")),
        // Listed for historical clarity; sunset IDs must not be offered as working choices.
        CiRYVAModel("o1-2024-12-17", "o1 (retired)", IncentiveGroup.NONE,
            retired = true, retirementNote = "API shutdown 2026-10-23"),
        CiRYVAModel("o4-mini-2025-04-16", "o4-mini (retired)", IncentiveGroup.NONE,
            retired = true, retirementNote = "API shutdown 2026-10-23"),
    )

    val selectable: List<CiRYVAModel> = all.filterNot { it.retired }
    fun find(modelId: String): CiRYVAModel? = all.firstOrNull { it.id == modelId }
}

/**
 * Optional model-specific controls attached to a request. Null means use
 * provider defaults. Disable rather than invent settings a model cannot accept.
 * Settings contain NO API keys, credentials or conversation content.
 */
data class GenerationSettings(
    val modelId: String,
    val reasoningEffort: String? = null,
    val verbosity: String? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val reasoningSummary: String? = null,
) {
    init {
        val model = requireNotNull(CiRYVAModelCatalog.find(modelId)) {
            "Unsupported model; choose a catalog entry"
        }
        require(!model.retired) { "Retired model is unavailable" }
        require(reasoningEffort == null || reasoningEffort in model.reasoningEfforts) {
            "Unsupported reasoning effort for the selected model"
        }
        require(verbosity == null ||
            (model.supportsVerbosity && verbosity in setOf("low", "medium", "high"))) {
            "Unsupported verbosity for the selected model"
        }
        require(reasoningSummary == null ||
            (model.reasoningEfforts.isNotEmpty() &&
                reasoningSummary in setOf("auto", "concise", "detailed"))) {
            "Unsupported reasoning summary setting"
        }
        require(temperature == null || (temperature.isFinite() && temperature in 0.0..2.0)) {
            "Temperature must be a finite number from 0 to 2"
        }
        require(topP == null || (topP.isFinite() && topP > 0.0 && topP <= 1.0)) {
            "Top-p must be a finite number above 0 and at most 1"
        }
        require(temperature == null || topP == null) {
            "Choose temperature or top-p, not both"
        }
        if (temperature != null || topP != null) {
            require(model.supportsSampling &&
                (!model.samplingOnlyWhenNoReasoning || reasoningEffort == "none")) {
                "Sampling controls require a compatible non-reasoning model/mode"
            }
        }
    }
}
