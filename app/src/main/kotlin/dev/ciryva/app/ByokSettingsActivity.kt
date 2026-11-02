package dev.ciryva.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class ByokSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots, recordings and the Android recents thumbnail.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(10, 12, 16))
        )
        val storage = AndroidByokKeyStore(applicationContext)
        setContent { ByokKeyScreen(storage, onBack = ::finish) }
    }
}

@Composable
private fun ByokKeyScreen(storage: AndroidByokKeyStore, onBack: () -> Unit) {
    // Never save the credential into Compose saved state or Android process
    // snapshots: rotation clears the pending entry intentionally.
    var pendingKey by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(storage.hasKey()) }
    var notice by remember { mutableStateOf("No API request is made from this screen.") }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF27B2FF),
        background = Color(0xFF0A0C10),
        surface = Color(0xFF151A21),
    )) {
        Surface(color = Color(0xFF0A0C10), modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Text("Your OpenAI API key", style = MaterialTheme.typography.titleLarge)
                Text(
                    "BYOK · This is your own API account. CiRYVA does not provide free inference. " +
                    "Your key stays encrypted on this device and is never uploaded to the app developer.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (hasKey) "A key is stored on this device (value never displayed)."
                    else "No API key stored.",
                    modifier = Modifier.testTag("byok-key-status"),
                )
                OutlinedTextField(
                    value = pendingKey,
                    onValueChange = { if (it.length <= 512) pendingKey = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                        .testTag("byok-key-entry"),
                    label = { Text("Paste your own OpenAI project key") },
                    visualTransformation = PasswordVisualTransformation(),
                    // Password input flags instruct the IME not to learn or suggest API keys.
                    // Visual masking alone does NOT enforce password keyboard behaviour.
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        try {
                            storage.save(pendingKey)
                            pendingKey = ""
                            hasKey = true
                            notice = "Key saved securely on this device."
                        } catch (_: Exception) {
                            notice = "Unable to save key securely. No key was sent."
                        }
                    },
                    enabled = pendingKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("byok-save-key")
                ) { Text("Save key on device") }
                OutlinedButton(
                    onClick = {
                        try {
                            storage.clear()
                            pendingKey = ""
                            hasKey = false
                            notice = "Stored API key removed."
                        } catch (_: Exception) {
                            notice = "Unable to remove stored key."
                        }
                    },
                    enabled = hasKey,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("byok-remove-key"),
                ) { Text("Remove stored key") }
                Spacer(Modifier.height(4.dp))
                Text(notice, modifier = Modifier.testTag("byok-notice"),
                    style = MaterialTheme.typography.bodySmall)
                Text(
                    "API charges and complimentary data-sharing tokens belong to your own " +
                    "OpenAI organization. Data sharing is an optional setting on OpenAI's " +
                    "website, not something CiRYVA switches on.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "This settings screen never sends an API request. " +
                    "Return to Live OpenAI chat to select a model and authorize each Send.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
