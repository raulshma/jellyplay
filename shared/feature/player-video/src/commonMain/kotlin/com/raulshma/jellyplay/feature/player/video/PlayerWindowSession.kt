package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.feature.player.video.session.CastSessionSlice
import com.raulshma.jellyplay.feature.player.video.state.WindowLifecyclePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The window/lifecycle cluster's ONE registration point — the remembered
 * holder `VideoPlayerScreen`'s root now calls instead of composing the pair
 * inline (the `rememberTrickplayFeeds` holder convention): the #145
 * programmatic-close collector (`closePlayer` parked via
 * `repeatOnLifecycle(RESUMED)`) followed by [PlayerWindowSessionEffects] at
 * the SAME position the contiguous block occupied, so effect dispatch order
 * is unchanged.
 *
 * Programmatic closes must never run against a STOPPED activity. The old
 * collectors were plain LaunchedEffects, which keep collecting behind the
 * keyguard — an episode ending (background audio, SyncPlay, a lockscreen
 * play) or a late PiP-dismiss signal while the phone was locked called
 * finish() on a hidden window, and unlock revealed the browse UI with the
 * player silently gone. PiP dismiss is now handled solely by the ViewModel
 * (its collector folds it into closePlayer); the screen consumes only
 * closePlayer, parked via repeatOnLifecycle(RESUMED). closePlayer is
 * Channel(BUFFERED), so events raised while stopped are deferred and
 * delivered on the next resume instead of tearing the window down unseen.
 *
 * The close lambda is captured live via `rememberUpdatedState` — the
 * collector keys on the lifecycle owner, so without it the screen would keep
 * invoking the onBack lambda captured at first composition (a nav lambda
 * that may have been rebuilt by the parent).
 */
@Composable
internal fun rememberPlayerWindowSession(
    lifecycleOwner: LifecycleOwner,
    closePlayer: Flow<Unit>,
    onClosePlayer: () -> Unit,
    windowOps: PlayerWindowOps,
    isInPipMode: Boolean,
    isTv: Boolean,
    isPlaying: Boolean,
    keepScreenOnDuringVideo: Boolean,
    frameRateMatching: Boolean,
    refreshRateMode: RefreshRateMode,
    videoFrameRate: Float?,
    mediaStreams: List<MediaStream>,
    rememberBrightness: Boolean,
    brightnessLevel: Float,
    isScreenLocked: Boolean,
    pipController: PipController,
    cast: CastSessionSlice,
    releasePlayer: () -> Unit,
    detachForBackgroundCast: () -> Unit,
    setScreenLocked: (Boolean) -> Unit,
    clearPlayerView: () -> Unit,
) {
    val currentOnClosePlayer by rememberUpdatedState(onClosePlayer)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(WindowLifecyclePolicy.programmaticCloseState) {
            closePlayer.collect { currentOnClosePlayer() }
        }
    }
    PlayerWindowSessionEffects(
        windowOps = windowOps,
        isInPipMode = isInPipMode,
        isTv = isTv,
        isPlaying = isPlaying,
        keepScreenOnDuringVideo = keepScreenOnDuringVideo,
        frameRateMatching = frameRateMatching,
        refreshRateMode = refreshRateMode,
        videoFrameRate = videoFrameRate,
        mediaStreams = mediaStreams,
        rememberBrightness = rememberBrightness,
        brightnessLevel = brightnessLevel,
        isScreenLocked = isScreenLocked,
        lifecycleOwner = lifecycleOwner,
        pipController = pipController,
        cast = cast,
        releasePlayer = releasePlayer,
        detachForBackgroundCast = detachForBackgroundCast,
        setScreenLocked = setScreenLocked,
        clearPlayerView = clearPlayerView,
    )
}

