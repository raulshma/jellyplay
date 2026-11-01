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
    /** The hero block's presentation: carousel, slideshow, detailed spotlight or TV slider. Default CAROUSEL. */
    val homeHeroStyle: HomeHeroStyle = HomeHeroStyle.CAROUSEL,
    /** The slideshow hero's auto-advance pacing in seconds (3–60). Default 8. */
    val homeHeroSlideshowIntervalSec: Int = 8,
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
    /** Time-aware greeting chip in the top dock. Default off. */
    val homeGreetingEnabled: Boolean = false,
    /** Hides the per-row "See All" pills. Default off. */
    val homeHideSeeAll: Boolean = false,
    /** The poster rows' card width scale. Default DEFAULT. */
    val homeCardScale: HomeCardScale = HomeCardScale.DEFAULT,
    /** The home cards' corner radius in dp (0-28). Default 12. */
    val homeCardCornerRadius: Int = 12,
    /** Whether the Video/Music mode switch renders in the top dock. Default on. */
    val homeShowModeSwitch: Boolean = true,
    /** Renders the date under the home clock. Default off. */
    val homeClockShowDate: Boolean = false,
    /** Whether the weekly newsletter banner renders on home. Default on. */
    val homeNewsletterBannerEnabled: Boolean = true,
    /** Always render the user switcher chip (even with a single user). Default off. */
    val homeAlwaysShowUserSwitcher: Boolean = false,
    /** Continue-watching progress display style. Default DEFAULT. */
    val homeCwProgressStyle: HomeCwProgressStyle = HomeCwProgressStyle.DEFAULT,
    /** Max presence chips rendered in the row (2-8). Default 4. */
    val homePresenceChipsLimit: Int = 4,
    /** Max genre chips in the genre rail (6-36). Default 24. */
    val homeGenreRailLimit: Int = 24,
    /** Persists the ask-continue dialog choice as the new click behavior. Default off. */
    val homeAskContinueRemember: Boolean = false,
    /** The section rows' one-shot entrance fade/translate. Default on. */
    val homeRowsAnimations: Boolean = true,
    /** Renders the dock capsules opaque. Default off. */
    val homeOpaqueDock: Boolean = false,
    /** The pre-section block's user order (normalized tokens; default canonical). */
    val homePreSectionOrder: List<String> = HomeDiscoveryPreferenceSpecsDefaults.PRE_SECTION_ORDER_CANONICAL,
    /** The sections whose cards render the NEW badge (default the arrival trio). */
    val homeNewBadgeSections: Set<HomeSectionType> = HomeDiscoveryPreferenceSpecsDefaults.NEW_BADGE_SECTIONS_CANONICAL,
    /** The home feed's automatic refresh cadence. Default DEFAULT. */
    val homeRefreshCadence: HomeRefreshCadence = HomeRefreshCadence.DEFAULT,
    /** The hero carousel's idle auto-rotation dwell in ms (3000-30000). Default 8000. */
    val homeHeroIdleRotationMs: Int = 8000,
    /** Whether hero rotation defers while the home list is scrolling. Default on. */
    val heroScrollDefer: Boolean = true,
    /** The discover rows' partition pattern. Default DEFAULT. */
    val homeDiscoverPattern: HomeDiscoverPattern = HomeDiscoverPattern.DEFAULT,
    /** The newsletter banner's weekday window. Default DEFAULT. */
    val homeNewsletterWindow: NewsletterBannerWindow = NewsletterBannerWindow.DEFAULT,
    /** The presence chips' foreground poll cadence in seconds (15-300). Default 60. */
    val homePresencePollSeconds: Int = 60,
    /** How long the TV trickplay peek holds before fading (1000-5000 ms). Default 2500. */
    val homeTrickplayPeekMs: Int = 2500,
    /** The home search results pane's height cap in dp (280-640). Default 400. */
    val homeSearchResultsHeightDp: Int = 400,
    /** The My Media tiles row's visible-column count (0 = the shipped chassis width). */
    val homeMyMediaColumns: Int = 0,
    /** The stats card's visible metrics (subset of played/unplayed/minutes; default all). */
    val homeStatsMetrics: Set<String> = HomeDiscoveryPreferenceSpecsDefaults.STATS_METRICS_CANONICAL,
    /** The NEW-badge arrival window in days (1-30). Default 30. */
    val homeNewBadgeDays: Int = 30,
    /** Unwatched counters in the section-row headers. Default off. */
    val homeRowUnwatchedCounters: Boolean = false,
    /** The hero starts at a random candidate instead of the first. Default off. */
    val homeHeroRandomStart: Boolean = false,
)

/**
 * The home QoL wave-3 defaults, mirrored core-side so the settings
 * projection's defaults never reference the datastore module. The
 * [HomeDiscoveryStore] slice and the [HomeScreenPreferences] projection both
 * serve these same values on absence — the canonical CSV token sets the
 * spec rows declare.
 */
object HomeDiscoveryPreferenceSpecsDefaults {
    /** The pre-section block's canonical item order — the shipped render order. */
    val PRE_SECTION_ORDER_CANONICAL: List<String> = listOf("stats_card", "genre_rail", "newsletter_banner")

    /** The sections the NEW badge renders on, as shipped — the arrival trio. */
    val NEW_BADGE_SECTIONS_CANONICAL: Set<HomeSectionType> =
        setOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED, HomeSectionType.UPCOMING)

    /** The stats card's metrics, as shipped — all three visible. */
    val STATS_METRICS_CANONICAL: Set<String> = setOf("played", "unplayed", "minutes")
}
