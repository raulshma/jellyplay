package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeActionBoxTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun contentDisplays_andActionFiresPastThreshold() {
        var actionFired = false
        composeTestRule.setContent {
            JellyPlayTheme(dynamicColor = false) {
                SwipeActionBox(
                    onAction = { actionFired = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    // Full-width content: the drag surface is the content Box,
                    // so the swipe must span the row to cross the commit
                    // threshold (55% of the 35% reveal distance).
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .testTag("swipe-content"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("row-content")
                    }
                }
            }
        }
        composeTestRule.onNodeWithText("row-content").assertExists()

        val restPosition = composeTestRule.onNodeWithTag("swipe-content")
            .fetchSemanticsNode().positionInRoot

        composeTestRule.onNodeWithTag("swipe-content").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()

        // The commit threshold sits at 55% of travel; a full-width swipe
        // overshoots it, so the action fires and the row settles back.
        assertTrue("swipe past threshold should commit the action", actionFired)
        composeTestRule.onNodeWithText("row-content").assertExists()
        // The settle-based commit sends the row back to its rest offset after
        // the action fires (within rounding noise).
        val settledPosition = composeTestRule.onNodeWithTag("swipe-content")
            .fetchSemanticsNode().positionInRoot
        assertTrue(
            "row should settle back after commit (rest=$restPosition, settled=$settledPosition)",
            abs(restPosition.x - settledPosition.x) < 1f,
        )
    }

    @Test
    fun disabledSwipe_doesNotFireAction() {
        var actionFired = false
        composeTestRule.setContent {
            JellyPlayTheme(dynamicColor = false) {
                SwipeActionBox(
                    onAction = { actionFired = true },
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    Text("locked-row")
                }
            }
        }
        composeTestRule.onNodeWithText("locked-row").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        assertTrue("enabled = false must swallow the swipe", !actionFired)
    }
}
