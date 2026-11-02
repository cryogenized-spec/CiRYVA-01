package dev.ciryva.core.stream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Minimal, bounded decoder for verified *text and lifecycle* events emitted by
 * the Responses API over SSE. Other events (including reasoning and tools) are
 * not turned into user-visible text; later layers may handle them separately.
 *
 * This is an envelope parser, not the HTTP transport, response assembler, or
 * permission/credential authority.
 */
sealed interface ResponsesStreamEvent {
    data class TextDelta(
        val delta: String,
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
    ) : ResponsesStreamEvent

    data class Completed(val responseId: String, val usage: TokenUsage?) : ResponsesStreamEvent
    data class Failed(val responseId: String, val code: String?, val message: String?) : ResponsesStreamEvent
    data class Incomplete(val responseId: String, val reason: String?) : ResponsesStreamEvent
    data class Error(val code: String?, val message: String) : ResponsesStreamEvent

    /** A well-formed event this narrow text/lifecycle parser does not interpret. */
    data class Other(val type: String) : ResponsesStreamEvent
}

/** Only provider-reported counts; null means the provider has not supplied them. */
data class TokenUsage(val inputTokens: Long, val outputTokens: Long, val totalTokens: Long)

class ResponsesEventParser(private val maxJsonChars: Int = 262_144) {
    init {
        require(maxJsonChars > 0)
    }

    fun parse(event: SseEvent): ResponsesStreamEvent {
        require(event.data.length <= maxJsonChars) { "Responses event JSON limit exceeded" }
        val root = Json.parseToJsonElement(event.data) as? JsonObject
            ?: throw IllegalArgumentException("Responses event must be a JSON object")
        val type = root.requiredString("type")
        require(event.type == "message" || event.type == type) {
            "SSE event name and JSON type disagree"
        }

        return when (type) {
            "response.output_text.delta" -> ResponsesStreamEvent.TextDelta(
                delta = root.requiredString("delta"),
                itemId = root.requiredString("item_id"),
                outputIndex = root.requiredIndex("output_index"),
                contentIndex = root.requiredIndex("content_index"),
            )
            "response.completed" -> {
                val response = root.requiredObject("response")
                require(response.requiredString("status") == "completed") { "Response did not complete" }
                ResponsesStreamEvent.Completed(
                    responseId = response.requiredString("id"),
                    usage = response.optionalObject("usage")?.let { usage ->
                        TokenUsage(
                            inputTokens = usage.requiredCount("input_tokens"),
                            outputTokens = usage.requiredCount("output_tokens"),
                            totalTokens = usage.requiredCount("total_tokens"),
                        )
                    },
                )
            }
            "response.failed" -> {
                val response = root.requiredObject("response")
                require(response.requiredString("status") == "failed") { "Not a failed response" }
                val error = response.optionalObject("error")
                ResponsesStreamEvent.Failed(
                    responseId = response.requiredString("id"),
                    code = error?.optionalString("code"),
                    message = error?.optionalString("message"),
                )
            }
            "response.incomplete" -> {
                val response = root.requiredObject("response")
                require(response.requiredString("status") == "incomplete") { "Not an incomplete response" }
                ResponsesStreamEvent.Incomplete(
                    responseId = response.requiredString("id"),
                    reason = response.optionalObject("incomplete_details")?.optionalString("reason"),
                )
            }
            "error" -> ResponsesStreamEvent.Error(
                code = root.optionalString("code"),
                message = root.requiredString("message"),
            )
            else -> ResponsesStreamEvent.Other(type)
        }
    }

    private fun JsonObject.requiredObject(key: String): JsonObject =
        this[key] as? JsonObject ?: throw IllegalArgumentException("Missing or invalid ${key} object")

    private fun JsonObject.optionalObject(key: String): JsonObject? {
        val value = this[key] ?: return null
        if (value == JsonNull) return null
        return value as? JsonObject ?: throw IllegalArgumentException("Invalid ${key} object")
    }

    private fun JsonObject.requiredString(key: String): String {
        val value = this[key] as? JsonPrimitive
        require(value?.isString == true && value.content.isNotEmpty()) {
            "Missing or invalid ${key} string"
        }
        return value.content
    }

    private fun JsonObject.optionalString(key: String): String? {
        val value = this[key] ?: return null
        if (value == JsonNull) return null
        val primitive = value as? JsonPrimitive
        require(primitive?.isString == true) { "Invalid ${key} string" }
        return primitive.content
    }

    private fun JsonObject.requiredIndex(key: String): Int {
        val value = (this[key] as? JsonPrimitive)?.intOrNull
            ?: throw IllegalArgumentException("Missing or invalid ${key} index")
        require(value >= 0) { "Negative ${key} index" }
        return value
    }

    private fun JsonObject.requiredCount(key: String): Long {
        val value = (this[key] as? JsonPrimitive)?.longOrNull
            ?: throw IllegalArgumentException("Missing or invalid ${key} count")
        require(value >= 0) { "Negative ${key} count" }
        return value
    }
}
