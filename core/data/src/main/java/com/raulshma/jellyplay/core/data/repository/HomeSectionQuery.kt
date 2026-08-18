package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PinnedHomeSection

/**
 * Bundles the inputs to [MediaRepository.getHomeSections] that always travel
 * together from the preferences collector in `HomeViewModel`. Replaces the
 * 7-argument call so callers don't have to keep the parameter order in sync
 * with the repository signature.
 *
 * Defaults mirror [MediaRepository.getHomeSections] so a query built from only
 * the enabled sections (the common case outside the home screen — TV Watch Next,
 * UserDataSyncWorker, widget workers) reads the same as before.
 */
data class HomeSectionQuery(
    val enabledSections: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
    val nextUpRewatching: Boolean = false,
    val nextUpMaxDays: Int = 0,
    val nextUpExcludedSeriesIds: Set<String> = emptySet(),
    val hiddenCwItemIds: Set<String> = emptySet(),
    val pinnedSections: List<PinnedHomeSection> = emptyList(),
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
        "${enabledSections.sortedBy { it.name }}|$libraryHomeSectionOverrides|$nextUpRewatching|$nextUpMaxDays|$nextUpExcludedSeriesIds|$hiddenCwItemIds|$pinnedSections"
    }
}
