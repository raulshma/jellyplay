package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.math.abs

/**
 * The pure admission policy behind `MediaEngineSessionPlayer`'s position-driven
 * `invalidateState()` — the same "caller passes values in, the guard owns only
 * the decision" shape as [StatsPublishGuard].
 *
 * Why a gate at all: every `invalidateState()` re-resolves the full media3
 * `State` and dispatches it to listeners plus (via the session) binder
 * transactions to system controllers — and the engine's position ticker emits
 * at the UI poll cadence (250 ms while the controls are visible, 1000 ms
 * hidden, see `pollingIntervalMs`). The session's progress readouts need a
 * fraction of that: media3's own session machinery re-dispatches position info
 * to `MediaController` clients periodically (3 s by default), and the system
 * MediaStyle notification/lockscreen extrapolates the playhead from the
 * (position, speed) snapshot it already holds — nothing on the platform
 * consumes per-tick invalidations of a smooth playhead. So position-only
 * emissions are admitted at most once per [minIntervalMs] (~1 Hz, at or above
 * every cadence the platform itself uses), while everything that is NOT a
 * smooth-playhead update stays immediate by construction:
 *
 *  - **First emission admits** — the session must stamp an initial position,
 *    never start from nothing.
 *  - **A jump admits immediately** — an emission whose position moved
 *    ≥ [jumpThresholdMs] from the last ADMITTED position is a seek or a
 *    correction, not smooth playback. Measured against the last admitted
 *    position (not the previous emission) because the gate's whole point is
 *    that most emissions never become observed state changes; the delta the
 *    outside world would see is the one from the last admitted value.
 *  - **The interval clock re-arms on EVERY admit** (jump admits included), so
 *    a seek lands instantly and normal playback resumes the ~1 Hz cadence
 *    from the seek's position — mirroring how StatsPublishGuard's cadence
 *    ticks on admit, not on publish.
 *
 * Non-position signals (play/pause, buffering, playback-state flips) never
 * pass through this gate — they ride their own collectors and invalidate
 * immediately. The gate is therefore purely a rate limiter for the playhead.
 * Since the media3 `State` re-reads the engine's live position on EVERY
 * invalidation (whatever triggered it), a play/pause/buffer invalidation
 * already stamps a fresh position at that instant — the gate needs no
 * "arm after a non-position change" input, and none is exposed.
 *
 * Deliberate trade-off: the jump test is speed-blind. Steady playback at 1x
 * advances ~[minIntervalMs]..[minIntervalMs]+poll-cadence per admitted step
 * (well under a 2 s threshold), and the interval arm dominates up to ~2x
 * speed; at 4x the cumulative advance from the last admitted position clears
 * the threshold every other tick (2 Hz); past ~8x every emission clears it and
 * the gate matches the historical invalidate-per-emission rate. That is the
 * safe direction to degrade in: fast-forward is exactly where playhead
 * extrapolation misleads and extra invalidations help, and the gate can never
 * invalidate LESS than the old always-invalidate behavior did. Scaling the
 * threshold by live speed was rejected: it would put a per-emission
 * playbackSpeed read on the hot path (mpv's speed getter is a JNI property
 * read) to save invalidations during a mode that is rare and self-correcting.
 *
 * Position events that never arrive are out of scope by construction: while
 * paused the ticker emits nothing (edge-suppressed), so the gate sees no
 * emissions to throttle or to pass — paused-state changes reach the session
 * through the immediate collectors and, for media-button-driven seeks, through
 * the automatic state re-resolution after the handler's done future.
 *
 * Threaded single-collector by contract: [admit] is called from exactly one
 * coroutine (the session player's position collector on the main looper), so
 * the mutable state needs no synchronization. The clock is injected
 * ([nowMs], monotonic) so the policy is JVM-testable without a handler.
 */
class PositionInvalidationGate(
    /** Minimum spacing between two admitted smooth-playhead emissions, in ms. */
    private val minIntervalMs: Long,
    /** Position delta from the last admitted value that counts as a seek, in ms. */
    private val jumpThresholdMs: Long,
    /** Monotonic clock (Android: `SystemClock.elapsedRealtime`). */
    private val nowMs: () -> Long,
) {
    /** Position at the last admit; `null` until the first emission is seen. */
    private var lastAdmittedPositionMs: Long? = null

    /** [nowMs] reading at the last admit (first admit included). */
    private var lastAdmitAtMs: Long = Long.MIN_VALUE

    /**
     * Whether this position emission must proceed to `invalidateState()`.
     * Admits the first emission, any emission inside the interval that jumped
     * ≥ [jumpThresholdMs] from the last admitted position, and otherwise the
     * first emission after [minIntervalMs] have elapsed since the last admit;
     * otherwise records nothing and returns false.
     */
    fun admit(positionMs: Long): Boolean {
        val now = nowMs()
        val lastAdmitted = lastAdmittedPositionMs
        val admitted = lastAdmitted == null ||
            now - lastAdmitAtMs >= minIntervalMs ||
            abs(positionMs - lastAdmitted) >= jumpThresholdMs
        if (admitted) {
            lastAdmittedPositionMs = positionMs
            lastAdmitAtMs = now
        }
        return admitted
    }
}
