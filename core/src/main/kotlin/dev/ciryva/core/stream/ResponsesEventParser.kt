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

    /** Only provider-supplied summary content; never a raw reasoning trace. */
    data class ReasoningSummaryDelta(val delta: String) : ResponsesStreamEvent

    data class Completed(val responseId: String, val usage: TokenUsage?) : ResponsesStreamEvent
    data class Failed(val responseId: String, val code: String?, val message: String?) : ResponsesStreamEvent
    data class Incomplete(val responseId: String, val reason: String?, val usage: TokenUsage? = null) : ResponsesStreamEvent
    data class Error(val code: String?, val message: String) : ResponsesStreamEvent

    /**
     * A verified provider stream tool event. No tool arguments, outputs or
     * private reasoning cross this boundary. "prepared" is never execution.
     */
    data class ToolActivity(
        val kind: String,
        val phase: String,
        val itemId: String,
    ) : ResponsesStreamEvent

    /** A well-formed event this narrow text/lifecycle parser does not interpret. */
    data class Other(val type: String) : ResponsesStreamEvent
}

/** Only provider-reported counts; null means the provider has not supplied them. */
data class TokenUsage(val inputTokens: Long, val outputTokens: Long, val totalTokens: Long)

class ResponsesEventParser(private val maxJsonChars: Int = 262_144) {
    companion object {
        private val TOOL_KINDS = setOf(
            "web_search_call", "file_search_call", "code_interpreter_call",
            "mcp_call", "function_call",
        )
    }
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
                delta = root.requiredString("delta", allowEmpty = true),
                itemId = root.requiredString("item_id"),
                outputIndex = root.requiredIndex("output_index"),
                contentIndex = root.requiredIndex("content_index"),
            )
            "response.reasoning_summary_text.delta" -> ResponsesStreamEvent.ReasoningSummaryDelta(
                delta = root.requiredString("delta", allowEmpty = true),
            )
            "response.output_item.added", "response.output_item.done" -> {
                val item = root["item"] as? JsonObject
                val kind = item?.optionalString("type")
                val id = item?.optionalString("id")
                if (kind in TOOL_KINDS && id != null && id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
                    ResponsesStreamEvent.ToolActivity(
                        kind = kind!!,
                        // An output item finishing does not imply a custom
                        // function or MCP request actually ran on this phone.
                        phase = if (type.endsWith(".added")) "proposed" else "item_done",
                        itemId = id,
                    )
                } else ResponsesStreamEvent.Other(type)
            }
            "response.web_search_call.in_progress",
            "response.web_search_call.searching",
            "response.web_search_call.completed",
            "response.file_search_call.in_progress",
            "response.file_search_call.searching",
            "response.file_search_call.completed",
            "response.code_interpreter_call.in_progress",
            "response.code_interpreter_call.interpreting",
            "response.code_interpreter_call.completed",
            "response.mcp_call.in_progress",
            "response.mcp_call.completed",
            "response.mcp_call.failed" -> {
                val kind = type.removePrefix("response.").substringBeforeLast('.')
                val id = root.optionalString("item_id")
                val phase = type.substringAfterLast('.')
                if (kind in TOOL_KINDS && id != null &&
                    id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
                    ResponsesStreamEvent.ToolActivity(kind, phase, id)
                } else ResponsesStreamEvent.Other(type)
            }
            "response.completed" -> {
                val response = root.requiredObject("response")
                require(response.requiredString("status") == "completed") { "Response did not complete" }
                ResponsesStreamEvent.Completed(
                    responseId = response.requiredString("id"),
                    usage = response.optionalUsage(),
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
                    usage = response.optionalUsage(),
                )
            }
            "error" -> ResponsesStreamEvent.Error(
                code = root.optionalString("code"),
                message = root.requiredString("message"),
            )
            else -> ResponsesStreamEvent.Other(type)
        }
    }

    /** A missing/null provider receipt stays unknown; real counts must be JSON integers. */
    private fun JsonObject.optionalUsage(): TokenUsage? =
        optionalObject("usage")?.let { usage ->
            TokenUsage(
                inputTokens = usage.requiredCount("input_tokens"),
                outputTokens = usage.requiredCount("output_tokens"),
                totalTokens = usage.requiredCount("total_tokens"),
            )
        }

    private fun JsonObject.requiredObject(key: String): JsonObject =
        this[key] as? JsonObject ?: throw IllegalArgumentException("Missing or invalid ${key} object")

    private fun JsonObject.optionalObject(key: String): JsonObject? {
        val value = this[key] ?: return null
        if (value == JsonNull) return null
        return value as? JsonObject ?: throw IllegalArgumentException("Invalid ${key} object")
    }

    private fun JsonObject.requiredString(key: String, allowEmpty: Boolean = false): String {
        val value = this[key] as? JsonPrimitive
        require(value?.isString == true && (allowEmpty || value.content.isNotEmpty())) {
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
        val primitive = this[key] as? JsonPrimitive
            ?: throw IllegalArgumentException("Missing or invalid ${key} index")
        require(!primitive.isString) { "Quoted ${key} index is not a JSON number" }
        val value = primitive.intOrNull
            ?: throw IllegalArgumentException("Missing or invalid ${key} index")
        require(value >= 0) { "Negative ${key} index" }
        return value
    }

    private fun JsonObject.requiredCount(key: String): Long {
        val primitive = this[key] as? JsonPrimitive
            ?: throw IllegalArgumentException("Missing or invalid ${key} count")
        require(!primitive.isString) { "Quoted ${key} count is not a JSON number" }
        val value = primitive.longOrNull
            ?: throw IllegalArgumentException("Missing or invalid ${key} count")
        require(value >= 0) { "Negative ${key} count" }
        return value
    }
}
