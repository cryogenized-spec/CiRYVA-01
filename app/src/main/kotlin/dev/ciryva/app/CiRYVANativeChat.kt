package dev.ciryva.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.ciryva.core.stream.CiRYVAModel
import dev.ciryva.core.stream.CiRYVAModelCatalog
import dev.ciryva.core.stream.GenerationSettings
import dev.ciryva.core.stream.TextResponseRequest

// CiRYVA's rose/burgundy identity with M3 tonal surfaces. Unlike the concept
// illustration, this layout has no portrait or heavyweight decorative frames.
private val ink = Color(0xFF161118)
private val surfaceRaised = Color(0xFF261C26)
private val surfaceHigh = Color(0xFF352532)
private val accent = Color(0xFFE6A7AD)
private val accentContainer = Color(0xFF633044)
private val ivory = Color(0xFFF8E9E5)
private val secondary = Color(0xFFD2B9BE)
private val lowContrast = Color(0xFFBBA2AD)
private val messageShape = RoundedCornerShape(19.dp)
private const val DEFAULT_VALUE = "Provider default"

/**
 * Elara-inspired, chat-first P04 screen: a real mobile view with stable composer,
 * scrollable answer, model bottom sheet, separate settings and compact navigation.
 * This intentionally presents ONE provider request/turn, not fictional saved chat
 * history or re-sent prior messages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChatScreen(
    snapshot: LiveChatSnapshot,
    hasKey: Boolean,
    onBack: () -> Unit,
    onKeys: () -> Unit,
    onSend: (TextResponseRequest) -> Boolean,
    onCancel: () -> Boolean,
) {
    val models = CiRYVAModelCatalog.selectable
    var modelId by rememberSaveable { mutableStateOf("gpt-5.4-mini-2026-03-17") }
    val model: CiRYVAModel = models.firstOrNull { it.id == modelId } ?: models.first()
    var effort by rememberSaveable { mutableStateOf(DEFAULT_VALUE) }
    var verbosity by rememberSaveable { mutableStateOf(DEFAULT_VALUE) }
    var sampling by rememberSaveable { mutableStateOf(DEFAULT_VALUE) }
    var temperature by rememberSaveable { mutableStateOf("1.0") }
    var topP by rememberSaveable { mutableStateOf("1.0") }
    var outputLimit by rememberSaveable { mutableStateOf("1024") }
    var summaryEnabled by rememberSaveable { mutableStateOf(true) }
    var prompt by rememberSaveable { mutableStateOf("") }
    // The charge permission is intentionally never restored after process death.
    var chargeAcknowledged by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf("chat") }
    var sheet by remember { mutableStateOf<String?>(null) }
    var summaryExpanded by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val samplingAllowed = model.supportsSampling &&
        (!model.samplingOnlyWhenNoReasoning || effort == "none")
    if (!samplingAllowed && sampling != DEFAULT_VALUE) sampling = DEFAULT_VALUE

    fun makeRequest(): TextResponseRequest {
        val limit = outputLimit.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid output token limit")
        val settings = GenerationSettings(
            modelId = modelId,
            reasoningEffort = effort.takeUnless { it == DEFAULT_VALUE },
            verbosity = verbosity.takeUnless { it == DEFAULT_VALUE },
            temperature = if (sampling == "temperature")
                (temperature.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid temperature"))
                else null,
            topP = if (sampling == "top_p")
                (topP.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid top-p"))
                else null,
            // Summaries are provider-authored, not private raw chain of thought.
            reasoningSummary = if (summaryEnabled && "auto" in model.summaryModes) "auto" else null,
        )
        return TextResponseRequest(
            model = modelId,
            prompt = prompt.trim(),
            maxOutputTokens = limit,
            settings = settings,
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = ink,
            background = ink,
            onBackground = ivory,
            surface = surfaceRaised,
            onSurface = ivory,
            surfaceContainer = surfaceRaised,
            surfaceContainerHigh = surfaceHigh,
            secondary = secondary,
            onSecondary = ink,
        ),
    ) {
        Surface(color = ink, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack, modifier = Modifier.testTag("live-back")) {
                        Text("‹", fontSize = 29.sp)
                    }
                    Text(
                        "✧ CiRYVA",
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        color = ivory,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { sheet = "model" },
                        modifier = Modifier.testTag("live-model-picker"),
                    ) {
                        Text(model.label.substringBefore(" (legacy"), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                        Text(" ⌄")
                    }
                }
                HorizontalDivider(color = accent.copy(alpha = 0.27f), thickness = 1.dp)

                if (tab == "chat") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = { sheet = "effort" },
                            label = { Text("Effort · " + (if (effort == DEFAULT_VALUE) "Default" else effort)) },
                            enabled = model.reasoningEfforts.isNotEmpty(),
                            modifier = Modifier.testTag("live-effort-picker"),
                        )
                        AssistChip(
                            onClick = { tab = "settings" },
                            label = { Text("More settings") },
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = onKeys,
                            modifier = Modifier.testTag("live-key-settings"),
                        ) {
                            Text(if (hasKey) "Key ✓" else "Add key", fontSize = 12.sp)
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (snapshot.userPrompt.isEmpty()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("✧", color = accent, fontSize = 43.sp)
                                Text("What shall we explore?", color = ivory,
                                    fontFamily = FontFamily.Serif, fontSize = 23.sp)
                                Text(
                                    "Your next message is a separate request. " +
                                        "Saved conversations will come in a later release.",
                                    color = lowContrast, fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().testTag("live-transcript"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 16.dp, vertical = 14.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                item {
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End) {
                                        Surface(
                                            color = accentContainer,
                                            shape = RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp),
                                            modifier = Modifier.fillMaxWidth(0.92f),
                                        ) {
                                            Text(
                                                snapshot.userPrompt,
                                                color = ivory,
                                                modifier = Modifier.padding(14.dp)
                                                    .testTag("live-user-text"),
                                                fontSize = 15.sp,
                                                lineHeight = 22.sp,
                                            )
                                        }
                                    }
                                }
                                if (snapshot.reasoningSummary.isNotBlank()) {
                                    item {
                                        Column {
                                            TextButton(
                                                onClick = { summaryExpanded = !summaryExpanded },
                                                modifier = Modifier.testTag("live-summary-toggle"),
                                            ) {
                                                Text("✧  Thinking summary  " +
                                                    if (summaryExpanded) "⌃" else "⌄")
                                            }
                                            if (summaryExpanded) {
                                                Surface(
                                                    color = surfaceRaised, shape = messageShape,
                                                    modifier = Modifier.fillMaxWidth()
                                                        .testTag("live-summary"),
                                                ) {
                                                    Column(Modifier.padding(13.dp)) {
                                                        Text(
                                                            "Provider-provided summary · not raw CoT",
                                                            color = lowContrast,
                                                            fontSize = 11.sp,
                                                        )
                                                        Spacer(Modifier.height(8.dp))
                                                        if (snapshot.busy) {
                                                            Text(snapshot.reasoningSummary, color = ivory,
                                                                fontSize = 14.sp, lineHeight = 21.sp)
                                                        } else ChatMarkdown(snapshot.reasoningSummary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (snapshot.assistantText.isNotBlank()) {
                                    item {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text("✧  CiRYVA", color = accent,
                                                fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            Spacer(Modifier.height(8.dp))
                                            Box(Modifier.testTag("live-assistant-text")) {
                                                if (snapshot.busy) {
                                                    Text(snapshot.assistantText, color = ivory,
                                                        fontSize = 15.sp, lineHeight = 23.sp)
                                                } else ChatMarkdown(snapshot.assistantText)
                                            }
                                        }
                                    }
                                }
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            snapshot.status,
                                            color = lowContrast,
                                            fontSize = 12.sp,
                                            modifier = Modifier.testTag("live-status"),
                                        )
                                        snapshot.usage?.let { usage ->
                                            Text(
                                                "Provider tokens · in " + usage.inputTokens +
                                                    " · out " + usage.outputTokens +
                                                    " · total " + usage.totalTokens,
                                                color = secondary,
                                                fontSize = 11.sp,
                                                modifier = Modifier.testTag("live-usage"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Surface(color = surfaceRaised, shape = RoundedCornerShape(23.dp)) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                OutlinedTextField(
                                    value = prompt,
                                    onValueChange = { if (it.length <= 4_000) prompt = it },
                                    placeholder = { Text("Message CiRYVA…", color = lowContrast) },
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("live-composer"),
                                    minLines = 1, maxLines = 4,
                                    shape = RoundedCornerShape(18.dp),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f)
                                            .toggleable(
                                                value = chargeAcknowledged,
                                                role = Role.Checkbox,
                                                enabled = !snapshot.busy,
                                                onValueChange = { chargeAcknowledged = it },
                                            ).testTag("live-charge-consent"),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = chargeAcknowledged,
                                            onCheckedChange = null,
                                            enabled = !snapshot.busy,
                                            modifier = Modifier.size(40.dp),
                                        )
                                        Text(
                                            "Authorize one API request (may charge)",
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            color = secondary,
                                        )
                                    }
                                    if (snapshot.busy) {
                                        OutlinedButton(
                                            onClick = { onCancel() },
                                            modifier = Modifier.testTag("live-cancel"),
                                        ) { Text("Stop") }
                                    } else {
                                        Button(
                                            onClick = {
                                                try {
                                                    val request = makeRequest()
                                                    if (onSend(request)) {
                                                        chargeAcknowledged = false
                                                        error = null
                                                        summaryExpanded = false
                                                    } else error = "A request is already running."
                                                } catch (_: IllegalArgumentException) {
                                                    error = "Check the selected model and generation settings."
                                                }
                                            },
                                            enabled = hasKey && chargeAcknowledged &&
                                                prompt.isNotBlank(),
                                            modifier = Modifier.testTag("live-send"),
                                        ) { Text("Send") }
                                    }
                                }
                            }
                        }
                        error?.let {
                            Text(it, color = accent, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 5.dp)
                                    .testTag("live-validation"))
                        }
                    }
                } else if (tab == "settings") {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 17.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Request settings", fontSize = 23.sp,
                            fontFamily = FontFamily.Serif, color = ivory)
                        Text("Options are model-specific. This screen makes no API call.",
                            fontSize = 12.sp, color = lowContrast)
                        SettingRow("Model", model.label) { sheet = "model" }
                        if (model.reasoningEfforts.isNotEmpty()) {
                            SettingRow("Thinking effort", effort) { sheet = "effort" }
                        }
                        if (model.supportsVerbosity) {
                            SettingRow("Answer verbosity", verbosity) { sheet = "verbosity" }
                        }
                        if (samplingAllowed) {
                            SettingRow("Sampling", sampling) { sheet = "sampling" }
                            if (sampling == "temperature" || sampling == "top_p") {
                                OutlinedTextField(
                                    value = if (sampling == "temperature") temperature else topP,
                                    onValueChange = {
                                        if (it.length <= 8) {
                                            if (sampling == "temperature") temperature = it
                                            else topP = it
                                        }
                                    },
                                    label = { Text(if (sampling == "temperature")
                                        "Temperature · 0–2" else "Top-p · >0–1") },
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("live-sampling-value"),
                                )
                            }
                        }
                        OutlinedTextField(
                            value = outputLimit,
                            onValueChange = {
                                if (it.length <= 4 && it.all(Char::isDigit)) outputLimit = it
                            },
                            label = { Text("Maximum output tokens (1–4096)") },
                            modifier = Modifier.fillMaxWidth()
                                .testTag("live-output-limit"),
                            singleLine = true,
                        )
                        if (model.summaryModes.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .background(surfaceRaised, RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Reasoning summaries", color = ivory)
                                    Text("Request an optional provider summary, not raw CoT",
                                        color = lowContrast, fontSize = 12.sp)
                                }
                                Switch(checked = summaryEnabled,
                                    onCheckedChange = { summaryEnabled = it },
                                    modifier = Modifier.testTag("live-summary-switch"))
                            }
                        }
                        model.retirementNote?.let {
                            Text(it, color = accent, fontSize = 12.sp)
                        }
                        HorizontalDivider(color = accent.copy(alpha = 0.22f))
                        SettingRow("API key", if (hasKey) "Stored on this device" else "Not configured", onKeys)
                        Text("ChatGPT Plus does not cover OpenAI API charges. " +
                                "Keep your own project key restricted and spending limits configured.",
                            color = lowContrast, fontSize = 12.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(23.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        Text("Your workspace", fontFamily = FontFamily.Serif,
                            fontSize = 24.sp, color = ivory)
                        Text("The current foundation keeps local drafts in a separate screen. " +
                                "Conversation archive and multi-turn context are later milestones.",
                            color = secondary, fontSize = 14.sp)
                        Button(onClick = onBack) { Text("Open offline draft home") }
                        OutlinedButton(onClick = { tab = "chat" }) { Text("Return to chat") }
                    }
                }
                NavigationBar(containerColor = surfaceRaised,
                    modifier = Modifier.fillMaxWidth().testTag("live-navigation")) {
                    for ((id, symbol, label) in listOf(
                        Triple("home", "⌂", "Home"),
                        Triple("chat", "✧", "Chat"),
                        Triple("settings", "⚙", "Settings"),
                    )) {
                        NavigationBarItem(
                            selected = tab == id,
                            onClick = { tab = id },
                            icon = { Text(symbol, fontSize = 20.sp) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("live-tab-" + id),
                        )
                    }
                }
            }
        }
        sheet?.let { which ->
            val options: List<Pair<String, String>> = when (which) {
                "model" -> models.map { it.id to it.label }
                "effort" -> listOf(DEFAULT_VALUE to "Provider default") +
                    listOf("none", "low", "medium", "high", "xhigh", "max")
                        .filter { it in model.reasoningEfforts }.map { it to it }
                "verbosity" -> listOf(DEFAULT_VALUE to "Provider default") +
                    listOf("low", "medium", "high").map { it to it }
                "sampling" -> listOf(
                    DEFAULT_VALUE to "Provider default",
                    "temperature" to "Temperature",
                    "top_p" to "Top-p",
                )
                else -> emptyList()
            }
            ModalBottomSheet(onDismissRequest = { sheet = null },
                containerColor = surfaceHigh,
                modifier = Modifier.testTag("live-choice-sheet")) {
                Column(Modifier.fillMaxWidth().heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Text(
                        when (which) {
                            "model" -> "Choose model"
                            "effort" -> "Thinking effort"
                            "verbosity" -> "Answer verbosity"
                            else -> "Sampling"
                        },
                        fontSize = 22.sp, color = ivory,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    options.forEach { (value, label) ->
                        ListItem(
                            headlineContent = {
                                Text(label, color = ivory,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                val current = when (which) {
                                    "model" -> modelId
                                    "effort" -> effort
                                    "verbosity" -> verbosity
                                    else -> sampling
                                }
                                if (current == value) Text("✓", color = accent)
                            },
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    when (which) {
                                        "model" -> {
                                            modelId = value
                                            effort = DEFAULT_VALUE
                                            verbosity = DEFAULT_VALUE
                                            sampling = DEFAULT_VALUE
                                        }
                                        "effort" -> {
                                            effort = value
                                            if (model.samplingOnlyWhenNoReasoning &&
                                                effort != "none") sampling = DEFAULT_VALUE
                                        }
                                        "verbosity" -> verbosity = value
                                        "sampling" -> sampling = value
                                    }
                                    error = null
                                    sheet = null
                                }.testTag("live-option-" + value),
                            colors = androidx.compose.material3.ListItemDefaults.colors(
                                containerColor = surfaceHigh,
                            ),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, detail: String, onClick: () -> Unit) {
    Surface(color = surfaceRaised, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = ivory, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(detail, color = secondary, fontSize = 12.sp)
            }
            Text("›", fontSize = 27.sp, color = accent)
        }
    }
}
