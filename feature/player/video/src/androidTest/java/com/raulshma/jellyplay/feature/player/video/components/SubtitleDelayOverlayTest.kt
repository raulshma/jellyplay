package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Covers [SubtitleDelayOverlay] (VLC-style): the readout shows a whole-ms
 * label ("0 ms", "1500 ms", "-200 ms"), the +/− steppers nudge by 50ms and
 * flush once the burst settles, reset zeroes the delay, and the close button
 * fires [onDismiss]. The overlay's TV-only auto-focus is a no-op here, so on
 * touch these tests run under the (default) non-TV path.
 */
class SubtitleDelayOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun overlay_zeroDelay_showsMsLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(currentDelayMs = 0L, onChange = {}, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithTag("subtitle_delay_value").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 ms").assertIsDisplayed()
    }

    @Test
    fun overlay_positiveDelay_showsWholeMsLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(currentDelayMs = 1500L, onChange = {}, onDismiss = {})
            }
        }
        // VLC-style: no leading "+", no decimals, whole milliseconds.
        composeTestRule.onNodeWithText("1500 ms").assertIsDisplayed()
    }

    @Test
    fun overlay_negativeDelay_showsSignedMsLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(currentDelayMs = -200L, onChange = {}, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText("-200 ms").assertIsDisplayed()
    }

    @Test
    fun overlay_resetButton_zeroesDelay() {
        var reported = 1500L
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(currentDelayMs = 1500L, onChange = { reported = it }, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithTag("subtitle_delay_reset").performClick()
        assertEquals(0L, reported)
    }

    @Test
    fun overlay_closeButton_firesDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(currentDelayMs = 0L, onChange = {}, onDismiss = { dismissed = true })
            }
        }
        composeTestRule.onNodeWithText("Close subtitle delay").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun overlay_plus_tapsAccumulateAndFlush() {
        val reported = java.util.concurrent.atomic.AtomicLong(0L)
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(currentDelayMs = 0L, onChange = { reported.set(it) }, onDismiss = {})
            }
        }
        // Ten 50ms increments → the debounce flushes a single +500ms update once
        // the burst settles.
        repeat(10) {
            composeTestRule.onNodeWithTag("subtitle_delay_plus").performClick()
        }
        composeTestRule.waitUntil(2_000) { reported.get() == 500L }
        assertEquals(500L, reported.get())
    }

    @Test
    fun overlay_minus_tapsAccumulateAndFlush() {
        val reported = java.util.concurrent.atomic.AtomicLong(0L)
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(currentDelayMs = 0L, onChange = { reported.set(it) }, onDismiss = {})
            }
        }
        repeat(6) {
            composeTestRule.onNodeWithTag("subtitle_delay_minus").performClick()
        }
        composeTestRule.waitUntil(2_000) { reported.get() == -300L }
        assertEquals(-300L, reported.get())
    }

    @Test
    fun overlay_minus_offGrid_snapsDownToAdjacentGridLine() {
        // Regression: an off-step value (e.g. from a two-tap sync) must snap to
        // the adjacent grid line in the button's direction. The minus path used
        // mixed-sign modulo and overshot by a full step (137 → 50, skipping 100).
        val reported = java.util.concurrent.atomic.AtomicLong(137L)
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(currentDelayMs = 137L, onChange = { reported.set(it) }, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithTag("subtitle_delay_minus").performClick()
        composeTestRule.waitUntil(2_000) { reported.get() == 100L }
        assertEquals(100L, reported.get())
    }

    @Test
    fun overlay_plus_offGrid_snapsUpToAdjacentGridLine() {
        val reported = java.util.concurrent.atomic.AtomicLong(137L)
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDelayOverlay(currentDelayMs = 137L, onChange = { reported.set(it) }, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithTag("subtitle_delay_plus").performClick()
        composeTestRule.waitUntil(2_000) { reported.get() == 150L }
        assertEquals(150L, reported.get())
    }
}
