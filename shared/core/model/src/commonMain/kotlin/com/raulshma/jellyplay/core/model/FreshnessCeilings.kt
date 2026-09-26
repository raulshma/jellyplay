package com.raulshma.jellyplay.core.model

/**
 * The named home for every non-home cache-freshness ceiling — "how stale may
 * THIS cache be?", one named constant per cache policy, cited by each site
 * instead of a private literal.
 *
 * Before this object, ~13 cache sites each declared their own private TTL
 * constant (or inline literal), so "what is stale where" had no readable
 * answer: the values lived in `core:data` repositories and `core:network`
 * API clients that cannot see each other, and every "keep these in
 * lockstep" rule (e.g. the episode catalogue matching the detail snapshot's
 * 2 minutes) was a comment instead of code. Like [HomeFreshness] and
 * [TtlCache] before it, the canonical values live in `core:model` — the
 * common ancestor both consumers depend on. Each constant below names ONE
 * cache policy; the owning site keeps its key grammar, its identity-keying
 * and its invalidation choreography, and cites the name for its ceiling.
 *
 * Deliberately NOT here:
 *  - Home screen freshness is [HomeFreshness]'s subject (its network
 *    sub-call, repo-memory and Room-SWR TTLs plus home's refresh cadence).
 *    This object and [HomeFreshness] are siblings — one per policy family —
 *    and cross-reference each other; the values happen to coincide in places
 *    (2-minute ceilings) but are separate policies.
 *  - `ArrRepository.SERVER_CACHE_TTL_MS` is a public interface constant on
 *    the Arr surface (its consumers cite it in KDoc); moving it is an
 *    interface change, not a constant fold.
 *  - The watch-history played-items memo (`WatchHistoryRepositoryImpl`) has
 *    NO duration at all: it is a single-flight memo that lives until the
 *    next `refreshPlaybackReportingStatus()`, deliberately declared here so
 *    the "one readable answer" includes its answer being "no TTL".
 *
 * All values are IDENTICAL to the private literals they replace — this names
 * policy, it does not change it.
 */
object FreshnessCeilings {

    // ── MediaRepository / MediaRepositoryInternals ──────────────────────────

    /** Library folders (+ the genres/studios sibling caches) — 10 minutes; folders change rarely during a session. */
    const val FOLDERS_TTL_MS = 10 * 60_000L

    /** "Latest media" per library — 2 minutes; latest content should feel fresh on re-entry. */
    const val LATEST_MEDIA_TTL_MS = 2 * 60_000L

    /**
     * The detail screen's item-scoped cluster (detail snapshot, similar
     * items, album tracks, theme songs), the collection-items page cache,
     * AND the episode catalogue's series snapshots — 2 minutes, short enough
     * that server changes are reflected quickly. The catalogue's ceiling was
     * historically a hand-synced copy of this value; citing the name replaces
     * that comment with code.
     */
    const val DETAIL_TTL_MS = 2 * 60_000L

    /** Photo-folder child image URLs (player backdrop fan-out) — 5 minutes. */
    const val PHOTO_URLS_TTL_MS = 5 * 60_000L

    // ── Playback / catalogue ────────────────────────────────────────────────

    /** Media segments (intro/credit markers) — 5 minutes; segments are near-static per item. */
    const val SEGMENTS_TTL_MS = 5 * 60_000L

    /** Seerr detail cache — 60 seconds. */
    const val SEERR_TTL_MS = 60_000L

    // ── core:network API clients ────────────────────────────────────────────

    /**
     * Admin dashboard's system info — 2 minutes: long enough to dedupe the
     * parallel calls `loadDashboard()` and the independent About-screen
     * `getSystemInfo()` fire, short enough to reflect server version changes
     * promptly. (Cache is keyed by a bare server-scoped key, NOT identity —
     * see the declared exception in CONTEXT.md "Core data repositories".)
     */
    const val ADMIN_SYSTEM_INFO_TTL_MS = 2 * 60_000L

    /** Admin dashboard's library item counts — 2 minutes, same reasoning as [ADMIN_SYSTEM_INFO_TTL_MS]. */
    const val ADMIN_ITEM_COUNTS_TTL_MS = 2 * 60_000L

    /**
     * Newsletter render's cached server display name — 30 minutes: the name
     * is effectively session-static and lives below the repository layer, so
     * every newsletter render previously bypassed any cache; a long TTL keeps
     * it fresh across server renames without per-render network calls.
     */
    const val MEDIA_INFO_SERVER_NAME_TTL_MS = 30 * 60_000L

    /**
     * The favorite-flag seed cache — 15 minutes, generous: the flags only
     * seed a toggle's "current" value until the first real read refreshes
     * them, and the identity-keyed composite key already guarantees a
     * switched user never sees the previous user's flags within any window.
     */
    const val FAVORITE_FLAGS_TTL_MS = 15 * 60_000L
}
