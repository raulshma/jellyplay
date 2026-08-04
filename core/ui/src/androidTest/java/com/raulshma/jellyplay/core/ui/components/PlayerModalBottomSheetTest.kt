package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)

/**
 * Regression tests for the in-window player sheet's dismiss behaviour.
 *
 * The sheet panel is rendered as a sibling on top of a full-screen scrim that
 * dismisses on tap. Taps on the sheet itself (including non-interactive areas
 * such as titles, labels, spacers and the drag handle on a plain tap) must be
 * consumed by the panel so they never fall through to the scrim and dismiss the
 * sheet. Only taps landing on the scrim (outside the panel) should dismiss.
 *
 * See `PlayerModalBottomSheet.kt` — `InWindowPlayerSheet`.
 */
class PlayerModalBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingSheetBody_doesNotDismiss() {
        var dismissCount by mutableIntStateOf(0)
        composeTestRule.setContent {
            MaterialTheme {
                PlayerModalBottomSheet(onDismissRequest = { dismissCount++ }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text("Sheet body", Modifier.testTag("sheet_body"))
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("sheet_body").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sheet_body").performTouchInput { click() }
        composeTestRule.waitForIdle()

        // Sheet stays up — a touch on the panel itself must not dismiss it.
        composeTestRule.onNodeWithTag("sheet_body").assertIsDisplayed()
        assert(dismissCount == 0) { "Tapping the sheet body should not dismiss, got $dismissCount dismissal(s)" }
    }

    @Test
    fun tappingScrimOutsideSheet_dismisses() {
        var dismissCount by mutableIntStateOf(0)
        composeTestRule.setContent {
            MaterialTheme {
                PlayerModalBottomSheet(onDismissRequest = { dismissCount++ }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text("Sheet body", Modifier.testTag("sheet_body"))
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("sheet_body").assertIsDisplayed()
        // Tap well above the bottom-anchored panel — guaranteed to land on the scrim.
        composeTestRule.onRoot().performTouchInput { click(percentOffset(0.5f, 0.02f)) }
        composeTestRule.waitForIdle()

        assert(dismissCount == 1) { "Tapping the scrim should dismiss once, got $dismissCount dismissal(s)" }
    }
}
