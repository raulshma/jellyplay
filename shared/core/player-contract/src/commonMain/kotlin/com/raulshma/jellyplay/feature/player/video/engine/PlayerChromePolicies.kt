package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Player-chrome policies shared by BOTH player screens (the VOD
 * `VideoPlayerScreen` and the live `LivePlayerScreen`). They live in the
 * engine-agnostic player-contract home (beside [EnginePositionTicker]) so the
 * two screens cite ONE implementation instead of carrying byte-identical
 * copies. Every declaration is a pure function except [mirrorPlaying], which
 * is lifecycle-attached (it launches a collector into the receiver scope).
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

/**
 * The play-state MIRROR both player hosts run over their engine's `isPlaying`
 * flow: every value fans out to the host's sinks in order — the uiState
 * write, the PiP icon mirror and (VOD only) the SyncPlay forward — so the
 * host Activity can render the correct play/pause icon on the PiP window and
 * its collaborators stay fed from ONE collector instead of hand-rolled
 * per-host copies (the live host previously lacked the guard — the drift
 * this dedup closes).
 *
 * Same-value emissions are swallowed BEFORE any sink runs: a redundant
 * `isPlaying` emission must not allocate a fresh uiState copy (invalidating
 * every uiState collector) nor re-fire the collaborator forwards. The guard
 * is per-mirror: a fresh mirror always delivers its FIRST value, so a
 * re-armed mirror replays the current state to its sinks — the VOD host
 * re-arms alongside its coordinator, and its uiState sink keeps its own
 * same-value check against the live uiState for exactly that replay.
 *
 * Launches the collector as a child of the receiver's scope and returns its
 * [Job] so the host can cancel it (the VOD host cancels via its parent
 * outputs job and ignores this return; the live host relies on `onCleared`
 * tearing down `viewModelScope`).
 *
 * Sink order is load-bearing: the [sinks] array is positional (the hosts
 * document which sink is which at their call sites — e.g. the uiState write
 * must precede the collaborator forwards), not a name-keyed protocol. The
 * guard assumes nothing about the source's conflation: a plain-Flow source
 * with genuine same-value repeats would have those repeats swallowed HERE
 * (by design — sinks are idempotent mirrors), while distinct consecutive
 * values always fan out.
 */
fun CoroutineScope.mirrorPlaying(
    source: Flow<Boolean>,
    vararg sinks: (Boolean) -> Unit,
): Job = launch {
    var last: Boolean? = null
    source.collect { isPlaying ->
        if (last == isPlaying) return@collect
        last = isPlaying
        sinks.forEach { it(isPlaying) }
    }
}
