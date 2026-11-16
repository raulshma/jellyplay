package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * Covers [SubtitleDelayOverlay] (VLC-style): the readout shows a whole-ms
 * label ("0 ms", "1500 ms", "-200 ms"), the +/− steppers nudge by 50ms and
 * flush once the burst settles, reset zeroes the delay, and the close button
 * fires [onDismiss]. The overlay's TV-only auto-focus is a no-op here, so on
 * touch these tests run under the (default) non-TV path.
 */
@RunWith(AndroidJUnit4::class)
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
        // The close affordance is an Icon, so its string lives in the
        // content-description semantics, not the Text property.
        composeTestRule.onNodeWithContentDescription("Close subtitle delay").performClick()
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

    // ── DECLARED FIX pins: the dispose final-flush (ReaderProgressReporter's
    // exit-flush shape). The overlay's debounce used to die with the
    // composition: closing inside the debounce window (✕, back, or the host's
    // PiP/lock gates removing the overlay without onDismiss) silently dropped
    // the LAST adjustment from the persisted per-item delay. The dispose hook
    // now flushes a still-pending value synchronously — and only then, so a
    // quiet close issues no redundant write (each delivery costs an
    // ExoPlayer media reload).

    /** The host-shaped removal gate: flipping it false drops the overlay. */
    private val overlayVisible = androidx.compose.runtime.mutableStateOf(true)

    private fun setContentFrozenClock(
        currentDelayMs: Long,
        onChange: (Long) -> Unit,
    ) {
        overlayVisible.value = true
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                if (overlayVisible.value) {
                    SubtitleDelayOverlay(
                        currentDelayMs = currentDelayMs,
                        onChange = onChange,
                        onDismiss = {},
                    )
                }
            }
        }
        advanceOneFrame()
    }

    /**
     * One frame under a frozen main clock: state changes must be observed by
     * layout/recomposition before the virtual clock advances (the
     * TimedSkipPromptTest house pattern).
     */
    private fun advanceOneFrame() {
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(16)
        composeTestRule.waitForIdle()
    }

    /**
     * Removes the overlay the way the host does — the conditional composition
     * gate flips false and the composable leaves composition (the onDispose
     * path under test).
     */
    private fun removeOverlay() {
        composeTestRule.runOnIdle { overlayVisible.value = false }
        advanceOneFrame()
    }

    @Test
    fun overlay_disposeInsideDebounceWindow_flushesLastAdjustment() {
        val reported = java.util.concurrent.atomic.AtomicLong(0L)
        setContentFrozenClock(currentDelayMs = 0L, onChange = { reported.set(it) })
        composeTestRule.onNodeWithTag("subtitle_delay_plus").performClick()
        advanceOneFrame()
        // The debounce window is armed but frozen — nothing delivered yet.
        assertEquals(0L, reported.get())
        // Leaving composition inside the window must deliver the pending value.
        removeOverlay()
        assertEquals(50L, reported.get())
    }

    @Test
    fun overlay_disposeInsideDebounceWindow_externalReseed_supersededAdjustmentNeverDelivered() {
        val writes = java.util.concurrent.atomic.AtomicInteger(0)
        val lastValue = java.util.concurrent.atomic.AtomicLong(-1L)
        val externalDelay = androidx.compose.runtime.mutableLongStateOf(0L)
        composeTestRule.mainClock.autoAdvance = false
        overlayVisible.value = true
        composeTestRule.setContent {
            MaterialTheme {
                if (overlayVisible.value) {
                    SubtitleDelayOverlay(
                        currentDelayMs = externalDelay.longValue,
                        onChange = { lastValue.set(it); writes.incrementAndGet() },
                        onDismiss = {},
                    )
                }
            }
        }
        advanceOneFrame()
        composeTestRule.onNodeWithTag("subtitle_delay_plus").performClick()
        advanceOneFrame()
        // An external write (hub reset / engine hydration) lands mid-debounce:
        // the guarded re-seed drops the dirty bit WITH the superseded
        // adjustment — the external value IS the persisted state, so the
        // dispose flush must deliver NOTHING (the first cut re-delivered the
        // already-current 100 here, a redundant engine media reload).
        composeTestRule.runOnIdle { externalDelay.longValue = 100L }
        advanceOneFrame()
        // The re-seed committed (readout shows the external value).
        composeTestRule.onNodeWithText("100 ms").assertIsDisplayed()
        removeOverlay()
        assertEquals(0, writes.get())
        assertEquals(-1L, lastValue.get())
    }

    @Test
    fun overlay_externalReseed_midDebounce_pendingFlushDropped() {
        // The guarded re-seed CANCELS the armed debounce: an external write
        // mid-window wins outright, so neither the debounce nor the dispose
        // flush may deliver anything afterwards — the superseded +50 can
        // never reach onChange, not even re-labelled as the re-seeded 100
        // (the pre-fix debounce delivered that redundant write).
        val writes = java.util.concurrent.atomic.AtomicInteger(0)
        val lastValue = java.util.concurrent.atomic.AtomicLong(-1L)
        val externalDelay = androidx.compose.runtime.mutableLongStateOf(0L)
        composeTestRule.mainClock.autoAdvance = false
        overlayVisible.value = true
        composeTestRule.setContent {
            MaterialTheme {
                if (overlayVisible.value) {
                    SubtitleDelayOverlay(
                        currentDelayMs = externalDelay.longValue,
                        onChange = { lastValue.set(it); writes.incrementAndGet() },
                        onDismiss = {},
                    )
                }
            }
        }
        advanceOneFrame()
        composeTestRule.onNodeWithTag("subtitle_delay_plus").performClick()
        advanceOneFrame()
        composeTestRule.runOnIdle { externalDelay.longValue = 100L }
        advanceOneFrame()
        composeTestRule.onNodeWithText("100 ms").assertIsDisplayed()
        // Unfreeze and run the clock well past the debounce window: the
        // cancelled job must not have delivered through any path.
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()
        assertEquals(0, writes.get())
        assertEquals(-1L, lastValue.get())
    }

    @Test
    fun overlay_disposeAfterDebounceDelivered_noDuplicateWrite() {
        val writes = java.util.concurrent.atomic.AtomicInteger(0)
        overlayVisible.value = true
        composeTestRule.setContent {
            MaterialTheme {
                if (overlayVisible.value) {
                    SubtitleDelayOverlay(
                        currentDelayMs = 0L,
                        onChange = { writes.incrementAndGet() },
                        onDismiss = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("subtitle_delay_plus").performClick()
        // Let the debounce deliver on its own.
        composeTestRule.waitUntil(2_000) { writes.get() == 1 }
        // Closing afterwards must NOT re-deliver (a redundant write costs an
        // engine media reload).
        removeOverlay()
        composeTestRule.waitForIdle()
        assertEquals(1, writes.get())
    }

    @Test
    fun overlay_disposeWithoutAdjustments_noWrite() {
        val writes = java.util.concurrent.atomic.AtomicInteger(0)
        overlayVisible.value = true
        composeTestRule.setContent {
            MaterialTheme {
                if (overlayVisible.value) {
                    SubtitleDelayOverlay(
                        currentDelayMs = 0L,
                        onChange = { writes.incrementAndGet() },
                        onDismiss = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        removeOverlay()
        composeTestRule.waitForIdle()
        assertEquals(0, writes.get())
    }
}
