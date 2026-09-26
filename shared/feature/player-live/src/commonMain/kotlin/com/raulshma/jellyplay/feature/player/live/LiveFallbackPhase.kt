package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState

/**
 * The live engine's error/fallback phase machine — the ONE decision core for
 * "can this failing tune still fall back to transcode, and what may the
 * follow-up player callbacks publish". Extracted verbatim from
 * `ExoLiveEngine`'s former hand-rolled `ErrorPhase` enum (previously the
 * separate `errorTerminal` + `fallbackInvoked` booleans), whose four subtleties
 * the androidMain engine can no longer drift on:
 *
 *  1. **Per-load reset** ([onLoadStarted]) — a previous channel's fallback
 *     latch must not carry over, otherwise a reused engine (live re-loads the
 *     SAME engine per zap) could never fire the transcode fallback for a new
 *     channel after one fallback fired on the old.
 *  2. **IDLE-gated fallback** ([onDirectError]) — a direct/direct-stream
 *     error fires the transcode re-resolve at most once per load; ExoPlayer
 *     can raise `onPlayerError` repeatedly during a rebuffer storm and each
 *     repeat must not re-trigger the resolve (or surface a spurious ERROR).
 *  3. **Stay-BUFFERING during fallback** ([DirectError.InvokeFallback]) —
 *     while the ViewModel re-resolves to transcode, the engine holds
 *     BUFFERING so the error overlay does not flash for a frame before the
 *     fallback reload clears it. If the fallback reload also fails, that
 *     error surfaces via the terminal arm instead.
 *  4. **TERMINAL latch** ([onStateChanged] returning `null`) — after a
 *     terminal error ExoPlayer emits a follow-up `STATE_IDLE`; masking it
 *     keeps ERROR published (which would otherwise flip back to the spinner
 *     and hide the error dialog). The latch clears on the next
 *     [onLoadStarted] only.
 *
 * The engine owns only the media3 application: it maps the raw
 * `PlaybackException`/playback-state ints, writes what the decisions declare
 * to its state flows, and invokes its fallback callback when
 * [DirectError.InvokeFallback] says so. Whether a fallback exists at all
 * (the current play method is not already transcode) is engine state — the
 * engine passes it as [onDirectError]'s `fallbackAvailable`.
 *
 * The phase itself is intentionally unobservable: every transition is pinned
 * through the decisions it produces ([LiveFallbackPhaseTest]), the same way
 * the shared MpvEventFold pins its latch machine.
 */
class LiveFallbackPhase {

    /** IDLE → (fallback available) FALLING_BACK → (retry failed) TERMINAL. */
    private enum class Phase { IDLE, FALLING_BACK, TERMINAL }

    /**
     * What a direct/direct-stream `onPlayerError` should do. [stateToPublish]
     * is the state the engine writes — BUFFERING while the fallback
     * re-resolves (arm 3 above), ERROR once none is left.
     */
    sealed interface DirectError {
        /** The state the engine publishes for this error. */
        val stateToPublish: LiveEngineState

        /**
         * The one fallback this load gets: hold BUFFERING and invoke the
         * transcode re-resolve exactly once.
         */
        data object InvokeFallback : DirectError {
            override val stateToPublish = LiveEngineState.BUFFERING
        }

        /**
         * No fallback available (already on transcode, or the fallback retry
         * also failed): publish ERROR and latch TERMINAL.
         */
        data object Terminal : DirectError {
            override val stateToPublish = LiveEngineState.ERROR
        }
    }

    @Volatile
    private var phase: Phase = Phase.IDLE

    /**
     * A new load started: clear any latch from the previous channel so a
     * reused engine can re-fire the fallback (arm 1 above).
     */
    fun onLoadStarted() {
        phase = Phase.IDLE
    }

    /**
     * A direct/direct-stream playback error. [fallbackAvailable] is false
     * once the engine is already on the transcode method — there is nothing
     * left to fall back to.
     */
    fun onDirectError(fallbackAvailable: Boolean): DirectError {
        if (phase == Phase.IDLE && fallbackAvailable) {
            phase = Phase.FALLING_BACK
            return DirectError.InvokeFallback
        }
        // No fallback available (already on transcode, or already retried, or
        // the retry itself failed) — latch so the follow-up STATE_IDLE does
        // not mask the error.
        phase = Phase.TERMINAL
        return DirectError.Terminal
    }

    /**
     * A player state change about to be published. Returns the state to
     * publish, or `null` to MASK it — the TERMINAL latch swallowing the
     * follow-up STATE_IDLE (arm 4 above). The engine's position/window
     * refresh is not part of the decision and always runs.
     */
    fun onStateChanged(state: LiveEngineState): LiveEngineState? =
        if (phase == Phase.TERMINAL) null else state
}
