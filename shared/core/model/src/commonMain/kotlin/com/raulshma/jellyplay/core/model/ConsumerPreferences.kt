package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Consumer-screen preference projections.
//
// Each type below is the *exact* field set one non-settings screen reads,
// projected centrally by `PreferenceProjections` off the store-owned slices.
// These replace the bespoke `combine(...)` projections the feature ViewModels
// hand-rolled (with vararg + `UNCHECKED_CAST`). Field names match the
// per-screen local shadows on purpose: screen bodies (`preferences.X`) stay
// untouched when a screen swaps to the projection.
//
// A field that several screens display (e.g. the four appearance-theme fields)
// is projected into each screen's type; a write recomposes every owner, and
// `distinctUntilChanged` de-duplicates each one.
// ---------------------------------------------------------------------------

/**
 * The four appearance-theme fields (`dynamicTheming`, `oledMode`, `colorStyle`,
 * `accentColorSwatch`) that travel together unchanged across every
 * consumer-screen preference projection. Extracted as a value type so the
 * clump is passed and destructured once instead of copied as four parallel
 * lines into every screen preference. Defaults mirror the prior per-field
 * defaults (see git history of each consumer type).
 */
@Immutable
@Serializable
data class AppearanceTheme(
    val dynamicTheming: Boolean = true,
    val oledMode: Boolean = false,
    val colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    val accentColorSwatch: String = "dynamic",
)

/**
 * The few preference fields the audio player screen reads (the
 * `audioLyricsVisible` toggle + the four theme fields for
 * `ArtworkThemeWrapper`). Renamed from the local `AudioPlayerPreferences` to
 * avoid colliding with the audio-effects [AudioPlayerPreferences].
 */
@Immutable
@Serializable
data class AudioPlayerUiPreferences(
    val audioLyricsVisible: Boolean = true,
    val theme: AppearanceTheme = AppearanceTheme(),
)

/** Fields read by `SeerrDetailScreen` — artwork theme + inline-trailer autoplay. */
@Immutable
@Serializable
data class SeerrDetailPreferences(
    val theme: AppearanceTheme = AppearanceTheme(),
    val trailerAutoplay: Boolean = true,
)

