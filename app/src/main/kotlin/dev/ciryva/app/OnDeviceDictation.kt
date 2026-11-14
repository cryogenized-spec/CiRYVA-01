package dev.ciryva.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.util.Locale

/**
 * Only finalized recognizer output enters the draft. Partials are display-only.
 * Existing typing and attachment selection remain under the composer's control.
 */
internal fun appendDictatedText(existing: String, recognition: String): String {
    val words = recognition.trim()
    if (words.isEmpty()) return existing
    if (existing.isEmpty() || existing.last().isWhitespace()) return existing + words
    return "$existing $words"
}

/**
 * An explicitly started, on-device ONLY recognizer. No cloud fallback,
 * background audio, recorded audio storage, or OpenAI request is initiated here.
 */
private class LocalSpeechSession(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onState: (Boolean, String?) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var closed = false

    fun start() {
        if (closed || recognizer != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onState(false, "On-device speech recognition is unavailable on this phone.")
            return
        }
        try {
            val instance = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = instance
            instance.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (recognizer === instance) onState(true, "Listening…")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (recognizer === instance) onState(true, "Finishing transcription…")
                }
                override fun onError(error: Int) {
                    if (recognizer === instance) finish(
                        null, when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech recognized. Try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission is required."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                "Speech recognition is busy. Try again."
                            else -> "Speech recognition stopped (Android error $error)."
                        }
                    )
                }
                override fun onResults(results: Bundle?) {
                    if (recognizer !== instance) return
                    val result = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                    finish(result, if (result == null) "No speech recognized."
                        else "Transcript added to your editable draft.")
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (recognizer !== instance) return
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() } ?: return
                    onState(true, "Hearing: ${partial.take(90)}")
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            onState(true, "Starting on-device dictation…")
            instance.startListening(intent)
        } catch (_: RuntimeException) {
            finish(null, "Unable to start on-device speech recognition.")
        }
    }

    fun stop() {
        val active = recognizer ?: return
        onState(true, "Finishing transcription…")
        try {
            active.stopListening()
        } catch (_: RuntimeException) {
            finish(null, "Could not finish transcription.")
        }
    }

    fun cancel(silent: Boolean = false) {
        val active = recognizer
        recognizer = null
        if (active != null) {
            runCatching { active.cancel() }
            runCatching { active.destroy() }
        }
        onState(false, if (silent) null else "Dictation cancelled; draft unchanged.")
    }

    fun close() {
        closed = true
        cancel(silent = true)
    }

    private fun finish(text: String?, message: String) {
        val active = recognizer ?: return
        recognizer = null
        runCatching { active.destroy() }
        if (!text.isNullOrBlank()) onText(text)
        onState(false, message)
    }
}

@Composable
internal fun OnDeviceDictationControl(
    enabled: Boolean,
    sessionKey: String?,
    onFinalTranscript: (String) -> Unit,
) {
    val context = LocalContext.current
    val currentOnTranscript = rememberUpdatedState(onFinalTranscript)
    var active by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val available = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }
                .getOrDefault(false)
    }
    // Changing conversations replaces and disposes the original recognizer.
    val session = remember(context, sessionKey) {
        LocalSpeechSession(context, { text -> currentOnTranscript.value(text) },
            { listening, message -> active = listening; notice = message })
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) session.start()
        else notice = "Microphone permission denied; you can continue typing."
    }
    LaunchedEffect(enabled, session) {
        // Never keep recording when Send, archive loading or a new journal locks editing.
        if (!enabled) session.cancel(silent = true)
    }
    val lifecycle = (context as? LifecycleOwner)?.lifecycle
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.cancel(silent = true)
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            session.close()
        }
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (active) {
                TextButton(onClick = session::stop, modifier = Modifier.testTag("vtt-stop")) {
                    Text("Stop dictation")
                }
                TextButton(onClick = { session.cancel() },
                    modifier = Modifier.testTag("vtt-cancel")) {
                    Text("Cancel")
                }
            } else {
                TextButton(
                    enabled = enabled && available,
                    modifier = Modifier.testTag("vtt-start"),
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED) {
                            session.start()
                        } else {
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                ) { Text("🎙 Dictate") }
            }
        }
        if (!available) {
            Text("On-device speech input unavailable (Android 12+ and a local recognizer required).",
                fontSize = 11.sp, modifier = Modifier.testTag("vtt-unavailable"))
        }
        notice?.let { message ->
            Text(message, fontSize = 11.sp, modifier = Modifier.testTag("vtt-status"))
        }
    }
}
