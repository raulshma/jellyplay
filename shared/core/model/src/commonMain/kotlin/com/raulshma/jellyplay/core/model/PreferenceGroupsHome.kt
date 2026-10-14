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
    /** H7 video hero billboard (touch/default form factors). Default off. */
    val homeHeroVideoEnabled: Boolean = false,
    /** H7 video hero billboard (TV form factor). Default off. */
    val homeHeroVideoTvEnabled: Boolean = false,
    val homeBackdropEnabled: Boolean = true,
    /** H10 focus-driven ambient backdrop (Android TV). Default off. */
    val homeFocusBackdrop: Boolean = false,
    /** H17 personal stats card. Default off. */
    val homeStatsCard: Boolean = false,
    /** H18 genre quick-access rail. Default off. */
    val homeGenreRail: Boolean = false,
    /** H19 watch-party / presence chips. Default off. */
    val homePresenceChips: Boolean = false,
    /** H21 new-section announcement toast. Default on. */
    val homeNewSectionAnnouncements: Boolean = true,
    val showClockOnHome: Boolean = false,
    val showSettingsInHomeSearch: Boolean = true,
    val hideTopHeaderOnScroll: Boolean = false,
    val continueWatchingClickBehavior: ContinueWatchingClickBehavior = ContinueWatchingClickBehavior.DETAILS,
    val hiddenCwItemIds: Set<String> = emptySet(),
    /** The series excluded from the home Next Up row (the hidden-list management screen's count). */
    val nextUpExcludedSeriesIds: Set<String> = emptySet(),
    /** The libraries hidden from the My Media tiles row (the hidden-list management screen's count). */
    val myMediaHiddenLibraryIds: Set<String> = emptySet(),
    val mergeContinueWatchingAndNextUp: Boolean = false,
    val nextUpMaxDays: Int = 0,
    val nextUpRewatching: Boolean = false,
    /** Classic (pre-Jellyfin-12) home-row semantics (#168). Default false. */
    val classicRows: Boolean = false,
    /** H6: exclude items with an active playback session from Continue Watching / Next Up. Default off. */
    val excludeActiveSessions: Boolean = false,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val pinnedHomeSections: List<PinnedHomeSection> = emptyList(),
    /** The user's custom Discover rows (config order). */
    val discoverRows: List<DiscoverRowConfig> = emptyList(),
    val homeLayoutPresets: List<HomeLayoutPreset> = emptyList(),
    /** H20 layout DisplayPreferences auto-push. Default off. */
    val homeLayoutAutopush: Boolean = false,
    /** The card-display quartet, moved here with its rows (PS-4). */
    val showUnwatchedBadge: Boolean = true,
    val showWatchedCheckmark: Boolean = true,
    val hideWatchedItems: Boolean = false,
    val showExternalRatings: Boolean = true,
    /** H11 quality badges on cards. Default on. */
    val showQualityBadges: Boolean = true,
    /** H12 "NEW" badges on cards. Default on. */
    val showNewBadges: Boolean = true,
    /** H13 trickplay preview on wide cards (TV focus). Default on. */
    val homeTrickplayPreview: Boolean = true,
    /** H15 anti-spoiler home master switch. Default off. */
    val antiSpoilerHome: Boolean = false,
)
