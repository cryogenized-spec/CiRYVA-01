package dev.ciryva.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Pure state rules for the one right-hand composer control. */
internal object ComposerActionRules {
    fun showsMicrophone(draft: String): Boolean = draft.isBlank()
    fun qualifiesVoiceSwipe(verticalPx: Float, elapsedMs: Long): Boolean =
        verticalPx <= -48f && elapsedMs >= 180L
}

/**
 * Native gesture: tap Send when text exists, tap microphone on blank draft,
 * or hold-and-slide upward on Send to start recording WITHOUT sending.
 * A long-press accessibility action is provided for screen-reader users.
 *
 * The user must separately stop, inspect and explicitly transcribe audio.
 */
@Composable
internal fun ComposerSendVoiceButton(
    draftHasText: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onRecord: () -> Unit,
) {
    val latestSend = rememberUpdatedState(onSend)
    val latestRecord = rememberUpdatedState(onRecord)
    val foreground = Color(0xFF26131F)
    val color = if (enabled) CiRYVAVisual.rose else CiRYVAVisual.muted.copy(alpha = .45f)
    val label = if (draftHasText) "Send message; hold and swipe up to record voice"
        else "Record voice"
    fun tap() {
        if (!enabled) return
        if (draftHasText) latestSend.value() else latestRecord.value()
    }
    fun record() { if (enabled) latestRecord.value() }

    Box(
        modifier = Modifier.size(52.dp)
            .background(color, CircleShape)
            .pointerInput(enabled, draftHasText) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var triggered = false
                    var ended = false
                    while (!ended) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        val travelY = change.position.y - down.position.y
                        val travelX = change.position.x - down.position.x
                        val duration = change.uptimeMillis - down.uptimeMillis
                        if (!triggered && enabled &&
                            ComposerActionRules.qualifiesVoiceSwipe(travelY, duration)) {
                            triggered = true
                            record()
                            change.consume()
                        }
                        if (change.changedToUpIgnoreConsumed()) {
                            ended = true
                            if (!triggered && kotlin.math.abs(travelY) < 22f &&
                                kotlin.math.abs(travelX) < 22f &&
                                duration <= 600L) tap()
                        } else if (!change.pressed) ended = true
                    }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick(label = if (draftHasText) "Send message" else "Record voice") {
                    tap()
                    true
                }
                onLongClick(label = "Record voice") {
                    record()
                    true
                }
            }
            .testTag(if (draftHasText) "live-send" else "vtt-start"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (draftHasText) R.drawable.ic_composer_send
                else R.drawable.ic_composer_mic),
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(24.dp),
        )
    }
}
