package dev.ciryva.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Canvas = CiRYVAVisual.ink
private val Graphite = CiRYVAVisual.raised
private val Silver = CiRYVAVisual.ivory
private val Muted = CiRYVAVisual.muted
private val Blue = CiRYVAVisual.rose

class ChatPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(10, 12, 16)),
        )
        setContent {
            ChatPreviewScreen(
                onBack = ::finish,
                onOpenByokSettings = {
                    startActivity(android.content.Intent(this, ByokSettingsActivity::class.java))
                },
            )
        }
    }
}

/**
 * P04 chat UI geometry preview: session-only user-authored bubbles.
 * This screen deliberately has NO Send, model picker, streaming indicator,
 * assistant bubbles, provider credentials or invented tool responses.
 */
@Composable
fun ChatPreviewScreen(onBack: () -> Unit, onOpenByokSettings: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    var messages by rememberSaveable { mutableStateOf(listOf<String>()) }
    val canAdd = draft.isNotBlank() &&
        draft.trim().length <= LocalChatPreview.MAX_MESSAGE_CHARS &&
        messages.size < LocalChatPreview.MAX_MESSAGES

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue, background = Canvas, surface = Graphite,
            onPrimary = Canvas, onBackground = Silver, onSurface = Silver,
        ),
    ) {
        CiRYVABackdrop {
            // Available height is measured AFTER system bars and IME.
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding(),
            ) {
                val compact = maxHeight < 520.dp
                Column(
                    modifier = Modifier.fillMaxSize()
                        .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.testTag("chat-back")) {
                        Text("Back")
                    }
                    Column {
                        Text("CiRYVA", color = Silver, fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("CHAT LAYOUT PREVIEW", color = Blue, fontSize = 11.sp,
                            letterSpacing = 1.sp)
                    }
                }
                OutlinedButton(
                    onClick = onOpenByokSettings,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("open-byok-settings"),
                ) { Text("BYOK key settings") }
                Spacer(Modifier.height(12.dp))
                Text(
                    "OFFLINE PREVIEW · NO MODEL CONNECTED",
                    modifier = Modifier.testTag("chat-offline-label"),
                    style = MaterialTheme.typography.labelMedium, color = Blue,
                )
                Text(
                    "Only messages you add appear below. No request is sent, " +
                        "and this preview has no permanent conversation archive.",
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .then(
                            if (compact) Modifier
                            else Modifier.weight(1f).verticalScroll(rememberScrollState())
                        )
                        .testTag("chat-preview-transcript"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (messages.isEmpty()) {
                        Text("No messages yet. Add a local example to examine the layout.",
                            color = Muted, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        messages.forEachIndexed { index, message ->
                            Surface(color = Graphite,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("local-chat-message-$index")) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("YOU · LOCAL ONLY", color = Blue,
                                        style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(6.dp))
                                    Text(message, color = Silver,
                                        style = MaterialTheme.typography.bodyLarge,
                                        lineHeight = 24.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= LocalChatPreview.MAX_MESSAGE_CHARS) draft = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                        .testTag("local-chat-composer"),
                    label = { Text("Preview composer") },
                    placeholder = { Text("Write a local layout example…") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp, lineHeight = 24.sp),
                    maxLines = 5,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = canAdd,
                        onClick = {
                            val next = LocalChatPreview.appendUserMessage(messages, draft)
                            if (next != null) {
                                messages = next
                                draft = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .testTag("chat-add-local"),
                    ) { Text("Add locally") }
                    OutlinedButton(
                        enabled = messages.isNotEmpty(),
                        onClick = { messages = emptyList() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .testTag("chat-clear-local"),
                    ) { Text("Clear preview") }
                }
                Text(
                    "Session preview: ${messages.size}/${LocalChatPreview.MAX_MESSAGES} local messages",
                    style = MaterialTheme.typography.labelSmall, color = Muted,
                )
                }
            }
        }
    }
}
