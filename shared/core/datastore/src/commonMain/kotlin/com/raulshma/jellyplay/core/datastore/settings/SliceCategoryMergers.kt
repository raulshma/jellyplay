package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.PreferenceResetCategory

/**
 * Field-level mergers for slices that are co-owned by multiple
 * [PreferenceResetCategory]s. Wholesale `store.restore(incoming)` would bleed
 * across categories (e.g. `AUDIO` + `PLAYBACK` both touch `AudioSlice`).
 *
 * Exclusive slices (AudioEffects, AudioCache, Screensaver, Security, etc.)
 * are not listed here — callers can wholesale-restore them when their single
 * owning category is selected.
 *
 * These `copy()` chains are hand-maintained per field, so a field added to a
 * co-owned slice can silently drop out of restore (the wizard's diff would
 * still advertise it). `SliceCategoryMergersTest` guards every merger
 * reflectively: each declared slice field must survive a full-category
 * merge, so a drift fails the suite instead of the user's restore.
 */
internal fun AppearanceSlice.mergeWith(
    incoming: AppearanceSlice,
    categories: Set<PreferenceResetCategory>,
): AppearanceSlice {
    val importAppearance = PreferenceResetCategory.APPEARANCE in categories
    val importMisc = PreferenceResetCategory.MISC_APP in categories
    // If neither category that touches this slice is selected, keep current.
    if (!importAppearance && !importMisc) return this
    return copy(
        dynamicTheming = if (importAppearance) incoming.dynamicTheming else dynamicTheming,
        themeMode = if (importAppearance) incoming.themeMode else themeMode,
        contrastLevel = if (importAppearance) incoming.contrastLevel else contrastLevel,
        oledMode = if (importAppearance) incoming.oledMode else oledMode,
        performanceMode = if (importAppearance) incoming.performanceMode else performanceMode,
        accentColorSwatch = if (importAppearance) incoming.accentColorSwatch else accentColorSwatch,
        colorStyle = if (importAppearance) incoming.colorStyle else colorStyle,
        themeVariant = if (importAppearance) incoming.themeVariant else themeVariant,
        synthwaveAccent = if (importAppearance) incoming.synthwaveAccent else synthwaveAccent,
        soothingAccent = if (importAppearance) incoming.soothingAccent else soothingAccent,
        vividAccent = if (importAppearance) incoming.vividAccent else vividAccent,
        auroraAccent = if (importAppearance) incoming.auroraAccent else auroraAccent,
        sakuraAccent = if (importAppearance) incoming.sakuraAccent else sakuraAccent,
        vectorPopAccent = if (importAppearance) incoming.vectorPopAccent else vectorPopAccent,
        showAdvancedSettings = if (importAppearance) incoming.showAdvancedSettings else showAdvancedSettings,
        reduceMotionEnabled = if (importAppearance) incoming.reduceMotionEnabled else reduceMotionEnabled,
        blueLightFilterEnabled = if (importAppearance) incoming.blueLightFilterEnabled else blueLightFilterEnabled,
        blueLightFilterStrength = if (importAppearance) incoming.blueLightFilterStrength else blueLightFilterStrength,
        backdropThemeMusicEnabled = if (importAppearance) incoming.backdropThemeMusicEnabled else backdropThemeMusicEnabled,
        detailsAwardsBadgesEnabled = if (importAppearance) incoming.detailsAwardsBadgesEnabled else detailsAwardsBadgesEnabled,
        dateFormatPreference = if (importAppearance) incoming.dateFormatPreference else dateFormatPreference,
        appFontScale = if (importAppearance) incoming.appFontScale else appFontScale,
        scheduledThemeStartHour = if (importAppearance) incoming.scheduledThemeStartHour else scheduledThemeStartHour,
        scheduledThemeEndHour = if (importAppearance) incoming.scheduledThemeEndHour else scheduledThemeEndHour,
        colorBlindMode = if (importAppearance) incoming.colorBlindMode else colorBlindMode,
        handMode = if (importAppearance) incoming.handMode else handMode,
        layoutMode = if (importAppearance) incoming.layoutMode else layoutMode,
        tvOverscan = if (importAppearance) incoming.tvOverscan else tvOverscan,
        hapticsEnabled = if (importMisc) incoming.hapticsEnabled else hapticsEnabled,
    )
}

