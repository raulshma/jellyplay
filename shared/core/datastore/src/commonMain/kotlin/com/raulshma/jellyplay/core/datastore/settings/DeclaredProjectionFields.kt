package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.navigation.NavigationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.model.AppearancePreferences
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.AppearanceTheme
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPlayerPreferences
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.ThemeMode

/**
 * The declared projection field-sets — the ONE home of the field lists that
 * more than one projection lane consumes.
 *
 * Historically each lane hand-copied its field list from the slices: the two
 * audio surfaces re-listed the same ~30 audio/effects fields, the appearance
 * core was re-listed three times (per-domain, appearance screen, main), and
 * the `AppearanceTheme` sub-object was rebuilt five times. Now the shared
 * field set is declared exactly once below — an explicit values holder whose
 * properties ARE the field list, each read off its owning slice by
 * [audioSurfaceValues] / [appearanceCoreValues] — and every lane derives its
 * projection from the declared set. No reflection: plain accessors, R8-safe.
 *
 * Adding a preference to one of these domains means adding one property to the
 * declared holder (plus the slice field itself) and one argument per
 * projection lane that surfaces it — instead of re-copying the source mapping
 * into every lane.
 *
 * Per-lane assembly keeps exact named-argument construction, so every
 * projection's return type and value semantics are unchanged; the whole set
 * is pinned against explicit expected values by `DeclaredProjectionFieldsTest`.
 */

/**
 * The appearance-art theme quad, declared once. Formerly hand-built five
 * times (audio-player UI, Seerr detail, media detail, onboarding, main).
 */
internal fun AppearanceSlice.appearanceTheme(): AppearanceTheme = AppearanceTheme(
    dynamicTheming = dynamicTheming,
    oledMode = oledMode,
    colorStyle = colorStyle,
    accentColorSwatch = accentColorSwatch,
)

// ---------------------------------------------------------------------------
// Audio surface: the fields one audio surface reads off AudioSlice +
// AudioEffectsSlice, declared once. The audio-player domain lane uses the
// playback subset; the audio settings screen adds the cache/experimental
// fields at assembly.
// ---------------------------------------------------------------------------

internal data class AudioSurfaceValues(
    val audioDefaultSpeed: Float,
    val audioNightModeVolume: Float,
    val audioNightModeGain: Int,
    val audioSkipPreviousThresholdMs: Long,
    val audioAutoplayNext: Boolean,
    val audioPreloadBufferSize: PreloadBufferSize,
    val audioNormalizationMode: AudioNormalizationMode,
    val audioNormalizationEnabled: Boolean,
    val replayGainPreAmpDb: Float,
    val channelMixMode: ChannelMixMode,
    val channelMixEnabled: Boolean,
    val audioGaplessEnabled: Boolean,
    val audioCrossfadeDurationMs: Long,
    val equalizerEnabled: Boolean,
    val equalizerSettings: EqualizerSettings,
    val equalizerPreset: EqualizerPreset,
    val bassBoostEnabled: Boolean,
    val bassBoostStrength: EffectStrength,
    val virtualizerEnabled: Boolean,
    val virtualizerStrength: Int,
    val reverbPreset: ReverbPreset,
    val lrBalance: Float,
    val autoEqByGenre: Boolean,
    val pitchSemitones: Float,
    val audioDelayMs: Long,
    val dialogueBoostEnabled: Boolean,
    val dialogueBoostStrength: EffectStrength,
    val nightModeEnabled: Boolean,
    val nightModeStrength: EffectStrength,
    val audioVisualizerEnabled: Boolean,
    val volumeBoostEnabled: Boolean,
    val volumeBoostGain: Int,
    val sleepTimerDurationMs: Long,
)

