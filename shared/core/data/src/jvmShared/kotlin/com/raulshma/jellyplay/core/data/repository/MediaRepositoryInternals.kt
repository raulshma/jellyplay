package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.session.SessionScopedCache
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.FreshnessCeilings
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
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
     * The LibraryApiClient family seam: the detail cluster's fetch paths call
     * getMediaDetail/getSimilarItems/getAlbumTracks/getThemeSongs through it,
     * exactly as they did when the group lived inside [MediaRepositoryImpl]
     * (the PlaybackRepositoryImpl ctor precedent — all four fetches are
     * LibraryApiClient members, so the family single composes the same impl
     * the former union delegated to).
     */
    apiClient: LibraryApiClient,
    homeSession: HomeSession,
    /**
     * The single home for identity reactions (see [SessionCacheRegistry]):
     * the group's four member caches each register their own identity action
     * through their [SessionScopedCache] chassis, so the registry must reach
     * the group at construction.
     */
    sessionCacheRegistry: SessionCacheRegistry,
) {
    /**
     * The detail screen's item-scoped cache group. Declared divergence
     * (facade split): constructed here instead of inside
     * [MediaRepositoryImpl] so [PlaylistRepositoryImpl] shares the instance;
     * the class body below is byte-identical to the one that moved.
     */
    val detailCaches = DetailCacheGroup(apiClient, homeSession, sessionCacheRegistry)
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
 * to its caller but never written back (the members'
 * [SessionScopedCache.getOrFetch] write guard and the shared injected
 * [epoch]) — a slow fetch that raced a user-data invalidation must not pin
 * the pre-mutation snapshot for the full TTL. Per-item invalidations bump
 * exactly once (the item's own shapes evict under the one bump); the
 * wholesale `invalidateAll` bumps once per member cache — monotonic, so the
 * veto only ever strengthens.
 *
 * ## Identity
 *
 * Every get/put/remove goes through the identity-keyed [SessionScopedCache]
 * surface (fetch paths read [HomeSession.cacheIdentity], the suspend
 * source-flow read; best-effort evictions read
 * [HomeSession.cacheIdentitySnapshot]) so a wrong identity is a guaranteed
 * miss by construction. Each member cache registers its own identity action
 * (epoch bump + wholesale clear) through the chassis; the ONE [epoch] is
 * injected into all four so a single bump stall-guards every member's
 * writers.
 */
internal class DetailCacheGroup(
    private val apiClient: LibraryApiClient,
    private val homeSession: HomeSession,
    sessionCacheRegistry: SessionCacheRegistry,
) {

    // The ONE epoch of the group (verbatim the pre-chassis detailCacheEpoch):
    // injected into every member's SessionScopedCache so invalidateItem's /
    // invalidateAll's single bump vetoes writes across the whole group —
    // a slow fetch that raced a user-data invalidation must not pin the
    // pre-mutation snapshot for the full TTL, whichever member it was
    // fetching into. Declared delta over the former registration (unobservable
    // beyond the veto itself): the identity transition bumps this epoch once
    // per member action (four bumps) instead of once — the veto is monotonic,
    // so more bumps reject strictly more stale writes and nothing else.
    private val epoch = AtomicLong(0L)

    // The identity supplier the four chassis share: the source-flow read (the
    // mirror lags a switch by a dispatch, which would key fetches under the
    // previous identity).
    private val identity: suspend () -> CacheIdentity = { homeSession.cacheIdentity() }

    private val detailCache = SessionScopedCache<MediaDetail>(
        owner = "media-detail",
        maxSize = DETAIL_CACHE_MAX_ENTRIES,
        ttlMs = FreshnessCeilings.DETAIL_TTL_MS,
        epoch = epoch,
        registry = sessionCacheRegistry,
        identity = identity,
    )

    private val similarCache = SessionScopedCache<List<MediaItem>>(
        owner = "media-similar-items",
        ttlMs = FreshnessCeilings.DETAIL_TTL_MS,
        epoch = epoch,
        registry = sessionCacheRegistry,
        identity = identity,
    )
    private val albumTracksCache = SessionScopedCache<List<MediaItem>>(
        owner = "media-album-tracks",
        ttlMs = FreshnessCeilings.DETAIL_TTL_MS,
        epoch = epoch,
        registry = sessionCacheRegistry,
        identity = identity,
    )

    // Theme songs for a detail item: ThemeMusicPlayer releases its player on
    // screen exit, so every detail re-entry re-fetched the (almost always
    // empty) list — one HTTP round-trip per navigation for nothing. Cached
    // with the same 2-minute TTL and epoch-guarded write as its siblings; a
    // stale list ≤2 min after a server change is harmless for ambient audio.
    private val themeSongsCache = SessionScopedCache<List<MediaItem>>(
        owner = "media-theme-songs",
        ttlMs = FreshnessCeilings.DETAIL_TTL_MS,
        epoch = epoch,
        registry = sessionCacheRegistry,
        identity = identity,
    )

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
        // No force on the chassis read: the group invalidation above already
        // evicted the item's companion shapes (similar/tracks bump-guarded),
        // which a chassis-level force could not express.
        return detailCache.getOrFetch(itemId) {
            apiClient.getMediaDetail(itemId)
        }
    }

    /** Similar items — limit-shaped key, epoch-guarded write. */
    suspend fun similarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        similarCache.getOrFetch(similarGetKey(itemId, limit)) {
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
        return albumTracksCache.getOrFetch(tracksKey(albumId)) {
            apiClient.getAlbumTracks(albumId)
        }
    }

    /** Theme songs — epoch-guarded write. */
    suspend fun themeSongs(itemId: String): Result<List<MediaItem>> =
        themeSongsCache.getOrFetch(themesKey(itemId)) {
            apiClient.getThemeSongs(itemId)
        }

    /** Best-effort pre-eviction read of the cached detail (snapshot identity). */
    fun cachedDetail(itemId: String): MediaDetail? =
        detailCache.get(homeSession.cacheIdentitySnapshot(), itemId)

    // ── Invalidation ───────────────────────────────────────────────────────

    /**
     * Drops EVERY cached shape of one item: the detail snapshot, every limit
     * variant of its similar items (prefix evict), and its theme songs —
     * plus the epoch bump that stall-guards in-flight writers. ONE bump
     * (through the detail entry's chassis invalidate) covers all three
     * evictions; the similar/themes removes are bump-less on purpose.
     */
    fun invalidateItem(itemId: String) {
        val snapshotIdentity = homeSession.cacheIdentitySnapshot()
        detailCache.invalidate(snapshotIdentity, itemId)
        similarCache.removeByKeyPrefix(snapshotIdentity, similarEvictAllLimitsPrefix(itemId))
        themeSongsCache.removeByKeyPrefix(snapshotIdentity, themesKey(itemId))
    }

    /**
     * Drops the album's cached track list plus the epoch bump that
     * stall-guards in-flight writers — [albumTracks]'s force lever, the
     * same shape [invalidateItem] gives [detail]. ([invalidateUserData]
     * keeps its direct, bump-less remove: it follows up with
     * [invalidateItem], which bumps.)
     */
    fun invalidateAlbumTracks(albumId: String) {
        albumTracksCache.invalidate(homeSession.cacheIdentitySnapshot(), tracksKey(albumId))
    }

    /**
     * Wholesale drop of the detail/similar/themes trio plus the epoch bump
     * (one per member chassis — monotonic, see the epoch-semantics KDoc).
     * This (not [clearAll]) is ALSO the identity-transition reaction: each
     * member's chassis registers this section as its own registry action.
     */
    fun invalidateAll() {
        detailCache.invalidateAll()
        similarCache.invalidateAll()
        themeSongsCache.invalidateAll()
    }

    /**
     * The composite "user data for [itemId] changed" eviction. Returns the
     * cached detail read BEFORE the drop (the caller's series-discovery
     * input — it must be captured before [invalidateItem] removes it),
     * removes the item's album tracks (a direct remove, not part of
     * [invalidateItem]), then runs the full per-item invalidation.
     */
    fun invalidateUserData(itemId: String): MediaDetail? {
        val snapshotIdentity = homeSession.cacheIdentitySnapshot()
        val cached = detailCache.get(snapshotIdentity, itemId)
        albumTracksCache.remove(snapshotIdentity, tracksKey(itemId))
        invalidateItem(itemId)
        return cached
    }

    /**
     * Wholesale drop for [MediaRepositoryImpl.invalidateCaches] (the
     * background sync-worker path): [invalidateAll] plus the album-tracks
     * cache — the two wholesale callers each clear every member exactly
     * once (the identity path reaches album tracks through its own chassis
     * registration instead). Declared resequencing, unobservable: the former
     * body cleared albumTracks AFTER the episode catalogue's invalidateAll();
     * the member caches are independent (no cross-cache read exists) and
     * each is cleared exactly once, so ordering within the drop cannot
     * matter — a racing getAlbumTracks put-after-clear was equally possible
     * before.
     */
    fun clearAll() {
        invalidateAll()
        albumTracksCache.invalidateAll()
    }
}
