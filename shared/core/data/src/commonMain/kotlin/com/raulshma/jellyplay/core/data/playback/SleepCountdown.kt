package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The injected tick source for [SleepCountdown]'s remaining-time math: a
 * within-process monotonic millis read. The countdown loop is delay-driven;
 * the clock only refines what "remaining" reads between wakes, so the seam
 * is what picks the host's timing model:
 *  - wall-clock (video/audio, production): the Koin owner binds the same
 *    `core.model.TimeSource` single every other clock rides —
 *    `SleepCountdownClock { timeSource.nowElapsedRealtimeMillis() }` — so a
 *    late dispatcher wake re-syncs the display to the real deadline (the
 *    exact model the former SleepTimerManager had);
 *  - step (reader): [SleepCountdownClock.perTick] advances exactly one tick
 *    per read, so remaining rides pure `delay` — coroutine virtual time can
 *    drive it and the reader suite's `advanceTimeBy` pins keep passing (the
 *    book's former hand-rolled delay-loop contract).
 */
fun interface SleepCountdownClock {

    /** Current monotonic elapsed milliseconds (deltas only, never absolute). */
    fun nowElapsedMillis(): Long

    companion object {
        /**
         * A clock that advances exactly [stepMillis] on every read. Paired
         * with [SleepCountdown.tickMillis] == [stepMillis], remaining
         * decrements by exactly one step per loop wake — the virtual-time
         * contract the reader's former delay-loop ticker gave its tests.
         */
        fun perTick(stepMillis: Long): SleepCountdownClock {
            var current = 0L
            return SleepCountdownClock {
                current += stepMillis
                current
            }
        }
    }
}

/**
 * The ONE countdown state: active flag, remaining millis (0 unless a timed
 * arm is running) and the end-of-episode mode flag. Consumers read the
 * derived [SleepCountdown.isSleepTimerActive] / [SleepCountdown.sleepTimerRemainingMs]
 * / [SleepCountdown.isEndOfEpisodeMode] projections instead of holding their
 * own aliases.
 */
data class SleepCountdownState(
    val active: Boolean = false,
    val remainingMs: Long = 0L,
    val endOfEpisodeMode: Boolean = false,
)

/**
 * The single deep countdown core behind every player sleep timer (video fade,
 * audio pause, reader stop) — the fold of the former jvmShared
 * `SleepTimerManager` (whose 6 alias pairs existed only because
 * player-audio/player-book commonMain cannot see a jvmShared impl) plus the
 * reader's hand-rolled delay-loop ticker. One core, ONE [SleepCountdownState]
 * flow, and the host-specific expiry behavior behind two callbacks:
 *  - [setOnExpiring] — the final-stretch ramp (video's volume fade, invoked
 *    with progress 1f → 0f; also 1f on cancel so hosts can restore, and 0f on
 *    natural expiry just before [setOnTimerExpired]);
 *  - [setOnTimerExpired] — the expiry action (video/audio pause, reader
 *    stop-read-aloud).
 *
 * Timing models ride the injected [SleepCountdownClock] (see its KDoc): the
 * default wall-clock binding preserves the former manager's re-sync reads,
 * while the reader's per-tick clock preserves its virtual-time contract — no
 * host changed when its countdown fires.
 *
 * Member vocabulary is the surviving (longer) half of the former alias
 * pairs: [startSleepTimer] / [startEndOfEpisodeTimer] / [cancelSleepTimer] /
 * [getSleepTimerDisplayText] / [isSleepTimerActive] / [sleepTimerRemainingMs];
 * the short halves (`start`, `startEndOfEpisode`, `cancel`, `getDisplayText`,
 * `isActive`, `remainingMs`) are gone.
 *
 * Not a Koin-constructed type per host: the shared Android/desktop session
 * single is Koin-owned (dataSessionPlaybackModule, bound to the wall-clock
 * TimeSource single); the reader constructs its own over the ViewModel scope
 * with the per-tick clock.
 */
