package com.raulshma.jellyplay.feature.player.video.chrome

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
 * Backward step-seek target (button / keyboard / D-pad commit path): floor at
 * zero, no upper clamp.
 */
fun seekBackTargetMs(currentPositionMs: Long, stepMs: Long): Long =
    (currentPositionMs - stepMs).coerceAtLeast(0L)

/**
 * Forward step-seek target. Live streams report no duration (`0`) until
 * resolved, which would pin every forward seek to 0 via the upper clamp, so
 * the clamp is skipped when there is no known duration — the engine clamps on
 * its own at seek time. Semantically a sibling of the gesture path's
 * [com.raulshma.jellyplay.feature.player.video.state.GestureSeekMath.seekTarget],
 * but a separate policy: gestures cap the per-gesture delta for live streams,
 * the step path has no cap and clamps direction-asymmetrically.
 */
fun seekForwardTargetMs(currentPositionMs: Long, stepMs: Long, durationMs: Long): Long =
    if (durationMs <= 0L) {
        (currentPositionMs + stepMs).coerceAtLeast(0L)
    } else {
        (currentPositionMs + stepMs).coerceAtMost(durationMs)
    }

/**
 * Direction-folded step target behind the ViewModel's `seekByStep` funnel —
 * the single owner of the discrete skip-step path shared by the screen's
 * skip buttons / keyboard / D-pad commits and the PiP transport's SKIP
 * actions. [direction] < 0 steps back ([seekBackTargetMs]); anything else
 * steps forward ([seekForwardTargetMs]). The gesture/hold paths do NOT go
 * through here.
 */
fun stepSeekTargetMs(
    direction: Int,
    currentPositionMs: Long,
    stepMs: Long,
    durationMs: Long,
): Long =
    if (direction < 0) seekBackTargetMs(currentPositionMs, stepMs)
    else seekForwardTargetMs(currentPositionMs, stepMs, durationMs)

// ── Modifier-stepped keyboard seek (VLC's arrow map) ─────────────────────
// The hardware-keyboard layer's per-press seek magnitudes. Plain arrows keep
// the user's configured jump; the modifier rows are VLC's
// (VideoPlayerActivity.kt:1288–1317): Shift = fine, Ctrl = coarse,
// Shift+Ctrl = mid, Alt+Ctrl = very coarse. Home/End take a small step and
// PgUp/PgDn the big one (jellyfin-media-player's semantics). The key→row
// mapping lives in player-video's PlayerKeyPolicy.mediaKeySeek — this file
// owns only the ms math, which is why the two live apart.

/** Shift+arrow seek step. */
const val KEY_SEEK_STEP_FINE_MS = 5_000L

/** Ctrl+arrow seek step. */
const val KEY_SEEK_STEP_CTRL_MS = 60_000L

/** Shift+Ctrl+arrow seek step (VLC's mid step — takes precedence over the single-modifier rows). */
const val KEY_SEEK_STEP_SHIFT_CTRL_MS = 30_000L

/** Alt+Ctrl+arrow seek step. */
const val KEY_SEEK_STEP_ALT_CTRL_MS = 300_000L

/** Home/End small step (jellyfin-media-player semantics). */
const val KEY_SEEK_STEP_HOME_END_MS = 10_000L

/** PgUp/PgDn big step (same magnitude as Alt+Ctrl, direction-fixed per key). */
const val KEY_SEEK_STEP_PAGE_MS = 300_000L

/**
 * The modifier fold over the table above. Row precedence is VLC's: the
 * two-modifier rows are checked before the single-modifier ones (Shift+Ctrl
 * is 30s, never the 5s or 60s a naive first-match would yield), and a plain
 * press falls through to the caller's configured jump step.
 */
fun keyboardSeekStepMs(
    isShiftPressed: Boolean,
    isCtrlPressed: Boolean,
    isAltPressed: Boolean,
    configuredStepMs: Long,
): Long =
    when {
        isShiftPressed && isCtrlPressed -> KEY_SEEK_STEP_SHIFT_CTRL_MS
        isAltPressed && isCtrlPressed -> KEY_SEEK_STEP_ALT_CTRL_MS
        isShiftPressed -> KEY_SEEK_STEP_FINE_MS
        isCtrlPressed -> KEY_SEEK_STEP_CTRL_MS
        else -> configuredStepMs
    }

