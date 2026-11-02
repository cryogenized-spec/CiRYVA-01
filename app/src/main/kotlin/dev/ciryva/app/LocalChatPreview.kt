package dev.ciryva.app

/**
 * Session-only chat UX preview. Not a durable archive or fake assistant.
 * Only the device user's own words can enter this transcript.
 */
object LocalChatPreview {
    const val MAX_MESSAGES = 12
    const val MAX_MESSAGE_CHARS = 1200

    fun appendUserMessage(messages: List<String>, draft: String): List<String>? {
        val trimmed = draft.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_MESSAGE_CHARS ||
            messages.size >= MAX_MESSAGES
        ) return null
        return messages + trimmed
    }
}