class SleepCountdown(
    private val clock: SleepCountdownClock,
    scope: CoroutineScope? = null,
    private val tickMillis: Long = DEFAULT_TICK_MILLIS,
) {

    /** Production default matches the former manager: own Main-dispatcher scope. */
    private val countdownScope: CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(SleepCountdownState())

    /** The ONE state slice every host projects from. */
    val state: StateFlow<SleepCountdownState> = _state.asStateFlow()

    // Derived projections as mirrored StateFlows (not `map`: that yields a
    // plain Flow, and stateIn would make the first read race the sharing
    // collector under the reader's virtual-time scope). Every _state write
    // goes through [publish], so the mirrors move synchronously with it.
    private val _isSleepTimerActive = MutableStateFlow(false)
    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    private val _isEndOfEpisodeMode = MutableStateFlow(false)

    /** Survivor projection of the former `isActive` / interface `isSleepTimerActive` alias pair. */
    val isSleepTimerActive: StateFlow<Boolean> = _isSleepTimerActive.asStateFlow()

    /** Survivor projection of the former `remainingMs` / interface `sleepTimerRemainingMs` alias pair. */
    val sleepTimerRemainingMs: StateFlow<Long> = _sleepTimerRemainingMs.asStateFlow()

    val isEndOfEpisodeMode: StateFlow<Boolean> = _isEndOfEpisodeMode.asStateFlow()

    private fun publish(state: SleepCountdownState) {
        _state.value = state
        _isSleepTimerActive.value = state.active
        _sleepTimerRemainingMs.value = state.remainingMs
        _isEndOfEpisodeMode.value = state.endOfEpisodeMode
    }

    private var timerJob: Job? = null

    private var onTimerExpired: (() -> Unit)? = null
    private var onExpiring: ((Float) -> Unit)? = null

    // Host callbacks are invoked inside try/catch (`catch (_: Exception)`):
    // a throwing fade/expiry callback must not kill the countdown loop's
    // coroutine and strand the timer mid-fade — the next tick still runs and
    // the state stays truthful. Non-Exception throwables propagate (same
    // policy as the repo's runCatchingRethrowingCancellation sweeps).
    /** The expiry action — pause (video/audio) or stop-read-aloud (reader). */
    fun setOnTimerExpired(callback: (() -> Unit)?) {
        onTimerExpired = callback
    }

    /** The final-stretch ramp — video's volume fade (null for the other hosts). */
    fun setOnExpiring(callback: ((Float) -> Unit)?) {
        onExpiring = callback
    }

    /**
     * Start a timed countdown of [durationMs], ramping [onExpiring] over the
     * final [fadeOutDurationMs] (capped at half the duration; pass 0 for no
     * ramp). [durationMs] shows as the initial remaining immediately; the
     * loop then ticks every [tickMillis] (every [FADE_OUT_TICK_MS] inside the
     * ramp window) and fires reset + [onExpiring]` (0f)` + [onTimerExpired]
     * when the deadline is reached. Re-arming replaces the running countdown.
     */
    fun startSleepTimer(durationMs: Long, fadeOutDurationMs: Long = DEFAULT_FADE_OUT_DURATION_MS) {
        cancelSleepTimer()
        publish(SleepCountdownState(active = true, remainingMs = durationMs))

        val deadlineMs = clock.nowElapsedMillis() + durationMs
        val fadeStartMs = fadeOutDurationMs.coerceAtMost(durationMs / 2)

        if (durationMs <= 0L) {
            // Zero-length arms expire on the same launch tick the former
            // manager did (no initial delay), not a full [tickMillis] later.
            timerJob = countdownScope.launch { expire() }
            return
        }

        timerJob = countdownScope.launch {
            var nextDelayMs = tickMillis
            while (isActive) {
                delay(nextDelayMs)
                val remaining = (deadlineMs - clock.nowElapsedMillis()).coerceAtLeast(0L)
                publish(_state.value.copy(remainingMs = remaining))
                if (remaining <= 0L) break
                val inFadeOut = fadeStartMs > 0L && remaining <= fadeStartMs
                if (inFadeOut) {
                    try {
                        onExpiring?.invoke((remaining.toFloat() / fadeStartMs).coerceIn(0f, 1f))
                    } catch (_: Exception) {
                    }
                    nextDelayMs = FADE_OUT_TICK_MS
                } else {
                    nextDelayMs = tickMillis
                }
            }
            expire()
        }
    }

    /**
     * Arm the boundary mode: active with no countdown and no ticker — fires
     * only via [triggerEndOfEpisode].
     */
    fun startEndOfEpisodeTimer() {
        cancelSleepTimer()
        publish(SleepCountdownState(active = true, remainingMs = 0L, endOfEpisodeMode = true))
    }

    /**
     * Cancel the active arm (either mode) and reset the state. Fires
     * [onExpiring]` (1f)` so the fading host restores its pre-fade level
     * (non-fading hosts leave the callback unset).
     */
    fun cancelSleepTimer() {
        val wasActive = timerJob != null || _state.value.active
        timerJob?.cancel()
        timerJob = null
        publish(SleepCountdownState())
        if (wasActive) {
            try {
                onExpiring?.invoke(1f)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Fire the boundary pause: no-op unless an end-of-episode arm is active.
     * Cancels first (so a stray later trigger is a no-op), then invokes
     * [onTimerExpired] exactly once.
     */
    fun triggerEndOfEpisode() {
        if (_state.value.endOfEpisodeMode && _state.value.active) {
            cancelSleepTimer()
            try {
                onTimerExpired?.invoke()
            } catch (_: Exception) {
            }
        }
    }

    /** Countdown display: `""` idle, `"End of episode"` for the boundary arm, `h:mm:ss` / `m:ss` otherwise. */
    fun getSleepTimerDisplayText(): String {
        val state = _state.value
        if (!state.active) return ""
        if (state.endOfEpisodeMode) return END_OF_EPISODE_TEXT
        return formatRemainingTime(state.remainingMs)
    }

    private fun expire() {
        publish(SleepCountdownState())
        try {
            onExpiring?.invoke(0f)
            onTimerExpired?.invoke()
        } catch (_: Exception) {
        }
    }

    private fun formatRemainingTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "$hours:${twoDigits(minutes)}:${twoDigits(seconds)}"
        } else {
            "$minutes:${twoDigits(seconds)}"
        }
    }

    private fun twoDigits(value: Long): String = if (value < 10) "0$value" else "$value"

    private companion object {
        const val DEFAULT_TICK_MILLIS = 5_000L
        const val FADE_OUT_TICK_MS = 100L
        const val DEFAULT_FADE_OUT_DURATION_MS = 10_000L
        const val END_OF_EPISODE_TEXT = "End of episode"
    }
}
