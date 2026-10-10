package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.state.GesturePrefsState
import com.raulshma.jellyplay.feature.player.video.state.PlayerUiPrefsState
import com.raulshma.jellyplay.feature.player.video.state.SegmentState
import com.raulshma.jellyplay.feature.player.video.state.VideoFxState

/**
 * The item-switch uiState rebuild, declared once: the surviving leaves are
 * exactly the constructor arguments here — everything else resets to its
 * slice default. Each listed slice is rebuilt FRESH (not a `.copy`), so an
 * unlisted leaf can never ride a stale slice object across the switch.
 *
 * Single-writer ordering: this rebuild runs BEFORE
 * [EpisodeContinuationController.resetForItemSwitch] in the item-switch
 * teardown. It wipes the whole state, the episode slice included; the
 * navigator — the episode slice's single writer — then re-derives that slice
 * through its seam from the post-rebuild state. Resetting the episode slice
 * before this rebuild would write into the outgoing state only for the
 * rebuild to clobber it.
 */
internal fun VideoPlayerUiState.keepAcrossItems(): VideoPlayerUiState = VideoPlayerUiState(
    preferredPlayerType = preferredPlayerType,
    // uiPrefs: the prefs-mirror leaves carry across an item switch
    // (orientation, controls timeout, metadata/clock/time-remaining
    // visibility, keep-screen-on); the per-item / runtime leaves
    // (stats overlay, pass-out hours, trickplay info + toggles,
    // quality, ABR, playback mode, lock/PIN flags) reset to defaults.
    uiPrefs = PlayerUiPrefsState(
        defaultOrientation = uiPrefs.defaultOrientation,
        controlsTimeoutMs = uiPrefs.controlsTimeoutMs,
        showPlaybackMetadata = uiPrefs.showPlaybackMetadata,
        showClock = uiPrefs.showClock,
        showTimeRemaining = uiPrefs.showTimeRemaining,
        keepScreenOnDuringVideo = uiPrefs.keepScreenOnDuringVideo,
    ),
    // gestures: the prefs-mirror leaves carry across an item switch
    // (seek window, gesture tier flags, default speed, swipe cap,
    // brightness flag + level); the runtime leaves (hold-speed
    // toggle/multiplier/active flag, indicator side, frame-rate
    // matching, refresh-rate mode) reset to defaults. Fresh slice —
    // same tight semantics as uiPrefs above.
    gestures = GesturePrefsState(
        seekDurationMs = gestures.seekDurationMs,
        gestureMode = gestures.gestureMode,
        defaultSpeed = gestures.defaultSpeed,
        swipeSeekMaxMs = gestures.swipeSeekMaxMs,
        rememberBrightness = gestures.rememberBrightness,
        brightnessLevel = gestures.brightnessLevel,
    ),
    // segmentState: only the behaviors carry across an item switch —
    // the per-item segment list resets to default (empty).
    segmentState = SegmentState(
        segmentBehaviors = segmentState.segmentBehaviors,
    ),
    // episodes resets through the navigator's seam right after this
    // update: only the browser feature toggle carries across an
    // item switch — adjacency, season/episode lists, season id and
    // the loading flag are per-item and reset to defaults.
    // videoFx: only the TV zoom carries across an item switch —
    // the per-item effects and both aspect fields reset to defaults.
    videoFx = VideoFxState(tvZoomModePercent = videoFx.tvZoomModePercent),
    subtitleStyle = subtitleStyle,
    // Reset per-item dialogue boost so it doesn't bleed into the next
    // item before the resolver re-applies the per-item rule. Dialogue
    // boost stays resolver-driven session state — see
    // VideoEffectsController's KDoc.
    dialogueBoostEnabled = false,
    dialogueBoostStrength = com.raulshma.jellyplay.core.model.EffectStrength.NONE,
)
