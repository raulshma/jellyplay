package com.raulshma.jellyplay.desktop.player

/**
 * Pure, immutable view of one engine's observed activity — the evidence model
 * the wave-13B session harness (DesktopSessionHarness) asserts from.
 *
 * Deliberately engine-free (plain data classes + pure functions) so the
 * classification logic is unit-testable without libmpv or AWT: the recorder
 * ([EngineActivityRecorder]) fills these in live, the tests build them by
 * hand.
 *
 * Timestamps are epoch milliseconds (System.currentTimeMillis) — wall clock,
 * not monotonic — matching how the harness correlates steps with screenshots
 * in the session report.
 */
data class EngineActivitySnapshot(
    /** MediaEngine.displayName of the recorded engine (e.g. "mpv"). */
    val displayName: String,
    /**
     * Which factory branch created the engine — [SURFACE_HWND],
     * [SURFACE_SOFTWARE], [SURFACE_WID_NULL] or [SURFACE_NO_OP]. Machine fact
     * surfaced in the session-harness report.
     */
    val surface: String,
    /** Epoch ms the factory recorded this engine. */
    val createdAtMs: Long,
    /** Deduped consecutive playbackState transitions, oldest first. */
    val transitions: List<StateTransition>,
    /** True when isPlaying was ever observed true. */
    val isPlayingObserved: Boolean,
    /** Position samples (recorder cadence, ~500 ms), oldest first. */
    val positionSamples: List<PositionSample>,
) {
    /** A deduped playback-state transition. */
    data class StateTransition(
        val atMs: Long,
        val toState: String,
    )

    /** One sampled playhead reading. */
    data class PositionSample(
        val atMs: Long,
        val positionMs: Long,
        val isPlaying: Boolean,
    )

    /** True when [state] appears among the recorded transitions. */
    fun sawState(state: String): Boolean = transitions.any { it.toState == state }

    /**
     * Playhead advance across PLAYING samples only (max − min), the playback
     * liveness measure: a real mpv session on the harness clip advances this
     * by ~1 s per wall second. 0 when fewer than two playing samples exist.
     */
    fun playingAdvanceMs(): Long {
        val playing = positionSamples.filter { it.isPlaying }
        if (playing.size < 2) return 0L
        return playing.maxOf { it.positionMs } - playing.minOf { it.positionMs }
    }

    /**
     * Playhead advance (any sample) strictly after [sinceMs] — used to prove a
     * keypress took effect (e.g. the playhead freezing after SPACE paused the
     * player) rather than playback liveness.
     */
    fun advanceSinceMs(sinceMs: Long): Long {
        val after = positionSamples.filter { it.atMs > sinceMs }
        if (after.size < 2) return 0L
        return after.maxOf { it.positionMs } - after.minOf { it.positionMs }
    }

    /**
     * True when the samples prove playback was RUNNING and then flipped to
     * paused at/after [sinceMs] — the wave-14A SPACE regression gate's toggle
     * evidence. Played at some point (any playing sample) AND the most recent
     * sample after [sinceMs] reads paused. The "latest" (not "all") shape
     * tolerates a pre-key playing sample straddling the injection instant (the
     * recorder samples on a ~500 ms cadence); polling clears once a post-pause
     * sample lands.
     */
    fun pausedSince(sinceMs: Long): Boolean =
        positionSamples.any { it.isPlaying } &&
            positionSamples.lastOrNull { it.atMs > sinceMs }?.isPlaying == false

    /**
     * The harness's playback gate: the engine reported isPlaying AND the
     * playhead advanced at least [minAdvanceMs] while playing.
     */
    fun playbackVerified(minAdvanceMs: Long = DEFAULT_MIN_ADVANCE_MS): Boolean =
        isPlayingObserved && playingAdvanceMs() >= minAdvanceMs

    companion object {
        const val SURFACE_HWND = "HWND"
        const val SURFACE_SOFTWARE = "SOFTWARE"
        const val SURFACE_WID_NULL = "WID_NULL"
        const val SURFACE_NO_OP = "NO_OP"

        /** Default liveness bar: playhead moved ≥ 1 s. */
        const val DEFAULT_MIN_ADVANCE_MS = 1_000L

        /** The empty snapshot used before any engine exists. */
        val NONE = EngineActivitySnapshot(
            displayName = "",
            surface = "",
            createdAtMs = 0L,
            transitions = emptyList(),
            isPlayingObserved = false,
            positionSamples = emptyList(),
        )
    }
}