/** Declared extraction of the audio-surface field set off its two owning slices. */
internal fun audioSurfaceValues(audio: AudioSlice, effects: AudioEffectsSlice): AudioSurfaceValues =
    AudioSurfaceValues(
        audioDefaultSpeed = audio.audioDefaultSpeed,
        audioNightModeVolume = audio.audioNightModeVolume,
        audioNightModeGain = audio.audioNightModeGain,
        audioSkipPreviousThresholdMs = audio.audioSkipPreviousThresholdMs,
        audioAutoplayNext = audio.audioAutoplayNext,
        audioPreloadBufferSize = audio.audioPreloadBufferSize,
        audioNormalizationMode = audio.audioNormalizationMode,
        audioNormalizationEnabled = audio.audioNormalizationEnabled,
        replayGainPreAmpDb = audio.replayGainPreAmpDb,
        channelMixMode = audio.channelMixMode,
        channelMixEnabled = audio.channelMixEnabled,
        audioGaplessEnabled = audio.audioGaplessEnabled,
        audioCrossfadeDurationMs = audio.audioCrossfadeDurationMs,
        equalizerEnabled = effects.equalizerEnabled,
        equalizerSettings = effects.equalizerSettings,
        equalizerPreset = effects.equalizerPreset,
        bassBoostEnabled = effects.bassBoostEnabled,
        bassBoostStrength = effects.bassBoostStrength,
        virtualizerEnabled = effects.virtualizerEnabled,
        virtualizerStrength = effects.virtualizerStrength,
        reverbPreset = effects.reverbPreset,
        lrBalance = effects.lrBalance,
        autoEqByGenre = effects.autoEqByGenre,
        pitchSemitones = effects.pitchSemitones,
        audioDelayMs = audio.audioDelayMs,
        dialogueBoostEnabled = effects.dialogueBoostEnabled,
        dialogueBoostStrength = effects.dialogueBoostStrength,
        nightModeEnabled = effects.nightModeEnabled,
        nightModeStrength = effects.nightModeStrength,
        audioVisualizerEnabled = audio.audioVisualizerEnabled,
        volumeBoostEnabled = effects.volumeBoostEnabled,
        volumeBoostGain = effects.volumeBoostGain,
        sleepTimerDurationMs = audio.sleepTimerDurationMs,
    )

/** Audio-player domain projection, derived from the declared audio-surface set. */
internal fun AudioSurfaceValues.toAudioPlayerPreferences(): AudioPlayerPreferences =
    AudioPlayerPreferences(
        audioDefaultSpeed = audioDefaultSpeed,
        audioNightModeVolume = audioNightModeVolume,
        audioNightModeGain = audioNightModeGain,
        audioSkipPreviousThresholdMs = audioSkipPreviousThresholdMs,
        audioAutoplayNext = audioAutoplayNext,
        audioPreloadBufferSize = audioPreloadBufferSize,
        audioNormalizationMode = audioNormalizationMode,
        audioNormalizationEnabled = audioNormalizationEnabled,
        replayGainPreAmpDb = replayGainPreAmpDb,
        channelMixMode = channelMixMode,
        channelMixEnabled = channelMixEnabled,
        audioGaplessEnabled = audioGaplessEnabled,
        audioCrossfadeDurationMs = audioCrossfadeDurationMs,
        equalizerEnabled = equalizerEnabled,
        equalizerSettings = equalizerSettings,
        equalizerPreset = equalizerPreset,
        bassBoostEnabled = bassBoostEnabled,
        bassBoostStrength = bassBoostStrength,
        virtualizerEnabled = virtualizerEnabled,
        virtualizerStrength = virtualizerStrength,
        reverbPreset = reverbPreset,
        lrBalance = lrBalance,
        autoEqByGenre = autoEqByGenre,
        pitchSemitones = pitchSemitones,
        audioDelayMs = audioDelayMs,
        dialogueBoostEnabled = dialogueBoostEnabled,
        dialogueBoostStrength = dialogueBoostStrength,
        nightModeEnabled = nightModeEnabled,
        nightModeStrength = nightModeStrength,
        audioVisualizerEnabled = audioVisualizerEnabled,
    )

