package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.feature.player.video.state.PlayerInputGates
import com.raulshma.jellyplay.feature.player.video.state.PlayerInputPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Pins the surface tap detector's double-tap arm end to end (issue #171
 * follow-up): the second press of a double tap must be recognized however the
 * panel ids it. Android pointer ids are only stable WITHIN one touch
 * sequence, so a new tap legally carries a fresh id — the detector must
 * accept any new down inside the double-tap window (foundation
 * detectTapGestures semantics), or every double tap degrades to a delayed
 * single tap on id-swapping panels.
 */
@RunWith(AndroidJUnit4::class)
class PlayerTapDoubleTapDetectorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class Counters {
        var toggleControls = 0
        var doubleTapCenter = 0
        var doubleTapBack = 0
        var doubleTapForward = 0
    }

    private fun setContent(counters: Counters) {
        val map: PlayerInputMap = PlayerInputDefaults.defaultMap()
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val isHoldSpeedActive = remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .playerTapAndZoomGestures(
                        gates = PlayerInputGates.of(map),
                        isScreenLocked = false,
                        doubleTapHoldSeekEnabled = true,
                        resolveAction = { PlayerInputPolicy.resolveAction(map, it) },
                        executePlayerAction = { true },
                        holdRepeatScope = scope,
                        onUserInteraction = {},
                        isHoldSpeedActive = { isHoldSpeedActive.value },
                        holdSpeedEnabled = { false },
                        stopHoldSpeed = {},
                        startHoldSpeed = {},
                        toggleControls = { counters.toggleControls++ },
                        onDoubleTapSeekBack = { counters.doubleTapBack++ },
                        onDoubleTapSeekForward = { counters.doubleTapForward++ },
                        onDoubleTapCenter = { counters.doubleTapCenter++ },
                        applyZoomDelta = {},
                    ),
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun double_tap_center_toggles_play_pause_arm_not_controls() {
        val counters = Counters()
        setContent(counters)
        composeTestRule.onRoot().performTouchInput {
            down(0, center); up(0)
            advanceEventTime(100)
            down(0, center); up(0)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, counters.doubleTapCenter, "center double tap must fire the play/pause arm")
        assertEquals(0, counters.toggleControls, "a recognized double tap must not fire the single-tap arm")
    }

    @Test
    fun double_tap_with_a_fresh_pointer_id_on_the_second_press_is_still_a_double_tap() {
        // The id-swapping-panel case: the second tap arrives under a NEW
        // pointer id (legal per Android) — the detector must not require an
        // id match (the v0.11.3 regression: double taps died entirely).
        val counters = Counters()
        setContent(counters)
        composeTestRule.onRoot().performTouchInput {
            down(0, center); up(0)
            advanceEventTime(100)
            down(1, center); up(1)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, counters.doubleTapCenter, "second press under a fresh pointer id must still pair")
        assertEquals(0, counters.toggleControls, "no delayed single tap may leak out of a paired double tap")
    }

    @Test
    fun double_tap_right_zone_seeks_forward() {
        val counters = Counters()
        setContent(counters)
        composeTestRule.onRoot().performTouchInput {
            down(Offset(right - 20f, center.y)); up(0)
            advanceEventTime(100)
            down(Offset(right - 20f, center.y)); up(0)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, counters.doubleTapForward)
        assertEquals(0, counters.doubleTapCenter)
    }
}
