package dev.ciryva.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatPreviewComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ChatPreviewActivity>()

    @Test fun offlineStatusIsVisibleAndNoAssistantMessageIsFabricated() {
        compose.onNodeWithTag("chat-offline-label").assertExists()
        compose.onNodeWithTag("chat-add-local").assertIsNotEnabled()
        compose.onNodeWithTag("local-chat-composer").performTextInput("Hello CiRYVA")
        compose.onNodeWithTag("chat-add-local").assertIsEnabled().performScrollTo().performClick()
        // On a narrow, large-font screen scroll the newly added message
        // through its actual scrollable ancestor (transcript or outer form).
        // Scrolling the transcript container itself has no scrollable parent.
        compose.onNodeWithTag("local-chat-message-0").performScrollTo().assertExists()
        compose.onNodeWithText("Hello CiRYVA").assertExists()
        compose.onNodeWithTag("chat-add-local").assertIsNotEnabled()
        compose.onNodeWithTag("chat-clear-local").performScrollTo().performClick()
        compose.onNodeWithTag("local-chat-message-0").assertDoesNotExist()
    }

    @Test fun landscapeWithKeyboardKeepsComposerActionsReachable() {
        compose.activity.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        try {
            compose.waitForIdle()
            compose.onNodeWithTag("local-chat-composer").performTextInput("landscape preview")
            compose.onNodeWithTag("chat-add-local").performScrollTo().performClick()
            compose.onNodeWithTag("local-chat-message-0").assertExists()
            compose.onNodeWithTag("chat-clear-local").performScrollTo().performClick()
            compose.onNodeWithTag("local-chat-message-0").assertDoesNotExist()
        } finally {
            compose.activity.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
