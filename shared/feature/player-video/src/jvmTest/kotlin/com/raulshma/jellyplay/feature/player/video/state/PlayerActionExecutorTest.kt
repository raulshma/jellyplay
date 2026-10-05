package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.feature.player.video.VideoPlayerUiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM tests for [PlayerActionExecutor] using recording lambdas (the
 * [GestureSeekControllerTest] pattern, no Compose composition). Pins the
 * extracted screen behavior:
 *
 *  - the key-down path: code → catalog key → [PlayerInputPolicy] resolution
 *    against the LIVE map, the mediaKeySeek ?: keyboardSeekStepMs seek fold
 *    accumulating into the shared D-pad seek chip, NONE consuming as a no-op,
 *    unmatched keys falling through, and the user-interaction bookkeeping
 *    firing for EVERY key-down (matched or not);
 *  - the discrete arms: every effect port + reader pair the former inline
 *    `executePlayerAction` drove, including the capability-gated audio-delay
 *    no-op and the brightness trio through a real [GestureSeekController];
 *  - the continuous class (the SWIPE family, ZOOM, HOLD_SPEED, NONE)
 *    returning false with zero interactions.
 *
 * Key events ride the desktop's synthetic-KeyDown factory, so `playerKeyCode`
 * and the modifier flags read exactly what the production jvmMain seam
 * reports for the same [Key].
 */
class PlayerActionExecutorTest {

    // ---- fixtures ----

    /** Recording probe for every port the executor drives. */
    private class Probe {
        var userInteractions = 0
        var togglePlayPauses = 0
        var plays = 0
        var pauses = 0
        var seekForwards = 0
        var seekBacks = 0
        val seekTargets = mutableListOf<Long>()
        var orientationToggles = 0
        val volumeAdjusts = mutableListOf<Boolean>()
        val audioDelays = mutableListOf<Long>()
        val showControlsWrites = mutableListOf<Boolean>()
        val delayOverlayWrites = mutableListOf<Boolean>()
        var backs = 0
        var screenshots = 0
        var haptics = 0
        val events = mutableListOf<VideoPlayerUiEvent>()
        val savedBrightnesses = mutableListOf<Float>()
    }

    /** The live reads, mutable so a rebound map / flipped flag is visible to an already-built executor. */
    private data class Inputs(
        var map: PlayerInputMap = PlayerInputDefaults.defaultMap(),
        var showControls: Boolean = false,
        var isPlaying: Boolean = false,
        var hideOsdOnPause: Boolean = false,
        var playbackSpeed: Float = 1.0f,
        var subtitleOffsetMs: Long = 0L,
        var supportsAudioDelay: Boolean = true,
        var audioDelayMs: Long = 0L,
        var seekStepMs: Long = 10_000L,
        var positionMs: Long = 60_000L,
        var durationMs: Long = 120_000L,
    )

    private fun gestureController(
        scope: CoroutineScope,
        probe: Probe,
        readWindowBrightness: () -> Float = { -1f },
    ): GestureSeekController = GestureSeekController(
        scope = scope,
        getEngine = { null },
        getSwipeSeekMaxMs = { 120_000L },
        isCastConnected = { false },
        getCastVolume = { 1f },
        readWindowBrightness = readWindowBrightness,
        writeWindowBrightness = { },
        restoreWindowBrightness = { },
        readStreamVolume = { 0 to 0 },
        writeStreamVolume = { },
        doSeekTo = { probe.seekTargets += it },
        saveBrightness = { probe.savedBrightnesses += it },
        setCastVolume = { },
    )

