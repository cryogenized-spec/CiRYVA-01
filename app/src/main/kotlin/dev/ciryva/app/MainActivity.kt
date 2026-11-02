package dev.ciryva.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        enableEdgeToEdge()
        val editor = DraftEditor(
            SharedPreferencesDraftStore(
                applicationContext.getSharedPreferences("ciryva_local", MODE_PRIVATE),
            ),
        )
        setContent {
            CiRYVAHome(editor)
        }
    }
}

/**
 * P02 working shell: save and reopen a real offline draft. No dummy Send,
 * model picker, threads, camera, agent or API controls are exposed yet.
 */
@Composable
private fun CiRYVAHome(editor: DraftEditor) {
    var draft by rememberSaveable { mutableStateOf(editor.load()) }
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
                        .weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                    ),
                    placeholder = { Text("Enter a draft…") },
                    singleLine = false,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = draft.isNotEmpty(),
                        onClick = {
                            status = if (editor.save(draft)) "Saved on device" else "Save failed"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    ) {
                        Text("Save draft")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = draft.isNotEmpty(),
                        onClick = {
                            if (editor.clear()) {
                                draft = ""
                                status = "Saved draft cleared"
                            } else {
                                status = "Clear failed"
                            }
                        },
                    ) {
                        Text("Clear draft")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "$status  ·  ${draft.length}/${DraftEditor.MAX_LENGTH}",
                    fontSize = 12.sp,
                    color = Muted,
                )
            }
        }
    }
}