/** Audio settings screen projection: the declared audio-surface set plus this screen's cache/experimental fields. */
internal fun AudioSurfaceValues.toAudioPreferences(
    cache: AudioCacheSlice,
    experimental: ExperimentalSlice,
): AudioPreferences =
    AudioPreferences(
        audioDefaultSpeed = audioDefaultSpeed,
        audioNightModeVolume = audioNightModeVolume,
        audioNightModeGain = audioNightModeGain,
        audioSkipPreviousThresholdMs = audioSkipPreviousThresholdMs,
        audioAutoplayNext = audioAutoplayNext,
        audioPreloadBufferSize = audioPreloadBufferSize,
        audioNormalizationMode = audioNormalizationMode,
        audioNormalizationEnabled = audioNormalizationEnabled,
        replayGainPreAmpDb = replayGainPreAmpDb,
        channelMixMode = channelMixMode,
        channelMixEnabled = channelMixEnabled,
        audioGaplessEnabled = audioGaplessEnabled,
        audioCrossfadeDurationMs = audioCrossfadeDurationMs,
        equalizerEnabled = equalizerEnabled,
        equalizerSettings = equalizerSettings,
        equalizerPreset = equalizerPreset,
        bassBoostEnabled = bassBoostEnabled,
        bassBoostStrength = bassBoostStrength,
        virtualizerEnabled = virtualizerEnabled,
        virtualizerStrength = virtualizerStrength,
        reverbPreset = reverbPreset,
        lrBalance = lrBalance,
        autoEqByGenre = autoEqByGenre,
        pitchSemitones = pitchSemitones,
        dialogueBoostEnabled = dialogueBoostEnabled,
        dialogueBoostStrength = dialogueBoostStrength,
        nightModeEnabled = nightModeEnabled,
        nightModeStrength = nightModeStrength,
        audioVisualizerEnabled = audioVisualizerEnabled,
        audioCachingEnabled = cache.audioCachingEnabled,
        audioCacheSizeMb = cache.audioCacheSizeMb,
        audioPrefetchLookahead = cache.audioPrefetchLookahead,
        audioPrefetchBackfill = cache.audioPrefetchBackfill,
        audioCacheNetworkPolicy = cache.audioCacheNetworkPolicy,
        sleepTimerDurationMs = sleepTimerDurationMs,
        preferAudioDescription = experimental.preferAudioDescription,
        volumeBoostEnabled = volumeBoostEnabled,
        volumeBoostGain = volumeBoostGain,
    )

// ---------------------------------------------------------------------------
// Appearance core: the theme/accent/layout fields shared by the per-domain
// appearance projection and the appearance settings screen projection,
// declared once off the four slices that own them.
// ---------------------------------------------------------------------------

internal data class AppearanceCoreValues(
    val dynamicTheming: Boolean,
    val themeMode: ThemeMode,
    val contrastLevel: ContrastLevel,
    val oledMode: Boolean,
    val accentColorSwatch: String,
    val colorStyle: ColorStyle,
    val navBarShowLabels: Boolean,
    val homeHeroEnabled: Boolean,
    val homeBackdropEnabled: Boolean,
    val performanceMode: Boolean,
    val themeVariant: String,
    val synthwaveAccent: String,
    val soothingAccent: String,
    val vividAccent: String,
    val auroraAccent: String,
    val sakuraAccent: String,
    val vectorPopAccent: String,
    val libraryViewMode: LibraryViewMode,
)

/** Declared extraction of the appearance-core field set off its four owning slices. */
internal fun appearanceCoreValues(
    appearance: AppearanceSlice,
    navigation: NavigationSlice,
    home: HomeDiscoverySlice,
    library: LibrarySlice,
): AppearanceCoreValues =
    AppearanceCoreValues(
        dynamicTheming = appearance.dynamicTheming,
        themeMode = appearance.themeMode,
        contrastLevel = appearance.contrastLevel,
        oledMode = appearance.oledMode,
        accentColorSwatch = appearance.accentColorSwatch,
        colorStyle = appearance.colorStyle,
        navBarShowLabels = navigation.navBarShowLabels,
        homeHeroEnabled = home.homeHeroEnabled,
        homeBackdropEnabled = home.homeBackdropEnabled,
        performanceMode = appearance.performanceMode,
        themeVariant = appearance.themeVariant,
        synthwaveAccent = appearance.synthwaveAccent,
        soothingAccent = appearance.soothingAccent,
        vividAccent = appearance.vividAccent,
        auroraAccent = appearance.auroraAccent,
        sakuraAccent = appearance.sakuraAccent,
        vectorPopAccent = appearance.vectorPopAccent,
        libraryViewMode = library.libraryViewMode,
    )

