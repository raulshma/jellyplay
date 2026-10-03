package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Fields read by `HomeSettingsScreen` — the home-screen config hub (display
 * rows, Continue Watching / Next Up behavior, the home layout editor, and —
 * since the finished Appearance → HomeSettings move, PS-4 — the card-display
 * toggles: unwatched badge, watched checkmark, hide watched, external
 * ratings). Everything here projects from the single [HomeDiscoveryStore]
 * slice.
 */
@Immutable
@Serializable
data class HomeScreenPreferences(
    val homeMode: HomeMode = HomeMode.VIDEO,
    val homeHeroEnabled: Boolean = true,
    val homeBackdropEnabled: Boolean = true,
    val showClockOnHome: Boolean = false,
    val showSettingsInHomeSearch: Boolean = true,
    val hideTopHeaderOnScroll: Boolean = false,
    val continueWatchingClickBehavior: ContinueWatchingClickBehavior = ContinueWatchingClickBehavior.DETAILS,
    val hiddenCwItemIds: Set<String> = emptySet(),
    /** The series excluded from the home Next Up row (the hidden-list management screen's count). */
    val nextUpExcludedSeriesIds: Set<String> = emptySet(),
    val mergeContinueWatchingAndNextUp: Boolean = false,
    val nextUpMaxDays: Int = 0,
    val nextUpRewatching: Boolean = false,
    /** Classic (pre-Jellyfin-12) home-row semantics (#168). Default false. */
    val classicRows: Boolean = false,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val pinnedHomeSections: List<PinnedHomeSection> = emptyList(),
    /** The user's custom Discover rows (config order). */
    val discoverRows: List<DiscoverRowConfig> = emptyList(),
    val homeLayoutPresets: List<HomeLayoutPreset> = emptyList(),
    /** The card-display quartet, moved here with its rows (PS-4). */
    val showUnwatchedBadge: Boolean = true,
    val showWatchedCheckmark: Boolean = true,
    val hideWatchedItems: Boolean = false,
    val showExternalRatings: Boolean = true,
)
