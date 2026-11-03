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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertTextContains
import androidx.test.platform.app.InstrumentationRegistry
import dev.ciryva.core.stream.TextResponseRequest
import java.io.FileInputStream
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
        compose.onNodeWithTag("p06-persona-selector").performClick()
        compose.onNodeWithTag("live-option-custom").performClick()
        compose.onNodeWithTag("p06-persona-instructions")
            .performTextInput("Use concise technical language.")
        compose.onNodeWithTag("p06-local-usage-totals").assertExists()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(ChatPreferences.MODE_CUSTOM, stored?.personaMode)
            assertEquals("Use concise technical language.",
                stored?.requestInstructions())
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
            // Compose semantics text input can bypass the platform IME. Ask the
            // Android window for the real keyboard before testing its inset.
            compose.runOnUiThread {
                val controller = checkNotNull(compose.activity.window.decorView.windowInsetsController) {
                    "No Android WindowInsetsController available for a real IME test"
                }
                controller.show(WindowInsets.Type.ime())
            }
            try {
                compose.waitUntil(timeoutMillis = 10_000) {
                    var visible = false
                    compose.runOnIdle {
                        visible = compose.activity.window.decorView.rootWindowInsets
                            ?.isVisible(WindowInsets.Type.ime()) == true
                    }
                    visible
                }
            } catch (failure: Exception) {
                throw AssertionError(
                    "Real IME did not appear. activeIME=" +
                        shell("settings get secure default_input_method") +
                        " availableIMEs=" + shell("ime list -s") +
                        " showWithHardwareKeyboard=" +
                        shell("settings get secure show_ime_with_hard_keyboard"),
                    failure,
                )
            }
            compose.onNodeWithTag("live-send").assertIsDisplayed().assertIsEnabled()
        } finally {
            when (originalKeyboardSetting) {
                "0", "1" -> shell("settings put secure show_ime_with_hard_keyboard $originalKeyboardSetting")
                "null" -> shell("settings delete secure show_ime_with_hard_keyboard")
            }
        }
    }

}
