package dev.ciryva.app

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumentation tests: compile in CI, execute at P02 device acceptance. */
@RunWith(AndroidJUnit4::class)
class DraftComposeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetSavedDraft() {
        compose.activity.getSharedPreferences("ciryva_local", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        compose.activityRule.scenario.recreate()
    }

    @Test fun savedDraftCanBeClearedEvenAfterTextIsDeleted() {
        compose.onNodeWithTag("draft-input").performTextInput("saved note")
        compose.onNodeWithTag("save-draft").performClick()
        compose.onNodeWithTag("draft-input").performTextClearance()
        compose.onNodeWithTag("clear-draft").assertIsEnabled().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("clear-draft").assertIsNotEnabled()
    }

    @Test fun shortLandscapeWindowKeepsEditorAndSaveReachable() {
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        try {
            compose.waitForIdle()
            compose.onNodeWithTag("draft-input").performTextInput("landscape draft")
            compose.onNodeWithTag("save-draft").performScrollTo().performClick()
        } finally {
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    @Test fun draftActionsRemainReadableAndReachable() {
        compose.onNodeWithTag("draft-input").performTextInput("a short note")
        compose.onNodeWithTag("save-draft").performScrollTo()
        compose.onNodeWithText("Save draft").assertIsDisplayed()
        assertLabelInsideButton("Save draft", "save-draft")
        compose.onNodeWithTag("clear-draft").performScrollTo()
        compose.onNodeWithText("Clear draft").assertIsDisplayed()
        assertLabelInsideButton("Clear draft", "clear-draft")
    }

    /**
     * Unlike assertIsDisplayed (which accepts a partially visible node),
     * ensure all of the unmerged text label fits inside its tappable parent.
     * CI runs this on a narrow 320dp screen at 1.5x font scale.
     */
    private fun assertLabelInsideButton(label: String, buttonTag: String) {
        val button = compose.onNodeWithTag(buttonTag).fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText(label, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val tolerance = 1f // pixel rounding from Compose measurement
        assertTrue("$label left edge clipped", text.left >= button.left - tolerance)
        assertTrue("$label right edge clipped", text.right <= button.right + tolerance)
        assertTrue("$label top edge clipped", text.top >= button.top - tolerance)
        assertTrue("$label bottom edge clipped", text.bottom <= button.bottom + tolerance)
        assertTrue("$label has no width", text.width > 0)
        assertTrue("$label has no height", text.height > 0)
    }

}
