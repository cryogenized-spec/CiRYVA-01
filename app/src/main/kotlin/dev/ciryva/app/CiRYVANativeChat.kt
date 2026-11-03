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
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.platform.LocalDensity
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
private val ink = CiRYVAVisual.ink
private val surfaceRaised = CiRYVAVisual.raised
private val surfaceHigh = Color(0xFF402436)
private val accent = CiRYVAVisual.rose
private val accentContainer = CiRYVAVisual.bubble
private val ivory = CiRYVAVisual.ivory
private val secondary = CiRYVAVisual.muted
private val lowContrast = Color(0xFFC7A7AF)
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
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
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
        CiRYVABackdrop {
            Column(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { tab = if (tab == "home") "chat" else "home" },
                        modifier = Modifier.testTag("live-back"),
                    ) { Text("☰", color = CiRYVAVisual.gold, fontSize = 23.sp) }
                    CiRYVAStar(Modifier.size(31.dp))
                    Spacer(Modifier.size(9.dp))
                    Text(
                        "CiRYVA",
                        fontSize = 29.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        color = ivory,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onKeys,
                        modifier = Modifier.testTag("live-key-settings"),
                    ) {
                        Text(if (hasKey) "KEY ✓" else "KEY +",
                            color = CiRYVAVisual.gold, fontSize = 11.sp)
                    }
                }
                CiRYVAOrnamentDivider(label = "✦")

                if (tab == "chat") {
                    if (!keyboardVisible) {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            CiRYVAControlTile(
                                title = "Model",
                                detail = model.label.substringBefore(" (legacy"),
                                icon = "◇",
                                enabled = true,
                                onClick = { sheet = "model" },
                                modifier = Modifier.weight(1.28f)
                                    .testTag("live-model-picker"),
                            )
                            CiRYVAControlTile(
                                title = "Effort",
                                detail = if (model.reasoningEfforts.isEmpty()) "N/A"
                                    else if (effort == DEFAULT_VALUE) "Default" else effort,
                                icon = "✧",
                                enabled = model.reasoningEfforts.isNotEmpty(),
                                onClick = { sheet = "effort" },
                                modifier = Modifier.weight(1.0f)
                                    .testTag("live-effort-picker"),
                            )
                            CiRYVAControlTile(
                                title = "More",
                                detail = "Options",
                                icon = "☷",
                                enabled = true,
                                onClick = { tab = "settings" },
                                modifier = Modifier.weight(0.91f)
                                    .testTag("live-more-settings"),
                            )
                        }
                        CiRYVAOrnamentDivider()
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (snapshot.userPrompt.isEmpty()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CiRYVAStar(Modifier.size(54.dp))
                                Text("What shall we explore?", color = ivory,
                                    fontFamily = FontFamily.Serif, fontSize = 23.sp)
                                Text(
                                    "One request at a time · messages stay on this screen only. " +
                                        "Saved conversations are not available yet.",
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
                                item { CiRYVAOrnamentDivider(label = "Today") }
                                item {
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End) {
                                        CiRYVAFramedPanel(
                                            color = accentContainer,
                                            corner = 21.dp,
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
                                        CiRYVAFramedPanel(
                                            modifier = Modifier.fillMaxWidth(),
                                            corner = 23.dp,
                                            color = surfaceRaised.copy(alpha = 0.94f),
                                        ) {
                                            Column(Modifier.padding(14.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    CiRYVAStar(Modifier.size(30.dp))
                                                    Spacer(Modifier.size(10.dp))
                                                    Text("CiRYVA", color = CiRYVAVisual.gold,
                                                        fontFamily = FontFamily.Serif,
                                                        fontSize = 19.sp, fontWeight = FontWeight.Medium)
                                                }
                                                Spacer(Modifier.height(11.dp))
                                                Box(Modifier.testTag("live-assistant-text")) {
                                                    if (snapshot.busy) {
                                                        Text(snapshot.assistantText, color = ivory,
                                                            fontSize = 15.sp, lineHeight = 23.sp)
                                                    } else ChatMarkdown(snapshot.assistantText)
                                                }
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
                                            CiRYVAFramedPanel(
                                                modifier = Modifier.fillMaxWidth()
                                                    .testTag("live-usage"),
                                                corner = 13.dp,
                                                color = CiRYVAVisual.wine.copy(alpha = 0.78f),
                                            ) {
                                                Text(
                                                    "ⓘ  Provider tokens · in: " + usage.inputTokens +
                                                        " · out: " + usage.outputTokens +
                                                        " · total: " + usage.totalTokens,
                                                    color = CiRYVAVisual.gold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp, vertical = 10.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        CiRYVAFramedPanel(
                            modifier = Modifier.fillMaxWidth(),
                            corner = 23.dp,
                            color = CiRYVAVisual.wine.copy(alpha = 0.92f),
                        ) {
                            Column(Modifier.padding(horizontal = 9.dp, vertical = 9.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    TextButton(
                                        onClick = onKeys,
                                        modifier = Modifier.testTag("live-open-key-setup"),
                                    ) {
                                        Text("⚙", color = CiRYVAVisual.gold,
                                            fontSize = 23.sp)
                                    }
                                    OutlinedTextField(
                                        value = prompt,
                                        onValueChange = {
                                            if (it.length <= 4_000) prompt = it
                                        },
                                        placeholder = {
                                            Text("Message CiRYVA…", color = lowContrast,
                                                fontSize = 13.sp)
                                        },
                                        modifier = Modifier.weight(1f)
                                            .testTag("live-composer"),
                                        minLines = 1,
                                        maxLines = if (keyboardVisible) 2 else 4,
                                        shape = RoundedCornerShape(19.dp),
                                    )
                                    if (snapshot.busy) {
                                        OutlinedButton(
                                            onClick = { onCancel() },
                                            modifier = Modifier.testTag("live-cancel"),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                                horizontal = 9.dp, vertical = 9.dp
                                            ),
                                        ) { Text("Stop", fontSize = 12.sp) }
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
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                                horizontal = 10.dp, vertical = 9.dp
                                            ),
                                        ) {
                                            Text("➤", fontSize = 20.sp,
                                                color = if (hasKey && chargeAcknowledged)
                                                    ink else lowContrast)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth()
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
                                        lineHeight = 15.sp,
                                        color = secondary,
                                    )
                                }
                            }
                        }
                        error?.let {
                            Text(
                                it,
                                color = accent,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 6.dp)
                                    .testTag("live-validation"),
                            )
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
                if (!keyboardVisible) NavigationBar(containerColor = CiRYVAVisual.wine,
                    modifier = Modifier.fillMaxWidth().testTag("live-navigation")) {
                    for ((id, symbol, label) in listOf(
                        Triple("home", "⌂", "Home"),
                        Triple("chat", "✦", "Chat"),
                        Triple("settings", "☷", "Settings"),
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
