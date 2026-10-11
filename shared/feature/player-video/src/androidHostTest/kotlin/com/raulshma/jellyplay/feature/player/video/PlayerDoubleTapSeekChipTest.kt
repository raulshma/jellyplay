package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.model.SwipeSide
import com.raulshma.jellyplay.core.ui.tv.components.DpadSeekState
import com.raulshma.jellyplay.core.ui.tv.input.DpadSeekAcceleration
import com.raulshma.jellyplay.feature.player.video.components.GestureOverlay
import com.raulshma.jellyplay.feature.player.video.components.GestureSwipeGates
import com.raulshma.jellyplay.feature.player.video.state.GestureSeekController
import com.raulshma.jellyplay.feature.player.video.state.PlayerInputGates
import com.raulshma.jellyplay.feature.player.video.state.PlayerInputPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Pins the double-tap seek chip end to end, with the REAL GestureOverlay
 * sibling above the tap surface — the screen's shape. The overlay's
 * gesture-end cleanup runs [GestureSeekController.onClearOverlays] plus
 * [DpadSeekState.reset] on EVERY pointer stream (taps included); this test
 * proves a double-tap seek's chip state (`direction != 0 && offsetMs > 0`,
 * the [GestureOverlay] render gate) SURVIVES that cleanup — if the cleanup
 * lands after the arm's addOffset, the chip dies same-frame and never shows
 * (the "seek works but no indicator" report).
 */
