package dev.ciryva.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dev.ciryva.core.stream.AudioTranscriptionResult
import kotlinx.coroutines.delay
import java.io.File

/** Finalized speech text is inserted only into the user's editable draft. */
internal fun appendDictatedText(existing: String, recognition: String): String {
    val words = recognition.trim()
    if (words.isEmpty()) return existing
    if (existing.isEmpty() || existing.last().isWhitespace()) return existing + words
    return "$existing $words"
}

/** Single app-private AAC/M4A recording; never stored in chat history. */
private class AudioDraftSession(
    private val context: Context,
    private val onHardwareLimit: () -> Unit,
) {
    private var recorder: MediaRecorder? = null
    private var temporary: File? = null
    var closed: Boolean = false
        private set

    fun start(): Boolean {
        if (closed || recorder != null || temporary != null) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) return false
        val file = try { File.createTempFile("ciryva_voice_", ".m4a", context.cacheDir) }
            catch (_: Exception) { return false }
        val microphone = try { MediaRecorder() }
            catch (_: Exception) { file.delete(); return false }
        try {
            microphone.setAudioSource(MediaRecorder.AudioSource.MIC)
            microphone.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            microphone.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            microphone.setAudioEncodingBitRate(48_000)
            microphone.setAudioSamplingRate(24_000)
            microphone.setOutputFile(file.absolutePath)
            microphone.setMaxDuration(120_000)
            microphone.setMaxFileSize(10L * 1024 * 1024)
            microphone.setOnInfoListener { _, code, _ ->
                if (code == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    code == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    // Hardware already stopped the recorder; do not upload automatically.
                    discard()
                    onHardwareLimit()
                }
            }
            microphone.prepare()
            microphone.start()
            recorder = microphone
            temporary = file
            return true
        } catch (_: Exception) {
            runCatching { microphone.reset() }
            runCatching { microphone.release() }
            file.delete()
            return false
        }
    }

    fun stop(): Boolean {
        val active = recorder ?: return false
        recorder = null
        val valid = try {
            active.stop()
            (temporary?.length() ?: 0) > 0
        } catch (_: RuntimeException) { false }
        finally {
            runCatching { active.reset() }
            runCatching { active.release() }
        }
        if (!valid) discard()
        return valid
    }

    /** Activity owns deletion after this handoff, even when upload is cancelled. */
    fun takeRecording(): File? {
        if (closed || recorder != null) return null
        val file = temporary
        temporary = null
        return file
    }

    fun discard() {
        recorder?.let {
            recorder = null
            runCatching { it.stop() }
            runCatching { it.reset() }
            runCatching { it.release() }
        }
        temporary?.delete()
        temporary = null
    }

    fun close() { closed = true; discard() }
}