internal fun PlaybackSlice.mergeWith(
    incoming: PlaybackSlice,
    categories: Set<PreferenceResetCategory>,
): PlaybackSlice {
    val importPlayback = PreferenceResetCategory.PLAYBACK in categories
    val importSubtitles = PreferenceResetCategory.SUBTITLES_LANGUAGE in categories
    val importSync = PreferenceResetCategory.SYNCPLAY_CASTING in categories
    val importMisc = PreferenceResetCategory.MISC_APP in categories
    if (!importPlayback && !importSubtitles && !importSync && !importMisc) return this
    return copy(
        preferredPlayer = if (importPlayback) incoming.preferredPlayer else preferredPlayer,
        preferredExternalPlayer = if (importPlayback) incoming.preferredExternalPlayer else preferredExternalPlayer,
        streamingQuality = if (importPlayback) incoming.streamingQuality else streamingQuality,
        cellularStreamingQuality = if (importPlayback) incoming.cellularStreamingQuality else cellularStreamingQuality,
        hdrFormatPreference = if (importPlayback) incoming.hdrFormatPreference else hdrFormatPreference,
        forceTranscodeAboveHeight = if (importPlayback) incoming.forceTranscodeAboveHeight else forceTranscodeAboveHeight,
        preferredTranscodeVideoCodec = if (importPlayback) incoming.preferredTranscodeVideoCodec else preferredTranscodeVideoCodec,
        preferredTranscodeAudioCodec = if (importPlayback) incoming.preferredTranscodeAudioCodec else preferredTranscodeAudioCodec,
        playbackMode = if (importPlayback) incoming.playbackMode else playbackMode,
        offlinePlaybackPreference = if (importPlayback) incoming.offlinePlaybackPreference else offlinePlaybackPreference,
        decoderMode = if (importPlayback) incoming.decoderMode else decoderMode,
        audioPassthrough = if (importPlayback) incoming.audioPassthrough else audioPassthrough,
        audioPassthroughCodecs = if (importPlayback) incoming.audioPassthroughCodecs else audioPassthroughCodecs,
        maxAudioChannels = if (importPlayback) incoming.maxAudioChannels else maxAudioChannels,
        downmixBoostDb = if (importPlayback) incoming.downmixBoostDb else downmixBoostDb,
        spatialAudioEnabled = if (importPlayback) incoming.spatialAudioEnabled else spatialAudioEnabled,
        spatialAudioOnlyHeadphones = if (importPlayback) incoming.spatialAudioOnlyHeadphones else spatialAudioOnlyHeadphones,
        frameRateMatching = if (importPlayback) incoming.frameRateMatching else frameRateMatching,
        refreshRateMode = if (importPlayback) incoming.refreshRateMode else refreshRateMode,
        keepScreenOnDuringVideo = if (importPlayback) incoming.keepScreenOnDuringVideo else keepScreenOnDuringVideo,
        pauseOnAudioFocusLoss = if (importPlayback) incoming.pauseOnAudioFocusLoss else pauseOnAudioFocusLoss,
        duckOnTransientFocusLoss = if (importPlayback) incoming.duckOnTransientFocusLoss else duckOnTransientFocusLoss,
        autoPlayCountdownSec = if (importPlayback) incoming.autoPlayCountdownSec else autoPlayCountdownSec,
        backgroundVideoAudioEnabled = if (importPlayback) incoming.backgroundVideoAudioEnabled else backgroundVideoAudioEnabled,
        autoEnterPip = if (importPlayback) incoming.autoEnterPip else autoEnterPip,
        sessionRestoreMode = if (importPlayback) incoming.sessionRestoreMode else sessionRestoreMode,
        offerDeleteAfterPlaybackMovies = if (importPlayback) incoming.offerDeleteAfterPlaybackMovies else offerDeleteAfterPlaybackMovies,
        offerDeleteAfterPlaybackEpisodes = if (importPlayback) incoming.offerDeleteAfterPlaybackEpisodes else offerDeleteAfterPlaybackEpisodes,
        customDeviceProfileJson = if (importPlayback) incoming.customDeviceProfileJson else customDeviceProfileJson,
        pgsSubtitleDirectPlay = if (importSubtitles) incoming.pgsSubtitleDirectPlay else pgsSubtitleDirectPlay,
        liveStreamOption = if (importSync) incoming.liveStreamOption else liveStreamOption,
        userDataSyncEnabled = if (importMisc) incoming.userDataSyncEnabled else userDataSyncEnabled,
        androidTvWatchNextEnabled = if (importMisc) incoming.androidTvWatchNextEnabled else androidTvWatchNextEnabled,
    )
}

