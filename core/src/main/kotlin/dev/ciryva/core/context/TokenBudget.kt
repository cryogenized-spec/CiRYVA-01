package dev.ciryva.core.context

/**
 * Conservative model-context admission arithmetic. Values must be actual token
 * counts obtained from a trusted tokenizer/provider; character length is NOT
 * a substitute for token measurement. This is not a billing calculator.
 */
data class TokenBudget(
    val contextLimit: Int,
    val outputReservation: Int,
    val instructionTokens: Int,
    val toolSchemaTokens: Int,
    val safetyMargin: Int = 1_024,
    val maxToolCalls: Int = 8,
) {
    init {
        require(contextLimit > 0)
        require(outputReservation >= 0)
        require(instructionTokens >= 0)
        require(toolSchemaTokens >= 0)
        require(safetyMargin >= 0)
        require(maxToolCalls >= 0)
        val reserved = outputReservation.toLong() + instructionTokens +
            toolSchemaTokens + safetyMargin
        require(reserved <= contextLimit.toLong()) { "Context reservations exceed model window" }
    }

    val availableInputTokens: Int
        get() = contextLimit - outputReservation - instructionTokens -
            toolSchemaTokens - safetyMargin

    fun accepts(inputTokens: Int, executedToolCalls: Int): Boolean {
        require(inputTokens >= 0 && executedToolCalls >= 0)
        return inputTokens <= availableInputTokens && executedToolCalls <= maxToolCalls
    }
}
