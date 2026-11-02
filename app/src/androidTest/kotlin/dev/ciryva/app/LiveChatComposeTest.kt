package dev.ciryva.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.ciryva.core.stream.TextResponseRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Fake-only Compose contract. The dedicated instrumentation APK Activity is
 * declared in src/androidTest/AndroidManifest.xml; nothing is exported in production.
 */
class LiveChatTestHostActivity : ComponentActivity()

class LiveChatComposeTest {
    @get:Rule val compose = createAndroidComposeRule<LiveChatTestHostActivity>()

    @Test fun onlyExplicitConsentAndStoredKeyEnableChargeableSend() {
        val submitted = mutableListOf<TextResponseRequest>()
        compose.setContent {
            LiveChatScreen(
                snapshot = LiveChatSnapshot(),
                hasKey = true,
                onBack = {},
                onKeys = {},
                onSend = { submitted += it; true },
                onCancel = { false },
            )
        }
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
        compose.onNodeWithTag("live-composer").performTextInput("hello from UI")
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
        compose.onNodeWithTag("live-charge-consent").performScrollTo().performClick()
        compose.onNodeWithTag("live-send").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, submitted.size)
            assertEquals("hello from UI", submitted.single().prompt)
            assertEquals("gpt-5.4-mini-2026-03-17", submitted.single().model)
            assertEquals(1024, submitted.single().maxOutputTokens)
        }
        // Acknowledgement is single-use, not sticky for unintended repeats.
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
    }

    @Test fun keyMissingPreventsSendingEvenAfterChargeConsent() {
        var sends = 0
        compose.setContent {
            LiveChatScreen(
                snapshot = LiveChatSnapshot(),
                hasKey = false,
                onBack = {},
                onKeys = {},
                onSend = { sends++; true },
                onCancel = { false },
            )
        }
        compose.onNodeWithTag("live-composer").performTextInput("must not send")
        compose.onNodeWithTag("live-charge-consent").performScrollTo().performClick()
        compose.onNodeWithTag("live-send").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, sends) }
    }
}
