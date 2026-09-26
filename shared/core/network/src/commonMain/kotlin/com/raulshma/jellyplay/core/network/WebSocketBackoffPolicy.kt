package com.raulshma.jellyplay.core.network

import kotlin.random.Random

/**
 * The one WebSocket-reconnect backoff law for the module.
 *
 * The identical exponential schedule previously lived twice —
 * [com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient]
 * (with additive jitter) and
 * [com.raulshma.jellyplay.core.network.realtime.ActivityLogRealtimeChannel]
 * (deterministic) — as copy-pasted formulas that could drift apart. Both
 * now own an instance of this class; the only per-caller choice is the
 * jitter source:
 *
 *  - attempt n (1-based) waits `BASE_DELAY_MS * 2^(n-1)` — the exponent
 *    saturates at [SHIFT_CAP] — plus the jitter source's additive
 *    contribution, capped at [MAX_DELAY_MS];
 *  - attempts outside `1..maxAttempts` return `null`: the caller's signal
 *    to leave the fast schedule (the client's slow background retry, the
 *    channel's REST polling fallback).
 *
 * Instances are immutable and safe to share across threads.
 */
class WebSocketBackoffPolicy(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val baseDelayMs: Long = BASE_DELAY_MS,
    val maxDelayMs: Long = MAX_DELAY_MS,

    /**
     * Additive jitter (ms) applied to the exponential term before the
     * ceiling. Default zero — the deterministic schedule.
     */
    private val jitterMs: () -> Long = { 0L },
) {

    /**
     * Delay before reconnect `attempt`, or `null` once past [maxAttempts]
     * (or below the first attempt).
     */
    fun delayMs(attempt: Int): Long? {
        if (attempt !in 1..maxAttempts) return null
        val exponential = baseDelayMs * (1L shl (attempt - 1).coerceAtMost(SHIFT_CAP))
        return (exponential + jitterMs()).coerceAtMost(maxDelayMs)
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
        const val BASE_DELAY_MS = 1_000L

        /**
         * Exponent saturation: `2^4` → the 16s step plateau. At [BASE_DELAY_MS]
         * the [MAX_DELAY_MS] ceiling never binds (the plateau sits at 16s); it
         * guards callers with a larger base.
         */
        const val SHIFT_CAP = 4
        const val MAX_DELAY_MS = 30_000L

        /**
         * The shared client's jitter shape: additive, uniform in
         * `[0, maxJitterMs]` — previously inlined as `(0..1000L).random()`.
         */
        fun uniformJitter(maxJitterMs: Long = DEFAULT_JITTER_MAX_MS): () -> Long =
            { Random.nextLong(0, maxJitterMs + 1) }

        const val DEFAULT_JITTER_MAX_MS = 1_000L
    }
}
