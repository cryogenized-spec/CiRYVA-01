package dev.ciryva.app

import android.view.WindowInsets
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertTextContains
import androidx.test.platform.app.InstrumentationRegistry
import dev.ciryva.core.stream.TextResponseRequest
import java.io.FileInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Fake-only Compose UI tests run in the real, non-exported target app Activity.
 * Overriding its ComposeView replaces the screen with injectable no-network
 * callbacks; tests never need stored credentials or live API access.
 */
class LiveChatComposeTest {
    @get:Rule val compose = createAndroidComposeRule<LiveChatActivity>()

    @Test fun localArchiveShowsProviderSummaryEvenWithoutAssistantText() {
        val turn = LocalTurn(
            id = "turn-summary-only",
            threadId = "test-thread",
            userText = "Why was the answer interrupted?",
            assistantText = "",
            reasoningSummary = "Provider supplied a summary before stopping.",
            modelId = "test-model",
            state = "incomplete",
            createdAtMs = 1234,
            inputTokens = 10,
            outputTokens = 3,
            totalTokens = 13,
            statusDetail = "Incomplete response; only provider-reported usage is shown.",
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = false,
                    onBack = {}, onKeys = {}, onSend = { false }, onCancel = { false },
                    archiveEnabled = true,
                    archiveTurns = listOf(turn),
                    currentThreadId = turn.threadId,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-transcript").performScrollToIndex(1)
        compose.onNodeWithText("Provider reasoning summary").assertExists()
        compose.onNodeWithText("Provider supplied a summary before stopping.").assertExists()
    }

    @Test fun archiveHomeOpensRealThreadsAndRequiresConfirmationBeforeDeleting() {
        val thread = LocalThread(
            id = "sample-thread-id", title = "Workshop planning",
            createdAtMs = 10, updatedAtMs = 20,
        )
        var opened: String? = null
        var deleted: String? = null
        var created = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = false,
                    onBack = {},
                    onKeys = {},
                    onSend = { false },
                    onCancel = { false },
                    archiveEnabled = true,
                    archiveThreads = listOf(thread),
                    archiveTurns = emptyList(),
                    currentThreadId = thread.id,
                    onNewThread = { created++ },
                    onOpenThread = { opened = it },
                    onDeleteThread = { deleted = it },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-tab-home").performClick()
        compose.onNodeWithTag("archive-open-" + thread.id).assertExists().performClick()
        compose.runOnIdle { assertEquals(thread.id, opened) }
        compose.onNodeWithTag("live-tab-home").performClick()
        compose.onNodeWithTag("archive-delete-" + thread.id).performClick()
        compose.runOnIdle { assertEquals(null, deleted) }
        compose.onNodeWithText("Keep conversation").performClick()
        compose.runOnIdle { assertEquals(null, deleted) }
        compose.onNodeWithTag("archive-delete-" + thread.id).performClick()
        compose.onNodeWithTag("archive-confirm-delete").performClick()
        compose.runOnIdle { assertEquals(thread.id, deleted) }
        compose.onNodeWithTag("archive-new-thread").performClick()
        compose.runOnIdle { assertEquals(1, created) }
    }

    @Test fun missingImportedOriginalFailsPreflightAndKeepsTheUnsentSelection() {
        val fake = LocalStoredMedia(
            UUID.randomUUID().toString(), "missing.pdf", "application/pdf", 123,
        )
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("p06-media-picker").assertIsEnabled()
            }.isSuccess
        }
        compose.runOnIdle {
            PendingMediaImport.selected.value = LocalMediaSelection(fake)
            val call = LiveChatActivity::class.java.getDeclaredMethod(
                "sendWithArchive", TextResponseRequest::class.java
            ).apply { isAccessible = true }
            assertEquals(true, call.invoke(
                compose.activity, TextResponseRequest("gpt-5.4-mini-2026-03-17",
                    "This PDF is unavailable")
            ))
        }
        compose.waitUntil(10_000) {
            PendingMediaImport.selected.value?.media?.id == fake.id &&
                runCatching {
                    compose.onNodeWithTag("p06-media-picker").assertIsEnabled()
                }.isSuccess
        }
        compose.onNodeWithTag("p06-media-selection")
            .assertTextContains("missing.pdf", substring = true)
        compose.onNodeWithTag("p06-media-picker").assertIsEnabled()
        compose.runOnIdle { PendingMediaImport.selected.value = null }
    }

    @Test fun cancellingBeforeArchiveReservationRestoresDraftMedia() {
        val fake = LocalStoredMedia(
            UUID.randomUUID().toString(), "unsent.png", "image/png", 444,
        )
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("p06-media-picker").assertIsEnabled()
            }.isSuccess
        }
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val workerField = LiveChatActivity::class.java.getDeclaredField("disk")
            .apply { isAccessible = true }
        val disk = workerField.get(compose.activity)
            as java.util.concurrent.ExecutorService
        try {
            disk.execute {
                blocked.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            assertEquals(true, blocked.await(3, TimeUnit.SECONDS))
            compose.runOnIdle {
                PendingMediaImport.selected.value = LocalMediaSelection(fake)
                val send = LiveChatActivity::class.java.getDeclaredMethod(
                    "sendWithArchive", TextResponseRequest::class.java
                ).apply { isAccessible = true }
                val cancel = LiveChatActivity::class.java.getDeclaredMethod(
                    "cancelCurrentRequest"
                ).apply { isAccessible = true }
                assertEquals(true, send.invoke(
                    compose.activity, TextResponseRequest(
                        "gpt-5.4-mini-2026-03-17", "Don't send this file yet"
                    )
                ))
                assertEquals(true, cancel.invoke(compose.activity))
                // The disk worker is still blocked; it has not made an
                // authoritative archive-or-draft ownership decision yet.
                assertEquals(null, PendingMediaImport.selected.value)
            }
            release.countDown()
            // The queued journal's finally block restores an uncommitted
            // file on the main thread. Do not assert restoration before it
            // has actually completed, or clear the fixture prematurely.
            compose.waitUntil(10_000) {
                !PendingMediaImport.journalPending.value &&
                    PendingMediaImport.selected.value?.media?.id == fake.id
            }
            compose.onNodeWithTag("p06-media-selection")
                .assertTextContains("unsent.png", substring = true)
        } finally {
            release.countDown()
            compose.runOnIdle { PendingMediaImport.selected.value = null }
        }
    }

    @Test fun delayedPrivateMediaImportFinishesAfterActivityRecreation() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.filesDir, "ciryva_media")
        directory.mkdirs()
        val id = UUID.randomUUID().toString()
        val file = File(directory, id)
        file.writeBytes("%PDF-1.4\\nTest material".toByteArray())
        val selection = LocalMediaSelection(
            LocalStoredMedia(id, "rotation.pdf", "application/pdf", file.length().toInt())
        )
        val started = CountDownLatch(1)
        val allowFinish = CountDownLatch(1)
        try {
            compose.runOnIdle {
                PendingMediaImport.importWith(app) {
                    started.countDown()
                    check(allowFinish.await(8, TimeUnit.SECONDS))
                    selection
                }
            }
            assertEquals(true, started.await(5, TimeUnit.SECONDS))
            // The Activity and UI are destroyed/recreated while the provider
            // read is blocked. Its result must be owned by the app process.
            compose.activityRule.scenario.recreate()
            allowFinish.countDown()
            compose.waitUntil(12_000) {
                PendingMediaImport.selected.value?.media?.id == id &&
                    !PendingMediaImport.loading.value
            }
            compose.onNodeWithTag("p06-media-selection")
                .assertTextContains("rotation.pdf", substring = true)
            assertEquals(true, file.exists())
        } finally {
            allowFinish.countDown()
            PendingMediaImport.selected.value = null
            file.delete()
        }
    }

    @Test fun sameProcessRecreationDoesNotResurrectAlreadyTransferredDraftMedia() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as android.app.Application
        val oldActivity = compose.activity
        val phantom = LocalMediaSelection(LocalStoredMedia(
            UUID.randomUUID().toString(), "already-committed.pdf", "application/pdf", 128,
        ))
        val destroyed = java.util.concurrent.atomic.AtomicBoolean(false)
        val lifecycle = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                if (activity === oldActivity) {
                    // Bundle already saved the old draft, but a journal worker
                    // has transferred the file before the replacement onCreate.
                    PendingMediaImport.selected.value = null
                    destroyed.set(true)
                }
            }
        }
        try {
            compose.runOnIdle { PendingMediaImport.selected.value = phantom }
            app.registerActivityLifecycleCallbacks(lifecycle)
            compose.activityRule.scenario.recreate()
            assertEquals(true, destroyed.get())
            compose.waitForIdle()
            assertEquals(null, PendingMediaImport.selected.value)
            compose.onNodeWithTag("p06-media-selection").assertDoesNotExist()
        } finally {
            app.unregisterActivityLifecycleCallbacks(lifecycle)
            compose.runOnIdle { PendingMediaImport.selected.value = null }
        }
    }

    @Test fun realMediaPickerControlDisplaysSelectedLocalFileAndRemovesIt() {
        val file = LocalStoredMedia(
            "11111111-2222-4333-8444-555555555555", "inspection.png",
            "image/png", 512,
        )
        var choices = 0
        var removed = 0
        val current = androidx.compose.runtime.mutableStateOf<LocalMediaSelection?>(null)
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = false,
                    onBack = {}, onKeys = {}, onSend = { false }, onCancel = { false },
                    mediaEnabled = true,
                    selectedMedia = current.value,
                    onChooseMedia = {
                        choices++
                        current.value = LocalMediaSelection(file)
                    },
                    onRemoveMedia = {
                        removed++
                        current.value = null
                    },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("p06-media-picker")
            .assertContentDescriptionEquals(
                "Attach a JPEG, PNG or PDF from this device"
            ).performClick()
        compose.onNodeWithTag("p06-media-selection").assertTextContains("inspection.png", substring = true)
        compose.onNodeWithTag("p06-media-remove")
            .assertContentDescriptionEquals("Remove selected attachment").performClick()
        compose.onNodeWithTag("p06-media-selection").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, choices)
            assertEquals(1, removed)
        }
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
    }

    @Test fun archivedOriginalHasARealViewerCallbackNotJustAFileName() {
        val media = LocalStoredMedia(
            "11111111-2222-4333-8444-555555555555", "record.pdf",
            "application/pdf", 800,
        )
        var opened: LocalStoredMedia? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = false,
                    onBack = {}, onKeys = {}, onSend = { false }, onCancel = { false },
                    mediaEnabled = true,
                    archiveEnabled = true,
                    archiveTurns = listOf(LocalTurn(
                        id = "archived-media", threadId = "test-thread",
                        userText = "Look at this", assistantText = "Reviewed",
                        reasoningSummary = "", modelId = "test",
                        state = "completed", createdAtMs = 1,
                        inputTokens = null, outputTokens = null, totalTokens = null,
                        media = media,
                    )),
                    currentThreadId = "test-thread",
                    onOpenStoredMedia = { opened = it },
                )
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            compose.activity.currentFocus?.clearFocus()
            compose.activity.window.insetsController?.hide(WindowInsets.Type.ime())
        }
        compose.onNodeWithTag("live-transcript").performScrollToIndex(1)
        compose.onNodeWithTag("archive-open-media-" + media.id)
            .performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            var current: LocalStoredMedia? = null
            compose.runOnIdle { current = opened }
            current == media
        }
        compose.runOnIdle { assertEquals(media, opened) }
    }

    @Test fun backdropReadingTypefaceAndQuietModeAreRealAndLocallyPersistable() {
        var saved: ChatPreferences? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = false,
                    onBack = {}, onKeys = {},
                    onSend = { false },
                    onCancel = { false },
                    preferences = ChatPreferences(),
                    onPreferencesChange = { saved = it },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-tab-settings").performClick()
        compose.onNodeWithTag("setting-row-backdrop").performScrollTo().performClick()
        compose.onNodeWithTag("live-option-graphite").performClick()
        compose.onNodeWithTag("setting-row-reading-typeface").performScrollTo().performClick()
        compose.onNodeWithTag("live-option-literary").performClick()
        compose.onNodeWithTag("p06-decorative-waves")
            .performScrollTo().assertContentDescriptionEquals("Decorative background waves")
            .performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(ChatPreferences.BACKGROUND_GRAPHITE, saved?.backgroundPreset)
            assertEquals(ChatPreferences.TYPEFACE_LITERARY, saved?.readingTypeface)
            assertEquals(false, saved?.decorativeWaves)
        }
        compose.onNodeWithTag("live-tab-chat").performClick()
        compose.onNodeWithTag("live-composer").assertExists()
        // Appearance changes are local; no stored API key is present.
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
    }

    @Test fun editedPersonaIsSavedAndIncludedOnlyWithAuthorizedSend() {
        val requests = mutableListOf<TextResponseRequest>()
        var stored: ChatPreferences? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = true,
                    onBack = {}, onKeys = {},
                    onSend = { requests += it; true },
                    onCancel = { false },
                    preferences = ChatPreferences(),
                    onPreferencesChange = { stored = it },
                    archiveEnabled = true,
                    localUsageTotals = LocalUsageTotals(2, 14, 7, 21),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-tab-settings").performClick()
        compose.onNodeWithTag("p06-persona-selector").performScrollTo().performClick()
        compose.onNodeWithTag("live-option-custom").performClick()
        compose.onNodeWithTag("p06-persona-instructions")
            .performScrollTo().performTextInput("Use concise technical language.")
        compose.onNodeWithTag("p06-local-usage-totals").performScrollTo().assertExists()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(ChatPreferences.MODE_CUSTOM, stored?.personaMode)
            assertEquals("Use concise technical language.",
                stored?.requestInstructions())
        }
        // Native chat deliberately hides bottom navigation while the IME is open.
        // Dismiss the keyboard before exercising the navigation control.
        compose.runOnUiThread {
            compose.activity.currentFocus?.clearFocus()
            compose.activity.window.insetsController?.hide(WindowInsets.Type.ime())
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("live-tab-chat").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("live-tab-chat").performClick()
        compose.onNodeWithTag("live-composer").performTextInput("Explain a model")
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
        compose.onNodeWithTag("live-charge-consent").performClick()
        compose.onNodeWithTag("live-send").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, requests.size)
            assertEquals("Use concise technical language.",
                requests.single().instructions)
        }
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
    }

    @Test fun roleplayModeAddsScopedInstructionsButStandardModeDoesNot() {
        val messages = mutableListOf<TextResponseRequest>()
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(), hasKey = true,
                    onBack = {}, onKeys = {}, onSend = { messages += it; true },
                    onCancel = { false },
                    preferences = ChatPreferences(
                        personaMode = ChatPreferences.MODE_ROLEPLAY,
                        roleplayInstructions = "Narrate as a fictional archivist.",
                    )
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-composer").performTextInput("Begin the scene")
        compose.onNodeWithTag("live-charge-consent").performClick()
        compose.onNodeWithTag("live-send").performClick()
        compose.runOnIdle {
            assertEquals(1, messages.size)
            assertEquals(true, messages.single().instructions
                ?.contains("Narrate as a fictional archivist."))
            assertEquals(true, messages.single().instructions
                ?.contains("fictional roleplay"))
        }
    }

    @Test fun premiumChromeOpensRealModelAndSettingsNavigation() {
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = false,
                    onBack = {},
                    onKeys = {},
                    onSend = { false },
                    onCancel = { false },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-back")
            .assertContentDescriptionEquals("Toggle workspace navigation")
        compose.onNodeWithTag("live-key-settings")
            .assertContentDescriptionEquals("Open API key settings")
        compose.onNodeWithTag("live-model-picker").assertIsDisplayed().performClick()
        compose.onNodeWithTag("live-option-o3-2025-04-16")
            .performScrollTo().performClick()
        compose.onNodeWithTag("live-model-picker")
            .assertTextContains("o3", substring = true)
        compose.onNodeWithTag("live-more-settings").performClick()
        compose.onNodeWithTag("live-output-limit").assertExists()
        compose.onNodeWithTag("live-tab-chat").performClick()
        compose.onNodeWithTag("live-composer").assertIsDisplayed()
        compose.onNodeWithTag("live-back").performClick()
        compose.onNodeWithTag("live-tab-home").assertExists()
    }

    @Test fun launcherOpensNativeChatRatherThanOfflineDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        assertEquals(LiveChatActivity::class.java.name, launch.component?.className)
    }

    @Test fun onlyExplicitConsentAndStoredKeyEnableChargeableSend() {
        val submitted = mutableListOf<TextResponseRequest>()
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = true,
                    onBack = {},
                    onKeys = {},
                    onSend = { submitted += it; true },
                    onCancel = { false },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
        compose.onNodeWithTag("live-send").assertContentDescriptionEquals("Send message")
        compose.onNodeWithTag("live-open-key-setup")
            .assertContentDescriptionEquals("Open API key settings")
        compose.onNodeWithTag("live-composer").performTextInput("hello from UI")
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
        compose.onNodeWithTag("live-send-guidance")
            .assertTextContains("Tick Authorize one API request", substring = true)
        compose.onNodeWithTag("live-charge-consent").performClick()
        compose.onNodeWithTag("live-send").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, submitted.size)
            assertEquals("hello from UI", submitted.single().prompt)
            assertEquals("gpt-5.4-mini-2026-03-17", submitted.single().model)
            assertEquals(1024, submitted.single().maxOutputTokens)
            assertEquals("auto", submitted.single().settings?.reasoningSummary)
        }
        // Acknowledgement is single-use, not sticky for unintended repeats.
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
    }

    @Test fun keyMissingPreventsSendingEvenAfterChargeConsent() {
        var sends = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = false,
                    onBack = {},
                    onKeys = {},
                    onSend = { sends++; true },
                    onCancel = { false },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-composer").performTextInput("must not send")
        compose.onNodeWithTag("live-charge-consent").performClick()
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
        compose.onNodeWithTag("live-send-guidance")
            .assertTextContains("Add your own OpenAI API key", substring = true)
        compose.runOnIdle { assertEquals(0, sends) }
    }

    @Test fun thinkingSummaryOnlyAppearsWhenProviderSuppliesIt() {
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(
                        userPrompt = "Explain your summary",
                        assistantText = "Here is the answer.",
                        reasoningSummary = "Provider-supplied concise summary.",
                    ),
                    hasKey = true,
                    onBack = {},
                    onKeys = {},
                    onSend = { true },
                    onCancel = { false },
                )
            }
        }
        compose.waitForIdle()
        // The transcript is a LazyColumn: the summary item may not yet be composed.
        // Native today divider is now item 0; the summary follows the user card.
        compose.onNodeWithTag("live-transcript").performScrollToIndex(2)
        compose.onNodeWithTag("live-summary-toggle").assertExists().performClick()
        compose.onNodeWithTag("live-summary").assertExists()
    }

    @Test fun settingsTabContainsSummaryAndGenerationControls() {
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = true,
                    onBack = {},
                    onKeys = {},
                    onSend = { true },
                    onCancel = { false },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-tab-settings").performClick()
        compose.onNodeWithTag("live-summary-switch").assertExists()
        compose.onNodeWithTag("live-output-limit").assertExists()
    }


    @Test fun modelSheetScrollsToLastEntryOnNarrowAndroidViewport() {
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = false,
                    onBack = {},
                    onKeys = {},
                    onSend = { true },
                    onCancel = { false },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-model-picker").performClick()
        compose.onNodeWithTag("live-option-o3-2025-04-16").performScrollTo().performClick()
        compose.onNodeWithTag("live-model-picker").assertTextContains("o3", substring = true)
    }


    @Test fun multilinePromptKeepsSendAccessibleForTyping() {
        compose.runOnUiThread {
            compose.activity.setContent {
                LiveChatScreen(
                    snapshot = LiveChatSnapshot(),
                    hasKey = true,
                    onBack = {},
                    onKeys = {},
                    onSend = { true },
                    onCancel = { false },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-composer")
            .performTextInput("line one\nline two\nline three\nline four")
        compose.onNodeWithTag("live-charge-consent").performClick()
        compose.onNodeWithTag("live-send").assertIsEnabled()
    }

    /**
     * Unlike performTextInput alone, this waits for a genuinely visible IME.
     * Runs only in the opt-in Android emulator workflow, never against OpenAI.
     */
    @Test fun multilineComposerKeepsSendVisibleWithRealImeInsets() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String): String {
            val output = instrumentation.uiAutomation.executeShellCommand(command)
            return output.use { fd ->
                FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText().trim() }
            }
        }
        val originalKeyboardSetting = shell("settings get secure show_ime_with_hard_keyboard")
        require(originalKeyboardSetting in setOf("0", "1", "null")) {
            "Cannot safely restore the original software-keyboard setting."
        }
        shell("settings put secure show_ime_with_hard_keyboard 1")
        try {
            compose.runOnUiThread {
                compose.activity.setContent {
                    LiveChatScreen(
                        snapshot = LiveChatSnapshot(),
                        hasKey = true,
                        onBack = {},
                        onKeys = {},
                        onSend = { true },
                        onCancel = { false },
                    )
                }
            }
            compose.waitForIdle()
            compose.onNodeWithTag("live-charge-consent").performClick()
            compose.onNodeWithTag("live-composer").performClick()
            compose.onNodeWithTag("live-composer")
                .performTextInput("line one\nline two\nline three\nline four")
            // Hosted devices can drop the first IME show request after a
            // Compose semantics input. Focus the native host and retry once.
            var visibleIme = false
            for (attempt in 1..2) {
                compose.runOnUiThread {
                    val activity = compose.activity
                    val target = activity.currentFocus ?: activity.window.decorView
                    target.requestFocus()
                    val manager = activity.getSystemService(
                        android.content.Context.INPUT_METHOD_SERVICE
                    ) as android.view.inputmethod.InputMethodManager
                    manager.showSoftInput(
                        target,
                        android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT,
                    )
                    activity.window.decorView.windowInsetsController
                        ?.show(WindowInsets.Type.ime())
                }
                visibleIme = runCatching {
                    compose.waitUntil(timeoutMillis = 8_000) {
                        var visible = false
                        compose.runOnIdle {
                            visible = compose.activity.window.decorView.rootWindowInsets
                                ?.isVisible(WindowInsets.Type.ime()) == true
                        }
                        visible
                    }
                    true
                }.getOrDefault(false)
                if (visibleIme) break
            }
            if (!visibleIme) throw AssertionError(
                "Real IME did not appear. activeIME=" +
                    shell("settings get secure default_input_method") +
                    " availableIMEs=" + shell("ime list -s") +
                    " showWithHardwareKeyboard=" +
                    shell("settings get secure show_ime_with_hard_keyboard")
            )
            compose.onNodeWithTag("live-send").assertIsDisplayed().assertIsEnabled()
        } finally {
            // Restore keyboard state so subsequent UI tests are independent.
            compose.runOnUiThread {
                compose.activity.currentFocus?.clearFocus()
                compose.activity.window.decorView.windowInsetsController
                    ?.hide(WindowInsets.Type.ime())
            }
            when (originalKeyboardSetting) {
                "0", "1" -> shell("settings put secure show_ime_with_hard_keyboard $originalKeyboardSetting")
                "null" -> shell("settings delete secure show_ime_with_hard_keyboard")
            }
        }
    }

}