/** Fields read by the media `DetailScreen`. */
@Immutable
@Serializable
data class DetailPreferences(
    val theme: AppearanceTheme = AppearanceTheme(),
    val trailerAutoplay: Boolean = true,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val showShareMediaOption: Boolean = true,
    val showExternalRatings: Boolean = true,
    val nextUpExcludedSeriesIds: Set<String> = emptySet(),
    val hiddenCwItemIds: Set<String> = emptySet(),
    /**
     * Per-series last-viewed season tab (seriesId → seasonId). Projected from
     * `HomeDiscoveryStore.lastViewedSeasonBySeries`; the detail screen resolves
     * the entry for the current series and passes it down as
     * `SeasonsSection.persistedSeasonId`. Empty until the user picks a season.
     */
    val lastViewedSeasonBySeries: Map<String, String> = emptyMap(),
    val skipSpecials: Boolean = false,
    val hideEpisodeThumbnails: Boolean = false,
    val episodesDescending: Boolean = true,
    val compactEpisodeList: Boolean = false,
    val showDetailUpNext: Boolean = true,
    /** Whether virtual (missing/unaired) episodes appear in season views. */
    val showMissingEpisodes: Boolean = false,
    /** Render the server clear-logo as the detail-screen title instead of text. */
    val preferLogos: Boolean = false,
    /** Show provider external-link buttons (TMDb / IMDb / server `externalUrls`) on the detail header. */
    val showExternalLinks: Boolean = true,
    /** Show the item's first tagline under the detail title. */
    val showTagline: Boolean = true,
    /** Prefer server-hosted local trailers over remote (YouTube) entries in the trailer list. */
    val preferLocalTrailers: Boolean = false,
    /** Show the "More in this collection" franchise row on movie/series detail. */
    val showMoreInCollection: Boolean = true,
    /** Show the "Airing next" strip on series detail. */
    val showUpcomingStrip: Boolean = true,
    /** Play a series' ambient theme music on detail open. */
    val playThemeMusic: Boolean = true,
    /**
     * Per-series "never play theme music here" memory (the detail top
     * bar's mute chip wrote it). Projected from
     * `HomeDiscoveryStore.themeMusicMutedSeriesIds`; a muted series's theme
     * enrichment doesn't start playback at all.
     */
    val themeMusicMutedSeriesIds: Set<String> = emptySet(),
    /** Show the reviews preview + ratings block on the detail screen. */
    val showReviews: Boolean = true,
    /** Show subtitle / audio layout stream badges on season episode rows. */
    val showEpisodeStreamBadges: Boolean = true,
    /** Show role names + role grouping on the person screen's filmography. */
    val showFilmographyRoles: Boolean = true,
    /** Show the "frequently appears with" co-star row on the person screen. */
    val showFrequentCostars: Boolean = true,
    /**
     * TV focus-gated trailer preview: mount the muted detail-hero embed only
     * while a Play/Trailer action holds focus (TV-only semantics; requires
     * [trailerAutoplay]).
     */
    val trailerPreviewOnFocus: Boolean = true,
    /** Promote the ⋮ options' fixed promoted set into the landscape/TV detail rail. */
    val showPromotedActionsLandscape: Boolean = true,
    /** Show live download progress on season episode rows. */
    val showDownloadProgress: Boolean = true,
    // ── The detail-screen QoL knobs (library domain; the Appearance screen's
    // "Library & Cards" group owns their rows) ─────────────────────────────

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
    /**
     * The persisted franchise eye-hide memory (the DetailViewModel's session
     * memory seeds from it when [persistSectionHides] is on). Projected from
     * `LibraryStore.detailHiddenFranchiseIds`; the eye-hide write merges into
     * it.
     */
    val hiddenFranchiseIds: Set<String> = emptySet(),
    /** "More like this" row bound (both stock arms). */
    val similarItemsLimit: Int = 12,
    /** "Airing next" strip bound. */
    val upcomingEpisodesLimit: Int = 12,
    // ── The detail-screen QoL knobs, wave 2 (D17–D28; the library domain owns
    // their rows — same residual shape as the wave above) ─────────────────

    /**
     * The detail body's section order: the permutation over the detail
     * section-kind NAMES that the `detail_section_order` row persists. Empty =
     * canonical order (the shipped emission). The feature-side registry
     * reconciles: unknown tokens dropped, missing sections appended in
     * canonical order, the pinned chrome sections (header / action row / book
     * reading card) never reordered.
     */
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
) {
    /** Whether [seriesId]'s theme music is per-series muted. */
    fun isThemeMusicMutedFor(seriesId: String): Boolean = seriesId in themeMusicMutedSeriesIds
}

/** Fields read by `OnboardingViewModel` across the multi-step onboarding flow. */
@Immutable
@Serializable
data class OnboardingPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val theme: AppearanceTheme = AppearanceTheme(),
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val homeHeroEnabled: Boolean = true,
    val performanceMode: Boolean = false,
    // The performance posture clump — names + set pinned by [PerformancePosture]
    // (the one declaration; performanceMode stays the derived legacy boolean):
    override val performanceLevel: PerformanceLevel = PerformanceLevel.STANDARD,
    override val perfAnimations: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfSharedElements: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfNavTransitions: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfImageDecodeCap: ImageDecodeCap = ImageDecodeCap.AUTO,
    override val perfImageCrossfade: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfBlurhash: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfQualityBadges: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfAmbientHeroVideo: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfBackdropBlur: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfScreensaverMotion: PerformanceOverride = PerformanceOverride.AUTO,
    /** Image memory-cache percent of the heap: 0 = AUTO (RAM-tiered), else 5..35. */
    val imageMemoryCachePercent: Int = 0,
    val homeMode: HomeMode = HomeMode.VIDEO,
    val navBarShowLabels: Boolean = true,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val videoSeekDurationMs: Long = 10_000L,
    val videoGestureMode: GestureMode = GestureMode.ALL,
    val videoDefaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val videoAutoplayNext: Boolean = true,
    val audioDefaultSpeed: Float = 1.0f,
    val audioGaplessEnabled: Boolean = true,
    val audioCrossfadeDurationMs: Long = 0L,
    val audioNormalizationEnabled: Boolean = false,
    val audioAutoplayNext: Boolean = true,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val pinLockEnabled: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val autoLockTimerMs: Long = 30_000L,
) : PerformancePosture

