package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.cache.getOrFetchGuarded
import com.raulshma.jellyplay.core.data.concurrency.SingleFlightFetcher
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.model.FreshnessCeilings
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import java.util.concurrent.atomic.AtomicLong

//  MediaRepository facade split: this file is the ONE owner of the state the
// MediaRepository family shares across implementation classes. The
// [DetailCacheGroup] cluster and its two TTL constants moved here verbatim
// from MediaRepositoryImpl.kt (same package); the only declared divergence
// is visibility — `private`/file-private widened to `internal` so the
// holder (and, through it, the extracted family impls) can reach the group.
// No method body changed; the group still has exactly one production
// instance, constructed by the [MediaRepositoryInternals] Koin single.

// Shared by [MediaRepositoryImpl] (the collection-items cache) and
// [DetailCacheGroup] below. The value used to be a local `internal` const
// here so the two sites hand-synced one number; both now cite the named
// policy `FreshnessCeilings.DETAIL_TTL_MS` (core:model), which is the same
// home the episode catalogue cites — one readable answer for "how stale may
// detail-scoped data be".
internal const val DETAIL_CACHE_MAX_ENTRIES = 30

/**
 * Internal sharing holder for the MediaRepository facade split: the ONE
 * owner of the state that [MediaRepositoryImpl] and an extracted family
 * implementation observe together. Constructed as a Koin single and
 * ctor-injected into every impl that needs it, so the cluster below stays
 * one instance across the split — a playlist edit that self-invalidates a
 * detail entry reaches the SAME epoch-guarded group the media repo's
 * getMediaDetail reads go through, never a second copy that could pin the
 * pre-edit snapshot for the TTL.
 *
 * Scope rule (do not gold-plate): only the machinery an extracted surface
 * actually observes lives here. Today that is exactly the detail-cache
 * cluster ([detailCaches]) — the playlist-edit self-invalidation path. The
 * home-sections SWR DAO, the episode catalogue, the played-state sync, the
 * stale-read ladder and the remaining TTL caches are consumed by
 * MediaRepository members only and stay on [MediaRepositoryImpl]; when a
 * future extraction needs one of those, it MOVES here (one owner per piece
 * of shared state) rather than growing a parallel copy.
 */
internal class MediaRepositoryInternals(
    /**
     * The union API client: the detail cluster's fetch paths call
     * getMediaDetail/getSimilarItems/getAlbumTracks/getThemeSongs through
     * it, exactly as they did when the group lived inside
     * [MediaRepositoryImpl]. (The extracted family impls that only FORWARD
     * take the narrow family clients instead — see the PlaybackRepositoryImpl
     * ctor precedent — but this holder keeps the union because the group's
     * fetch surface spans families.)
     */
    apiClient: JellyfinApiClient,
    homeSession: HomeSession,
) {
    /**
     * The detail screen's item-scoped cache group. Declared divergence
     * (facade split): constructed here instead of inside
     * [MediaRepositoryImpl] so [PlaylistRepositoryImpl] shares the instance;
     * the class body below is byte-identical to the one that moved.
     */
    val detailCaches = DetailCacheGroup(apiClient, homeSession)
}

/**
 * The detail screen's item-scoped cache group — the single owner of the four
 * caches that co-evict with one detail item (the detail snapshot, similar
 * items, album tracks, theme songs) plus the ONE epoch that guards every
 * write against that invalidation stream. Beside
 * [MediaRepositoryInternals] (the `EpisodeCatalogueImpl` shape in miniature: a
 * cache cluster + its invalidation choreography, constructor-injected with
 * its two collaborators); the repo's public members are one-line delegates.
 *
 * ## Key grammar — the drift this group exists to kill
 *
 * Every item-scoped key is constructed here and nowhere else. The historical
 * bug class: `getSimilarItems` stored under `similar_${id}_$limit` while the
 * invalidation removed `similar_$id` (no suffix) — a no-op that pinned every
 * limit variant for the full TTL. The grammar makes the get-key /
 * evict-prefix co-eviction structural:
 *  - similar items are LIMIT-SHAPED — the get key is derived from the
 *    eviction prefix plus `_$limit` ([similarGetKey] literally calls
 *    [similarEvictAllLimitsPrefix]), so a different limit never serves
 *    another limit's truncated list AND one prefix eviction drops every
 *    limit variant at once;
 *  - theme songs and album tracks are UNshaped (`themes_$id`,
 *    `tracks_$id`) — the prefix evict is exact-match equivalent.
 *
 * ## Epoch semantics (verbatim the pre-group `detailCacheEpoch`)
 *
 * One epoch guards every write in the group. `invalidateItem` /
 * `invalidateAll` bump it; a fetch that completes after a bump is returned
 * to its caller but never written back (the `getOrFetchGuarded` write guard
 * and [SingleFlightFetcher]'s guard share [epoch]) — a slow fetch that raced
 * a user-data invalidation must not pin the pre-mutation snapshot for the
 * full TTL. Exactly one bump per invalidation call, no more.
 *
 * ## Identity
 *
 * Every get/put/remove goes through the [TtlCache] identity overloads
 * (fetch paths read [HomeSession.cacheIdentity], the suspend source-flow
 * read; best-effort evictions read [HomeSession.cacheIdentitySnapshot]) so a
 * wrong identity is a guaranteed miss by construction.
 */
