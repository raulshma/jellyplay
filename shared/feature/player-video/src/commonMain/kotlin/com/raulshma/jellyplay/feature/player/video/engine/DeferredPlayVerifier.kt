package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The deferred-play verify machine behind LibVlcPlayerEngine's `pendingPlay`
 * latch — the pure decision half of the libVLC load-verify fix, mirroring the
 * mpv load-verify cluster (`MpvPlayerEngine.loadVerify` + its watchdog
 * ticker's LOAD_CONFIRM_MS re-issue — the v0.11.8 black-screen fix recorded in
 * CONTEXT.md's engine layer). Extracted per the infra-machine convention
 * (StateManagementConventions §3: a pure decision object beside the latch, the
 * rules unit-testable without the engine's scopes, handlers or native player)
 * and placed beside [EngineDurationFallback] / [EnginePositionTicker] — the
 * player-video commonMain pure-policy home — so player-video jvmTest pins it
 * without libVLC (the real engine needs a native lib and is androidMain-only).
 *
 * The failure it closes: `load()` arms `pendingPlay = true` and playback starts
 * ONLY through (a) the inline attach+play arm (the video layout already
 * exists) or (b) the surface-attach listener. If the surface never attaches,
 * `mp.play()` is never issued, no libVLC event ever arrives, `playbackState`
 * stays at its pre-start value forever, and nothing errors — silent dead air.
 * The session-level guard cannot cover it: EngineEventCoordinator's
 * initial-buffering watchdog arms only on BUFFERING, a state the stranded
 * latch never reaches because play never ran. While the latch is armed the
 * machine holds a verify record (anchor + confirm window — the mpv
 * `loadVerify = request to elapsedRealtime` shape); the engine maps the record
 * onto ONE `mainHandler.postDelayed` post (the engine's existing per-media
 * scheduling idiom, already swept by resetForNextMedia's
 * `removeCallbacksAndMessages(null)`) and executes the tick's verdict: clear
 * the latch (one-shot) and publish `EngineError.Timeout` on the engine's
 * error flow, which EngineEventCoordinator's errors policy surfaces as a
 * user-visible, retryable failure (or converts to the automatic transcode
 * fallback under FORCE_DIRECT_PLAY).
 *
 * State space: Disarmed → Armed(armedAtMs) on [arm]; Armed → Disarmed on
 * [consume] (a success arm ran the play), [standDown] (the stalled-vs-stopped
 * guard: playback actually began / the player was deliberately stopped / the
 * load already surfaced its own error — the mpv START_FILE-close analog) and
 * [reset] (per-media teardown / terminal release); [evaluate] fires ONE-shot
 * [Verdict.TimedOut] when the window elapses on an armed record, disarming as
 * it fires so a late second tick is [Verdict.Quiet].
 *
 * Seams: [nowMs] is the monotonic clock (`SystemClock.elapsedRealtime` on
 * Android — wall-clock jumps must neither fake nor fizzle the window;
 * `SystemClock::elapsedRealtime` is the same clock the engines' stats guards
 * inject); [scheduleTimeout] / [cancelTimeout] are the transport posts. The
 * machine re-checks elapsed time in [evaluate] instead of trusting the post's
 * cadence: a handler post can run late under load without distorting the
 * decision, and a re-anchored record with a stale co-post stays sound — the
 * early post reads Quiet, the on-window post fires.
 *
 * DECLARED divergence from the latch it verifies: the machine can stand down
 * while the engine's `pendingPlay` stays armed (a Playing/Stopped event
 * landing before any consume) — the stop→detach→re-attach replay nuance lives
 * in the latch, NOT here; a later attach still consumes the latch, with no
 * timeout outstanding. The latch and the record only ever agree at the sites
 * the engine wires (arm with arm, clear with consume/reset); playback-began
 * and stop events deliberately split them.
 */
internal class DeferredPlayVerifier(
    /**
     * The confirm window (ms). The engine pins mpv's LOAD_CONFIRM_MS
     * precedent (3_000): a healthy attach+play completes well inside it — mpv
     * measured the whole loadfile→start-file hop at 0.5–1.2 s on green runs.
     */
    private val confirmMs: Long,
    /** Monotonic now (ms) — the anchor on [arm] and the elapsed check on [evaluate]. */
    private val nowMs: () -> Long,
    /** Schedules the timeout post for one armed record (delay = [confirmMs]). */
    private val scheduleTimeout: (delayMs: Long) -> Unit,
    /** Cancels the outstanding post (consume / standDown / reset / fire). */
    private val cancelTimeout: () -> Unit,
) {

    /**
     * The engine-executed outcome of one timeout tick — the VlcSlaveRegistration
     * verdict split: the machine decides, the engine does.
     */
    sealed interface Verdict {

        /**
         * The window elapsed on an armed record — the engine clears the latch
         * and publishes `EngineError.Timeout` on its error flow. One-shot: the
         * record is disarmed before this verdict is returned.
         */
        data object TimedOut : Verdict

        /**
         * Nothing to execute: the record was consumed / stood down / reset /
         * already fired since the post was scheduled, or the window has not
         * elapsed yet (an early or re-anchored post).
         */
        data object Quiet : Verdict
    }

    private var armedAtMs: Long? = null

    /** True while a verify record is outstanding (inspection + test seam). */
    val isArmed: Boolean get() = armedAtMs != null

    /**
     * Opens the verify record at the current clock reading and schedules the
     * timeout post. Re-arm while armed re-anchors; the engine's re-arm paths
     * all run through [reset] first (load() → resetForNextMedia()), so this is
     * defensive — and the elapsed re-check in [evaluate] keeps any stale
     * co-post Quiet regardless.
     */
    fun arm() {
        armedAtMs = nowMs()
        scheduleTimeout(confirmMs)
    }

    /**
     * A success arm consumed the latch (the inline attach+play arm, the
     * surface-attach listener) — the load verified, the record closes and the
     * timeout post cancels.
     */
    fun consume() {
        disarm()
    }

    /**
     * The stalled-vs-stopped guard's stand-down: playback actually began
     * (`Event.Playing` — this engine's START_FILE analog), the player was
     * deliberately stopped (`Event.Stopped` — a legitimately stopped player
     * must not error via the verify timeout), or the load already surfaced
     * its own error (`Event.EncounteredError` — never stack the synthetic
     * timeout behind a real one). NOT a consume: the engine's latch survives
     * these events by declaration (the replay nuance), only the verify record
     * closes. Paused needs no arm — a pause implies a prior Playing, which
     * already stood the record down.
     */
    fun standDown() {
        disarm()
    }

    /** Per-media teardown (`resetForNextMedia`, shared by [release] via its caller). */
    fun reset() {
        disarm()
    }

    /**
     * One timeout tick. Fires the one-shot [Verdict.TimedOut] exactly when a
     * record is armed and the confirm window has fully elapsed on its anchor;
     * anything else (disarmed since scheduling, window not yet elapsed) is
     * [Verdict.Quiet].
     */
    fun evaluate(): Verdict {
        val armedAt = armedAtMs ?: return Verdict.Quiet
        if (nowMs() - armedAt < confirmMs) return Verdict.Quiet
        disarm()
        return Verdict.TimedOut
    }

    /** Closes the record and cancels the outstanding post (no-op when disarmed). */
    private fun disarm() {
        if (armedAtMs == null) return
        armedAtMs = null
        cancelTimeout()
    }
}