/**
 * The player screen's host-window/lifecycle effect session, extracted verbatim
 * from `VideoPlayerScreen` (the `PlayerScreenPolicies` orbit's effect-shell
 * precedent — `PlayerKeyboardFocusGrabEffect` is the nearest composable
 * sibling): the eight window/lifecycle effects that ride together at the top
 * of the screen's composition order moved into ONE composable called from the
 * SAME position, so effect dispatch order is preserved (Compose runs effects
 * in composition order and the block was contiguous).
 *
 * The bodies, effect types, keys and `rememberUpdatedState` captures are
 * byte-identical to the inline originals — they carry race-documented guards
 * that must not drift:
 *  - the focus `snapshotFlow` skips the immersive re-hide while the host is in
 *    (or transitioning into) PiP — the PiP-transition focus flip;
 *  - the ON_RESUME brightness observer re-applies the saved level on every
 *    foregrounding, because the flag-keyed `LaunchedEffect` only re-fires when
 *    the *flag* changes (the ON_RESUME brightness race);
 *  - the teardown `onDispose` reads `pipController.isInPipMode`'s LIVE value at
 *    dispose time (not the collected state, which lags a frame) and splits the
 *    background-cast handoff from the full release;
 *  - the ON_STOP observer reads the screen lock through
 *    `rememberUpdatedState`, keyed only on the lifecycle owner, so a lock
 *    engaged after install still resets (the issue-#145 unlock trap).
 *
 * The ViewModel dispatches and the mutable view-ref write arrive as plain
 * lambdas; the PiP controller and the cast session slice are passed whole
 * (the seams the screen already reads). No Compose state is returned — the
 * session composes the effects and nothing else. Every predicate the bodies
 * arm on is the state module's [WindowLifecyclePolicy] fold (the
 * [SeekChipCommitClock] precedent — pure decisions, JVM-testable).
 */