/** Declared appearance-core extraction off the appearance-screen combine's bundle half. */
internal fun AppearanceScreenBundle.appearanceCoreValues(): AppearanceCoreValues =
    appearanceCoreValues(
        appearance = appearance,
        navigation = navigation,
        home = home,
        library = library,
    )

/** Per-domain appearance projection, derived from the declared appearance-core set. */
internal fun AppearanceCoreValues.toAppearancePreferences(): AppearancePreferences = AppearancePreferences(
        dynamicTheming = dynamicTheming,
        themeMode = themeMode,
        contrastLevel = contrastLevel,
        oledMode = oledMode,
        accentColorSwatch = accentColorSwatch,
        colorStyle = colorStyle,
        navBarShowLabels = navBarShowLabels,
        homeHeroEnabled = homeHeroEnabled,
        homeBackdropEnabled = homeBackdropEnabled,
        performanceMode = performanceMode,
        themeVariant = themeVariant,
        synthwaveAccent = synthwaveAccent,
        soothingAccent = soothingAccent,
        vividAccent = vividAccent,
        auroraAccent = auroraAccent,
        sakuraAccent = sakuraAccent,
        vectorPopAccent = vectorPopAccent,
        libraryViewMode = libraryViewMode,
    )

/**
 * Appearance settings screen projection: the declared appearance-core set
 * plus this screen's accessibility / home-layout / discovery / library /
 * newsletter fields.
 */
internal fun AppearanceCoreValues.toAppearanceScreenPreferences(
    appearance: AppearanceSlice,
    home: HomeDiscoverySlice,
    library: LibrarySlice,
    experimental: ExperimentalSlice,
    notification: NotificationSlice,
): AppearanceScreenPreferences =
    AppearanceScreenPreferences(
        dynamicTheming = dynamicTheming,
        themeMode = themeMode,
        contrastLevel = contrastLevel,
        oledMode = oledMode,
        accentColorSwatch = accentColorSwatch,
        colorStyle = colorStyle,
        navBarShowLabels = navBarShowLabels,
        homeHeroEnabled = homeHeroEnabled,
        homeBackdropEnabled = homeBackdropEnabled,
        performanceMode = performanceMode,
        themeVariant = themeVariant,
        synthwaveAccent = synthwaveAccent,
        soothingAccent = soothingAccent,
        vividAccent = vividAccent,
        auroraAccent = auroraAccent,
        sakuraAccent = sakuraAccent,
        vectorPopAccent = vectorPopAccent,
        libraryViewMode = libraryViewMode,
        reduceMotionEnabled = appearance.reduceMotionEnabled,
        blueLightFilterEnabled = appearance.blueLightFilterEnabled,
        blueLightFilterStrength = appearance.blueLightFilterStrength,
        appFontScale = appearance.appFontScale,
        dateFormatPreference = appearance.dateFormatPreference,
        colorBlindMode = appearance.colorBlindMode,
        handMode = appearance.handMode,
        hapticsEnabled = appearance.hapticsEnabled,
        scheduledThemeStartHour = appearance.scheduledThemeStartHour,
        scheduledThemeEndHour = appearance.scheduledThemeEndHour,
        backdropThemeMusicEnabled = appearance.backdropThemeMusicEnabled,
        homeMode = home.homeMode,
        enabledHomeSectionTypes = home.enabledHomeSectionTypes,
        homeSectionOrder = home.homeSectionOrder,
        pinnedHomeSections = home.pinnedHomeSections,
        discoverRows = home.discoverRows,
        homeLayoutPresets = home.homeLayoutPresets,
        libraryHomeSectionOverrides = home.libraryHomeSectionOverrides,
        hiddenCwItemIds = home.hiddenCwItemIds,
        showUnwatchedBadge = home.showUnwatchedBadge,
        hideWatchedItems = home.hideWatchedItems,
        mergeContinueWatchingAndNextUp = home.mergeContinueWatchingAndNextUp,
        nextUpMaxDays = home.nextUpMaxDays,
        nextUpRewatching = home.nextUpRewatching,
        continueWatchingClickBehavior = home.continueWatchingClickBehavior,
        showWatchedCheckmark = home.showWatchedCheckmark,
        hideEpisodeThumbnails = library.hideEpisodeThumbnails,
        skipSpecials = library.skipSpecials,
        compactEpisodeList = library.compactEpisodeList,
        confirmLibraryReset = library.confirmLibraryReset,
        showExternalRatings = home.showExternalRatings,
        showShareMediaOption = experimental.showShareMediaOption,
        hideSearchHistory = experimental.hideSearchHistory,
        showClockOnHome = home.showClockOnHome,
        showSettingsInHomeSearch = home.showSettingsInHomeSearch,
        hideTopHeaderOnScroll = home.hideTopHeaderOnScroll,
        newsletterEnabled = notification.newsletterEnabled,
        newsletterDayOfWeek = notification.newsletterDayOfWeek,
        enabledNewsletterSections = notification.enabledNewsletterSections,
        newsletterSectionOrder = notification.newsletterSectionOrder,
    )

