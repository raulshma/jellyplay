package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Covers [SubtitleDelayOverlay]: the delay readout reflects the current value,
 * steppers adjust it via [onChange], and reset zeroes it. The close button
 * fires [onDismiss]. The overlay's auto-hide timer is TV-gated out, so on touch
 * these tests run under the (default) non-TV path and rely on the manual close.
 */
class SubtitleDelayOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun overlay_zeroDelay_showsNeutralLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(
                    currentDelayMs = 0L,
                    onChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("subtitle_delay_value").assertIsDisplayed()
        composeTestRule.onNodeWithText("0.0s").assertIsDisplayed()
    }

    @Test
    fun overlay_positiveDelay_showsPlusLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(
                    currentDelayMs = 1500L,
                    onChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("+1.5s").assertIsDisplayed()
    }

    @Test
    fun overlay_resetChip_zeroesDelay() {
        var reported: Long = 1500L
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(
                    currentDelayMs = 1500L,
                    onChange = { reported = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("subtitle_delay_reset").performClick()
        org.junit.Assert.assertEquals(0L, reported)
    }

    @Test
    fun overlay_closeButton_firesDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(
                    currentDelayMs = 0L,
                    onChange = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Close subtitle delay").performClick()
        org.junit.Assert.assertTrue(dismissed)
    }

    @Test
    fun overlay_coarsePlus_tapsAccumulateAndFlush() {
        val reported = java.util.concurrent.atomic.AtomicLong(0L)
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(
                    currentDelayMs = 0L,
                    onChange = { reported.set(it) },
                    onDismiss = {},
                )
            }
        }
        // Three +1.0s taps → the debounce flushes a single +3000ms update once
        // the burst settles.
        repeat(3) {
            composeTestRule.onNodeWithTag("subtitle_delay_plus_coarse").performClick()
        }
        composeTestRule.waitUntil(2_000) { reported.get() == 3_000L }
        org.junit.Assert.assertEquals(3_000L, reported.get())
    }
}
