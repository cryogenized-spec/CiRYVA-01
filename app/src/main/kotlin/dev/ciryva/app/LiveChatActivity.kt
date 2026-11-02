package dev.ciryva.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ciryva.core.stream.CiRYVAModel
import dev.ciryva.core.stream.CiRYVAModelCatalog
import dev.ciryva.core.stream.GenerationSettings
import dev.ciryva.core.stream.ResponsesHttpTransport
import dev.ciryva.core.stream.TextResponseRequest

private val canvas = Color(0xFF0A0C10)
private val graphite = Color(0xFF151A21)
private val silver = Color(0xFFEAF2FF)
private val muted = Color(0xFF9FB0C5)
private val blue = Color(0xFF27B2FF)

/**
 * The first chargeable UI is deliberately separate from the previously tested
 * OFFLINE PREVIEW. No key or prompt is submitted until a user presses Send.
 */
class LiveChatActivity : ComponentActivity() {
    private val snapshot = mutableStateOf(LiveChatSnapshot())
    private val keyPresent = mutableStateOf(false)
    private var controller: LiveChatController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(10, 12, 16)),
        )
        val keyStore = AndroidByokKeyStore(applicationContext)
        controller = LiveChatController(
            client = ChatStreamClient { request, key, cancel, onEvent ->
                ResponsesHttpTransport().stream(request, key, cancel, onEvent)
            },
            credentialProvider = { keyStore.load() },
            onUpdate = { value ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) snapshot.value = value
                }
            },
        )
        setContent {
            LiveChatScreen(
                snapshot = snapshot.value,
                hasKey = keyPresent.value,
                onBack = ::finish,
                onKeys = { startActivity(Intent(this, ByokSettingsActivity::class.java)) },
                onSend = { controller?.send(it) ?: false },
                onCancel = { controller?.cancel() ?: false },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        keyPresent.value = AndroidByokKeyStore(applicationContext).hasKey()
    }

    // Never leave a chargeable request running invisibly in the background.
    override fun onStop() {
        controller?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        controller?.close()
        controller = null
        super.onDestroy()
    }
}