/** Fields read by the top-level `SettingsScreen` landing page. */
@Immutable
@Serializable
data class SettingsScreenPreferences(
    val showAdvancedSettings: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicTheming: Boolean = true,
    val oledMode: Boolean = false,
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val performanceMode: Boolean = false,
    // The performance posture clump — names + set pinned by [PerformancePosture]
    // (the one declaration; performanceMode stays the derived legacy boolean):
    override val performanceLevel: PerformanceLevel = PerformanceLevel.STANDARD,
    override val perfAnimations: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfSharedElements: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfNavTransitions: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfImageDecodeCap: ImageDecodeCap = ImageDecodeCap.AUTO,
    override val perfImageCrossfade: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfBlurhash: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfQualityBadges: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfAmbientHeroVideo: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfBackdropBlur: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfScreensaverMotion: PerformanceOverride = PerformanceOverride.AUTO,
    /** Image memory-cache percent of the heap: 0 = AUTO (RAM-tiered), else 5..35. */
    val imageMemoryCachePercent: Int = 0,
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val audioDefaultSpeed: Float = 1.0f,
    val preferredAudioLanguage: String? = null,
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    val pinLockEnabled: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val dreamImageCategories: Set<DreamImageCategory> = setOf(
        DreamImageCategory.MOVIES,
        DreamImageCategory.SERIES,
    ),
    val dreamSlideshowIntervalMs: Long = 15_000L,
    val dreamShowTitle: Boolean = true,
    val dreamKenBurnsEnabled: Boolean = true,
    val dreamTransitionStyle: DreamTransitionStyle = DreamTransitionStyle.CROSSFADE,
    /** The dream's local maximum parental rating (canonical age); null = no local cap. */
    val dreamMaxParentalRating: Int? = null,
    /** Slideshow runtime after which the dream dims; 0 = never dims. */
    val dreamDimAfterMs: Long = 0L,
    /** The dim scrim's target opacity in percent (0–95). */
    val dreamDimPercent: Int = 50,
    /** Desktop idle ambient screen toggle + timeout (minutes; 0 = off). */
    val idleAmbientEnabled: Boolean = true,
    val idleAmbientTimeoutMin: Long = 5L,
    /** Desktop Discord Rich Presence toggle (feature 4.2). */
    val discordPresenceEnabled: Boolean = false,
    /** Desktop playback-event shell hooks (feature 4.3): master + five mpv-shim-named commands. */
    val hooksEnabled: Boolean = false,
    val hooksPlayCmd: String = "",
    val hooksStopCmd: String = "",
    val hooksEndedCmd: String = "",
    val hooksIdleCmd: String = "",
    val hooksIdleEndedCmd: String = "",
    val enabledExperimentalFeatures: Set<ExperimentalFeature> = emptySet(),
) : PerformancePosture

/**
 * Slice-derived portion of `MainViewModel`'s preferences. The two runtime-only
 * fields (`pinLockoutUntilEpochMs`, `onboardingCompleted`) are merged in by the
 * VM via a typed `combine` off the projection; they are absent here because
 * they do not live in a preference slice.
 */
