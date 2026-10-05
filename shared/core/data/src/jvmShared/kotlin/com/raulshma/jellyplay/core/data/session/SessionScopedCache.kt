package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.data.concurrency.SingleFlightFetcher
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.monotonicNowMillis
import java.util.concurrent.atomic.AtomicLong

/**
 * The identity-cache chassis — ONE owner of the ritual that
 * `PlaybackRepositoryImpl`'s segments cache, `EpisodeCatalogueImpl`,
 * `DetailCacheGroup` and `MediaRepositoryImpl`'s spec caches each used to
 * hand-assemble from the same four parts:
 *
 * ```
 * TtlCache<V>(...)                          ← the entries
 * + AtomicLong epoch                        ← the write veto
 * + SingleFlightFetcher(cache, epoch)       ← the in-flight dedup
 * + registry.registerCaches / registerAction ← the identity reaction
 * ```
 *
 * Construction IS the registration: the init block registers ONE action per
 * cache under [owner] — `epoch.incrementAndGet(); cache.clear()` — so
 * entries die with the (server, user) identity on every non-`SignedIn`
 * transition (the registry skips `SignedIn` wholesale — no previous identity
 * to drop). The bump-then-clear ORDER is `SingleFlight.invalidateAll`'s and
 * `EpisodeCatalogueImpl`'s former registration verbatim, and it subsumes the
 * plain `registerCaches` list the other sites used: an in-flight fetch
 * completing after the bump has its write-back VETOED (it still returns its
 * result to its caller), and one completing between the registry's cache
 * phase and the action phase is wiped by the clear — either way the cleared
 * cache stays empty. (The plain cache-list clear was only ever the SECONDARY
 * reclaim — identity-keying is the primary invalidation; see
 * [SessionCacheRegistry].)
 *
 * Members, one per adopted need:
 *  - [getOrFetch] / [getOrFetchStorable] — the single-flight read engines
 *    (concurrent callers for one key share one fetch; a failed fetch or a
 *    `store = false` vote reaches its caller but never the cache; a fetch
 *    that raced an epoch bump is returned but not written back). [getOrFetch]'s
 *    `force` is the freshness lever: the invalidate-then-read sequence.
 *  - [get] / [put] / [remove] / [removeByKeyPrefix] — the identity-keyed
 *    surface for call sites whose choreography reads/writes the cache
 *    directly (the catalogue's merge paths, `DetailCacheGroup`'s key-grammar
 *    evictions). All identity-keyed: a wrong identity is a guaranteed miss.
 *  - [invalidate] / [invalidateAll] — bump + evict as one unit (the
 *    stall-guard for in-flight writers).
 *  - [currentEpoch] — the one read for call sites that thread the epoch into
 *    their own guarded writes (the catalogue snapshot's merge guards).
 *
 * [epoch] is injected, not owned (the [SingleFlightFetcher] doctrine): a
 * GROUP of caches sharing one invalidation stream passes one `AtomicLong` to
 * each member, so a single bump stall-guards every member's writers
 * (`DetailCacheGroup`'s shape — `invalidateItem` bumps once and evicts
 * across all four caches).
 *
 * Declared divergences — the ritual sites that deliberately stayed OUT:
 *  - `MediaRepositoryImpl.homeSectionsCache` — its writes ride the home
 *    write-generation funnel (a generation token shared with the network
 *    layer's per-row memos, bumped by the roll protocol), not an identity
 *    epoch; the funnel is the home path's own ceremony.
 *  - `WatchHistoryRepositoryImpl`'s played-items memo — deliberately TTL-less
 *    and identity-less (its KDoc argues the policy); a plain-map
 *    [com.raulshma.jellyplay.core.data.concurrency.SingleFlight] memo.
 *  - `SeerrRepositoryImpl` — a commonMain class; this module is jvmShared
 *    (the JVM `AtomicLong`/[SingleFlightFetcher] machinery), and its ritual
 *    is the minimal two-line `registerCaches("seerr", ...)` with no epoch and
 *    no flight — nothing for the chassis to absorb.
 *  - `PlaybackReportingStatusStore` — a StateFlow reset, not a TtlCache; its
 *    one `registerAction` stays hand-rolled.
 *
 * jvmShared (not commonMain): the epoch is a `java.util.concurrent`
 * `AtomicLong` and the fetcher is the jvmShared [SingleFlightFetcher].
 */
