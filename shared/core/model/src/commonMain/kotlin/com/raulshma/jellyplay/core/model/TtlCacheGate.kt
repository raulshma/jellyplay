package com.raulshma.jellyplay.core.model

/**
 * The ONE TTL freshness gate beside [TtlCache] — the decision of whether a
 * refresh should run now, or be skipped because the data is fresh enough. It
 * holds no data, only the freshness decision: the caller owns the data and
 * calls [markFetched] after a successful fetch, [invalidate] to force the next
 * check through (e.g. swipe-to-refresh), and [shouldFetch] to gate entry.
 *
 * Where [TtlCache] memoises payloads, this gate memoises only the
 * "fetched-at" stamp, so a refresh body can stay readable instead of growing
 * two mutable fields (`lastFetchEpochMs` + a pending-invalidation flag) and a
 * `now - last < TTL && !invalidated` check per concept. Giving the TTL policy
 * a single home and a direct test means the bug class "refresh loops forever"
 * / "cache never invalidates" is a one-line fix in one file.
 *
 * Thread-safety regime: the two fields are `@Volatile` so the gate is honest
 * when read/written from fetch coroutines off a single-threaded dispatcher
 * (the core:network home-fan-out reads it from the caller's dispatcher).
 * Racing fetchers may both pass [shouldFetch] and both stamp — benign (both
 * writes are valid fresh results; last writer wins), the same
 * not-single-flight doctrine as [TtlCache] itself.
 *
 * Lives in `core:model` for the same reason [TtlCache] does: the feature
 * (home's discover grid) and core:network's home-sections fetcher sit in
 * layers that both already depend on `core:model`, so one implementation
 * serves both and neither keeps a ported twin.
 *
 * Passive-`now` API like [HomeFreshness.isRoomSnapshotFresh]: callers that
 * already hold a timestamp pass it explicitly ([shouldFetch]/[markFetched]
 * default to the injected [clock] otherwise). One caveat the interface
 * carries: a gate's stamps and checks must share ONE time base — a gate
 * stamped with wall-clock epoch millis must be checked with wall-clock epoch
 * millis (feature/home's discover grid does exactly that), the monotonic
 * [clock] default serves callers that omit [now] on both sides (the
 * network-layer Seerr-rows gate).
 *
 * @param ttlMs  time-to-live in milliseconds between [markFetched] stamps.
 * @param clock  monotonic time source; defaults to [monotonicNowMillis]
 *               (`SystemClock.elapsedRealtime` on Android, `System.nanoTime`
 *               on desktop). Injectable for unit tests.
 */
public class TtlCacheGate(
    private val ttlMs: Long,
    private val clock: () -> Long = ::monotonicNowMillis,
) {

    @Volatile
    private var lastFetchEpochMs: Long = 0L

    @Volatile
    private var invalidated: Boolean = true

    /** True when the data is stale (or never fetched, or invalidated since last fetch). */
    public fun shouldFetch(now: Long = clock()): Boolean =
        invalidated || now - lastFetchEpochMs >= ttlMs

    /** Records a successful fetch at [now]. Clears any pending invalidation. */
    public fun markFetched(now: Long = clock()) {
        lastFetchEpochMs = now
        invalidated = false
    }

    /** Forces the next [shouldFetch] to return true regardless of age. */
    public fun invalidate() {
        invalidated = true
    }
}