@Immutable
@Serializable
data class MainPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val theme: AppearanceTheme = AppearanceTheme(),
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val performanceMode: Boolean = false,
    // The performance posture clump — names + set pinned by [PerformancePosture]
    // (the one declaration; performanceMode stays the derived legacy boolean):
    override val performanceLevel: PerformanceLevel = PerformanceLevel.STANDARD,
    override val perfAnimations: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfSharedElements: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfNavTransitions: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfImageDecodeCap: ImageDecodeCap = ImageDecodeCap.AUTO,
    override val perfImageCrossfade: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfBlurhash: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfQualityBadges: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfAmbientHeroVideo: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfBackdropBlur: PerformanceOverride = PerformanceOverride.AUTO,
    override val perfScreensaverMotion: PerformanceOverride = PerformanceOverride.AUTO,
    /** Image memory-cache percent of the heap: 0 = AUTO (RAM-tiered), else 5..35. */
    val imageMemoryCachePercent: Int = 0,
    val reduceMotionEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val themeVariant: String = "standard",
    val synthwaveAccent: String = "magenta",
    val soothingAccent: String = "ocean",
    val vividAccent: String = "punch",
    val auroraAccent: String = "emerald",
    val sakuraAccent: String = "rose",
    val vectorPopAccent: String = "cobalt",
    val appFontScale: AppFontScale = AppFontScale.DEFAULT,
    val scheduledThemeStartHour: Int = 22,
    val scheduledThemeEndHour: Int = 7,
    val blueLightFilterEnabled: Boolean = false,
    val blueLightFilterStrength: Float = 0.3f,
    val colorBlindMode: ColorBlindMode = ColorBlindMode.NONE,
    val handMode: HandMode = HandMode.RIGHT,
    val pinLockEnabled: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val autoLockTimerMs: Long = 30_000L,
    val pinLockoutUntilEpochMs: Long = 0L,
    val homeMode: HomeMode = HomeMode.VIDEO,
    val showUnwatchedBadge: Boolean = true,
    val hideWatchedItems: Boolean = false,
    val showWatchedCheckmark: Boolean = true,
    /** Quality badges (4K / HDR / DV / Atmos) on cards (home-discovery knob). */
    val showQualityBadges: Boolean = true,
    /** "NEW" badges for items added since the last home visit (home-discovery knob). */
    val showNewBadges: Boolean = true,
    /** Anti-spoiler home master switch (home-discovery knob). */
    val antiSpoilerHome: Boolean = false,
    val hiddenNavItems: Set<String> = emptySet(),
    val navItemOrder: List<String> = emptyList(),
    val hideBottomNavOnScroll: Boolean = true,
    val navBarShowLabels: Boolean = true,
    /** The start tab (a top-level destination key; "" = the shipped Home default). */
    val startTab: String = "",
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
    /** The NETWORK domain's data-saver master (imagery hold-back reads it — see LocalImagesDataSaver). */
    val dataSaverEnabled: Boolean = false,
    /** Manual adaptive-layout override; AUTO follows the window. */
    val layoutMode: LayoutMode = LayoutMode.AUTO,
    /** The TV overscan safe-area calibration; only consumed on the TV form factor. */
    val tvOverscan: TvOverscan = TvOverscan.FIVE,
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    /** Which third-party app the EXTERNAL arm hands off to (chooser when unset). */
    val preferredExternalPlayer: ExternalPlayerApp = ExternalPlayerApp.SYSTEM_CHOOSER,
    val onboardingCompleted: Boolean = false,
    val enabledExperimentalFeatures: Set<ExperimentalFeature> = emptySet(),
    val appLanguage: String? = null,
) : PerformancePosture

/** Convenience helper mirroring the legacy `UserPreferences.isExperimentalEnabled`. */
fun MainPreferences.isExperimentalEnabled(feature: ExperimentalFeature): Boolean =
    feature in enabledExperimentalFeatures