internal fun AudioSlice.mergeWith(
    incoming: AudioSlice,
    categories: Set<PreferenceResetCategory>,
): AudioSlice {
    val importPlayback = PreferenceResetCategory.PLAYBACK in categories
    val importAudio = PreferenceResetCategory.AUDIO in categories
    if (!importPlayback && !importAudio) return this
    return copy(
        audioDelayMs = if (importPlayback) incoming.audioDelayMs else audioDelayMs,
        audioDefaultSpeed = if (importAudio) incoming.audioDefaultSpeed else audioDefaultSpeed,
        audioNightModeVolume = if (importAudio) incoming.audioNightModeVolume else audioNightModeVolume,
        audioNightModeGain = if (importAudio) incoming.audioNightModeGain else audioNightModeGain,
        audioSkipPreviousThresholdMs = if (importAudio) incoming.audioSkipPreviousThresholdMs else audioSkipPreviousThresholdMs,
        audioAutoplayNext = if (importAudio) incoming.audioAutoplayNext else audioAutoplayNext,
        audioPreloadBufferSize = if (importAudio) incoming.audioPreloadBufferSize else audioPreloadBufferSize,
        audioNormalizationMode = if (importAudio) incoming.audioNormalizationMode else audioNormalizationMode,
        audioNormalizationEnabled = if (importAudio) incoming.audioNormalizationEnabled else audioNormalizationEnabled,
        replayGainPreAmpDb = if (importAudio) incoming.replayGainPreAmpDb else replayGainPreAmpDb,
        channelMixMode = if (importAudio) incoming.channelMixMode else channelMixMode,
        channelMixEnabled = if (importAudio) incoming.channelMixEnabled else channelMixEnabled,
        audioGaplessEnabled = if (importAudio) incoming.audioGaplessEnabled else audioGaplessEnabled,
        audioCrossfadeDurationMs = if (importAudio) incoming.audioCrossfadeDurationMs else audioCrossfadeDurationMs,
        audioLyricsVisible = if (importAudio) incoming.audioLyricsVisible else audioLyricsVisible,
        audioVisualizerEnabled = if (importAudio) incoming.audioVisualizerEnabled else audioVisualizerEnabled,
        sleepTimerDurationMs = if (importAudio) incoming.sleepTimerDurationMs else sleepTimerDurationMs,
        sleepTimerEndOfEpisode = if (importAudio) incoming.sleepTimerEndOfEpisode else sleepTimerEndOfEpisode,
    )
}

internal fun NotificationSlice.mergeWith(
    incoming: NotificationSlice,
    categories: Set<PreferenceResetCategory>,
): NotificationSlice {
    val importNotifications = PreferenceResetCategory.NOTIFICATIONS in categories
    val importNewsletter = PreferenceResetCategory.NEWSLETTER in categories
    if (!importNotifications && !importNewsletter) return this
    return copy(
        notificationPreferences = if (importNotifications) incoming.notificationPreferences else notificationPreferences,
        newsletterEnabled = if (importNewsletter) incoming.newsletterEnabled else newsletterEnabled,
        newsletterDayOfWeek = if (importNewsletter) incoming.newsletterDayOfWeek else newsletterDayOfWeek,
        newsletterLastViewedMs = if (importNewsletter) incoming.newsletterLastViewedMs else newsletterLastViewedMs,
        enabledNewsletterSections = if (importNewsletter) incoming.enabledNewsletterSections else enabledNewsletterSections,
        newsletterSectionOrder = if (importNewsletter) incoming.newsletterSectionOrder else newsletterSectionOrder,
    )
}

