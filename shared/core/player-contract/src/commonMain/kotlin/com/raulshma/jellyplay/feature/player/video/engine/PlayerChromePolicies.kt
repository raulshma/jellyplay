package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.delay

/**
 * Pure player-chrome timing policies shared by BOTH player screens (the VOD
 * `VideoPlayerScreen` and the live `LivePlayerScreen`). They live in the
 * engine-agnostic player-contract home (beside [EnginePositionTicker]) so the
 * two screens cite ONE implementation instead of carrying byte-identical
 * copies.
 */

/**
 * Controls auto-hide timeout fold: TV keeps controls twice as long as touch
 * form factors (a remote user reads the chrome from a distance; a touch user
 * just tapped it). Both player screens' auto-hide effects pass their
 * preference-sourced base timeout through this one fold.
 */
fun controlsAutoHideTimeoutMs(baseTimeoutMs: Long, isTv: Boolean): Long =
    if (isTv) baseTimeoutMs * 2 else baseTimeoutMs

/**
 * Cadence of the live DVR-window refresh poll (the live screen's
 * `viewModel.refreshPosition()` tick).
 *
 * Why a poll at all — the VOD player drives position purely through engine
 * flows (`PlaybackProgressReporter` over `MediaEngine.positionFlow`), but the
 * live engine contract is media3's PULL model: `LivePlayerEngine` only
 * republishes `positionMs` / `durationMs` / `isAtLiveEdge` when its
 * `refreshLiveWindow()` is called (media3 pushes state *changes*, not a
 * continuously-advancing position), so SOMETHING must tick to make the seek
 * bar move and the live-edge read stay accurate. The tick therefore lives at
 * the presentation edge, gated on the chrome being visible (its consumers —
 * the seek bar and live badge — all render inside the auto-hiding overlay) —
 * see [liveWindowRefreshLoop], the shared loop shape both the gating and the
 * cadence come from.
 */
const val LIVE_WINDOW_REFRESH_TICK_MS = 500L

/**
 * The live player's DVR-window refresh loop: call [onTick] every [tickMs]
 * while [active] holds. An [EnginePositionTicker]-style shared ticker — same
 * `while(active) { work; delay }` vocabulary — hoisted so the live screen's
 * composition keeps only the effect shell (`LaunchedEffect`) and the tick
 * body, and the loop's gating semantics (re-check [active] BEFORE the first
 * tick, so a launched-but-inactive window no-ops) live in exactly one place.
 */
suspend fun liveWindowRefreshLoop(
    active: () -> Boolean,
    tickMs: Long = LIVE_WINDOW_REFRESH_TICK_MS,
    onTick: () -> Unit,
) {
    while (active()) {
        onTick()
        delay(tickMs)
    }
}