@RunWith(AndroidJUnit4::class)
class PlayerDoubleTapSeekChipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun double_tap_seek_chip_state_survives_the_overlay_cleanup() {
        val seekDurationMs = 10_000L
        val commits = mutableListOf<Long>()
        lateinit var seekState: DpadSeekState

        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val controller = remember {
                GestureSeekController(
                    scope = scope,
                    getEngine = { null },
                    getSwipeSeekMaxMs = { 120_000L },
                    isCastConnected = { false },
                    getCastVolume = { 0f },
                    readWindowBrightness = { 0.5f },
                    writeWindowBrightness = {},
                    restoreWindowBrightness = {},
                    readStreamVolume = { 0 to 0 },
                    writeStreamVolume = {},
                    doSeekTo = {},
                    saveBrightness = {},
                    setCastVolume = {},
                )
            }
            seekState = remember {
                DpadSeekState(
                    acceleration = DpadSeekAcceleration.Default,
                    getBaseStepMs = { seekDurationMs },
                    getCurrentPositionMs = { 60_000L },
                    getDurationMs = { 600_000L },
                    onCommit = { commits += it },
                )
            }
            val map: PlayerInputMap = PlayerInputDefaults.defaultMap()
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
                        isHoldSpeedActive = { false },
                        holdSpeedEnabled = { false },
                        stopHoldSpeed = {},
                        startHoldSpeed = {},
                        toggleControls = {},
                        onDoubleTapSeekBack = {
                            // Verbatim screen arm: addOffset + immediate commit.
                            seekState.addOffset(-1, seekDurationMs)
                        },
                        onDoubleTapSeekForward = {
                            seekState.addOffset(1, seekDurationMs)
                        },
                        onDoubleTapCenter = {},
                        applyZoomDelta = {},
                    ),
            ) {
                GestureOverlay(
                    seekState = seekState,
                    brightnessFlow = controller.brightnessOverlay,
                    volumeFlow = controller.volumeOverlay,
                    indicatorSide = GestureIndicatorSide.OPPOSITE,
                    gates = GestureSwipeGates(
                        brightnessSwipe = true,
                        volumeSwipe = false,
                        seekSwipe = false,
                        edgeSwipeLeft = false,
                        edgeSwipeRight = false,
                    ),
                    swipeSeekMaxMs = 120_000L,
                    showControls = false,
                    onSeekGesture = { },
                    onBrightnessGesture = { },
                    onVolumeGesture = { },
                    onClearOverlays = {
                        controller.onClearOverlays()
                        seekState.reset()
                    },
                    onEdgeSwipe = { },
                    resolveVerticalAction = { null },
                    onStartGesture = { controller.onStartGesture() },
                    onCancelOverlays = {
                        controller.onCancelOverlays()
                        seekState.reset()
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().performTouchInput {
            down(0, Offset(left + 40f, center.y)); up(0)
            advanceEventTime(100)
            down(0, Offset(left + 40f, center.y)); up(0)
        }
        composeTestRule.waitForIdle()

        assertEquals(-1, seekState.direction, "double-tap-back must leave the chip direction set")
        assertTrue(seekState.offsetMs > 0, "the chip's render gate (offsetMs > 0) must hold after the overlay cleanup")
    }

    @Test
    fun chip_linger_reset_survives_a_controller_rebuild() {
        // The screen shapes its controller after the controller's remember keys
        // (engine/cast/swipe-max): a change mints a NEW DpadSeekState, and a
        // collector keyed on Unit keeps resetting the dead instance — the chip
        // rendered from the new one then never auto-hides. The effect must key
        // on the seekState instance so the linger reset follows rebuilds.
        val lingerMs = 200L
        val engineKey = androidx.compose.runtime.mutableIntStateOf(0)
        lateinit var seekState: DpadSeekState
        val seekDurationMs = 10_000L

        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val controller = remember(engineKey.intValue) {
                GestureSeekController(
                    scope = scope,
                    getEngine = { null },
                    getSwipeSeekMaxMs = { 120_000L },
                    isCastConnected = { false },
                    getCastVolume = { 0f },
                    readWindowBrightness = { 0.5f },
                    writeWindowBrightness = {},
                    restoreWindowBrightness = {},
                    readStreamVolume = { 0 to 0 },
                    writeStreamVolume = {},
                    doSeekTo = {},
                    saveBrightness = {},
                    setCastVolume = {},
                )
            }
            val currentSeekState = remember(controller) {
                DpadSeekState(
                    acceleration = DpadSeekAcceleration.Default,
                    getBaseStepMs = { seekDurationMs },
                    getCurrentPositionMs = { 60_000L },
                    getDurationMs = { 600_000L },
                    onCommit = {},
                )
            }
            // The test class field is the live-read delegate (the screen's
            // rememberUpdatedState shape): the pointerInput detectors freeze
            // their callbacks, so the arms must read the CURRENT instance at
            // invoke time, never the composition local captured at launch.
            seekState = currentSeekState

            // The screen's collector shape, keyed on the INSTANCE.
            androidx.compose.runtime.LaunchedEffect(currentSeekState) {
                snapshotFlow { currentSeekState.timestamp to currentSeekState.direction }
                    .filter { (_, direction) -> direction != 0 }
                    .collectLatest {
                        kotlinx.coroutines.delay(lingerMs)
                        currentSeekState.reset()
                    }
            }

            val map: PlayerInputMap = PlayerInputDefaults.defaultMap()
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
                        isHoldSpeedActive = { false },
                        holdSpeedEnabled = { false },
                        stopHoldSpeed = {},
                        startHoldSpeed = {},
                        toggleControls = {},
                        onDoubleTapSeekBack = { seekState.addOffset(-1, seekDurationMs) },
                        onDoubleTapSeekForward = { seekState.addOffset(1, seekDurationMs) },
                        onDoubleTapCenter = {},
                        applyZoomDelta = {},
                    ),
            ) {
                GestureOverlay(
                    seekState = currentSeekState,
                    brightnessFlow = controller.brightnessOverlay,
                    volumeFlow = controller.volumeOverlay,
                    indicatorSide = GestureIndicatorSide.OPPOSITE,
                    gates = GestureSwipeGates(
                        brightnessSwipe = true,
                        volumeSwipe = false,
                        seekSwipe = false,
                        edgeSwipeLeft = false,
                        edgeSwipeRight = false,
                    ),
                    swipeSeekMaxMs = 120_000L,
                    showControls = false,
                    onSeekGesture = { },
                    onBrightnessGesture = { },
                    onVolumeGesture = { },
                    onClearOverlays = {
                        controller.onClearOverlays()
                        seekState.reset()
                    },
                    onEdgeSwipe = { },
                    resolveVerticalAction = { null },
                    onStartGesture = { controller.onStartGesture() },
                    onCancelOverlays = {
                        controller.onCancelOverlays()
                        seekState.reset()
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Generation 1: double-tap seek, chip sets, linger resets it.
        composeTestRule.onRoot().performTouchInput {
            down(0, Offset(left + 40f, center.y)); up(0)
            advanceEventTime(100)
            down(0, Offset(left + 40f, center.y)); up(0)
        }
        composeTestRule.waitForIdle()
        assertEquals(-1, seekState.direction)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { seekState.direction == 0 }

        // Rebuild the controller chain (an engine/cast change's shape), then
        // double-tap again: the NEW chip must linger-reset too.
        engineKey.intValue = 1
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performTouchInput {
            down(0, Offset(left + 40f, center.y)); up(0)
            advanceEventTime(100)
            down(0, Offset(left + 40f, center.y)); up(0)
        }
        composeTestRule.waitForIdle()
        assertEquals(-1, seekState.direction, "the rebuilt chip must render")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            seekState.direction == 0 && seekState.offsetMs == 0L
        }
    }
}