internal class DetailCacheGroup(
    private val apiClient: JellyfinApiClient,
    private val homeSession: HomeSession,
) {

    private val detailCache = TtlCache<MediaDetail>(
        maxSize = DETAIL_CACHE_MAX_ENTRIES,
        ttlMs = FreshnessCeilings.DETAIL_TTL_MS,
    )

    private val similarCache = TtlCache<List<MediaItem>>(ttlMs = FreshnessCeilings.DETAIL_TTL_MS)
    private val albumTracksCache = TtlCache<List<MediaItem>>(ttlMs = FreshnessCeilings.DETAIL_TTL_MS)

    // Theme songs for a detail item: ThemeMusicPlayer releases its player on
    // screen exit, so every detail re-entry re-fetched the (almost always
    // empty) list — one HTTP round-trip per navigation for nothing. Cached
    // with the same 2-minute TTL and epoch-guarded write as its siblings; a
    // stale list ≤2 min after a server change is harmless for ambient audio.
    private val themeSongsCache = TtlCache<List<MediaItem>>(ttlMs = FreshnessCeilings.DETAIL_TTL_MS)

    // Single-flight dedup for `detail`: the detail screen is reachable from
    // many entry points (home row tap, deep link, "play next" notification,
    // cast handshake, download resume) and TtlCache's get-check-put is not
    // atomic — two near-simultaneous entries share one flight instead of
    // firing two round-trips. The fetch semantics (caller-scope async,
    // lock-scope re-check, epoch guard, cancellation ladder) live in
    // [SingleFlightFetcher]; the epoch it shares with the guarded writes
    // above/below is [epoch].
    private val epoch = AtomicLong(0L)
    private val detailFetcher = SingleFlightFetcher(detailCache, epoch)

    // ── Key grammar (the one home of every item-scoped key) ────────────────

    /**
     * The limit-agnostic similar-items eviction prefix — a prefix of
     * [similarGetKey] for EVERY limit, so one prefix eviction drops all of
     * the item's limit variants.
     */
    private fun similarEvictAllLimitsPrefix(itemId: String) = "similar_$itemId"

    /** The per-limit similar-items get key, DERIVED from the eviction prefix. */
    private fun similarGetKey(itemId: String, limit: Int) =
        "${similarEvictAllLimitsPrefix(itemId)}_$limit"

    /** Theme-songs key — unshaped; the prefix evict is exact-match equivalent. */
    private fun themesKey(itemId: String) = "themes_$itemId"

    /** Album-tracks key — unshaped; removed by exact key. */
    private fun tracksKey(albumId: String) = "tracks_$albumId"

    // ── Accessors ──────────────────────────────────────────────────────────

    /**
     * The detail snapshot: single-flight cache-through read with the force
     * freshness lever (drop the cached entry first — the invalidate-then-read
     * sequence callers used to run by hand; the epoch bump inside
     * [invalidateItem] also guards a racing fetch from re-inserting the
     * stale snapshot).
     */
    suspend fun detail(itemId: String, force: Boolean): Result<MediaDetail> {
        if (force) invalidateItem(itemId)
        return detailFetcher.getOrFetch({ homeSession.cacheIdentity() }, itemId) {
            apiClient.getMediaDetail(itemId)
        }
    }

    /** Similar items — limit-shaped key, epoch-guarded write. */
    suspend fun similarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        similarCache.getOrFetchGuarded(
            { homeSession.cacheIdentity() },
            similarGetKey(itemId, limit),
            currentEpoch = epoch::get,
        ) {
            apiClient.getSimilarItems(itemId, limit)
        }

    /**
     * Album tracks — epoch-guarded write. [force] mirrors [detail]'s
     * freshness lever: drop the cached list (plus the epoch bump that
     * stall-guards in-flight writers) before the cache-through read. Needed
     * because [invalidateUserData] can only evict `tracks_<itemId>` — a
     * flip on a TRACK never touches the album's `tracks_<albumId>` entry,
     * so a deferred silent refresh that must show the post-flip rows cannot
     * get them without the explicit force.
     */
    suspend fun albumTracks(albumId: String, force: Boolean): Result<List<MediaItem>> {
        if (force) invalidateAlbumTracks(albumId)
        return albumTracksCache.getOrFetchGuarded(
            { homeSession.cacheIdentity() },
            tracksKey(albumId),
            currentEpoch = epoch::get,
        ) {
            apiClient.getAlbumTracks(albumId)
        }
    }

    /** Theme songs — epoch-guarded write. */
    suspend fun themeSongs(itemId: String): Result<List<MediaItem>> =
        themeSongsCache.getOrFetchGuarded(
            { homeSession.cacheIdentity() },
            themesKey(itemId),
            currentEpoch = epoch::get,
        ) {
            apiClient.getThemeSongs(itemId)
        }

    /** Best-effort pre-eviction read of the cached detail (snapshot identity). */
    fun cachedDetail(itemId: String): MediaDetail? =
        detailCache.get(homeSession.cacheIdentitySnapshot(), itemId)

    // ── Invalidation ───────────────────────────────────────────────────────

    /**
     * Drops EVERY cached shape of one item: the detail snapshot, every limit
     * variant of its similar items (prefix evict), and its theme songs —
     * plus the epoch bump that stall-guards in-flight writers.
     */
    fun invalidateItem(itemId: String) {
        epoch.incrementAndGet()
        val identity = homeSession.cacheIdentitySnapshot()
        detailCache.remove(identity, itemId)
        similarCache.removeByKeyPrefix(identity, similarEvictAllLimitsPrefix(itemId))
        themeSongsCache.removeByKeyPrefix(identity, themesKey(itemId))
    }

    /**
     * Drops the album's cached track list plus the epoch bump that
     * stall-guards in-flight writers — [albumTracks]'s force lever, the
     * same shape [invalidateItem] gives [detail]. ([invalidateUserData]
     * keeps its direct, bump-less remove: it follows up with
     * [invalidateItem], which bumps.)
     */
    fun invalidateAlbumTracks(albumId: String) {
        epoch.incrementAndGet()
        albumTracksCache.remove(homeSession.cacheIdentitySnapshot(), tracksKey(albumId))
    }

    /**
     * Wholesale drop of the detail/similar/themes trio + the epoch bump.
     * This (not [clearAll]) is the identity-transition reaction: album
     * tracks rides the registry's cache list there instead — see
     * [registryCaches].
     */
    fun invalidateAll() {
        epoch.incrementAndGet()
        detailCache.clear()
        similarCache.clear()
        themeSongsCache.clear()
    }

    /**
     * The composite "user data for [itemId] changed" eviction. Returns the
     * cached detail read BEFORE the drop (the caller's series-discovery
     * input — it must be captured before [invalidateItem] removes it),
     * removes the item's album tracks (a direct remove, not part of
     * [invalidateItem]), then runs the full per-item invalidation.
     */
    fun invalidateUserData(itemId: String): MediaDetail? {
        val identity = homeSession.cacheIdentitySnapshot()
        val cached = detailCache.get(identity, itemId)
        albumTracksCache.remove(identity, tracksKey(itemId))
        invalidateItem(itemId)
        return cached
    }

    /**
     * Wholesale drop for [MediaRepositoryImpl.invalidateCaches] (the
     * background sync-worker path): [invalidateAll] plus the album-tracks
     * cache, which the identity path clears via the registry's cache list
     * instead — the two wholesale callers each clear every member exactly
     * once. Declared resequencing, unobservable: the former body cleared
     * albumTracks AFTER the episode catalogue's invalidateAll(); the member
     * caches are independent (no cross-cache read exists) and each is
     * cleared exactly once, so ordering within the drop cannot matter —
     * a racing getAlbumTracks put-after-clear was equally possible before.
     */
    fun clearAll() {
        invalidateAll()
        albumTracksCache.clear()
    }

    /**
     * The group's contribution to the repo's `SessionCacheRegistry`
     * registration: the member caches a plain registry wholesale clear fully
     * invalidates. Only album tracks qualifies — the detail/similar/themes
     * trio's reaction needs the epoch bump (an in-flight previous-identity
     * fetch must not write back into the cleared cache), which a plain clear
     * cannot express; that trio rides the repo's `media-identity-clear`
     * ACTION ([invalidateAll]) instead.
     */
    val registryCaches: List<TtlCache<*>> get() = listOf(albumTracksCache)
}