@Composable
internal fun PlayerWindowSessionEffects(
    windowOps: PlayerWindowOps,
    isInPipMode: Boolean,
    isTv: Boolean,
    isPlaying: Boolean,
    keepScreenOnDuringVideo: Boolean,
    frameRateMatching: Boolean,
    refreshRateMode: RefreshRateMode,
    videoFrameRate: Float?,
    mediaStreams: List<MediaStream>,
    rememberBrightness: Boolean,
    brightnessLevel: Float,
    isScreenLocked: Boolean,
    lifecycleOwner: LifecycleOwner,
    pipController: PipController,
    cast: CastSessionSlice,
    releasePlayer: () -> Unit,
    detachForBackgroundCast: () -> Unit,
    setScreenLocked: (Boolean) -> Unit,
    clearPlayerView: () -> Unit,
) {
    // Restore immersive mode when leaving PiP
    LaunchedEffect(isInPipMode) {
        if (WindowLifecyclePolicy.rehideSystemBarsOnPipExit(isInPipMode)) {
            windowOps.hideSystemBars()
        }
    }

    val windowInfo = LocalWindowInfo.current
    val isWindowFocused = rememberUpdatedState(windowInfo.isWindowFocused)
    LaunchedEffect(windowOps) {
        snapshotFlow { isWindowFocused.value }.distinctUntilChanged().collect { focused ->
            // Skip the immersive re-hide while in PiP (or mid-transition into
            // it): PlayerActivity.onPipModeChanged shows the bars on PiP entry
            // to force the relayout that anchors the gesture-nav handle at the
            // bottom. Without this guard the window-focus flip during the PiP
            // transition re-hides them here, defeating that fix and leaving the
            // handle floating mid-screen. Uses the host's authoritative
            // isInPictureInPictureMode flag (synchronously current, unlike the
            // collected isInPipMode state which lags a frame).
            if (WindowLifecyclePolicy.rehideSystemBarsOnWindowFocus(focused, windowOps.isInPipMode)) {
                windowOps.hideSystemBars()
            }
        }
    }

    // External-player handoff is handled centrally by the app-level
    // ActivityResultLauncher in JellyPlayApp's navigateFilter, which reads the
    // external player's returned position and credits watched progress. This
    // screen is never composed for the EXTERNAL case (navigation is intercepted
    // before reaching it), so no local launch logic is needed here.

    // Guard against releasing the engine when the composable is torn down
    // during a PiP transition. The engine must survive until PiP is dismissed.

    DisposableEffect(Unit) {
        windowOps.hideSystemBars()

        onDispose {
            val currentlyInPip = pipController.isInPipMode.value
            val isBgCasting = cast.isConnectedFlow.value && cast.castIsPlaying.value &&
                cast.backgroundCastingEnabled
            val restoreOrientation = if (isTv)
                PlayerOrientationLock.TV_LANDSCAPE
            else PlayerOrientationLock.UNSPECIFIED
            // restoreOnPlayerExit bundles the host-window teardown the screen
            // used to do inline: unlock orientation, clear FLAG_KEEP_SCREEN_ON,
            // restore OS-default brightness, re-show the system bars and hand
            // the display mode back (all host-alive guarded on Android).
            when (
                WindowLifecyclePolicy.teardown(
                    isBackgroundCasting = isBgCasting,
                    isInPipMode = currentlyInPip,
                )
            ) {
                WindowLifecyclePolicy.TeardownAction.DetachForBackgroundCast -> {
                    windowOps.restoreOnPlayerExit(restoreOrientation)
                    clearPlayerView()
                    detachForBackgroundCast()
                }
                WindowLifecyclePolicy.TeardownAction.Release -> {
                    windowOps.restoreOnPlayerExit(restoreOrientation)
                    clearPlayerView()
                    releasePlayer()
                }
                WindowLifecyclePolicy.TeardownAction.None -> {}
            }
        }
    }

    LaunchedEffect(isPlaying, keepScreenOnDuringVideo) {
        windowOps.setKeepScreenOn(
            WindowLifecyclePolicy.keepScreenOn(isPlaying, keepScreenOnDuringVideo),
        )
    }



    LaunchedEffect(frameRateMatching, refreshRateMode, videoFrameRate) {
        if (WindowLifecyclePolicy.shouldMatchFrameRate(frameRateMatching, refreshRateMode, videoFrameRate)) {
            val videoStream = WindowLifecyclePolicy.frameRateTargetStream(mediaStreams)
            windowOps.matchFrameRate(
                frameRate = videoFrameRate,
                targetWidth = videoStream?.width,
                targetHeight = videoStream?.height,
                mode = refreshRateMode,
            )
        }
    }

    LaunchedEffect(rememberBrightness) {
        // -1f (BRIGHTNESS_OVERRIDE_NONE) is the "user hasn't set a level" sentinel;
        // 0.5f is a legitimate brightness a user can pick, so it must not be used
        // as the guard. Re-applies the saved level on recreate/resume.
        if (WindowLifecyclePolicy.appliesSavedBrightness(rememberBrightness, brightnessLevel)) {
            windowOps.applyWindowBrightness(brightnessLevel)
        }
    }

    // The system resets window.attributes.screenBrightness to the OS default on
    // ON_PAUSE/ON_STOP (e.g. screen-off, app switch), and the LaunchedEffect above
    // only re-fires when the rememberBrightness *flag* changes — not on plain
    // foregrounding. Re-apply the saved level on every ON_RESUME so the user's
    // chosen brightness survives navigation away and back.
    // lifecycleOwner arrives from the screen (the Issue #145 programmatic-close
    // observers' owner — one declaration, shared).
    DisposableEffect(windowOps, rememberBrightness, brightnessLevel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (WindowLifecyclePolicy.resumeReappliesBrightness(event, rememberBrightness, brightnessLevel)) {
                windowOps.applyWindowBrightness(brightnessLevel)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The screen lock has no lifecycle reset anywhere, and its
    // overlay is a transparent fillMaxSize layer that consumes every
    // pointer event (LockScreenOverlay). If it stayed engaged across a system
    // lock/unlock, the player silently swallowed all input after unlock.
    // Leaving fullscreen entirely always disengages the lock; setScreenLocked
    // also mirrors isControlsLocked for the PiP auto-entry gate.
    // The observer reads the lock through rememberUpdatedState: keyed only on
    // lifecycleOwner, a captured lock state would freeze at install time and
    // the ON_STOP reset would silently no-op for any lock engaged afterwards.
    val screenLockedAtStop by rememberUpdatedState(isScreenLocked)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (WindowLifecyclePolicy.stopResetsScreenLock(event, screenLockedAtStop)) {
                setScreenLocked(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
