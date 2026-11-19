package com.raulshma.jellyplay.feature.player.video.state

import androidx.lifecycle.Lifecycle
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.StreamType

/**
 * The player screen's window/lifecycle effect pack policy — everything the
 * host-window and lifecycle effects DECIDE, extracted from
 * `PlayerWindowSession`'s effect bodies (the [SeekChipCommitClock]
 * precedent: a pure decision fold so the verdicts are JVM-testable without
 * composition). The session composable keeps only the effect registration;
 * this module owns every predicate those effects arm on and every
 * lifecycle-event → action verdict they execute:
 *
 *  - **The programmatic-close parking state** ([programmaticCloseState]):
 *    closePlayer is delivered only while RESUMED — the old plain
 *    LaunchedEffects kept collecting behind the keyguard, and a programmatic
 *    close against a STOPPED activity tore the window down unseen (the #145
 *    fix; closePlayer is Channel(BUFFERED), so events raised while stopped
 *    defer to the next resume).
 *  - **The immersive re-hide gates**: on the PiP-exit edge
 *    ([rehideSystemBarsOnPipExit]) and on the window-focus edge
 *    ([rehideSystemBarsOnWindowFocus] — which skips the re-hide while the
 *    host is in or transitioning into PiP: the PiP-transition focus flip,
 *    where PlayerActivity shows the bars on PiP entry to anchor the
 *    gesture-nav handle).
 *  - **The teardown split** ([teardown]): background-cast hands off
 *    (detach) instead of releasing; a live PiP transition tears NOTHING down
 *    (the engine must survive until PiP is dismissed).
 *  - **The keep-screen-on conjunction**, **the frame-rate match gate** and
 *    its video-stream pick ([shouldMatchFrameRate] / [frameRateTargetStream]).
 *  - **The brightness sentinel guard** ([appliesSavedBrightness]):
 *    -1f (BRIGHTNESS_OVERRIDE_NONE) is the "user hasn't set a level"
 *    sentinel; 0.5f — and 0f — are legitimate levels a user can pick, so the
 *    guard must not reject them.
 *  - **The lifecycle-event → action verdicts** ([resumeReappliesBrightness]
 *    / [stopResetsScreenLock]): ON_RESUME re-applies the saved brightness
 *    (the flag-keyed effect only re-fires when the flag changes — the
 *    ON_RESUME brightness race), and ON_STOP resets a screen lock left
 *    engaged (the transparent lock overlay would otherwise swallow every
 *    input after a system lock/unlock — the issue-#145 unlock trap; the
 *    observer reads the lock through `rememberUpdatedState` so a lock
 *    engaged after install still resets).
 */
internal object WindowLifecyclePolicy {

    /**
     * The lifecycle state the programmatic-close collector parks on —
     * RESUMED only (see the module KDoc's #145 note).
     */
    val programmaticCloseState: Lifecycle.State = Lifecycle.State.RESUMED

    /** The PiP-exit edge re-hides the system bars; PiP entry leaves them. */
    fun rehideSystemBarsOnPipExit(isInPipMode: Boolean): Boolean = !isInPipMode

    /**
     * The window-focus edge re-hides the system bars unless the HOST is in
     * PiP (or mid-transition into it) — the host's authoritative
     * `isInPictureInPictureMode` flag, synchronously current, unlike the
     * collected state which lags a frame.
     */
    fun rehideSystemBarsOnWindowFocus(focused: Boolean, hostInPipMode: Boolean): Boolean =
        focused && !hostInPipMode

    /** What the screen's teardown `onDispose` executes. */
    enum class TeardownAction {
        /** Background casting continues elsewhere — detach, keep the engine. */
        DetachForBackgroundCast,
        /** Full teardown: restore the host window and release the engine. */
        Release,
        /** A live PiP transition — the engine survives until PiP is dismissed. */
        None,
    }

    /**
     * The teardown split — exactly the former inline `if/else if` in the
     * dispose body: background-cast handoff first, then the full release,
     * both suppressed by a live PiP transition.
     */
    fun teardown(isBackgroundCasting: Boolean, isInPipMode: Boolean): TeardownAction = when {
        isBackgroundCasting && !isInPipMode -> TeardownAction.DetachForBackgroundCast
        !isInPipMode -> TeardownAction.Release
        else -> TeardownAction.None
    }

    /** Keep the screen on only while the engine plays AND the pref allows. */
    fun keepScreenOn(isPlaying: Boolean, keepScreenOnDuringVideo: Boolean): Boolean =
        isPlaying && keepScreenOnDuringVideo

    /**
     * The frame-rate match gate: the master pref, a non-OFF mode, and a
     * known video frame rate must all hold.
     */
    fun shouldMatchFrameRate(
        frameRateMatching: Boolean,
        refreshRateMode: RefreshRateMode,
        videoFrameRate: Float?,
    ): Boolean = frameRateMatching && refreshRateMode != RefreshRateMode.OFF && videoFrameRate != null

    /**
     * The frame-rate match's target geometry: the FIRST video stream's
     * dimensions (null when no video stream — the gate above never fires).
     */
    fun frameRateTargetStream(mediaStreams: List<MediaStream>): MediaStream? =
        mediaStreams.firstOrNull { it.type == StreamType.VIDEO }

    /**
     * Whether a saved brightness level applies: the remember pref AND a
     * non-sentinel level (-1f = BRIGHTNESS_OVERRIDE_NONE, "not set"; 0f is a
     * legitimate full-dim level a user can pick).
     */
    fun appliesSavedBrightness(rememberBrightness: Boolean, brightnessLevel: Float): Boolean =
        rememberBrightness && brightnessLevel >= 0f

    /**
     * The ON_RESUME observer's verdict: re-apply the saved brightness on
     * foregrounding, through the sentinel guard. The flag-keyed effect only
     * re-fires when the FLAG changes, so this observer is the arm that makes
     * the level survive plain navigation away and back (the ON_RESUME
     * brightness race).
     */
    fun resumeReappliesBrightness(
        event: Lifecycle.Event,
        rememberBrightness: Boolean,
        brightnessLevel: Float,
    ): Boolean = event == Lifecycle.Event.ON_RESUME && appliesSavedBrightness(rememberBrightness, brightnessLevel)

    /**
     * The ON_STOP observer's verdict: an engaged screen lock disengages when
     * the system stops the window — leaving fullscreen always disengages the
     * lock (the transparent overlay would otherwise swallow every input
     * after a system lock/unlock).
     */
    fun stopResetsScreenLock(event: Lifecycle.Event, screenLocked: Boolean): Boolean =
        event == Lifecycle.Event.ON_STOP && screenLocked
}
