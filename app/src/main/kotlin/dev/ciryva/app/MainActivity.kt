package dev.ciryva.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Canvas = Color(0xFF0A0C10)
private val Graphite = Color(0xFF151A21)
private val Silver = Color(0xFFEAF2FF)
private val Muted = Color(0xFF9FB0C5)
private val Blue = Color(0xFF27B2FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Compose is dark even when the system theme is light. Keep system-bar
        // icons light for contrast against CiRYVA's near-black canvas.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(10, 12, 16)),
        )
        val editor = DraftEditor(
            SharedPreferencesDraftStore(
                applicationContext.getSharedPreferences("ciryva_local", MODE_PRIVATE),
            ),
        )
        setContent {
            CiRYVAHome(editor, onOpenChatPreview = {
                startActivity(Intent(this, ChatPreviewActivity::class.java))
            })
        }
    }
}

/**
 * P02 working shell: save and reopen a real offline draft. No dummy Send,
 * model picker, threads, camera, agent or API controls are exposed yet.
 */
@Composable
private fun CiRYVAHome(editor: DraftEditor, onOpenChatPreview: () -> Unit) {
    val storedAtLaunch = remember { editor.load() }
    var draft by rememberSaveable { mutableStateOf(storedAtLaunch) }
    var status by rememberSaveable { mutableStateOf("Draft stored only on this device") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue,
            background = Canvas,
            surface = Graphite,
            onPrimary = Canvas,
            onBackground = Silver,
            onSurface = Silver,
        ),
    ) {
        Surface(color = Canvas, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "CiRYVA",
                    fontSize = 20.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Silver,
                )
                Text(
                    "LOCAL DRAFT  /  ANDROID FOUNDATION",
                    fontSize = 11.sp,
                    color = Blue,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Write a message or note offline.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Silver,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Saving works now. OpenAI chat will appear only when the real connection is implemented.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { newText ->
                        if (newText.length <= DraftEditor.MAX_LENGTH) {
                            draft = newText
                            status = "Unsaved changes"
                        }
                    },
                    label = { Text("Local draft") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .testTag("draft-input"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                    ),
                    placeholder = { Text("Enter a draft…") },
                    singleLine = false,
                )
                Spacer(Modifier.height(12.dp))
                // Save(empty) overwrites stale drafts; Clear always removes persisted data.
                // Do not disable actions based on ephemeral editor state.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("save-draft"),
                        onClick = {
                            if (editor.save(draft)) {
                                status = "Saved on device"
                            } else {
                                status = "Save failed"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    ) {
                        Text("Save draft", modifier = Modifier.heightIn(min = 40.dp), fontSize = 16.sp, lineHeight = 24.sp, softWrap = false)
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("clear-draft"),
                        onClick = {
                            if (editor.clear()) {
                                draft = ""
                                status = "Saved draft cleared"
                            } else {
                                status = "Clear failed"
                            }
                        },
                    ) {
                        Text("Clear draft", modifier = Modifier.heightIn(min = 40.dp), fontSize = 16.sp, lineHeight = 24.sp, softWrap = false)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "$status  ·  ${draft.length}/${DraftEditor.MAX_LENGTH}",
                    fontSize = 12.sp,
                    color = Muted,
                )
                Spacer(Modifier.height(22.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("open-chat-preview"),
                    onClick = onOpenChatPreview,
                ) {
                    Text("Open chat layout preview", fontSize = 16.sp)
                }
                Text(
                    "Preview only: local user messages, no OpenAI connection or conversation archive.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