internal fun ExperimentalSlice.mergeWith(
    incoming: ExperimentalSlice,
    categories: Set<PreferenceResetCategory>,
): ExperimentalSlice {
    val importExp = PreferenceResetCategory.EXPERIMENTAL in categories
    val importMisc = PreferenceResetCategory.MISC_APP in categories
    if (!importExp && !importMisc) return this
    return copy(
        enabledExperimentalFeatures = if (importExp) incoming.enabledExperimentalFeatures else enabledExperimentalFeatures,
        selfUpdateCheckEnabled = if (importMisc) incoming.selfUpdateCheckEnabled else selfUpdateCheckEnabled,
        selfUpdateDownloadEnabled = if (importMisc) incoming.selfUpdateDownloadEnabled else selfUpdateDownloadEnabled,
        appLanguage = if (importMisc) incoming.appLanguage else appLanguage,
        showShareMediaOption = if (importMisc) incoming.showShareMediaOption else showShareMediaOption,
        hideSearchHistory = if (importMisc) incoming.hideSearchHistory else hideSearchHistory,
        preferAudioDescription = if (importMisc) incoming.preferAudioDescription else preferAudioDescription,
        dismissedUpdateVersion = if (importMisc) incoming.dismissedUpdateVersion else dismissedUpdateVersion,
        dismissedUpdateAtMs = if (importMisc) incoming.dismissedUpdateAtMs else dismissedUpdateAtMs,
        updateDismissPeriod = if (importMisc) incoming.updateDismissPeriod else updateDismissPeriod,
    )
}

internal fun SubtitleSlice.mergeWith(
    incoming: SubtitleSlice,
    categories: Set<PreferenceResetCategory>,
): SubtitleSlice {
    val importSubs = PreferenceResetCategory.SUBTITLES_LANGUAGE in categories
    val importMisc = PreferenceResetCategory.MISC_APP in categories
    if (!importSubs && !importMisc) return this
    return copy(
        preferredSubtitleLanguage = if (importSubs) incoming.preferredSubtitleLanguage else preferredSubtitleLanguage,
        subtitlesForcedOnly = if (importSubs) incoming.subtitlesForcedOnly else subtitlesForcedOnly,
        showOnlyPreferredSubtitles = if (importSubs) incoming.showOnlyPreferredSubtitles else showOnlyPreferredSubtitles,
        preferredAudioLanguage = if (importSubs) incoming.preferredAudioLanguage else preferredAudioLanguage,
        subtitleDelayByItem = if (importSubs) incoming.subtitleDelayByItem else subtitleDelayByItem,
        languageRules = if (importSubs) incoming.languageRules else languageRules,
        subtitleStyle = if (importSubs) incoming.subtitleStyle else subtitleStyle,
        subtitlePreviewInSettings = if (importSubs) incoming.subtitlePreviewInSettings else subtitlePreviewInSettings,
        highContrastSubtitles = if (importSubs) incoming.highContrastSubtitles else highContrastSubtitles,
        hdrSubtitleStyleEnabled = if (importSubs) incoming.hdrSubtitleStyleEnabled else hdrSubtitleStyleEnabled,
        hdrSubtitleStyle = if (importSubs) incoming.hdrSubtitleStyle else hdrSubtitleStyle,
        userStylePresets = if (importSubs) incoming.userStylePresets else userStylePresets,
        // SubtitleSlice also carries appLanguage/preferAudioDescription for MISC_APP,
        // but those duplicate ExperimentalSlice's fields — keep MISC_APP there.
        // This slice's MISC_APP keys are appLanguage/preferAudioDescription as well;
        // merge them when MISC_APP is requested so both stores stay consistent.
        appLanguage = if (importMisc) incoming.appLanguage else appLanguage,
        preferAudioDescription = if (importMisc) incoming.preferAudioDescription else preferAudioDescription,
    )
}
