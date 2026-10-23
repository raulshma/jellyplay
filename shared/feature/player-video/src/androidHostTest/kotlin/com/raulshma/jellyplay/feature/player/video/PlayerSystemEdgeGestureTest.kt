package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.SwipeSide
import com.raulshma.jellyplay.core.ui.tv.components.DpadSeekState
import com.raulshma.jellyplay.core.ui.tv.input.DpadSeekAcceleration
import com.raulshma.jellyplay.feature.player.video.components.GestureOverlay
import com.raulshma.jellyplay.feature.player.video.components.GestureSwipeGates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real drag detector, including the hidden-system-bars case. */
@RunWith(AndroidJUnit4::class)
class PlayerSystemEdgeGestureTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private class Gestures {
        val brightness = mutableListOf<Float>()
        val volume = mutableListOf<Float>()
        val seeks = mutableListOf<Long>()
        var edges = 0
        var starts = 0
        var clears = 0
        var cancels = 0

        fun assertUntouched() {
            assertTrue(brightness.isEmpty())
            assertTrue(volume.isEmpty())
            assertTrue(seeks.isEmpty())
            assertEquals(0, edges)
            assertEquals(0, starts)
            assertEquals(0, clears)
            assertEquals(0, cancels)
        }
    }

    private fun setContent(gestures: Gestures) {
        composeTestRule.setContent {
            val seekState = remember {
                DpadSeekState(
                    acceleration = DpadSeekAcceleration.Default,
                    getBaseStepMs = { 10_000L },
                    getCurrentPositionMs = { 60_000L },
                    getDurationMs = { 600_000L },
                    onCommit = {},
                )
            }
            val hiddenValue = remember { MutableStateFlow(-1f) }
            Box(Modifier.size(400.dp).testTag("player")) {
                GestureOverlay(
                    seekState = seekState,
                    brightnessFlow = hiddenValue,
                    volumeFlow = hiddenValue,
                    gates = GestureSwipeGates(true, true, true, true, true),
                    swipeSeekMaxMs = 120_000L,
                    showControls = false,
                    onSeekGesture = { gestures.seeks += it },
                    onBrightnessGesture = { gestures.brightness += it },
                    onVolumeGesture = { gestures.volume += it },
                    onClearOverlays = { gestures.clears++ },
                    onEdgeSwipe = { gestures.edges++ },
                    resolveVerticalAction = {
                        if (it == SwipeSide.LEFT) PlayerAction.SWIPE_BRIGHTNESS
                        else PlayerAction.SWIPE_VOLUME
                    },
                    onStartGesture = { gestures.starts++ },
                    onCancelOverlays = { gestures.cancels++ },
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun swipeDownFromTop(xFraction: Float) {
        composeTestRule.onNodeWithTag("player").performTouchInput {
            swipe(
                start = Offset(width * xFraction, 16.dp.toPx()),
                end = Offset(width * xFraction, height * 0.8f),
                durationMillis = 400,
            )
        }
    }

    @Test
    fun notification_swipe_on_left_does_not_change_brightness() {
        val gestures = Gestures()
        setContent(gestures)
        swipeDownFromTop(0.25f)
        gestures.assertUntouched()
    }

    @Test
    fun notification_swipe_on_right_does_not_change_volume() {
        val gestures = Gestures()
        setContent(gestures)
        swipeDownFromTop(0.75f)
        gestures.assertUntouched()
    }

    @Test
    fun next_swipe_after_notification_swipe_still_adjusts_volume() {
        val gestures = Gestures()
        setContent(gestures)
        swipeDownFromTop(0.75f)
        gestures.assertUntouched()
        composeTestRule.onNodeWithTag("player").performTouchInput {
            swipe(Offset(width * 0.75f, height * 0.4f), Offset(width * 0.75f, height * 0.8f))
        }
        assertTrue(gestures.volume.isNotEmpty())
        assertTrue(gestures.volume.all { it < 0f })
        assertEquals(1, gestures.starts)
        assertEquals(1, gestures.clears)
    }

    @Test
    fun ordinary_vertical_swipes_adjust_brightness_and_volume_in_both_directions() {
        val gestures = Gestures()
        setContent(gestures)
        for (xFraction in listOf(0.25f, 0.75f)) {
            composeTestRule.onNodeWithTag("player").performTouchInput {
                val x = width * xFraction
                swipe(Offset(x, height * 0.4f), Offset(x, height * 0.8f))
                swipe(Offset(x, height * 0.8f), Offset(x, height * 0.4f))
            }
        }
        for (deltas in listOf(gestures.brightness, gestures.volume)) {
            assertTrue(deltas.any { it < 0f })
            assertTrue(deltas.any { it > 0f })
        }
        assertTrue(gestures.seeks.isEmpty())
        assertEquals(0, gestures.edges)
    }

    @Test
    fun ordinary_swipe_can_enter_the_top_band_without_being_interrupted() {
        val gestures = Gestures()
        setContent(gestures)
        composeTestRule.onNodeWithTag("player").performTouchInput {
            swipe(Offset(width * 0.25f, center.y), Offset(width * 0.25f, 1f))
        }
        assertTrue(gestures.brightness.isNotEmpty())
        assertTrue(gestures.brightness.all { it > 0f })
        assertEquals(1, gestures.starts)
        assertEquals(1, gestures.clears)
    }

    @Test
    fun ordinary_horizontal_and_side_edge_swipes_still_work() {
        val gestures = Gestures()
        setContent(gestures)
        composeTestRule.onNodeWithTag("player").performTouchInput {
            swipe(Offset(width * 0.25f, center.y), Offset(width * 0.75f, center.y))
            swipe(Offset(1f, center.y), center)
        }
        assertTrue(gestures.seeks.any { it > 0L })
        assertEquals(1, gestures.edges)
        assertTrue(gestures.brightness.isEmpty())
        assertTrue(gestures.volume.isEmpty())
    }
}