class SessionScopedCache<V : Any>(
    owner: String,
    maxSize: Int = TtlCache.DEFAULT_MAX_SIZE,
    ttlMs: Long = TtlCache.DEFAULT_TTL_MS,
    clock: () -> Long = ::monotonicNowMillis,
    private val epoch: AtomicLong = AtomicLong(0L),
    registry: SessionCacheRegistry,
    private val identity: suspend () -> CacheIdentity,
) {

    private val cache = TtlCache<V>(maxSize = maxSize, ttlMs = ttlMs, clock = clock)
    private val fetcher = SingleFlightFetcher(cache, epoch)

    init {
        // One registration per cache: bump + clear as ONE action. Splitting it
        // into a registerCaches entry (cache phase) + a bump-only action (as
        // PlaybackRepositoryImpl used to) would open the clear-then-bump window
        // where an in-flight fetch pins a stale entry that only the
        // switch-back-within-TTL corner could ever read — the combined action
        // closes it (see the class KDoc).
        registry.registerAction(owner) {
            epoch.incrementAndGet()
            cache.clear()
        }
    }

    /** Identity-keyed read (the direct-cache choreographies' hit check). */
    fun get(identity: CacheIdentity, key: String): V? = cache.get(identity, key)

    /** Identity-keyed write (the direct-cache choreographies' merge/rewrite). */
    fun put(identity: CacheIdentity, key: String, value: V) {
        cache.put(identity, key, value)
    }

    /** Identity-keyed single-entry evict, WITHOUT an epoch bump. */
    fun remove(identity: CacheIdentity, key: String) {
        cache.remove(identity, key)
    }

    /** Identity-keyed prefix evict, WITHOUT an epoch bump. */
    fun removeByKeyPrefix(identity: CacheIdentity, prefix: String) {
        cache.removeByKeyPrefix(identity, prefix)
    }

    /** The epoch read for call sites that guard their own writes against it. */
    fun currentEpoch(): Long = epoch.get()

    /**
     * The single-flight read with the force freshness lever: `force = true`
     * evicts the entry (plus the epoch bump that stall-guards in-flight
     * writers) BEFORE the read — the invalidate-then-read sequence — so a
     * failed forced fetch leaves nothing behind. [flightKey] scopes the
     * in-flight join (pass a distinct one when the same key is fetched
     * through transports that must not share results — the catalogue's
     * online-vs-offline split); the cache key is always [key].
     */
    suspend fun getOrFetch(
        key: String,
        force: Boolean = false,
        flightKey: String = key,
        fetch: suspend (epochAtStart: Long) -> Result<V>,
    ): Result<V> {
        if (force) invalidate(identity(), key)
        return fetcher.getOrFetch(identity, key, flightKey, fetch)
    }

    /**
     * [getOrFetch] with a per-flight store decision: [fetch] additionally
     * returns whether its result may be written back (the segments fallback
     * shape — a failed segments API must not cache its legacy fallback). The
     * epoch guard still applies on top of the flag.
     */
    suspend fun getOrFetchStorable(
        key: String,
        flightKey: String = key,
        fetch: suspend (epochAtStart: Long) -> Pair<Result<V>, Boolean>,
    ): Result<V> = fetcher.getOrFetchStorable(identity, key, flightKey, fetch)

    /**
     * Drops one identity-keyed entry plus the epoch bump — the
     * stall-guarded single-item invalidation.
     */
    fun invalidate(identity: CacheIdentity, key: String) {
        fetcher.invalidate(identity, key)
    }

    /**
     * Wholesale drop plus the epoch bump — the same section the identity
     * transition's action runs.
     */
    fun invalidateAll() {
        fetcher.invalidateAll()
    }
}
