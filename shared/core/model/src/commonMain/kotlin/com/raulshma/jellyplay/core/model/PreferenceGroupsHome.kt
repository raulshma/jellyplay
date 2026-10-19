package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Fields read by `HomeSettingsScreen` — the home-screen config hub (display
 * rows, Continue Watching / Next Up behavior, the home layout editor, and the
 * card-display toggles: unwatched badge, watched checkmark, hide watched,
 * external ratings). Everything here projects from the single
 * [HomeDiscoveryStore]
 * slice.
 */
@Immutable
@Serializable
data class HomeScreenPreferences(
    val homeMode: HomeMode = HomeMode.VIDEO,
    val homeHeroEnabled: Boolean = true,
    /** Video hero billboard (touch/default form factors). Default off. */
    val homeHeroVideoEnabled: Boolean = false,
    /** Video hero billboard (TV form factor). Default off. */
    val homeHeroVideoTvEnabled: Boolean = false,
    val homeBackdropEnabled: Boolean = true,
    /** Focus-driven ambient backdrop (Android TV). Default off. */
    val homeFocusBackdrop: Boolean = false,
    /** Personal stats card. Default off. */
    val homeStatsCard: Boolean = false,
    /** Genre quick-access rail. Default off. */
    val homeGenreRail: Boolean = false,
    /** Watch-party / presence chips. Default off. */
    val homePresenceChips: Boolean = false,
    /** New-section announcement toast. Default on. */
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
    /** Classic (pre-Jellyfin-12) home-row semantics. Default false. */
    val classicRows: Boolean = false,
    /** Exclude items with an active playback session from Continue Watching / Next Up. Default off. */
    val excludeActiveSessions: Boolean = false,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val pinnedHomeSections: List<PinnedHomeSection> = emptyList(),
    /** The user's custom Discover rows (config order). */
    val discoverRows: List<DiscoverRowConfig> = emptyList(),
    val homeLayoutPresets: List<HomeLayoutPreset> = emptyList(),
    /** Layout DisplayPreferences auto-push. Default off. */
    val homeLayoutAutopush: Boolean = false,
    /** The card-display quartet, owned here with its rows. */
    val showUnwatchedBadge: Boolean = true,
    val showWatchedCheckmark: Boolean = true,
    val hideWatchedItems: Boolean = false,
    val showExternalRatings: Boolean = true,
    /** Quality badges on cards. Default on. */
    val showQualityBadges: Boolean = true,
    /** "NEW" badges on cards. Default on. */
    val showNewBadges: Boolean = true,
    /** Trickplay preview on wide cards (TV focus). Default on. */
    val homeTrickplayPreview: Boolean = true,
    /** Anti-spoiler home master switch. Default off. */
    val antiSpoilerHome: Boolean = false,
)
