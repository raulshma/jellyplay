package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The appearance preference aggregates: the logical appearance domain and the
 * AppearanceSettingsScreen slice.
 */

@Immutable
@Serializable
data class AppearancePreferences(
    val dynamicTheming: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val oledMode: Boolean = false,
    val accentColorSwatch: String = "dynamic",
    val colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    val navBarShowLabels: Boolean = true,
    val homeHeroEnabled: Boolean = true,
    val homeBackdropEnabled: Boolean = true,
    val performanceMode: Boolean = false,
    val themeVariant: String = "standard",
    val synthwaveAccent: String = "magenta",
    val soothingAccent: String = "ocean",
    val vividAccent: String = "punch",
    val auroraAccent: String = "emerald",
    val sakuraAccent: String = "rose",
    val vectorPopAccent: String = "cobalt",
    val libraryViewMode: LibraryViewMode = LibraryViewMode.GRID,
)

/**
 * Fields read by `AppearanceSettingsScreen`. This is the broadest slice because
 * the Appearance screen surfaces theme, layout, library-card display,
 * newsletter, and accessibility settings together. Navigation-customization
 * fields are excluded — they live in [navigationCustomization]; the
 * home-discovery card-display quartet is excluded too — it lives (with its
 * rows) on `HomeScreenPreferences`. The card-badge toggles appear here as
 * well: the same two rows show in Appearance → Library & Cards, writing the
 * SAME home-store prefs.
 */
@Immutable
@Serializable
data class AppearanceScreenPreferences(
    val dynamicTheming: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val oledMode: Boolean = false,
    val accentColorSwatch: String = "dynamic",
    val colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    val navBarShowLabels: Boolean = true,
    val homeHeroEnabled: Boolean = true,
    val homeBackdropEnabled: Boolean = true,
    val performanceMode: Boolean = false,
    val themeVariant: String = "standard",
    val synthwaveAccent: String = "magenta",
    val soothingAccent: String = "ocean",
    val vividAccent: String = "punch",
    val auroraAccent: String = "emerald",
    val sakuraAccent: String = "rose",
    val vectorPopAccent: String = "cobalt",
    val libraryViewMode: LibraryViewMode = LibraryViewMode.GRID,
    val reduceMotionEnabled: Boolean = false,
    val blueLightFilterEnabled: Boolean = false,
    val blueLightFilterStrength: Float = 0.3f,
    val appFontScale: AppFontScale = AppFontScale.DEFAULT,
    val dateFormatPreference: DateFormatPreference = DateFormatPreference.SYSTEM,
    val colorBlindMode: ColorBlindMode = ColorBlindMode.NONE,
    val handMode: HandMode = HandMode.RIGHT,
    val layoutMode: LayoutMode = LayoutMode.AUTO,
    /** The TV overscan safe-area calibration; the row only renders on TV. */
    val tvOverscan: TvOverscan = TvOverscan.FIVE,
    val hapticsEnabled: Boolean = true,
    val scheduledThemeStartHour: Int = 22,
    val scheduledThemeEndHour: Int = 7,
    val backdropThemeMusicEnabled: Boolean = false,
    val homeMode: HomeMode = HomeMode.VIDEO,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val pinnedHomeSections: List<PinnedHomeSection> = emptyList(),
    /** The user's custom Discover rows (config order). */
    val discoverRows: List<DiscoverRowConfig> = emptyList(),
    val homeLayoutPresets: List<HomeLayoutPreset> = emptyList(),
    val libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
    val hiddenCwItemIds: Set<String> = emptySet(),
    val mergeContinueWatchingAndNextUp: Boolean = false,
    val nextUpMaxDays: Int = 0,
    val nextUpRewatching: Boolean = false,
    val continueWatchingClickBehavior: ContinueWatchingClickBehavior = ContinueWatchingClickBehavior.DETAILS,
    val hideEpisodeThumbnails: Boolean = false,
    val skipSpecials: Boolean = false,
    val compactEpisodeList: Boolean = false,
    /** Whether virtual (missing/unaired) episodes appear in season views. */
    val showMissingEpisodes: Boolean = false,
    /** Render the server clear-logo as the detail-screen title instead of text. */
    val preferLogos: Boolean = false,
    /** Show provider external-link buttons on the detail header. */
    val showExternalLinks: Boolean = true,
    /** Show the item's first tagline under the detail title. */
    val showTagline: Boolean = true,
    /** Prefer server-hosted local trailers over remote (YouTube) entries. */
    val preferLocalTrailers: Boolean = false,
    /** Show the "More in this collection" franchise row on detail. */
    val showMoreInCollection: Boolean = true,
    /** Show the "Airing next" strip on series detail. */
    val showUpcomingStrip: Boolean = true,
    /** Play a series' ambient theme music on detail open. */
    val playThemeMusic: Boolean = true,
    /** Show the reviews preview + ratings block on the detail screen. */
    val showReviews: Boolean = true,
    /** Show subtitle / audio layout stream badges on season episode rows. */
    val showEpisodeStreamBadges: Boolean = true,
    /** Show role names + role grouping on the person screen's filmography. */
    val showFilmographyRoles: Boolean = true,
    /** Show the "frequently appears with" co-star row on the person screen. */
    val showFrequentCostars: Boolean = true,
    /** TV focus-gated trailer preview on the detail hero (TV-only semantics). */
    val trailerPreviewOnFocus: Boolean = true,
    /** Promote the ⋮ options' fixed promoted set into the landscape/TV detail rail. */
    val showPromotedActionsLandscape: Boolean = true,
    /** Show live download progress on season episode rows. */
    val showDownloadProgress: Boolean = true,
    /** Whether the library "Reset" pill shows a confirmation dialog before clearing. */
    val confirmLibraryReset: Boolean = true,
    val showShareMediaOption: Boolean = true,
    val hideSearchHistory: Boolean = false,
    val showClockOnHome: Boolean = false,
    /** Show settings search results alongside media in the home search bar. */
    val showSettingsInHomeSearch: Boolean = true,
    /** Quality badges (4K / HDR / DV / Atmos) on cards — the home-store pref, also surfaced in Library & Cards. */
    val showQualityBadges: Boolean = true,
    /** NEW badges for items added since the last home visit — the home-store pref, also surfaced in Library & Cards. */
    val showNewBadges: Boolean = true,
    /**
     * Whether the home screen's top header dock auto-hides on scroll-down and
     * reappears on scroll-up. Default `false` — the dock stays pinned (current
     * behaviour) until the user opts in. Mirrors the floating nav-bar
     * `hideBottomNavOnScroll` toggle.
     */
    val hideTopHeaderOnScroll: Boolean = false,
    val newsletterEnabled: Boolean = true,
    val newsletterDayOfWeek: Int = 7,
    val enabledNewsletterSections: Set<NewsletterSectionType> = setOf(
        NewsletterSectionType.RECENTLY_ADDED,
        NewsletterSectionType.LIBRARY_STATS,
        NewsletterSectionType.CONTINUE_WATCHING,
        NewsletterSectionType.NEXT_UP,
        NewsletterSectionType.CURATED_PICKS,
        NewsletterSectionType.ACTIVITY_DIGEST,
    ),
    val newsletterSectionOrder: List<NewsletterSectionType> = NewsletterSectionType.DEFAULT_ORDER,
)
