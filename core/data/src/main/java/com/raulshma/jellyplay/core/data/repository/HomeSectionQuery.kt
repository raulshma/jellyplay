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
)
