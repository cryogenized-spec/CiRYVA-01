package dev.ciryva.app

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shape
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
import kotlinx.coroutines.launch
import java.util.UUID
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
    // Activity callback includes an unguessable local journal identity. Fake-only
    // injected UI tests can continue using onSend without any network/journal.
    onJournaledSend: ((TextResponseRequest, String) -> Boolean)? = null,
    journalCommittedId: String? = null,
    mediaEnabled: Boolean = false,
    selectedMedia: LocalMediaSelection? = null,
    mediaLoading: Boolean = false,
    mediaNotice: String? = null,
    onChooseMedia: () -> Unit = {},
    onRemoveMedia: () -> Unit = {},
    onOpenStoredMedia: (LocalStoredMedia) -> Unit = {},
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
    val wavesLabel = stringResource(R.string.decorative_background_waves)
    val attachLabel = stringResource(R.string.attach_local_document)
    val removeLabel = stringResource(R.string.remove_local_attachment)
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
    // Distinguishes a fresh user edit from identical text typed earlier.
    var draftRevision by rememberSaveable { mutableStateOf(0L) }
    var editorExpanded by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf("chat") }
    var drawerSearch by rememberSaveable { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<String?>(null) }
    var summaryExpanded by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Do not clear an accepted draft until the exact reservation has been
    // committed by SQLite. Keeping the original text in the composer also
    // protects it across rotation and Stop-before-journal cancellation.
    var pendingAcceptedDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDraftRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingSubmissionId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<LocalThread?>(null) }

    // An initially selected archive thread (null -> loaded ID), or a first
    // conversation created by Send, must not wipe a draft during recovery.
    // Actual user thread switches still clear the old thread's draft.
    var previousThreadId by rememberSaveable { mutableStateOf(currentThreadId) }
    LaunchedEffect(currentThreadId) {
        if (previousThreadId != currentThreadId) {
            if (previousThreadId != null) {
                prompt = ""
                pendingAcceptedDraft = null
                pendingDraftRevision = null
                pendingSubmissionId = null
                editorExpanded = false
            }
            summaryExpanded = false
            error = null
            previousThreadId = currentThreadId
        }
    }

    LaunchedEffect(journalCommittedId, pendingSubmissionId) {
        val identity = pendingSubmissionId ?: return@LaunchedEffect
        if (identity != journalCommittedId) return@LaunchedEffect
        // A committed request owns the original text. Never overwrite or
        // remove a newer edit that the user typed during the journal window.
        if (prompt == pendingAcceptedDraft && draftRevision == pendingDraftRevision) {
            prompt = ""
            editorExpanded = false
        }
        pendingAcceptedDraft = null
        pendingDraftRevision = null
        pendingSubmissionId = null
        error = null
    }

    LaunchedEffect(snapshot.status, snapshot.busy, archiveBusy, pendingSubmissionId) {
        if (pendingSubmissionId != null && !snapshot.busy && !archiveBusy &&
            (snapshot.status.startsWith("Cancelled before") ||
                snapshot.status.startsWith("Unable to save a local request") ||
                snapshot.status.startsWith("Request did not start"))) {
            // Failed/cancelled before the journal was acknowledged: the draft
            // never left the composer, so no restoration is needed.
            pendingAcceptedDraft = null
            pendingDraftRevision = null
            pendingSubmissionId = null
        }
    }

    // Global model and persona settings persist across Activity restarts.
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

    // Tapping Send is explicit authorization for one request, not an
    // automatic retry. An asynchronous archive journal must acknowledge this
    // exact reservation before the original draft can be cleared.
    fun submitCurrentPrompt() {
        if (snapshot.busy || !hasKey || prompt.isBlank() || archiveBusy ||
            archiveSaveRetryable || mediaLoading) return
        try {
            val request = makeRequest()
            val accepted = prompt
            val identity = UUID.randomUUID().toString()
            val journaled = onJournaledSend != null
            if ((onJournaledSend?.invoke(request, identity) ?: onSend(request))) {
                if (journaled) {
                    pendingSubmissionId = identity
                    pendingAcceptedDraft = accepted
                    pendingDraftRevision = draftRevision
                    // Leave prompt untouched until the matching SQLite ack.
                    // Send is already guarded by the Activity's reservation.
                } else {
                    // Fake/injected UI-only caller has no async journal.
                    prompt = ""
                    editorExpanded = false
                }
                error = null
                summaryExpanded = false
            } else error = "Unable to start or save this request."
        } catch (_: IllegalArgumentException) {
            error = "Check model, generation settings and persona instructions."
        }
    }

    // An inactive Send must explain what the user can do to unlock it.
    // Keep this in presentation only: it must never bypass BYOK or cost consent.
    val sendBlockedGuidance: String? = when {
        snapshot.busy -> null
        !hasKey -> "Open Settings → API key to enable Send."
        archiveSaveRetryable -> "Retry saving the previous response before sending."
        archiveBusy -> "Loading local conversations; Send will unlock when ready."
        mediaLoading -> "Importing the attachment; Send will unlock when ready."
        prompt.isBlank() -> null
        else -> null
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
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = surfaceHigh,
                    modifier = Modifier.width(320.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("CiRYVA · Conversations", color = ivory,
                                fontSize = 19.sp, modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { drawerScope.launch { drawerState.close() } },
                                modifier = Modifier.testTag("drawer-close"),
                            ) { Text("✕") }
                        }
                        OutlinedTextField(
                            value = drawerSearch,
                            onValueChange = { drawerSearch = it },
                            placeholder = { Text("Search conversations") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                                .testTag("drawer-search"),
                        )
                        TextButton(
                            onClick = {
                                if (!snapshot.busy && !archiveBusy && !archiveSaveRetryable) {
                                    onNewThread()
                                    tab = "chat"
                                    drawerScope.launch { drawerState.close() }
                                }
                            },
                            enabled = archiveEnabled && !snapshot.busy &&
                                !archiveBusy && !archiveSaveRetryable,
                            modifier = Modifier.fillMaxWidth().testTag("archive-new-thread"),
                        ) { Text("+ New conversation") }
                        HorizontalDivider(color = accent.copy(alpha = 0.22f))
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth()
                                .testTag("drawer-threads"),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (!archiveEnabled) {
                                item {
                                    Text("Local conversation history is not available.",
                                        color = secondary,
                                        modifier = Modifier.padding(10.dp))
                                }
                            } else {
                                items(
                                    archiveThreads.filter {
                                        it.title.contains(drawerSearch, ignoreCase = true)
                                    },
                                    key = { it.id },
                                ) { thread ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            onClick = {
                                                onOpenThread(thread.id)
                                                tab = "chat"
                                                drawerScope.launch { drawerState.close() }
                                            },
                                            enabled = !snapshot.busy && !archiveBusy &&
                                                !archiveSaveRetryable,
                                            modifier = Modifier.weight(1f)
                                                .testTag("archive-open-" + thread.id),
                                        ) {
                                            Text(thread.title,
                                                color = if (thread.id == currentThreadId)
                                                    CiRYVAVisual.gold else ivory,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        TextButton(
                                            onClick = { deleteCandidate = thread },
                                            enabled = !snapshot.busy && !archiveBusy &&
                                                !archiveSaveRetryable,
                                            modifier = Modifier.testTag("archive-delete-" + thread.id),
                                        ) { Text("⋮", color = accent, fontSize = 19.sp) }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = accent.copy(alpha = 0.22f))
                        TextButton(
                            onClick = {
                                tab = "settings"
                                drawerScope.launch { drawerState.close() }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("drawer-settings"),
                        ) { Text("⚙  Settings", color = ivory) }
                    }
                }
            },
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
                        onClick = { drawerScope.launch { drawerState.open() } },
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
                }
                CiRYVAOrnamentDivider(label = "✦")

                if (tab == "chat") {
                    if (!keyboardVisible) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TextButton(
                                onClick = { sheet = "model" },
                                modifier = Modifier.testTag("live-model-picker"),
                            ) { Text(model.label.substringBefore(" (legacy") + "  ⌄",
                                color = CiRYVAVisual.gold, maxLines = 1) }
                            if (model.reasoningEfforts.isNotEmpty()) {
                                TextButton(
                                    onClick = { sheet = "effort" },
                                    modifier = Modifier.testTag("live-effort-picker"),
                                ) { Text(if (effort == DEFAULT_VALUE) "Effort: default"
                                    else "Effort: " + effort, fontSize = 12.sp) }
                            }
                        }
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
                                                    saved.media?.let { media ->
                                                        Row(
                                                            Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text(
                                                                "📎 " + media.filename +
                                                                    " · " + (media.sizeBytes / 1024) + " KB",
                                                                color = CiRYVAVisual.gold,
                                                                fontSize = 12.sp,
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                            if (mediaEnabled) {
                                                                TextButton(
                                                                    onClick = { onOpenStoredMedia(media) },
                                                                    modifier = Modifier.testTag(
                                                                        "archive-open-media-" + media.id
                                                                    ),
                                                                ) { Text("Open", fontSize = 12.sp) }
                                                            }
                                                        }
                                                    }
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
                                if (mediaEnabled && (selectedMedia != null || mediaLoading)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(bottom = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                        selectedMedia?.thumbnail?.let { thumbnail ->
                                            Image(
                                                bitmap = thumbnail.asImageBitmap(),
                                                contentDescription = selectedMedia.media.filename,
                                                modifier = Modifier.size(42.dp),
                                            )
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                if (mediaLoading) "Importing local file…"
                                                else "📎 " + selectedMedia?.media?.filename.orEmpty(),
                                                color = ivory, fontSize = 12.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.testTag("p06-media-selection"),
                                            )
                                            if (!mediaLoading && selectedMedia != null) Text(
                                                (selectedMedia.media.sizeBytes / 1024).toString() +
                                                    " KB · sent only when you tap Send",
                                                color = lowContrast, fontSize = 10.sp,
                                            )
                                        }
                                        if (selectedMedia != null && !snapshot.busy) {
                                            TextButton(
                                                onClick = onRemoveMedia,
                                                modifier = Modifier.testTag("p06-media-remove")
                                                    .semantics { contentDescription = removeLabel },
                                            ) { Text("✕", color = ivory) }
                                        }
                                    }
                                }
                                // Keep the text field full-width during IME layout changes.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(
                                        onClick = { editorExpanded = true },
                                        modifier = Modifier.testTag("live-expand-editor")
                                            .semantics { contentDescription = "Expand message editor" },
                                    ) { Text("⛶  Expand", color = CiRYVAVisual.gold, fontSize = 12.sp) }
                                }
                                OutlinedTextField(
                                    value = prompt,
                                    onValueChange = { edited ->
                                        if (edited != prompt) {
                                            prompt = edited
                                            draftRevision++
                                        }
                                    },
                                    placeholder = { Text("Message…", color = lowContrast) },
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("live-composer"),
                                    minLines = 1,
                                    maxLines = 5,
                                    shape = RoundedCornerShape(19.dp),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (mediaEnabled) {
                                        TextButton(
                                            onClick = onChooseMedia,
                                            enabled = !snapshot.busy && !archiveBusy && !mediaLoading,
                                            modifier = Modifier.testTag("p06-media-picker")
                                                .semantics { contentDescription = attachLabel },
                                        ) { Text("📎", color = CiRYVAVisual.gold, fontSize = 20.sp) }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (snapshot.busy) {
                                        OutlinedButton(
                                            onClick = { onCancel() },
                                            modifier = Modifier.testTag("live-cancel"),
                                        ) { Text("Stop") }
                                    } else {
                                        Button(
                                            onClick = { submitCurrentPrompt() },
                                            enabled = hasKey && prompt.isNotBlank() &&
                                                !archiveBusy && !archiveSaveRetryable && !mediaLoading,
                                            modifier = Modifier.testTag("live-send")
                                                .semantics { contentDescription = sendMessageLabel },
                                        ) { Text("➤", fontSize = 20.sp, color = ink) }
                                    }
                                }
                                sendBlockedGuidance?.let { guidance ->
                                    Text(
                                        text = guidance,
                                        modifier = Modifier.padding(top = 3.dp)
                                            .testTag("live-send-guidance"),
                                        color = lowContrast,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                    )
                                }
                            }
                        }
                        mediaNotice?.let { notice ->
                            Text(
                                notice, color = accent, fontSize = 12.sp,
                                modifier = Modifier.testTag("p06-media-warning"),
                            )
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
                        TextButton(
                            onClick = { tab = "chat" },
                            modifier = Modifier.testTag("live-chat-back"),
                        ) { Text("‹  Back to conversation") }
                        Text("Request settings", fontSize = 23.sp,
                            fontFamily = FontFamily.Serif, color = ivory)
                        Text("Options are model-specific. This screen makes no API call.",
                            fontSize = 12.sp, color = lowContrast)
                        Text("Preferences are saved on this device. They only affect an API " +
                                "request when you tap Send.",
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
                                Text(wavesLabel, color = ivory)
                                Text(
                                    "Turn off subtle background ornaments for quieter reading",
                                    color = lowContrast, fontSize = 12.sp,
                                )
                            }
                            Switch(
                                checked = decorativeWaves,
                                onCheckedChange = { decorativeWaves = it },
                                modifier = Modifier.testTag("p06-decorative-waves")
                                    .semantics { contentDescription = wavesLabel },
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
                }
            }
        }
        }
        if (editorExpanded) {
            // Both editor modes share this exact draft and request path.
            Dialog(
                onDismissRequest = { editorExpanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(color = ink, modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .imePadding().padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Long message", color = ivory, fontSize = 20.sp,
                                modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { editorExpanded = false },
                                modifier = Modifier.testTag("essay-done"),
                            ) { Text("Done") }
                        }
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { edited ->
                                        if (edited != prompt) {
                                            prompt = edited
                                            draftRevision++
                                        }
                                    },
                            placeholder = { Text("Write your message…") },
                            modifier = Modifier.fillMaxWidth().weight(1f)
                                .testTag("essay-composer"),
                            shape = RoundedCornerShape(18.dp),
                        )
                        // The modal editor must surface its own errors: the normal
                        // chat error banner is hidden behind this fullscreen Dialog.
                        error?.let { visibleError ->
                            Text(
                                visibleError,
                                color = Color(0xFFFFB4AB),
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .testTag("essay-validation-error"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (snapshot.busy) {
                                OutlinedButton(onClick = { onCancel() }) { Text("Stop") }
                            } else {
                                Button(
                                    onClick = { submitCurrentPrompt() },
                                    enabled = hasKey && prompt.isNotBlank() &&
                                        !archiveBusy && !archiveSaveRetryable && !mediaLoading,
                                    modifier = Modifier.testTag("essay-send"),
                                ) { Text("Send") }
                            }
                        }
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
