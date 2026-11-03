package dev.ciryva.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    preferences: ChatPreferences = ChatPreferences(),
    onPreferencesChange: (ChatPreferences) -> Unit = {},
    localUsageTotals: LocalUsageTotals? = null,
    archiveEnabled: Boolean = false,
    archiveThreads: List<LocalThread> = emptyList(),
    archiveTurns: List<LocalTurn> = emptyList(),
    currentThreadId: String? = null,
    archiveNotice: String? = null,
    archiveBusy: Boolean = false,
    archiveSaveRetryable: Boolean = false,
    onRetryArchive: () -> Unit = {},
    onNewThread: () -> Unit = {},
    onOpenThread: (String) -> Unit = {},
    onDeleteThread: (String) -> Unit = {},
) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val sendMessageLabel = stringResource(R.string.send_message)
    val keySettingsLabel = stringResource(R.string.open_api_key_settings)
    val navigationLabel = stringResource(R.string.toggle_workspace_navigation)
    val models = CiRYVAModelCatalog.selectable
    var modelId by rememberSaveable { mutableStateOf(preferences.modelId) }
    val model: CiRYVAModel = models.firstOrNull { it.id == modelId } ?: models.first()
    var effort by rememberSaveable { mutableStateOf(preferences.effort) }
    var verbosity by rememberSaveable { mutableStateOf(preferences.verbosity) }
    var sampling by rememberSaveable { mutableStateOf(preferences.sampling) }
    var temperature by rememberSaveable { mutableStateOf(preferences.temperature) }
    var topP by rememberSaveable { mutableStateOf(preferences.topP) }
    var outputLimit by rememberSaveable { mutableStateOf(preferences.outputLimit) }
    var summaryEnabled by rememberSaveable { mutableStateOf(preferences.summaryEnabled) }
    var personaMode by rememberSaveable { mutableStateOf(preferences.personaMode) }
    var customInstructions by rememberSaveable {
        mutableStateOf(preferences.customInstructions)
    }
    var roleplayInstructions by rememberSaveable {
        mutableStateOf(preferences.roleplayInstructions)
    }
    var backgroundPreset by rememberSaveable {
        mutableStateOf(preferences.backgroundPreset)
    }
    var readingTypeface by rememberSaveable {
        mutableStateOf(preferences.readingTypeface)
    }
    var decorativeWaves by rememberSaveable {
        mutableStateOf(preferences.decorativeWaves)
    }
    val readingFont = if (readingTypeface == ChatPreferences.TYPEFACE_LITERARY)
        FontFamily.Serif else FontFamily.Default
    var prompt by rememberSaveable { mutableStateOf("") }
    // The charge permission is intentionally never restored after process death.
    var chargeAcknowledged by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf("chat") }
    var sheet by remember { mutableStateOf<String?>(null) }
    var summaryExpanded by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<LocalThread?>(null) }

    // Save the identity with Compose state so rotation of the SAME thread
    // preserves an unsent draft; switching threads invalidates its one-use consent.
    var previousThreadId by rememberSaveable { mutableStateOf(currentThreadId) }
    LaunchedEffect(currentThreadId) {
        if (previousThreadId != currentThreadId) {
            prompt = ""
            chargeAcknowledged = false
            summaryExpanded = false
            error = null
            previousThreadId = currentThreadId
        }
    }

    // Global model and persona settings persist across Activity restarts.
    // A billing acknowledgement is deliberately *not* included here.
    LaunchedEffect(
        modelId, effort, verbosity, sampling, temperature, topP, outputLimit,
        summaryEnabled, personaMode, customInstructions, roleplayInstructions,
        backgroundPreset, readingTypeface, decorativeWaves,
    ) {
        onPreferencesChange(
            ChatPreferences(
                modelId = modelId,
                effort = effort,
                verbosity = verbosity,
                sampling = sampling,
                temperature = temperature,
                topP = topP,
                outputLimit = outputLimit,
                summaryEnabled = summaryEnabled,
                personaMode = personaMode,
                customInstructions = customInstructions,
                roleplayInstructions = roleplayInstructions,
                backgroundPreset = backgroundPreset,
                readingTypeface = readingTypeface,
                decorativeWaves = decorativeWaves,
            )
        )
    }

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
        val persona = ChatPreferences(
            personaMode = personaMode,
            customInstructions = customInstructions,
            roleplayInstructions = roleplayInstructions,
        )
        if (personaMode == ChatPreferences.MODE_CUSTOM &&
            persona.requestInstructions() == null) {
            throw IllegalArgumentException("Custom instructions must not be empty")
        }
        return TextResponseRequest(
            model = modelId,
            prompt = prompt.trim(),
            maxOutputTokens = limit,
            settings = settings,
            instructions = persona.requestInstructions(),
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
        CiRYVABackdrop(
            preset = backgroundPreset,
            decorativeWaves = decorativeWaves,
        ) {
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
                        modifier = Modifier.testTag("live-back")
                            .semantics { contentDescription = navigationLabel },
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
                        modifier = Modifier.testTag("live-key-settings")
                            .semantics { contentDescription = keySettingsLabel },
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
                                detail = model.label.substringBefore(" (legacy").removePrefix("GPT-"),
                                enabled = true,
                                onClick = { sheet = "model" },
                                modifier = Modifier.weight(1.28f)
                                    .testTag("live-model-picker"),
                            )
                            CiRYVAControlTile(
                                title = "Effort",
                                detail = if (model.reasoningEfforts.isEmpty()) "N/A"
                                    else if (effort == DEFAULT_VALUE) "Default" else effort,
                                enabled = model.reasoningEfforts.isNotEmpty(),
                                onClick = { sheet = "effort" },
                                modifier = Modifier.weight(1.0f)
                                    .testTag("live-effort-picker"),
                            )
                            CiRYVAControlTile(
                                title = "More",
                                detail = "Options",
                                enabled = true,
                                onClick = { tab = "settings" },
                                modifier = Modifier.weight(0.91f)
                                    .testTag("live-more-settings"),
                            )
                        }
                        CiRYVAOrnamentDivider()
                    }

                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val compactTranscript = maxHeight < 245.dp
                        if (snapshot.userPrompt.isEmpty() && archiveTurns.isEmpty()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center)
                                    .padding(horizontal = 22.dp, vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(
                                    if (compactTranscript) 3.dp else 12.dp
                                ),
                            ) {
                                if (!compactTranscript) {
                                    CiRYVAStar(Modifier.size(48.dp))
                                }
                                Text(
                                    "What shall we explore?",
                                    color = ivory,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = if (compactTranscript) 16.sp else 23.sp,
                                    lineHeight = if (compactTranscript) 22.sp else 29.sp,
                                    maxLines = 2,
                                )
                                if (!compactTranscript) {
                                    Text(
                                        if (archiveEnabled)
                                            "Conversations are stored on this device. " +
                                                "Your next request may include recent completed turns."
                                        else "One independent request at a time. " +
                                            "Saved conversations are not available in this preview.",
                                        color = lowContrast,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().testTag("live-transcript"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 16.dp, vertical = 14.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                if (archiveTurns.isNotEmpty()) {
                                    item {
                                        Text(
                                            "Saved locally · last " + archiveTurns.size +
                                                " turn(s)",
                                            color = secondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.testTag("archive-local-indicator"),
                                        )
                                    }
                                    items(archiveTurns, key = { it.id }) { saved ->
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                                .testTag("archive-turn-" + saved.id),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                CiRYVAFramedPanel(
                                                    color = accentContainer,
                                                    corner = 19.dp,
                                                    modifier = Modifier.fillMaxWidth(0.92f),
                                                ) {
                                                    Text(
                                                        saved.userText,
                                                        fontFamily = readingFont,
                                                        modifier = Modifier.padding(13.dp),
                                                        color = ivory,
                                                        fontSize = 15.sp,
                                                        lineHeight = 22.sp,
                                                    )
                                                }
                                            }
                                            if (saved.assistantText.isNotBlank() ||
                                                saved.reasoningSummary.isNotBlank()) {
                                                CiRYVAFramedPanel(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    corner = 20.dp,
                                                    color = surfaceRaised,
                                                ) {
                                                    Column(Modifier.padding(13.dp)) {
                                                        Text(
                                                            "✦ CiRYVA · " + saved.modelId,
                                                            color = CiRYVAVisual.gold,
                                                            fontSize = 12.sp,
                                                        )
                                                        if (saved.assistantText.isNotBlank()) {
                                                            Spacer(Modifier.height(7.dp))
                                                            ChatMarkdown(saved.assistantText, readingFont)
                                                        }
                                                        if (saved.reasoningSummary.isNotBlank()) {
                                                            Spacer(Modifier.height(9.dp))
                                                            Text(
                                                                "Provider reasoning summary",
                                                                color = secondary,
                                                                fontSize = 11.sp,
                                                            )
                                                            ChatMarkdown(saved.reasoningSummary, readingFont)
                                                        }
                                                    }
                                                }
                                            }
                                            Text(
                                                "Local record · " + saved.state +
                                                    (saved.totalTokens?.let {
                                                        " · provider tokens: " + it
                                                    } ?: ""),
                                                color = lowContrast,
                                                fontSize = 11.sp,
                                            )
                                            if (saved.statusDetail.isNotBlank()) {
                                                Text(
                                                    saved.statusDetail,
                                                    color = secondary,
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                                if (snapshot.userPrompt.isNotBlank()) {
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
                                                fontFamily = readingFont,
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
                                                        } else ChatMarkdown(snapshot.reasoningSummary, readingFont)
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
                                                            fontFamily = readingFont,
                                                            fontSize = 15.sp, lineHeight = 23.sp)
                                                    } else ChatMarkdown(snapshot.assistantText, readingFont)
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
                                        modifier = Modifier.testTag("live-open-key-setup")
                                            .semantics { contentDescription = keySettingsLabel },
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
                                            Text("Message…", color = lowContrast,
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
                                                    } else error = "Unable to start or save this request."
                                                } catch (_: IllegalArgumentException) {
                                                    error = "Check model, generation settings and persona instructions."
                                                }
                                            },
                                            enabled = hasKey && chargeAcknowledged &&
                                                prompt.isNotBlank() && !archiveBusy &&
                                                !archiveSaveRetryable,
                                            modifier = Modifier.testTag("live-send")
                                                .semantics { contentDescription = sendMessageLabel },
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
                        archiveNotice?.let {
                            Text(
                                it, color = accent, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 5.dp)
                                    .testTag("archive-storage-warning"),
                            )
                        }
                        if (archiveSaveRetryable) {
                            OutlinedButton(
                                onClick = onRetryArchive,
                                enabled = !archiveBusy,
                                modifier = Modifier.testTag("archive-retry-save"),
                            ) { Text("Retry saving response") }
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
                        Text("Preferences are saved on this device. They only affect an API " +
                                "request when you explicitly authorize Send.",
                            fontSize = 12.sp, color = lowContrast)
                        HorizontalDivider(color = accent.copy(alpha = 0.22f))
                        Text("Appearance", fontSize = 20.sp,
                            fontFamily = FontFamily.Serif, color = ivory)
                        SettingRow(
                            "Backdrop",
                            when (backgroundPreset) {
                                ChatPreferences.BACKGROUND_MIDNIGHT -> "Midnight plum"
                                ChatPreferences.BACKGROUND_GRAPHITE -> "Graphite ink"
                                else -> "Classic burgundy"
                            },
                        ) { sheet = "backdrop" }
                        SettingRow(
                            "Reading typeface",
                            if (readingTypeface == ChatPreferences.TYPEFACE_LITERARY)
                                "Literary serif" else "Modern sans-serif",
                        ) { sheet = "typeface" }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .background(surfaceRaised, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Decorative background waves", color = ivory)
                                Text(
                                    "Turn off subtle background ornaments for quieter reading",
                                    color = lowContrast, fontSize = 12.sp,
                                )
                            }
                            Switch(
                                checked = decorativeWaves,
                                onCheckedChange = { decorativeWaves = it },
                                modifier = Modifier.testTag("p06-decorative-waves"),
                            )
                        }
                        Text(
                            "Appearance is local to this device; changing it does not " +
                                "send a request or modify conversation records.",
                            color = lowContrast, fontSize = 12.sp,
                        )
                        HorizontalDivider(color = accent.copy(alpha = 0.22f))
                        Text("Assistant persona", fontSize = 20.sp,
                            fontFamily = FontFamily.Serif, color = ivory)
                        SettingRow(
                            "Conversation mode",
                            when (personaMode) {
                                ChatPreferences.MODE_CUSTOM -> "Custom instructions"
                                ChatPreferences.MODE_ROLEPLAY -> "Fictional roleplay"
                                else -> "Standard assistant"
                            }
                        ) { sheet = "persona" }
                        if (personaMode == ChatPreferences.MODE_CUSTOM ||
                            personaMode == ChatPreferences.MODE_ROLEPLAY) {
                            val roleplay = personaMode == ChatPreferences.MODE_ROLEPLAY
                            OutlinedTextField(
                                value = if (roleplay) roleplayInstructions
                                    else customInstructions,
                                onValueChange = { text ->
                                    if (text.length <= ChatPreferences.MAX_PERSONA_CHARS) {
                                        if (roleplay) roleplayInstructions = text
                                        else customInstructions = text
                                    }
                                },
                                label = { Text(if (roleplay)
                                    "Optional roleplay scenario / character instructions"
                                    else "Your custom assistant instructions") },
                                supportingText = {
                                    Text(
                                        (if (roleplay) roleplayInstructions.length
                                            else customInstructions.length).toString() +
                                            " / " + ChatPreferences.MAX_PERSONA_CHARS +
                                            " · saved locally; included only with Send",
                                        color = lowContrast,
                                    )
                                },
                                placeholder = { Text(if (roleplay)
                                    "e.g. Narrate as a fictional companion"
                                    else "e.g. Prefer brief, technical explanations") },
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("p06-persona-instructions"),
                                minLines = 2,
                                maxLines = 5,
                            )
                        }
                        Text(
                            "These preferences are stored as app-private text (not encrypted). " +
                                "Do not put passwords or API keys here. " +
                                "Enabled persona instructions add input tokens and may incur costs.",
                            color = lowContrast, fontSize = 12.sp, lineHeight = 17.sp,
                        )
                        HorizontalDivider(color = accent.copy(alpha = 0.22f))
                        Text("Generation", fontSize = 20.sp,
                            fontFamily = FontFamily.Serif, color = ivory)
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
                        if (archiveEnabled) {
                            Text("Recorded API usage", fontSize = 20.sp,
                                fontFamily = FontFamily.Serif, color = ivory)
                            Text(
                                if (localUsageTotals == null)
                                    "Loading local provider receipts…"
                                else "Provider-reported usage across locally retained " +
                                    "completed and incomplete responses.",
                                color = lowContrast, fontSize = 12.sp, lineHeight = 17.sp,
                            )
                            localUsageTotals?.let { total ->
                                CiRYVAFramedPanel(
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("p06-local-usage-totals"),
                                    color = surfaceRaised, corner = 16.dp,
                                ) {
                                    Column(Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(
                                            total.recordedTurns.toString() +
                                                " responses with provider receipts",
                                            color = CiRYVAVisual.gold, fontSize = 14.sp,
                                        )
                                        Text(
                                            "Input: " + total.inputTokens +
                                                " · Output: " + total.outputTokens +
                                                " · Total: " + total.totalTokens + " tokens",
                                            color = ivory, fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                            Text(
                                "Not a bill or live estimate. Missing receipts and deleted " +
                                    "conversations are excluded; prices vary by model.",
                                color = lowContrast, fontSize = 12.sp, lineHeight = 17.sp,
                            )
                            HorizontalDivider(color = accent.copy(alpha = 0.22f))
                        }
                        SettingRow("API key", if (hasKey) "Stored on this device" else "Not configured", onKeys)
                        Text("ChatGPT Plus does not cover OpenAI API charges. " +
                                "Keep your own project key restricted and spending limits configured.",
                            color = lowContrast, fontSize = 12.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Text("Your conversations", fontFamily = FontFamily.Serif,
                            fontSize = 24.sp, color = ivory)
                        if (archiveEnabled) {
                            Text(
                                "Stored on this device · completed replies provide " +
                                    "bounded context on the next request.",
                                color = secondary, fontSize = 12.sp, lineHeight = 18.sp,
                            )
                            Button(
                                enabled = !snapshot.busy && !archiveBusy &&
                                    !archiveSaveRetryable,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("archive-new-thread"),
                                onClick = {
                                    onNewThread()
                                    tab = "chat"
                                },
                            ) { Text("+ New conversation") }
                            if (archiveThreads.isEmpty()) {
                                Text("No saved conversations yet.", color = lowContrast)
                            }
                            archiveThreads.forEach { thread ->
                                CiRYVAFramedPanel(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (thread.id == currentThreadId)
                                        CiRYVAVisual.wine else surfaceRaised,
                                    corner = 16.dp,
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            enabled = !snapshot.busy && !archiveBusy &&
                                                !archiveSaveRetryable,
                                            modifier = Modifier.weight(1f)
                                                .testTag("archive-open-" + thread.id),
                                            onClick = {
                                                onOpenThread(thread.id)
                                                tab = "chat"
                                            },
                                        ) {
                                            Text(
                                                thread.title,
                                                color = ivory,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        TextButton(
                                            enabled = !snapshot.busy && !archiveBusy &&
                                                !archiveSaveRetryable,
                                            modifier = Modifier.testTag(
                                                "archive-delete-" + thread.id),
                                            onClick = { deleteCandidate = thread },
                                        ) {
                                            Text("Delete", color = accent, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            Text(
                                "Showing up to 100 recent conversations. " +
                                    "Older records remain in local storage.",
                                color = lowContrast, fontSize = 11.sp,
                            )
                        } else {
                            Text(
                                "Open the offline draft editor or return to chat. " +
                                    "Saved conversation history is not available in this preview.",
                                color = secondary, fontSize = 13.sp,
                            )
                        }
                        OutlinedButton(onClick = onBack,
                            modifier = Modifier.fillMaxWidth().testTag("archive-open-drafts")) {
                            Text("Open offline drafts")
                        }
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
        deleteCandidate?.let { candidate ->
            AlertDialog(
                onDismissRequest = { deleteCandidate = null },
                title = { Text("Delete conversation?") },
                text = {
                    Text("This permanently deletes the local conversation and its " +
                        "messages. It cannot be undone.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteThread(candidate.id)
                            deleteCandidate = null
                        },
                        modifier = Modifier.testTag("archive-confirm-delete"),
                    ) { Text("Delete permanently") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteCandidate = null }) {
                        Text("Keep conversation")
                    }
                },
                containerColor = surfaceHigh,
            )
        }
        sheet?.let { which ->
            val options: List<Pair<String, String>> = when (which) {
                "model" -> models.map { it.id to it.label }
                "persona" -> listOf(
                    ChatPreferences.MODE_STANDARD to "Standard assistant",
                    ChatPreferences.MODE_CUSTOM to "Custom instructions",
                    ChatPreferences.MODE_ROLEPLAY to "Fictional roleplay",
                )
                "backdrop" -> listOf(
                    ChatPreferences.BACKGROUND_BURGUNDY to "Classic burgundy",
                    ChatPreferences.BACKGROUND_MIDNIGHT to "Midnight plum",
                    ChatPreferences.BACKGROUND_GRAPHITE to "Graphite ink",
                )
                "typeface" -> listOf(
                    ChatPreferences.TYPEFACE_MODERN to "Modern sans-serif",
                    ChatPreferences.TYPEFACE_LITERARY to "Literary serif",
                )
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
                            "persona" -> "Assistant persona"
                            "backdrop" -> "Background palette"
                            "typeface" -> "Reading typeface"
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
                                    "persona" -> personaMode
                                    "backdrop" -> backgroundPreset
                                    "typeface" -> readingTypeface
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
                                        "persona" -> personaMode = value
                                        "backdrop" -> backgroundPreset = value
                                        "typeface" -> readingTypeface = value
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
        modifier = Modifier.fillMaxWidth()
            .testTag(if (title == "Conversation mode") "p06-persona-selector"
                else "setting-row-" + title.lowercase().replace(" ", "-"))
            .clickable(onClick = onClick)) {
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
