package dev.ciryva.app

import android.os.Bundle
import androidx.compose.runtime.mutableStateOf

/**
 * Owner-selected OCR and paid voice-note transcripts pending explicit review.
 * The process-wide state survives Activity recreation. A bounded Android saved
 * state copy additionally survives a cold process restart with the same task.
 * No message is sent, no API key is stored, and no archive entry is created.
 */
internal object PendingImportedText {
    private const val TEXT_KEY = "ciryva.intake.review.text"
    private const val SOURCE_KEY = "ciryva.intake.review.source"
    private const val MAX_TEXT_CHARS = 32_768
    private const val MAX_SOURCE_CHARS = 120

    val text = mutableStateOf<String?>(null)
    val source = mutableStateOf("Extracted text")

    fun save(outState: Bundle) {
        val current = text.value ?: return
        if (current.length > MAX_TEXT_CHARS) return
        outState.putString(TEXT_KEY, current)
        outState.putString(SOURCE_KEY, source.value.take(MAX_SOURCE_CHARS))
    }

    /** Only call after actual process startup, NOT ordinary Activity rotation. */
    fun restoreCold(savedState: Bundle?) {
        if (text.value != null) return // a newer in-process result always wins
        val saved = savedState?.getString(TEXT_KEY) ?: return
        if (saved.length > MAX_TEXT_CHARS) return
        text.value = saved
        source.value = savedState.getString(SOURCE_KEY)
            ?.take(MAX_SOURCE_CHARS) ?: "Extracted text"
    }

    fun dismiss() {
        text.value = null
        source.value = "Extracted text"
    }
}
