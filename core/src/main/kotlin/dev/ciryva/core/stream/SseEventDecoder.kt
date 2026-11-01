package dev.ciryva.core.stream

/**
 * Incremental Server-Sent Events framing. This component receives already decoded
 * UTF-8 text; a future transport MUST use a stateful UTF-8 decoder (rather than
 * converting arbitrary network byte chunks to String independently).
 *
 * This class is independent of the OpenAI JSON schema and of Android.
 * It bounds individual lines and assembled event bodies to avoid unbounded memory growth.
 */
data class SseEvent(val type: String, val data: String, val id: String?)

class SseEventDecoder(
    private val maxLineChars: Int = 65_536,
    private val maxEventChars: Int = 262_144,
) {
    init {
        require(maxLineChars > 0)
        require(maxEventChars > 0)
    }

    private val pendingLine = StringBuilder()
    private val dataLines = ArrayList<String>()
    private var eventType: String? = null
    private var lastId: String? = null
    private var eventChars = 0

    fun feed(chunk: String): List<SseEvent> {
        val ready = mutableListOf<SseEvent>()
        for (char in chunk) {
            if (char == '\n') {
                acceptLine(pendingLine.toString().removeSuffix("\r"), ready)
                pendingLine.setLength(0)
            } else {
                pendingLine.append(char)
                require(pendingLine.length <= maxLineChars) { "SSE line limit exceeded" }
            }
        }
        return ready
    }

    /** Finish a deliberately ended stream, preserving an event lacking the final blank line. */
    fun finish(): List<SseEvent> {
        val ready = mutableListOf<SseEvent>()
        if (pendingLine.isNotEmpty()) acceptLine(pendingLine.toString().removeSuffix("\r"), ready)
        pendingLine.setLength(0)
        dispatch(ready)
        return ready
    }

    private fun acceptLine(line: String, out: MutableList<SseEvent>) {
        if (line.isEmpty()) {
            dispatch(out)
            return
        }
        if (line.startsWith(":")) return // SSE keepalive/comment
        val colon = line.indexOf(':')
        val field = if (colon == -1) line else line.substring(0, colon)
        val raw = if (colon == -1) "" else line.substring(colon + 1).removePrefix(" ")
        when (field) {
            "event" -> eventType = raw
            "id" -> if ('\u0000' !in raw) lastId = raw
            "data" -> {
                val nextSize = eventChars + raw.length + if (dataLines.isEmpty()) 0 else 1
                require(nextSize <= maxEventChars) { "SSE event limit exceeded" }
                eventChars = nextSize
                dataLines.add(raw)
            }
        }
    }

    private fun dispatch(out: MutableList<SseEvent>) {
        if (dataLines.isNotEmpty()) {
            out.add(SseEvent(eventType?.takeIf { it.isNotEmpty() } ?: "message",
                dataLines.joinToString("\n"), lastId))
        }
        dataLines.clear()
        eventType = null
        eventChars = 0
    }
}