    private fun executor(
        scope: CoroutineScope,
        inputs: Inputs = Inputs(),
        probe: Probe = Probe(),
        gestures: GestureSeekController = gestureController(scope, probe),
    ): PlayerActionExecutor = PlayerActionExecutor(
        getInputMap = { inputs.map },
        getShowControls = { inputs.showControls },
        getIsPlaying = { inputs.isPlaying },
        getHideOsdOnPause = { inputs.hideOsdOnPause },
        getPlaybackSpeed = { inputs.playbackSpeed },
        getSubtitleOffsetMs = { inputs.subtitleOffsetMs },
        getSupportsAudioDelay = { inputs.supportsAudioDelay },
        getAudioDelayMs = { inputs.audioDelayMs },
        getSeekStepMs = { inputs.seekStepMs },
        getCurrentPositionMs = { inputs.positionMs },
        getDurationMs = { inputs.durationMs },
        onSeekCommit = { probe.seekTargets += it },
        doTogglePlayPause = { probe.togglePlayPauses++ },
        doPlay = { probe.plays++ },
        doPause = { probe.pauses++ },
        toggleOrientation = { probe.orientationToggles++ },
        streamVolumeAdjuster = { up -> probe.volumeAdjusts += up },
        setAudioDelay = {
            inputs.audioDelayMs = it // the real funnel writes live; the next press reads it back
            probe.audioDelays += it
        },
        onUiEvent = { probe.events += it },
        doSeekForward = { probe.seekForwards++ },
        doSeekBack = { probe.seekBacks++ },
        setShowControls = {
            inputs.showControls = it
            probe.showControlsWrites += it
        },
        setShowDelayOverlay = { probe.delayOverlayWrites += it },
        onBack = { probe.backs++ },
        onScreenshotClick = { probe.screenshots++ },
        performConfirmHaptic = { probe.haptics++ },
        onUserInteraction = { probe.userInteractions++ },
        gestureController = gestures,
    )

    /**
     * A synthetic KeyDown the desktop seam recognises: `playerKeyCode` folds
     * the [Key] through the production jvmMain table, so an uncatalogued key
     * (no branch there) arrives as the unknown code, exactly like a real
     * unmatched press. The factory is the ui-desktop test constructor
     * (internal-API opt-in — nothing else in Compose builds a bare KeyEvent).
     */
    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun keyDown(
        key: Key,
        isShiftPressed: Boolean = false,
        isCtrlPressed: Boolean = false,
        isAltPressed: Boolean = false,
    ): KeyEvent = KeyEvent(
        key = key,
        type = KeyEventType.KeyDown,
        isShiftPressed = isShiftPressed,
        isCtrlPressed = isCtrlPressed,
        isAltPressed = isAltPressed,
    )

    // ---- key-down path: seek fold ----

    @Test
    fun `seek key-down accumulates into the seek chip and the debounced commit fires`() = runTest {
        val inputs = Inputs()
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.handleMediaKeyDown(keyDown(Key.L)))

        // The L row's fold: configured 10s step, forward direction.
        assertEquals(1, ex.seekState.direction)
        assertEquals(10_000L, ex.seekState.offsetMs)
        assertEquals(1, ex.seekState.timestamp)
        assertEquals(1, ex.keyboardSeekStamp)
        assertEquals(1L, ex.lastKeyboardSeekChipTimestamp)
        assertEquals(listOf(true), probe.showControlsWrites)
        assertEquals(1, probe.userInteractions)

        // Repeats accumulate (the chip's addOffset path, not a fresh press).
        assertTrue(ex.handleMediaKeyDown(keyDown(Key.L)))
        assertEquals(20_000L, ex.seekState.offsetMs)
        assertEquals(2, ex.keyboardSeekStamp)

