package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.TrickplayInfo

/**
 * Player UI / system preferences: control visibility, orientation, trickplay,
 * streaming quality, playback mode, lock/PIN. These are low-frequency (set once
 * or via settings) — splitting them out insulates the player screen from the
 * high-churn position/buffering flows.
 */
@Immutable
data class PlayerUiPrefsState(
    val controlsTimeoutMs: Long = 5_000L,
    /**
     * (jellyfin-androidtv #3924): when on, PAUSING must not summon the
     * control overlay. Default **off** — today's show-on-pause behavior.
     * Only the summons is gated: an already-visible overlay is never
     * force-hidden (the [controlsTimeoutMs] auto-hide owns dismissal), and
     * resuming still summons as before.
     */
    val hideOsdOnPause: Boolean = false,
    val defaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val passOutProtectionHours: Int = 0,
    val showVideoStats: Boolean = false,
    val showPlaybackMetadata: Boolean = true,
    val showClock: Boolean = false,
    val showTimeRemaining: Boolean = false,
    val keepScreenOnDuringVideo: Boolean = true,
    val usePinForPlayerLock: Boolean = false,
    /**
     * Presence flag for the player-lock PIN. The hash itself never
     * leaves the VM/prefs — surfacing it through per-frame UiState churned
     * state identity on PIN change and exposed the hash to equals/hashCode/
     * toString (log risk). Callers only ever gate on presence.
     */
    val hasPin: Boolean = false,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val trickplayInfo: TrickplayInfo? = null,
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val adaptiveBitrateEnabled: Boolean = true,
    val playbackMode: PlaybackMode = PlaybackMode.AUTO,
)