@Composable
private fun ChoiceField(
    label: String,
    chosen: String,
    options: List<Pair<String, String>>,
    tag: String,
    onChoose: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = muted, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
        ) {
            Text(options.firstOrNull { it.first == chosen }?.second ?: chosen)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, description) ->
                DropdownMenuItem(
                    text = { Text(description) },
                    onClick = {
                        onChoose(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun LiveChatScreen(
    snapshot: LiveChatSnapshot,
    hasKey: Boolean,
    onBack: () -> Unit,
    onKeys: () -> Unit,
    onSend: (TextResponseRequest) -> Boolean,
    onCancel: () -> Boolean,
) {
    val default = "Provider default"
    val models = CiRYVAModelCatalog.selectable
    var modelId by rememberSaveable { mutableStateOf("gpt-5.4-mini-2026-03-17") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var effort by rememberSaveable { mutableStateOf(default) }
    var summary by rememberSaveable { mutableStateOf(default) }
    var verbosity by rememberSaveable { mutableStateOf(default) }
    var sampling by rememberSaveable { mutableStateOf("Provider default") }
    var temperature by rememberSaveable { mutableStateOf("1.0") }
    var topP by rememberSaveable { mutableStateOf("1.0") }
    var outputLimit by rememberSaveable { mutableStateOf("1024") }
    var chargeAcknowledged by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val model: CiRYVAModel = models.firstOrNull { it.id == modelId } ?: models.first()
    val samplingAllowed = model.supportsSampling &&
        (!model.samplingOnlyWhenNoReasoning || effort == "none")

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = blue, background = canvas, surface = graphite,
            onPrimary = canvas, onBackground = silver, onSurface = silver,
        ),
    ) {
        Surface(color = canvas, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.testTag("live-back")) {
                        Text("Back")
                    }
                    Column {
                        Text("CiRYVA", color = silver, fontSize = 20.sp)
                        Text("LIVE OPENAI · USER-OWNED KEY", color = blue, fontSize = 11.sp)
                    }
                }
                Text(
                    "This screen can make paid OpenAI API requests with YOUR own key. " +
                    "ChatGPT Plus does not pay for API usage. Never use an administrator key. " +
                    "A compromised phone can expose client-side API keys.",
                    color = muted, style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onKeys,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("live-key-settings"),
                ) { Text(if (hasKey) "Manage stored API key" else "Add your own API key") }
                ChoiceField(
                    label = "Model (availability depends on your OpenAI project)",
                    chosen = modelId,
                    options = models.map { it.id to it.label },
                    tag = "live-model-picker",
                ) { selected ->
                    modelId = selected
                    effort = default
                    summary = default
                    verbosity = default
                    sampling = default
                    localError = null
                }
                model.retirementNote?.let {
                    Text(it, color = muted, style = MaterialTheme.typography.bodySmall)
                }
                if (model.reasoningEfforts.isNotEmpty()) {
                    ChoiceField(
                        "Thinking effort",
                        effort,
                        listOf(default to default) +
                            model.reasoningEfforts.sorted().map { it to it },
                        "live-effort-picker",
                    ) {
                        effort = it
                        if (model.samplingOnlyWhenNoReasoning && effort != "none") sampling = default
                    }
                }
                if (model.summaryModes.isNotEmpty()) {
                    ChoiceField(
                        "Reasoning summary (provider may omit it)",
                        summary,
                        listOf(default to default) + model.summaryModes.sorted().map { it to it },
                        "live-summary-picker",
                    ) { summary = it }
                }
                if (model.supportsVerbosity) {
                    ChoiceField(
                        "Answer verbosity",
                        verbosity,
                        listOf(default to default) + listOf("low", "medium", "high").map { it to it },
                        "live-verbosity-picker",
                    ) { verbosity = it }
                }
                if (samplingAllowed) {
                    ChoiceField(
                        "Sampling",
                        sampling,
                        listOf(default to default, "temperature" to "Temperature", "top_p" to "Top-p"),
                        "live-sampling-picker",
                    ) { sampling = it }
                    if (sampling == "temperature" || sampling == "top_p") {
                        OutlinedTextField(
                            value = if (sampling == "temperature") temperature else topP,
                            onValueChange = {
                                if (it.length <= 8) {
                                    if (sampling == "temperature") temperature = it else topP = it
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("live-sampling-value"),
                            label = { Text(if (sampling == "temperature") "Temperature 0–2" else "Top-p >0–1") },
                            singleLine = true,
                        )
                    }
                }
                OutlinedTextField(
                    value = outputLimit,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) outputLimit = it },
                    label = { Text("Maximum output tokens (1–4096)") },
                    modifier = Modifier.fillMaxWidth().testTag("live-output-limit"),
                    singleLine = true,
                )
                Text(
                    "Each Send is one independent prompt. Previous messages are NOT sent as " +
                    "context yet; the durable archive is a later development phase.",
                    color = muted, style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { if (it.length <= 4_000) prompt = it },
                    label = { Text("Your prompt (max 4,000 characters)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp)
                        .testTag("live-composer"),
                    minLines = 3,
                    maxLines = 6,
                )
                Row {
                    Checkbox(
                        checked = chargeAcknowledged,
                        onCheckedChange = { chargeAcknowledged = it },
                        modifier = Modifier.testTag("live-charge-consent"),
                    )
                    Text(
                        "I authorize one API request when I press Send and understand " +
                        "my OpenAI account may be charged.",
                        color = silver,
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    enabled = hasKey && chargeAcknowledged && prompt.isNotBlank() && !snapshot.busy,
                    onClick = {
                        try {
                            val limit = outputLimit.toIntOrNull()
                                ?: throw IllegalArgumentException("Invalid token limit")
                            val settings = GenerationSettings(
                                modelId = modelId,
                                reasoningEffort = effort.takeUnless { it == default },
                                reasoningSummary = summary.takeUnless { it == default },
                                verbosity = verbosity.takeUnless { it == default },
                                temperature = if (sampling == "temperature")
                                    (temperature.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid temperature"))
                                    else null,
                                topP = if (sampling == "top_p")
                                    (topP.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid top-p"))
                                    else null,
                            )
                            val request = TextResponseRequest(
                                model = modelId, prompt = prompt.trim(),
                                maxOutputTokens = limit, settings = settings,
                            )
                            if (onSend(request)) {
                                localError = null
                                chargeAcknowledged = false
                            } else localError = "A request is already running."
                        } catch (_: IllegalArgumentException) {
                            localError = "Check sampling values and output-token cap."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("live-send"),
                ) { Text("Send to OpenAI (may incur charges)") }
                OutlinedButton(
                    onClick = { onCancel() },
                    enabled = snapshot.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("live-cancel"),
                ) { Text("Cancel request") }
                localError?.let { Text(it, color = blue, modifier = Modifier.testTag("live-validation")) }
                Text(snapshot.status, color = blue, modifier = Modifier.testTag("live-status"))
                if (snapshot.userPrompt.isNotEmpty()) {
                    Text("YOU · ${snapshot.modelId}", color = muted)
                    Surface(color = graphite) {
                        Text(snapshot.userPrompt, color = silver, modifier = Modifier
                            .fillMaxWidth().padding(12.dp).testTag("live-user-text"))
                    }
                }
                if (snapshot.assistantText.isNotEmpty()) {
                    Text("OPENAI · ${snapshot.outcome ?: "streaming"}", color = muted)
                    Surface(color = graphite) {
                        Text(snapshot.assistantText, color = silver, modifier = Modifier
                            .fillMaxWidth().padding(12.dp).testTag("live-assistant-text"))
                    }
                }
                snapshot.usage?.let { usage ->
                    Text(
                        "Provider receipt: input ${usage.inputTokens}, output " +
                        "${usage.outputTokens}, total ${usage.totalTokens} tokens.",
                        color = muted, modifier = Modifier.testTag("live-usage"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