        // The commit routes through DpadSeekState's onCommit: 60s + 20s.
        ex.seekState.commitForward()
        assertEquals(listOf(80_000L), probe.seekTargets)
    }

    @Test
    fun `seek key-down takes the modifier ladder`() = runTest {
        val inputs = Inputs()
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        // Shift widens the J row to the ±5s fine step (still backward).
        assertTrue(ex.handleMediaKeyDown(keyDown(Key.J, isShiftPressed = true)))
        assertEquals(-1, ex.seekState.direction)
        assertEquals(5_000L, ex.seekState.offsetMs)

        // PgDn is a direction-fixed page row (±300s), not the configured jump.
        val pageInputs = Inputs()
        val pageEx = executor(backgroundScope, pageInputs, probe)
        assertTrue(pageEx.handleMediaKeyDown(keyDown(Key.PageDown)))
        assertEquals(-1, pageEx.seekState.direction)
        assertEquals(60_000L, pageEx.seekState.offsetMs) // clamped to the 60s remaining behind

        // The backward commit lands on 0s.
        pageEx.seekState.commitBackward()
        assertEquals(listOf(0L), probe.seekTargets)
    }

    @Test
    fun `unmatched key returns false and still counts the interaction`() = runTest {
        val inputs = Inputs()
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        // Q is outside the key catalog entirely.
        assertFalse(ex.handleMediaKeyDown(keyDown(Key.Q)))
        assertEquals(1, probe.userInteractions)
        assertEquals(0, probe.showControlsWrites.size)
        assertEquals(0, ex.keyboardSeekStamp)
    }

    @Test
    fun `NONE-mapped key consumes as a no-op`() = runTest {
        val inputs = Inputs(
            map = PlayerInputDefaults.defaultMap()
                .withBindingAction(PlayerInputDefaults.ID_KEY_M, PlayerAction.NONE),
        )
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.handleMediaKeyDown(keyDown(Key.M)))
        assertEquals(1, probe.userInteractions)
        // Explicit unbind: consumed, no effects at all.
        assertEquals(0, probe.events.size)
        assertEquals(0, probe.showControlsWrites.size)
    }

    @Test
    fun `rebound key dispatches the rebound action`() = runTest {
        // V's row rebounded from TOGGLE_SUBTITLES to TOGGLE_MUTE via the model API.
        val inputs = Inputs(
            map = PlayerInputDefaults.defaultMap()
                .withBindingAction(PlayerInputDefaults.ID_KEY_V, PlayerAction.TOGGLE_MUTE),
        )
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.handleMediaKeyDown(keyDown(Key.V)))
        assertEquals(listOf<VideoPlayerUiEvent>(VideoPlayerUiEvent.ToggleMute), probe.events)
        assertEquals(listOf(true), probe.showControlsWrites)
    }

    // ---- discrete arms ----

    @Test
    fun `SEEK arms step once with haptic and summon`() = runTest {
        val inputs = Inputs()
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.executeAction(PlayerAction.SEEK_FORWARD))
        assertEquals(1, probe.seekForwards)
        assertTrue(ex.executeAction(PlayerAction.SEEK_BACK))
        assertEquals(1, probe.seekBacks)
        assertEquals(2, probe.haptics)
        assertEquals(listOf(true, true), probe.showControlsWrites)
    }

    @Test
    fun `TOGGLE_CONTROLS flips via the live controls reader`() = runTest {
        val inputs = Inputs(showControls = false)
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.executeAction(PlayerAction.TOGGLE_CONTROLS))
        assertEquals(listOf(true), probe.showControlsWrites)
        assertTrue(ex.executeAction(PlayerAction.TOGGLE_CONTROLS))
        assertEquals(listOf(true, false), probe.showControlsWrites)
    }

    @Test
    fun `BACK_OR_HIDE hides when visible and backs when hidden`() = runTest {
        val visible = Inputs(showControls = true)
        val visibleProbe = Probe()
        val visibleEx = executor(backgroundScope, visible, visibleProbe)
        assertTrue(visibleEx.executeAction(PlayerAction.BACK_OR_HIDE))
        assertEquals(listOf(false), visibleProbe.showControlsWrites)
        assertEquals(0, visibleProbe.backs)

        val hidden = Inputs(showControls = false)
        val hiddenProbe = Probe()
        val hiddenEx = executor(backgroundScope, hidden, hiddenProbe)
        assertTrue(hiddenEx.executeAction(PlayerAction.BACK_OR_HIDE))
        assertEquals(1, hiddenProbe.backs)
        assertEquals(0, hiddenProbe.showControlsWrites.size)
    }

    @Test
    fun `EXIT_PLAYER backs`() = runTest {
        val inputs = Inputs()
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.executeAction(PlayerAction.EXIT_PLAYER))
        assertEquals(1, probe.backs)
    }

    @Test
    fun `VOLUME arms adjust the system stream and summon`() = runTest {
        val inputs = Inputs()
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.executeAction(PlayerAction.VOLUME_UP))
        assertTrue(ex.executeAction(PlayerAction.VOLUME_DOWN))
        assertEquals(listOf(true, false), probe.volumeAdjusts)
        assertEquals(listOf(true, true), probe.showControlsWrites)
    }

    @Test
    fun `BRIGHTNESS arms step through the gesture controller trio and commit`() = runTest {
        val inputs = Inputs()
        val probe = Probe()
        val gestures = gestureController(backgroundScope, probe, readWindowBrightness = { 0.5f })
        val ex = executor(backgroundScope, inputs, probe, gestures)

        assertTrue(ex.executeAction(PlayerAction.BRIGHTNESS_UP))
        assertEquals(listOf(0.6f), probe.savedBrightnesses)
        // The controller's indicator dismiss is a delayed commit — let it run
        // so the next arm steps from a freshly captured level, like a real
        // press after the bar has cleared.
        advanceTimeBy(GestureSeekController.GESTURE_BARS_DISMISS_MS + 1L)
        assertTrue(ex.executeAction(PlayerAction.BRIGHTNESS_DOWN))
        assertEquals(listOf(0.6f, 0.4f), probe.savedBrightnesses)
        assertEquals(0, probe.events.size)
    }

    @Test
    fun `CYCLE_SPEED walks the ladder and wraps past the top`() = runTest {
        val inputs = Inputs(playbackSpeed = 1.0f)
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.executeAction(PlayerAction.CYCLE_SPEED))
        assertEquals(listOf<VideoPlayerUiEvent>(VideoPlayerUiEvent.SetPlaybackSpeed(1.25f)), probe.events)
        assertEquals(listOf(true), probe.showControlsWrites)

        inputs.playbackSpeed = 2.0f
        probe.events.clear()
        assertTrue(ex.executeAction(PlayerAction.CYCLE_SPEED))
        assertEquals(listOf<VideoPlayerUiEvent>(VideoPlayerUiEvent.SetPlaybackSpeed(1.0f)), probe.events)
    }

    @Test
    fun `AUDIO_DELAY arms are gated on the engine capability`() = runTest {
        val gated = Inputs(supportsAudioDelay = false)
        val gatedProbe = Probe()
        val gatedEx = executor(backgroundScope, gated, gatedProbe)
        assertTrue(gatedEx.executeAction(PlayerAction.AUDIO_DELAY_INCREASE))
        assertTrue(gatedProbe.audioDelays.isEmpty())
        assertTrue(gatedProbe.events.isEmpty())

        val capable = Inputs(supportsAudioDelay = true, audioDelayMs = 0L)
        val capableProbe = Probe()
        val capableEx = executor(backgroundScope, capable, capableProbe)
        assertTrue(capableEx.executeAction(PlayerAction.AUDIO_DELAY_INCREASE))
        assertTrue(capableEx.executeAction(PlayerAction.AUDIO_DELAY_DECREASE))
        assertEquals(listOf(100L, 0L), capableProbe.audioDelays)
        // No OSD event: the change applies directly (the AVSync sheet's funnel).
        assertTrue(capableProbe.events.isEmpty())
    }

    @Test
    fun `SUBTITLE_DELAY arms step by 50ms and raise the delay overlay`() = runTest {
        val inputs = Inputs(subtitleOffsetMs = 0L)
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.executeAction(PlayerAction.SUBTITLE_DELAY_INCREASE))
        assertTrue(ex.executeAction(PlayerAction.SUBTITLE_DELAY_DECREASE))
        assertEquals(
            listOf<VideoPlayerUiEvent>(
                VideoPlayerUiEvent.SetSubtitleDelay(50L),
                VideoPlayerUiEvent.SetSubtitleDelay(-50L),
            ),
            probe.events,
        )
        assertEquals(listOf(true, true), probe.delayOverlayWrites)
        assertEquals(0, probe.showControlsWrites.size)
    }

    @Test
    fun `transport arms dispatch per the isPlaying reader`() = runTest {
        val inputs = Inputs(isPlaying = false)
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.executeAction(PlayerAction.PLAY))
        assertEquals(1, probe.plays)
        assertTrue(ex.executeAction(PlayerAction.PAUSE))
        assertEquals(1, probe.pauses)

        // Toggle while paused plays (and summons — the summon gate only
        // suppresses when the toggle will PAUSE with hide-OSD on).
        assertTrue(ex.executeAction(PlayerAction.TOGGLE_PLAY_PAUSE))
        assertEquals(1, probe.togglePlayPauses)
        assertEquals(1, probe.haptics)
        assertEquals(listOf(true), probe.showControlsWrites)

        inputs.isPlaying = true
        inputs.hideOsdOnPause = true
        probe.showControlsWrites.clear()
        assertTrue(ex.executeAction(PlayerAction.TOGGLE_PLAY_PAUSE))
        assertEquals(2, probe.togglePlayPauses)
        // Pausing with hide-OSD on leaves the overlay hidden.
        assertEquals(0, probe.showControlsWrites.size)
    }

    @Test
    fun `LOCK_CONTROLS locks and hides the controls`() = runTest {
        val inputs = Inputs()
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.executeAction(PlayerAction.LOCK_CONTROLS))
        assertEquals(listOf<VideoPlayerUiEvent>(VideoPlayerUiEvent.SetScreenLocked(true)), probe.events)
        assertEquals(listOf(false), probe.showControlsWrites)
    }

    @Test
    fun `SCREENSHOT rides the injected capture path`() = runTest {
        val inputs = Inputs()
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.executeAction(PlayerAction.SCREENSHOT))
        assertEquals(1, probe.screenshots)
    }

    @Test
    fun `episode and toggle arms dispatch their events`() = runTest {
        val inputs = Inputs()
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        assertTrue(ex.executeAction(PlayerAction.NEXT_EPISODE))
        assertTrue(ex.executeAction(PlayerAction.PREVIOUS_EPISODE))
        assertTrue(ex.executeAction(PlayerAction.TOGGLE_SUBTITLES))
        assertTrue(ex.executeAction(PlayerAction.TOGGLE_MUTE))
        assertTrue(ex.executeAction(PlayerAction.TOGGLE_ORIENTATION))
        assertEquals(
            listOf<VideoPlayerUiEvent>(
                VideoPlayerUiEvent.PlayNextEpisode,
                VideoPlayerUiEvent.PlayPreviousEpisode,
                VideoPlayerUiEvent.ToggleSubtitles,
                VideoPlayerUiEvent.ToggleMute,
            ),
            probe.events,
        )
        // Subtitles are a video effect, not chrome — no summon; mute and
        // orientation summon like the volume arms.
        assertEquals(listOf(true, true), probe.showControlsWrites)
        assertEquals(1, probe.orientationToggles)
    }

    // ---- continuous class ----

    @Test
    fun `continuous-class actions return false with zero interactions`() = runTest {
        val inputs = Inputs()
        val probe = Probe()
        val ex = executor(backgroundScope, inputs, probe)

        for (action in listOf(
            PlayerAction.SWIPE_SEEK,
            PlayerAction.SWIPE_VOLUME,
            PlayerAction.SWIPE_BRIGHTNESS,
            PlayerAction.ZOOM,
            PlayerAction.HOLD_SPEED,
            PlayerAction.NONE,
        )) {
            assertFalse(ex.executeAction(action), "$action must not execute from a discrete site")
        }
        assertEquals(0, probe.userInteractions)
        assertEquals(0, probe.events.size)
        assertEquals(0, probe.haptics)
        assertEquals(0, probe.showControlsWrites.size)
        assertEquals(0, probe.savedBrightnesses.size)
    }
}
