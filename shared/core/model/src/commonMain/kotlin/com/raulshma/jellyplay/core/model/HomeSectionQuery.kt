package com.raulshma.jellyplay.core.model

/**
 * Bundles the seven inputs to the home-sections fetch that always travel
 * together from the preferences collector in `HomeViewModel`. The value object
 * crosses the repository → network seam intact: `LibraryApiClient.getHomeSections`
 * consumes it directly instead of re-declaring each field as a positional
 * parameter, so callers don't have to keep the parameter order in sync with
 * the boundary signature.
 *
 * Lives in `core:model` (with its only type dependencies, [HomeSectionType]
 * and [PinnedHomeSection]) because it is shared by `core:data` and
 * `core:network`, whose sole common ancestor is this module — same precedent
 * as [TtlCache]/[CacheIdentity].
 *
 * Defaults mirror the repository/network defaults so a query built from only
 * the enabled sections (the common case outside the home screen — TV Watch
 * Next, UserDataSyncWorker, widget workers) reads the same as before.
 */
data class HomeSectionQuery(
    val enabledSections: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
    val nextUpRewatching: Boolean = false,
    val nextUpMaxDays: Int = 0,
    val nextUpExcludedSeriesIds: Set<String> = emptySet(),
    val hiddenCwItemIds: Set<String> = emptySet(),
    val pinnedSections: List<PinnedHomeSection> = emptyList(),
    /**
     * The user's configured Discover rows (all sources; list order is the
     * within-block render order). Only ENABLED JELLYFIN rows are fetched by
     * the home fetcher — Seerr rows ride the feature-layer discover path and
     * are spliced in at the DISCOVER block position afterwards.
     */
    val discoverRows: List<DiscoverRowConfig> = emptyList(),
    /**
     * Classic (pre-Jellyfin-12) home-row semantics (#168), true 1:1 with the
     * pre-12 wire results: Continue Watching keeps the exact pre-12 request
     * (the Series/Season resume rollups a 12.x server reports are dropped by
     * a client-side fold), and TV latest rows re-run the 10.x grouping
     * client-side over a raw-Episode pool — a series with several recent
     * episodes becomes a Series card, a single one stays its Episode card,
     * ordered by episode recency (grouping stops where 10.x's own row-limit
     * break did — an episode arriving after the row filled never joins its
     * group). False (default) = modern server
     * behavior, unchanged. Rides [cacheKey] so a flip re-fetches.
     */
    val classicRows: Boolean = false,
) {
    /**
     * Structural fingerprint of the query params, used as the `cacheKey` for both
     * the in-memory home-sections cache and the Room-backed SWR snapshot. Lives
     * here so a new query field only needs to be added in one place — the value
     * object — rather than threaded through every signature that derives a key.
     *
     * Memoized per instance (all fields are immutable vals); `copy()` produces
     * a fresh instance, and with it a fresh key.
     */
    fun cacheKey(): String = cachedKey

    private val cachedKey by lazy {
        "${enabledSections.sortedBy { it.name }}|$libraryHomeSectionOverrides|$nextUpRewatching|$nextUpMaxDays|$nextUpExcludedSeriesIds|$hiddenCwItemIds|$pinnedSections|$discoverRows|$classicRows"
    }
}