/** Start microphone -> Stop locally -> explicit billed OpenAI transcription -> editable draft. */
@Composable
internal fun CloudDictationControl(
    enabled: Boolean,
    hasKey: Boolean,
    sessionKey: String?,
    onTranscribe: (File, (AudioTranscriptionResult) -> Unit) -> Unit,
    onCancelTranscription: () -> Unit,
    onFinalTranscript: (String) -> Boolean,
    onBusyChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val latestOnTranscribe = rememberUpdatedState(onTranscribe)
    val latestOnCancel = rememberUpdatedState(onCancelTranscription)
    val latestOnFinal = rememberUpdatedState(onFinalTranscript)
    val latestOnBusy = rememberUpdatedState(onBusyChange)
    var phase by remember(sessionKey) { mutableStateOf("idle") }
    var notice by remember(sessionKey) { mutableStateOf<String?>(null) }
    // Preserve a successfully billed transcript even if the chat draft is full.
    var overflowTranscript by rememberSaveable(sessionKey) {
        mutableStateOf<String?>(null)
    }
    var requestEpoch by remember(sessionKey) { mutableStateOf(0) }
    val session = remember(context, sessionKey) {
        AudioDraftSession(context) {
            phase = "idle"
            notice = "Recording limit reached; audio discarded. Try a shorter clip."
        }
    }
    // Pending permission is deliberately NOT saved across Activity recreation.
    // A grant delivered to a new Activity must not silently start a microphone.
    var permissionPending by remember(session) { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val originatedHere = permissionPending
        permissionPending = false
        val foreground = (context as? LifecycleOwner)?.lifecycle?.currentState
            ?.isAtLeast(Lifecycle.State.STARTED) == true
        if (originatedHere && !session.closed && enabled && hasKey &&
            foreground && phase == "idle") {
            if (granted && session.start()) {
                phase = "recording"
                notice = "Recording privately on your phone."
            } else notice = if (granted) "Unable to start microphone."
                else "Microphone permission denied."
        }
    }
    fun discardAll() {
        permissionPending = false
        requestEpoch++
        if (phase == "transcribing") latestOnCancel.value()
        session.discard()
        phase = "idle"
    }
    LaunchedEffect(enabled, session) {
        if (!enabled) discardAll()
    }
    LaunchedEffect(phase, session) {
        latestOnBusy.value(phase != "idle")
    }
    LaunchedEffect(phase, session) {
        if (phase == "recording") {
            // Stop before MediaRecorder's hard 120-second ceiling. Never auto-upload.
            delay(110_000)
            if (phase == "recording") {
                if (session.stop()) {
                    phase = "ready"
                    notice = "Recording stopped at the time limit. Review and transcribe."
                } else {
                    phase = "idle"
                    notice = "Recording was interrupted. Try again."
                }
            }
        }
    }
    val lifecycle = (context as? LifecycleOwner)?.lifecycle
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) discardAll()
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            discardAll()
            session.close()
            latestOnBusy.value(false)
        }
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            when (phase) {
                "recording" -> {
                    TextButton(
                        onClick = {
                            if (session.stop()) {
                                phase = "ready"
                                notice = "Recording ready. Transcribe sends audio to OpenAI."
                            } else {
                                phase = "idle"
                                notice = "Recording too short or interrupted."
                            }
                        },
                        modifier = Modifier.testTag("vtt-stop"),
                    ) { Text("Stop recording") }
                    TextButton(onClick = {
                        discardAll()
                        notice = "Recording discarded."
                    }, modifier = Modifier.testTag("vtt-cancel")) { Text("Discard") }
                }
                "ready" -> {
                    TextButton(
                        enabled = enabled && hasKey,
                        modifier = Modifier.testTag("vtt-transcribe"),
                        onClick = {
                            val audio = session.takeRecording() ?: return@TextButton
                            val epoch = ++requestEpoch
                            phase = "transcribing"
                            notice = "Transcribing with gpt-transcribe…"
                            latestOnTranscribe.value(audio) { result ->
                                if (!session.closed && epoch == requestEpoch) {
                                    phase = "idle"
                                    when (result) {
                                        is AudioTranscriptionResult.Success -> {
                                            val inserted = enabled && latestOnFinal.value(result.text)
                                            if (inserted) overflowTranscript = null
                                            else overflowTranscript = result.text
                                            notice = if (inserted)
                                                "Transcript added. Review before Send."
                                            else "Draft full. Transcript kept below: copy or split it before dismissing."

                                        }
                                        is AudioTranscriptionResult.Failure -> notice = result.message
                                        AudioTranscriptionResult.Cancelled ->
                                            notice = "Cancelled. A request already received may be billed."
                                    }
                                }
                            }
                        },
                    ) { Text("Transcribe with OpenAI") }
                    TextButton(onClick = {
                        discardAll()
                        notice = "Recording discarded."
                    }, modifier = Modifier.testTag("vtt-cancel")) { Text("Discard") }
                }
                "transcribing" -> {
                    TextButton(
                        onClick = {
                            discardAll()
                            notice = "Upload cancelled; an already received request may be billed."
                        },
                        modifier = Modifier.testTag("vtt-cancel-upload"),
                    ) { Text("Cancel transcription") }
                }
                else -> {
                    TextButton(
                        enabled = enabled && hasKey,
                        modifier = Modifier.testTag("vtt-start"),
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED) {
                                if (session.start()) {
                                    phase = "recording"
                                    notice = "Recording privately on your phone."
                                } else notice = "Could not start microphone."
                            } else {
                                permissionPending = true
                                permission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    ) { Text("🎙 Record voice") }
                }
            }
        }
        if (!hasKey) Text("Configure an OpenAI API key to transcribe speech.",
            fontSize = 11.sp, modifier = Modifier.testTag("vtt-missing-key"))
        if (phase == "idle" && notice == null) Text(
            "gpt-transcribe · billed per minute · maximum 2 min per recording.",
            fontSize = 11.sp,
        )
        notice?.let { Text(it, fontSize = 11.sp, modifier = Modifier.testTag("vtt-status")) }
        overflowTranscript?.let { recovered ->
            OutlinedTextField(
                value = recovered,
                onValueChange = {},
                readOnly = true,
                maxLines = 5,
                label = { Text("Recovered voice transcript") },
                modifier = Modifier.testTag("vtt-recovered-transcript"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? ClipboardManager
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("CiRYVA voice transcript", recovered))
                        notice = "Transcript copied. Review the clipboard before pasting."
                    },
                    modifier = Modifier.testTag("vtt-copy-transcript"),
                ) { Text("Copy transcript") }
                TextButton(
                    onClick = { overflowTranscript = null },
                    modifier = Modifier.testTag("vtt-dismiss-transcript"),
                ) { Text("Dismiss") }
            }
        }
    }
}
