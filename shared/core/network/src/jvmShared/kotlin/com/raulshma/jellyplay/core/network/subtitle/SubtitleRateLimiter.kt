package com.raulshma.jellyplay.core.network.subtitle

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * Coroutine-safe **minimum-interval** rate limiter for a single subtitle
 * provider instance.
 *
 * OpenSubtitles enforces roughly 1 request/second (and 40 requests/10s/IP),
 * so hammering it with concurrent searches/keyword variants trips a 429 even
 * when each individual call is well-formed. This limiter serializes calls so
 * at least [minIntervalMs] elapses between the *start* of successive requests
 * on this provider, deferring over-eager concurrent requests rather than
 * rejecting them.
 *
 * The lock is held **across** [acquire]'s [block] (the network call): merely
 * spacing gate-passes would let a slow request A overlap request B that starts
 * ~`minIntervalMs` later, keeping two requests in flight at once — exactly what
 * trips OpenSubtitles' 1 req/s ceiling. Holding the lock across the call makes
 * the provider strictly serial, one in-flight request at a time.
 *
 * The [RetryPolicy] layer still owns exponential backoff for transient 429/5xx;
 * this limiter is a *client-side* guard that prevents most of those 429s from
 * happening in the first place (defense in depth — Wyzie/OpenSubtitles docs
 * both recommend throttling proactively rather than reacting to 429s).
 *
 * One instance per provider impl (`@Inject constructor` + `@Singleton` gives a
 * single shared limiter across the app for that provider).
 */
class SubtitleRateLimiter(private val minIntervalMs: Long) {
    private val mutex = Mutex()
    private var lastRequestAtMs: Long = 0L

    /**
     * Runs [block] under [mutex], after ensuring at least [minIntervalMs] has
     * elapsed since the last invocation *started*. The lock is held across
     * [block] itself so calls are strictly serial — at most one in flight per
     * provider instance — which is what keeps OpenSubtitles' 1 req/s ceiling
     * satisfied even when a single call is slow.
     */
    suspend fun <T> acquire(block: suspend () -> T): T = mutex.withLock {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestAtMs
        val wait = max(0L, minIntervalMs - elapsed)
        if (wait > 0) delay(wait)
        lastRequestAtMs = System.currentTimeMillis()
        block()
    }

    companion object {
        /** OpenSubtitles: ~1 req/s ceiling → 1100ms keeps us under it. */
        const val OPENSUBTITLES_MIN_INTERVAL_MS = 1100L
        /** Wyzie: no published per-second ceiling; a light spacing avoids bursts. */
        const val WYZIE_MIN_INTERVAL_MS = 250L
    }
}
