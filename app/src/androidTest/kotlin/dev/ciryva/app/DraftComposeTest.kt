package dev.ciryva.app

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
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
        compose.onNodeWithTag("save-draft").performScrollTo().performClick()
        // Prove that Save actually committed, not just that Clear is clickable.
        compose.activityRule.scenario.recreate()
        assertEquals(
            "saved note",
            compose.activity.getSharedPreferences(
                "ciryva_local", android.content.Context.MODE_PRIVATE,
            ).getString("local_draft", null),
        )
        compose.onNodeWithTag("draft-input").performScrollTo().performTextClearance()
        compose.onNodeWithTag("clear-draft").assertIsEnabled().performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        assertNull(
            compose.activity.getSharedPreferences(
                "ciryva_local", android.content.Context.MODE_PRIVATE,
            ).getString("local_draft", null),
        )
        compose.onNodeWithTag("clear-draft").assertIsEnabled()
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
        val button = compose.onNodeWithTag(buttonTag).getUnclippedBoundsInRoot()
        val textNode = compose.onNodeWithText(label, useUnmergedTree = true)
        val text = textNode.getUnclippedBoundsInRoot()
        val tolerance = 1.dp // measurement rounding
        assertTrue("$label left edge clipped", text.left >= button.left - tolerance)
        assertTrue("$label right edge clipped", text.right <= button.right + tolerance)
        assertTrue("$label top edge clipped", text.top >= button.top - tolerance)
        assertTrue("$label bottom edge clipped", text.bottom <= button.bottom + tolerance)
        assertTrue("$label has no width", text.width > 0.dp)
        assertTrue("$label has no height", text.height > 0.dp)

        // Semantics geometry alone cannot detect a Text node whose own layout
        // overflows. Inspect the real Compose text-layout result as well.
        val layouts = mutableListOf<TextLayoutResult>()
        val readLayout = textNode.fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertTrue("$label missing text layout action", readLayout != null)
        assertTrue("$label layout action failed", readLayout!!.invoke(layouts))
        assertTrue("$label missing text layout result", layouts.isNotEmpty())
        assertTrue("$label text visually overflows", layouts.none { it.hasVisualOverflow })
    }

}
