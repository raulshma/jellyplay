package com.raulshma.jellyplay.feature.home

/**
 * Deep module: a minimal TTL cache gate — the decision of whether a refresh
 * should run now, or be skipped because the data is fresh enough.
 *
 * Previously this policy was hand-rolled inline on the 1053-LOC
 * [HomeViewModel] as two mutable fields (`lastDiscoverFetchEpochMs` +
 * `discoverCacheInvalidated`) and a `now - last < TTL && !invalidated` check,
 * duplicated conceptually across the discover/calendar caches. Centralizing the
 * gate gives the TTL policy a single home and a direct test: the bug class
 * "refresh loops forever" or "cache never invalidates" becomes a one-line fix.
 *
 * Not a general cache store — it holds no data, only the freshness decision.
 * The caller owns the data and calls [markFetched] after a successful fetch,
 * [invalidate] to force the next check through (e.g. swipe-to-refresh), and
 * [shouldFetch] to gate entry.
 */
internal class TtlCacheGate(private val ttlMs: Long) {

    private var lastFetchEpochMs: Long = 0L
    private var invalidated: Boolean = true

    /** True when the data is stale (or never fetched, or invalidated since last fetch). */
    fun shouldFetch(now: Long): Boolean =
        invalidated || now - lastFetchEpochMs >= ttlMs

    /** Records a successful fetch at [now]. Clears any pending invalidation. */
    fun markFetched(now: Long) {
        lastFetchEpochMs = now
        invalidated = false
    }

    /** Forces the next [shouldFetch] to return true regardless of age. */
    fun invalidate() {
        invalidated = true
    }
}
