package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Consumer-screen preference projections.
//
// Each type below is the *exact* field set one non-settings screen reads,
// projected centrally by `PreferenceProjections` off the store-owned slices.
// These are the successors to the bespoke `combine(...)` projections that the
// feature ViewModels used to hand-roll (with vararg + `UNCHECKED_CAST`). Field
// names are identical to the former local shadows on purpose: it keeps screen
// bodies (`preferences.X`) untouched when a screen swaps to the projection.
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
)

/** Fields read by `OnboardingViewModel` across the multi-step onboarding flow. */
@Immutable
@Serializable
data class OnboardingPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val theme: AppearanceTheme = AppearanceTheme(),
    val contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    val homeHeroEnabled: Boolean = true,
    val performanceMode: Boolean = false,
    val homeMode: HomeMode = HomeMode.VIDEO,
    val navBarShowLabels: Boolean = true,
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val videoSeekDurationMs: Long = 10_000L,
    val videoGesturesEnabled: Boolean = true,
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
)

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
    val enabledExperimentalFeatures: Set<ExperimentalFeature> = emptySet(),
)

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
    val hiddenNavItems: Set<String> = emptySet(),
    val navItemOrder: List<String> = emptyList(),
    val hideBottomNavOnScroll: Boolean = true,
    val navBarShowLabels: Boolean = true,
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val onboardingCompleted: Boolean = false,
    val enabledExperimentalFeatures: Set<ExperimentalFeature> = emptySet(),
    val appLanguage: String? = null,
)

/** Convenience helper mirroring the legacy `UserPreferences.isExperimentalEnabled`. */
fun MainPreferences.isExperimentalEnabled(feature: ExperimentalFeature): Boolean =
    feature in enabledExperimentalFeatures
