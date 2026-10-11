package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
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

    /** The handles the assertions read after [setChipContent] returns. */
    private class ChipHarness {
        val commits = mutableListOf<Long>()
        lateinit var seekState: DpadSeekState
    }

    /**
     * The screen's chip shape, shared by both tests. [engineKey] non-null
     * keys the controller chain on it (the rebuild case); [lingerResetMs]
     * non-null installs the screen's chip linger collector.
     */
    private fun setChipContent(
        harness: ChipHarness,
        engineKey: MutableIntState? = null,
        lingerResetMs: Long? = null,
    ) {
        val seekDurationMs = 10_000L
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val controller = remember(engineKey?.intValue) {
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
                    onCommit = { harness.commits += it },
                )
            }
            // The harness field is the live-read delegate (the screen's
            // rememberUpdatedState shape): the pointerInput detectors freeze
            // their callbacks, so the arms must read the CURRENT instance at
            // invoke time, never the composition local captured at launch.
            harness.seekState = currentSeekState
            if (lingerResetMs != null) {
                // The screen's collector shape, keyed on the INSTANCE.
                LaunchedEffect(currentSeekState) {
                    snapshotFlow { currentSeekState.timestamp to currentSeekState.direction }
                        .filter { (_, direction) -> direction != 0 }
                        .collectLatest {
                            delay(lingerResetMs)
                            currentSeekState.reset()
                        }
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
                        onDoubleTapSeekBack = {
                            // Verbatim screen arm: addOffset + immediate commit.
                            harness.seekState.addOffset(-1, seekDurationMs)
                        },
                        onDoubleTapSeekForward = {
                            harness.seekState.addOffset(1, seekDurationMs)
                        },
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
                        harness.seekState.reset()
                    },
                    onEdgeSwipe = { },
                    resolveVerticalAction = { null },
                    onStartGesture = { controller.onStartGesture() },
                    onCancelOverlays = {
                        controller.onCancelOverlays()
                        harness.seekState.reset()
                    },
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /** A double-tap-back in the left seek zone. */
    private fun doubleTapBack() {
        composeTestRule.onRoot().performTouchInput {
            down(0, Offset(left + 40f, center.y)); up(0)
            advanceEventTime(100)
            down(0, Offset(left + 40f, center.y)); up(0)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun double_tap_seek_chip_state_survives_the_overlay_cleanup() {
        val harness = ChipHarness()
        setChipContent(harness)

        doubleTapBack()

        assertEquals(-1, harness.seekState.direction, "double-tap-back must leave the chip direction set")
        assertTrue(harness.seekState.offsetMs > 0, "the chip's render gate (offsetMs > 0) must hold after the overlay cleanup")
    }

    @Test
    fun chip_linger_reset_survives_a_controller_rebuild() {
        // The screen shapes its controller after the controller's remember keys
        // (engine/cast/swipe-max): a change mints a NEW DpadSeekState, and a
        // collector keyed on Unit keeps resetting the dead instance — the chip
        // rendered from the new one then never auto-hides. The effect must key
        // on the seekState instance so the linger reset follows rebuilds.
        val harness = ChipHarness()
        val engineKey = mutableIntStateOf(0)
        setChipContent(harness, engineKey = engineKey, lingerResetMs = 200L)

        // Generation 1: double-tap seek, chip sets, linger resets it.
        doubleTapBack()
        assertEquals(-1, harness.seekState.direction)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { harness.seekState.direction == 0 }

        // Rebuild the controller chain (an engine/cast change's shape), then
        // double-tap again: the NEW chip must linger-reset too.
        engineKey.intValue = 1
        composeTestRule.waitForIdle()
        doubleTapBack()
        assertEquals(-1, harness.seekState.direction, "the rebuilt chip must render")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            harness.seekState.direction == 0 && harness.seekState.offsetMs == 0L
        }
    }
}
