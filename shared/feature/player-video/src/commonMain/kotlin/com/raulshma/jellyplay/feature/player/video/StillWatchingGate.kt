package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.StillWatchingMode

/**
 * Which trigger raised the "Still watching?" confirm overlay (feature 1.3).
 * The arms share one overlay; the [StillWatchingPromptState.reason] decides
 * what **Continue** does (advance to the next episode vs. resume the paused
 * engine).
 */
enum class StillWatchingReason {
    /** The episode arm: N consecutive auto-played episodes without interaction. */
    EPISODE_COUNT,

    /** The hours arm: the pass-out protection tripped while the mode includes HOURS. */
    HOURS_IDLE,
}

/**
 * The confirm overlay's state — the pure half of the overlay state machine
 * (visible → ticking → answered | expired). The ViewModel owns the flow and
 * maps [tick]'s `null` expiry to the Stop action (auto-dismiss treats no
 * answer as "stop autoplaying"); the composable is a thin renderer that
 * emits ticks once per second.
 */
@Immutable
data class StillWatchingPromptState(
    val reason: StillWatchingReason,
    /** Auto-dismiss budget in seconds — the up-next countdown duration. */
    val countdownSeconds: Int,
) {
    /**
     * One countdown tick: decrements the budget; `null` when it expired —
     * the caller treats that as the Stop action (no answer = stop).
     */
    fun tick(): StillWatchingPromptState? =
        if (countdownSeconds > 0) copy(countdownSeconds = countdownSeconds - 1) else null

    companion object {
        /** Entry state; a non-positive budget collapses to the 1-second grace beat. */
        fun forReason(reason: StillWatchingReason, countdownSeconds: Int): StillWatchingPromptState =
            StillWatchingPromptState(
                reason = reason,
                countdownSeconds = countdownSeconds.coerceAtLeast(0),
            )
    }
}

/**
 * The "Still watching?" gate decisions (feature 1.3) — pure, so the
 * mode × threshold × SyncPlay × interaction truth table is unit-testable
 * without the ViewModel.
 */
internal object StillWatchingGate {

    /**
     * The end-of-episode arm, consulted by the VM's `handlePlaybackEnded`
     * BEFORE the auto-advance: fires when the mode includes EPISODES
     * ([StillWatchingMode.EPISODES] or [StillWatchingMode.BOTH]), the
     * autoplay controller's unattended streak reached the armed threshold
     * ([episodeCheck] — `AutoPlayController.needsStillWatchingCheck()`), and
     * no SyncPlay session is live (group pacing wins — the same early-return
     * `SegmentCalculator.shouldShowUpNextInternal` applies). Every other
     * combination advances silently.
     */
    fun shouldPrompt(
        mode: StillWatchingMode,
        episodeCheck: Boolean,
        isInSyncPlaySession: Boolean,
    ): Boolean = when (mode) {
        StillWatchingMode.EPISODES, StillWatchingMode.BOTH -> episodeCheck && !isInSyncPlaySession
        StillWatchingMode.OFF, StillWatchingMode.HOURS -> false
    }

    /**
     * The hours arm: when the mode includes HOURS, the coordinator's
     * `PassOutPause` decision upgrades from the silent pause + toast into the
     * same confirm overlay (the session emits `SessionEvent.StillWatchingPrompt`
     * instead of `SessionEvent.PassOutPause`).
     */
    fun upgradesPassOutToOverlay(mode: StillWatchingMode): Boolean =
        mode == StillWatchingMode.HOURS || mode == StillWatchingMode.BOTH
}