/**
 * Main-screen projection: the theme/accessibility fields (with the declared
 * [AppearanceTheme]) plus security, home, navigation, playback and
 * experimental runtime fields. Shares the appearance-core's theme fields via
 * [AppearanceSlice.appearanceTheme]; the rest are main-specific.
 */
internal fun mainScreenPreferences(
    appearance: AppearanceSlice,
    security: SecuritySlice,
    home: HomeDiscoverySlice,
    navigation: NavigationSlice,
    experimental: ExperimentalSlice,
    playback: PlaybackSlice,
): MainPreferences =
    MainPreferences(
        themeMode = appearance.themeMode,
        theme = appearance.appearanceTheme(),
        contrastLevel = appearance.contrastLevel,
        performanceMode = appearance.performanceMode,
        reduceMotionEnabled = appearance.reduceMotionEnabled,
        hapticsEnabled = appearance.hapticsEnabled,
        themeVariant = appearance.themeVariant,
        synthwaveAccent = appearance.synthwaveAccent,
        soothingAccent = appearance.soothingAccent,
        vividAccent = appearance.vividAccent,
        auroraAccent = appearance.auroraAccent,
        sakuraAccent = appearance.sakuraAccent,
        vectorPopAccent = appearance.vectorPopAccent,
        appFontScale = appearance.appFontScale,
        scheduledThemeStartHour = appearance.scheduledThemeStartHour,
        scheduledThemeEndHour = appearance.scheduledThemeEndHour,
        blueLightFilterEnabled = appearance.blueLightFilterEnabled,
        blueLightFilterStrength = appearance.blueLightFilterStrength,
        colorBlindMode = appearance.colorBlindMode,
        handMode = appearance.handMode,
        pinLockEnabled = security.pinLockEnabled,
        biometricLockEnabled = security.biometricLockEnabled,
        pinHash = security.pinHash,
        autoLockTimerMs = security.autoLockTimerMs,
        homeMode = home.homeMode,
        showUnwatchedBadge = home.showUnwatchedBadge,
        hideWatchedItems = home.hideWatchedItems,
        showWatchedCheckmark = home.showWatchedCheckmark,
        hiddenNavItems = navigation.hiddenNavItems,
        navItemOrder = navigation.navItemOrder,
        hideBottomNavOnScroll = navigation.hideBottomNavOnScroll,
        navBarShowLabels = navigation.navBarShowLabels,
        preferredPlayer = playback.preferredPlayer,
        enabledExperimentalFeatures = experimental.enabledExperimentalFeatures,
        appLanguage = experimental.appLanguage,
    )
