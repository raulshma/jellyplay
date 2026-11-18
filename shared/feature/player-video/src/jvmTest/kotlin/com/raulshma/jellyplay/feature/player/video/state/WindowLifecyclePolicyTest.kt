package com.raulshma.jellyplay.feature.player.video.state

import androidx.lifecycle.Lifecycle
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [WindowLifecyclePolicy] — the window/lifecycle effect
 * pack's decision fold, extracted from `PlayerWindowSession`'s effect bodies
 * (the [SeekChipCommitClockTest] pattern: no Compose, no coroutines). Pins
 * the predicates the former inline guards carried with zero coverage:
 *
 *  - the programmatic-close parking state (the #145 locked-window guard);
 *  - the two immersive re-hide gates (the PiP-exit edge, and the
 *    window-focus edge's PiP-transition skip);
 *  - the teardown split table (background-cast detach vs full release vs a
 *    live PiP transition tearing nothing down);
 *  - the keep-screen-on conjunction and the frame-rate match gate + stream
 *    pick;
 *  - the brightness sentinel guard (-1f = "not set"; 0f is a legitimate
 *    level);
 *  - the lifecycle-event → action verdicts (ON_RESUME brightness re-apply,
 *    ON_STOP lock reset, every other event a no-op).
 */
class WindowLifecyclePolicyTest {

    // ---- programmaticCloseState: the #145 parking guard ----

    @Test
    fun `programmatic closes park until RESUMED`() {
        // Plain LaunchedEffects keep collecting behind the keyguard; a close
        // against a STOPPED activity tore the window down unseen. closePlayer
        // is Channel(BUFFERED), so parking on RESUMED defers the events.
        assertEquals(Lifecycle.State.RESUMED, WindowLifecyclePolicy.programmaticCloseState)
    }

    // ---- the immersive re-hide gates ----

    @Test
    fun `the PiP-exit edge re-hides the bars - PiP entry leaves them`() {
        assertTrue(WindowLifecyclePolicy.rehideSystemBarsOnPipExit(isInPipMode = false))
        assertFalse(WindowLifecyclePolicy.rehideSystemBarsOnPipExit(isInPipMode = true))
    }

    @Test
    fun `the window-focus edge re-hides the bars only when focused and not in PiP`() {
        assertTrue(
            WindowLifecyclePolicy.rehideSystemBarsOnWindowFocus(focused = true, hostInPipMode = false),
        )
        assertFalse(
            WindowLifecyclePolicy.rehideSystemBarsOnWindowFocus(focused = false, hostInPipMode = false),
        )
    }

    @Test
    fun `the window-focus edge skips the re-hide during the PiP transition`() {
        // PlayerActivity.onPipModeChanged shows the bars on PiP entry to
        // anchor the gesture-nav handle; a re-hide here would leave the
        // handle floating mid-screen.
        assertFalse(
            WindowLifecyclePolicy.rehideSystemBarsOnWindowFocus(focused = true, hostInPipMode = true),
        )
        assertFalse(
            WindowLifecyclePolicy.rehideSystemBarsOnWindowFocus(focused = false, hostInPipMode = true),
        )
    }

    // ---- teardown: the dispose split ----

    @Test
    fun `background casting detaches instead of releasing`() {
        assertEquals(
            WindowLifecyclePolicy.TeardownAction.DetachForBackgroundCast,
            WindowLifecyclePolicy.teardown(isBackgroundCasting = true, isInPipMode = false),
        )
    }

    @Test
    fun `a plain exit releases the engine`() {
        assertEquals(
            WindowLifecyclePolicy.TeardownAction.Release,
            WindowLifecyclePolicy.teardown(isBackgroundCasting = false, isInPipMode = false),
        )
    }

    @Test
    fun `a live PiP transition tears nothing down`() {
        // The engine must survive until PiP is dismissed — both arms are
        // suppressed mid-transition.
        assertEquals(
            WindowLifecyclePolicy.TeardownAction.None,
            WindowLifecyclePolicy.teardown(isBackgroundCasting = false, isInPipMode = true),
        )
        assertEquals(
            WindowLifecyclePolicy.TeardownAction.None,
            WindowLifecyclePolicy.teardown(isBackgroundCasting = true, isInPipMode = true),
        )
    }

    // ---- keep-screen-on + frame-rate match ----

    @Test
    fun `keep screen on is the play AND pref conjunction`() {
        assertTrue(WindowLifecyclePolicy.keepScreenOn(isPlaying = true, keepScreenOnDuringVideo = true))
        assertFalse(WindowLifecyclePolicy.keepScreenOn(isPlaying = false, keepScreenOnDuringVideo = true))
        assertFalse(WindowLifecyclePolicy.keepScreenOn(isPlaying = true, keepScreenOnDuringVideo = false))
    }

    @Test
    fun `the frame-rate gate needs pref mode and a known rate`() {
        assertTrue(
            WindowLifecyclePolicy.shouldMatchFrameRate(
                frameRateMatching = true,
                refreshRateMode = RefreshRateMode.FRAME_RATE_ONLY,
                videoFrameRate = 23.976f,
            ),
        )
        assertFalse(
            WindowLifecyclePolicy.shouldMatchFrameRate(
                frameRateMatching = false,
                refreshRateMode = RefreshRateMode.FRAME_RATE_ONLY,
                videoFrameRate = 23.976f,
            ),
        )
        assertFalse(
            WindowLifecyclePolicy.shouldMatchFrameRate(
                frameRateMatching = true,
                refreshRateMode = RefreshRateMode.OFF,
                videoFrameRate = 23.976f,
            ),
        )
        assertFalse(
            WindowLifecyclePolicy.shouldMatchFrameRate(
                frameRateMatching = true,
                refreshRateMode = RefreshRateMode.FRAME_RATE_ONLY,
                videoFrameRate = null,
            ),
        )
    }

    @Test
    fun `the frame-rate target is the FIRST video stream`() {
        val audio = MediaStream(index = 0, type = StreamType.AUDIO)
        val videoA = MediaStream(index = 1, type = StreamType.VIDEO)
        val videoB = MediaStream(index = 2, type = StreamType.VIDEO)
        assertEquals(videoA, WindowLifecyclePolicy.frameRateTargetStream(listOf(audio, videoA, videoB)))
        assertEquals(videoA, WindowLifecyclePolicy.frameRateTargetStream(listOf(videoA, videoB)))
        assertNull(WindowLifecyclePolicy.frameRateTargetStream(listOf(audio)))
        assertNull(WindowLifecyclePolicy.frameRateTargetStream(emptyList()))
    }

    // ---- the brightness sentinel guard ----

    @Test
    fun `a remembered non-sentinel level applies`() {
        assertTrue(WindowLifecyclePolicy.appliesSavedBrightness(rememberBrightness = true, brightnessLevel = 0.5f))
    }

    @Test
    fun `the -1f sentinel never applies even when remembered`() {
        // -1f (BRIGHTNESS_OVERRIDE_NONE) is the "user hasn't set a level"
        // sentinel — it must not be used as a real level.
        assertFalse(WindowLifecyclePolicy.appliesSavedBrightness(rememberBrightness = true, brightnessLevel = -1f))
    }

    @Test
    fun `zero is a legitimate full-dim level not the sentinel`() {
        assertTrue(WindowLifecyclePolicy.appliesSavedBrightness(rememberBrightness = true, brightnessLevel = 0f))
    }

    @Test
    fun `the pref off arm never applies`() {
        assertFalse(WindowLifecyclePolicy.appliesSavedBrightness(rememberBrightness = false, brightnessLevel = 0.5f))
    }

    // ---- lifecycleReset: the event → action verdicts ----

    @Test
    fun `ON_RESUME re-applies the saved brightness through the sentinel guard`() {
        // The flag-keyed effect only re-fires when the FLAG changes, so this
        // observer is the arm that makes the level survive plain
        // foregrounding (the ON_RESUME brightness race).
        assertEquals(
            true,
            WindowLifecyclePolicy.resumeReappliesBrightness(
                Lifecycle.Event.ON_RESUME,
                rememberBrightness = true,
                brightnessLevel = 0.5f,
            ),
        )
        assertEquals(
            false,
            WindowLifecyclePolicy.resumeReappliesBrightness(
                Lifecycle.Event.ON_RESUME,
                rememberBrightness = true,
                brightnessLevel = -1f,
            ),
        )
        assertEquals(
            false,
            WindowLifecyclePolicy.resumeReappliesBrightness(
                Lifecycle.Event.ON_RESUME,
                rememberBrightness = false,
                brightnessLevel = 0.5f,
            ),
        )
    }

    @Test
    fun `only ON_RESUME re-applies the brightness`() {
        for (event in listOf(
            Lifecycle.Event.ON_CREATE,
            Lifecycle.Event.ON_START,
            Lifecycle.Event.ON_PAUSE,
            Lifecycle.Event.ON_STOP,
            Lifecycle.Event.ON_DESTROY,
            Lifecycle.Event.ON_ANY,
        )) {
            assertEquals(
                false,
                WindowLifecyclePolicy.resumeReappliesBrightness(event, rememberBrightness = true, brightnessLevel = 0.5f),
                "$event must not re-apply the brightness",
            )
        }
    }

    @Test
    fun `ON_STOP resets an engaged screen lock`() {
        // The transparent lock overlay would otherwise swallow every input
        // after a system lock/unlock — leaving fullscreen always disengages.
        assertEquals(
            true,
            WindowLifecyclePolicy.stopResetsScreenLock(Lifecycle.Event.ON_STOP, screenLocked = true),
        )
    }

    @Test
    fun `ON_STOP with the lock down is a no-op`() {
        assertEquals(
            false,
            WindowLifecyclePolicy.stopResetsScreenLock(Lifecycle.Event.ON_STOP, screenLocked = false),
        )
    }

    @Test
    fun `only ON_STOP resets the lock`() {
        for (event in listOf(
            Lifecycle.Event.ON_CREATE,
            Lifecycle.Event.ON_START,
            Lifecycle.Event.ON_RESUME,
            Lifecycle.Event.ON_PAUSE,
            Lifecycle.Event.ON_DESTROY,
            Lifecycle.Event.ON_ANY,
        )) {
            assertEquals(
                false,
                WindowLifecyclePolicy.stopResetsScreenLock(event, screenLocked = true),
                "$event must not reset the screen lock",
            )
        }
    }
}