/**
 * The keyboard seek chip's commit debounce: the seek commits this long after
 * the LAST seek key-down. Key-up is not reliably observable (the desktop
 * deterministic bridge delivers KeyDown only), so the D-pad's commit-on-release
 * is mirrored with a restart-on-every-press delay instead — held-and-repeating
 * keys keep re-arming it, and the chip commits once after the final repeat.
 */
const val KEYBOARD_SEEK_COMMIT_DELAY_MS = 500L

/**
 * Whether the auto-hide timer may be scheduled at all: controls must be
 * visible with no seek gesture, open sheet, or overflow menu in progress, and
 * on non-TV a controls layer holding focus (the user is actively using the
 * controls) suppresses the hide entirely.
 */
fun shouldScheduleControlsAutoHide(
    showControls: Boolean,
    isSeeking: Boolean,
    isSheetOpen: Boolean,
    isOverflowMenuOpen: Boolean,
    isTv: Boolean,
    controlsHasFocus: Boolean,
): Boolean =
    showControls && !isSeeking && !isSheetOpen && !isOverflowMenuOpen &&
        (isTv || !controlsHasFocus)

/**
 * Whether a play/pause transport toggle may SUMMON the control overlay — the
 * The pref's decision (jellyfin-androidtv #3924: pausing should not summon the
 * control overlay). [isPause] is the toggle's DIRECTION: the play arm keeps
 * today's summon (the option is "don't summon on pause", not "never show"),
 * and the pause arm is suppressed exactly when [hideOsdOnPause] is on.
 *
 * Strictly a SUMMONS gate, not a visibility freeze: an overlay already
 * visible when the user pauses stays visible — the auto-hide timeout
 * ([shouldScheduleControlsAutoHide]) continues to own dismissal, and
 * re-pressing play re-summons as before.
 */
fun shouldSummonControlsOnPause(
    isPause: Boolean,
    hideOsdOnPause: Boolean,
): Boolean = !(isPause && hideOsdOnPause)

/**
 * The PiP-transition teardown gate: a player screen disposed while the host
 * is in (or mid-transition into) PiP must NOT mutate the host window.
 * PlayerActivity SHOWS the system bars on PiP entry on purpose (forcing the
 * relayout that anchors the gesture-nav handle at the bottom) — the VOD
 * session's focus guard skips the immersive re-hide for exactly that reason
 * (PlayerWindowSessionEffects's `snapshotFlow(isWindowFocused)` block) — so a
 * teardown firing behind the PiP window would clobber that state, re-showing
 * bars and resetting orientation under a floating PiP window.
 *
 * Reads the host's SYNCHRONOUSLY-CURRENT PiP flag
 * (`PlayerWindowOps.isInPipMode` on Android) — a collected uiState lags a
 * frame and reads stale mid-transition. The engine handoff pairs with the
 * same gate (VOD defers the engine release in its dispose effect's
 * `else if (!currentlyInPip)` arms; live's PiP-dismiss collector owns the
 * exit). The VOD dispose still folds the gate inline (its arms also split the
 * background-cast handoff); adopting the shared fold there is deferred — the
 * live screen cites this ONE implementation today.
 */
fun shouldRestoreHostWindowOnDispose(currentlyInPip: Boolean): Boolean = !currentlyInPip

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
 * The live player's single seek-step constant: both seek entry points (the
 * bottom-bar seek buttons and the D-pad stepping inside the seek bar) route
 * `position ± step` through [seekBackTargetMs]/[seekForwardTargetMs], and the
 * step they feed those policies is this ONE value — the live player has no
 * step preference (the VOD player sources its step from playback prefs), so
 * the constant lives beside the policies it parameterizes instead of being
 * re-declared per call site.
 */
const val LIVE_SEEK_STEP_MS = 10_000L

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
