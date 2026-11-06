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
    /** The start tab (a top-level destination key; "" = the shipped Home default). */
    val startTab: String = "",
    val homeHeroEnabled: Boolean = true,
    val homeBackdropEnabled: Boolean = true,
    val performanceMode: Boolean = false,
    // The performance master level (the hub's dial; `performanceMode` stays
    // the derived legacy boolean). Standard falls back to the level-less
    // summary faces.
    val performanceLevel: PerformanceLevel = PerformanceLevel.STANDARD,
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
    /** The details screen's external-award badges (OMDb/oscars-derived). Default on. */
    val detailsAwardsBadgesEnabled: Boolean = true,
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
    // ── The detail-screen QoL knobs (library domain; the rows sit in the
    // "Library & Cards" group beside the other detail toggles) ────────────

    /** Cast rail item size on the media-detail header (people geometry). */
    val castItemSize: DetailDensity = DetailDensity.DEFAULT,
    /** Cast "See all" threshold: people hide behind it above this count. */
    val castSeeAllLimit: Int = 12,
    /** Parallax (translate + zoom) scroll on the detail hero backdrop. */
    val backdropParallax: Boolean = true,
    /** Overview synopsis collapsed line count (the "Read more" toggle). */
    val overviewCollapseLines: Int = 3,
    /** Hide played episodes from season lists. */
    val hideWatchedEpisodes: Boolean = false,
    /** Ask for confirmation before marking a single item played/unplayed. */
    val confirmMarkPlayed: Boolean = false,
    /** Header studio chips. */
    val showStudioChips: Boolean = true,
    /** Header genre chips. */
    val showGenreChips: Boolean = true,
    /** Header tag chips. */
    val showTagChips: Boolean = true,
    /** Header skip-intro/credits availability chips. */
    val showSkipChips: Boolean = true,
    /** Small poster thumbnails on the season tab bar. */
    val seasonTabsArtwork: Boolean = false,
    /** Cast & Crew grid density (the grid's column minimum). */
    val peopleGridDensity: DetailDensity = DetailDensity.DEFAULT,
    /** Local/remote source badges on the trailer cards. */
    val showTrailerBadges: Boolean = true,
    /** Persist the detail screen's inline section hides (the franchise eye-hide memory). */
    val persistSectionHides: Boolean = false,
    /** "More like this" row bound (both stock arms). */
    val similarItemsLimit: Int = 12,
    /** "Airing next" strip bound. */
    val upcomingEpisodesLimit: Int = 12,
    // ── The detail-screen QoL knobs, wave 2 (D17–D28) — the rows sit in the
    // "Library & Cards" group beside the wave above ─────────────────────────

    /** The detail body's stored section permutation (empty = canonical order). */
    val sectionOrder: List<String> = emptyList(),
    /** The entrance-stagger step (FAST/DEFAULT/SLOW over the shipped 0.045f). */
    val staggerStyle: DetailStaggerStyle = DetailStaggerStyle.DEFAULT,
    /** The hero backdrop's decode target (720p/1080p/1440p). */
    val backdropResolution: DetailBackdropResolution = DetailBackdropResolution.FHD_1920,
    /** The chapter thumbnail row's card size multiplier (0.8/1.0/1.2). */
    val chapterCardSize: DetailChapterCardSize = DetailChapterCardSize.DEFAULT,
    /** Review-card preview line count on the detail reviews section (clamped 2..12). */
    val reviewsPreviewLines: Int = 6,
    /** The person screen's filmography ordering. */
    val filmographySort: FilmographySort = FilmographySort.DEFAULT,
    /** Franchise "More in this collection" arm: row (BoxSet) bound (clamped 1..5). */
    val franchiseBoxLimit: Int = 2,
    /** Franchise arm: per-row member page bound (clamped 10..100). */
    val franchiseMemberLimit: Int = 50,
    /** The smart-play dispatch's candidate preference (resume/next-up/replay order). */
    val smartPlayPriority: SmartPlayPriority = SmartPlayPriority.DEFAULT,
    /** Stream-picker fallback language when no preferred language matched (3-letter code). */
    val streamFallbackLanguage: String = "eng",
    /** Which preset of ⋮-menu entries the landscape/TV promoted rail shows. */
    val promotedRailSource: PromotedRailSource = PromotedRailSource.DEFAULT,
    /** 2-line overview snippets on the season lists' episode rows. */
    val episodeOverviewPreview: Boolean = false,
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
    /** The app-wide 24-hour clock override. Default off (follow system). */
    val timeUse24h: Boolean = false,
    /** The media cards' animated press-scale. Default on. */
    val cardPressAnimations: Boolean = true,
    /** The pull-to-refresh gesture master toggle. Default on. */
    val pullToRefreshEnabled: Boolean = true,
    /** The app-wide backdrop dim level. Default = shipped look. */
    val appBackdropDim: BackdropDimLevel = BackdropDimLevel.DEFAULT,
    /** The confirmation-haptic strength remap. Default = shipped mapping. */
    val hapticsIntensity: HapticsIntensity = HapticsIntensity.MEDIUM,
    /** The server image-request quality override. Default = original. */
    val imageQualityPreset: ImageQualityPreset = ImageQualityPreset.ORIGINAL,
    /** Settings/list row density. Default = shipped spacing. */
    val appListDensity: ListDensity = ListDensity.DEFAULT,
    /** The press-feedback style for clickable surfaces. Default = Material ripple. */
    val appPressEffect: PressEffectStyle = PressEffectStyle.DEFAULT,
    /** Flat cards: remove the card shells' elevation/shadow. Default off. */
    val appFlatCards: Boolean = false,
    /** The scrollable containers' overscroll effect. Default = platform. */
    val appOverscrollStyle: OverscrollStyle = OverscrollStyle.PLATFORM,
)
