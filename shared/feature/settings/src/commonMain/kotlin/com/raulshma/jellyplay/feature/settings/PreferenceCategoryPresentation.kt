package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AccessPoint
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.BrandSublimeText
import com.composables.icons.tabler.outline.Cards
import com.composables.icons.tabler.outline.Cash
import com.composables.icons.tabler.outline.Cpu
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.Flask
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Moon
import com.composables.icons.tabler.outline.Music
import com.composables.icons.tabler.outline.Palette
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerTrackNext
import com.composables.icons.tabler.outline.ScreenShare
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.ShieldLock
import com.composables.icons.tabler.outline.Subtitles
import com.composables.icons.tabler.outline.Volume
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.HasDisplayName
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_appearance
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_audio
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_audio_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_downloads_network
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_experimental
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_home_discovery
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_misc_app
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_newsletter
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_notifications
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_player_engines
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_playback
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_screensaver
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_security
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_subtitles_language
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_syncplay_casting

/**
 * Presentation model for a single preference field shown on the Factory Reset
 * screen: its localized label plus the formatted current and factory values.
 * [changed] is precomputed by [PreferenceCategoryView.changedFields] so the UI
 * never needs to re-derive equality.
 */
@Immutable
data class PreferenceField(
    val label: String,
    val currentValue: String,
    val factoryValue: String,
) {
    val changed: Boolean get() = currentValue != factoryValue
}

/**
 * Presentation bundle for one [PreferenceResetCategory]: icon, display name,
 * and the ordered list of user-facing fields to surface. "User-facing" means
 * the fields a user would recognize from the corresponding settings screen —
 * internal bookkeeping keys (migration flags, recall slots) are intentionally
 * omitted even though the store's reset key list still covers them.
 *
 * Callers pass the factory baseline [UserPreferences] once (the
 * [FactoryResetViewModel] exposes it) so it isn't reconstructed per field;
 * use [changedFields] for the diff subset and [fields] for the full list.
 */
@Immutable
data class PreferenceCategoryView(
    val category: PreferenceResetCategory,
    val icon: ImageVector,
    val displayNameRes: StringResource,
    val fields: (prefs: UserPreferences, factory: UserPreferences) -> List<PreferenceField>,
) {
    /** Fields whose current value differs from the factory default. */
    fun changedFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> =
        fields(prefs, factory).filter { it.changed }

    /** Total fields surfaced for this category (for "X of Y changed"). */
    fun totalFields(prefs: UserPreferences, factory: UserPreferences): Int =
        fields(prefs, factory).size
}

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

private fun Boolean.onOff(): String = if (this) "On" else "Off"

/** Prettify an enum without a `displayName` (e.g. `HW_PREFERRED` → `Hw Preferred`). */
private fun Enum<*>.pretty(): String =
    name.split('_').joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.titlecase() }
    }

private fun Any?.enumDisplay(): String = when (this) {
    null -> "System Default"
    is HasDisplayName -> displayName
    is Enum<*> -> pretty()
    else -> toString()
}

private fun Float.fmt1(): String = String.format("%.1f", this)
private fun Float.pct(): String = "${(this * 100).toInt()}%"
private fun Long.millisToSeconds(): String = "${this / 1000.0}s"
private fun Long.millisToMinutes(): String = "${this / 60_000.0}m"

/** Short, stable summary of a [SubtitleStyle] (skips nullable/empty fields). */
private fun SubtitleStyle.summary(): String =
    listOfNotNull(
        "Size ${fontSize}pt",
        fontColor.name.lowercase().replaceFirstChar { it.titlecase() },
        if (backgroundOpacity > 0f) "BG ${(backgroundOpacity * 100).toInt()}%" else null,
        edgeType.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
        if (bold) "Bold" else null,
        if (italic) "Italic" else null,
    ).joinToString(", ")

private fun EqualizerSettings.summary(): String =
    "Preset bands: ${bandLevels.joinToString(",") { "%+d".format(it) }}"

// ---------------------------------------------------------------------------
// Field builders — one lambda per category. Each returns the user-facing fields.
// `factory` is the baseline instance; `prefs` is live.
// ---------------------------------------------------------------------------

private fun appearanceFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Theme Mode", prefs.themeMode.enumDisplay(), factory.themeMode.enumDisplay()),
    PreferenceField("Dynamic Theming", prefs.dynamicTheming.onOff(), factory.dynamicTheming.onOff()),
    PreferenceField("OLED Mode", prefs.oledMode.onOff(), factory.oledMode.onOff()),
    PreferenceField("Contrast Level", prefs.contrastLevel.enumDisplay(), factory.contrastLevel.enumDisplay()),
    PreferenceField("Accent Color", prefs.accentColorSwatch, factory.accentColorSwatch),
    PreferenceField("Color Style", prefs.colorStyle.enumDisplay(), factory.colorStyle.enumDisplay()),
    PreferenceField("Performance Mode", prefs.performanceMode.onOff(), factory.performanceMode.onOff()),
    PreferenceField("Reduce Motion", prefs.reduceMotionEnabled.onOff(), factory.reduceMotionEnabled.onOff()),
    PreferenceField("Synthwave Mode", prefs.synthwaveMode.onOff(), factory.synthwaveMode.onOff()),
    PreferenceField("Soothing Mode", prefs.soothingMode.onOff(), factory.soothingMode.onOff()),
    PreferenceField("Monochrome Mode", prefs.monochromeMode.onOff(), factory.monochromeMode.onOff()),
    PreferenceField("Backdrop Theme Music", prefs.backdropThemeMusicEnabled.onOff(), factory.backdropThemeMusicEnabled.onOff()),
    PreferenceField("Blue Light Filter", prefs.blueLightFilterEnabled.onOff(), factory.blueLightFilterEnabled.onOff()),
    PreferenceField("Blue Light Strength", prefs.blueLightFilterStrength.pct(), factory.blueLightFilterStrength.pct()),
    PreferenceField("Date Format", prefs.dateFormatPreference.enumDisplay(), factory.dateFormatPreference.enumDisplay()),
    PreferenceField("Font Scale", prefs.appFontScale.enumDisplay(), factory.appFontScale.enumDisplay()),
    PreferenceField("Scheduled Theme Start", "${prefs.scheduledThemeStartHour}:00", "${factory.scheduledThemeStartHour}:00"),
    PreferenceField("Scheduled Theme End", "${prefs.scheduledThemeEndHour}:00", "${factory.scheduledThemeEndHour}:00"),
    PreferenceField("Color Blind Mode", prefs.colorBlindMode.enumDisplay(), factory.colorBlindMode.enumDisplay()),
    PreferenceField("Hand Mode", prefs.handMode.enumDisplay(), factory.handMode.enumDisplay()),
)

private fun playbackFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Preferred Player", prefs.preferredPlayer.enumDisplay(), factory.preferredPlayer.enumDisplay()),
    PreferenceField("Wi-Fi Streaming Quality", prefs.streamingQuality.enumDisplay(), factory.streamingQuality.enumDisplay()),
    PreferenceField("Cellular Streaming Quality", prefs.cellularStreamingQuality.enumDisplay(), factory.cellularStreamingQuality.enumDisplay()),
    PreferenceField("Playback Mode", prefs.playbackMode.enumDisplay(), factory.playbackMode.enumDisplay()),
    PreferenceField("Decoder Mode", prefs.decoderMode.enumDisplay(), factory.decoderMode.enumDisplay()),
    PreferenceField("Audio Passthrough", prefs.audioPassthrough.onOff(), factory.audioPassthrough.onOff()),
    PreferenceField("Frame Rate Matching", prefs.frameRateMatching.onOff(), factory.frameRateMatching.onOff()),
    PreferenceField("Orientation", prefs.videoDefaultOrientation.enumDisplay(), factory.videoDefaultOrientation.enumDisplay()),
    PreferenceField("Aspect Ratio", prefs.videoDefaultAspectRatio, factory.videoDefaultAspectRatio),
    PreferenceField("Preload Buffer", prefs.videoPreloadBufferSize.enumDisplay(), factory.videoPreloadBufferSize.enumDisplay()),
    PreferenceField("Gestures", prefs.videoGesturesEnabled.onOff(), factory.videoGesturesEnabled.onOff()),
    PreferenceField("Pass-Out Protection (h)", prefs.videoPassOutProtectionHours.toString(), factory.videoPassOutProtectionHours.toString()),
    PreferenceField("Skip Back On Resume", prefs.videoSkipBackOnResumeMs.millisToSeconds(), factory.videoSkipBackOnResumeMs.millisToSeconds()),
    PreferenceField("Hold-To-Speed", prefs.videoHoldSpeedEnabled.onOff(), factory.videoHoldSpeedEnabled.onOff()),
    PreferenceField("Hold Speed Multiplier", prefs.videoHoldSpeedMultiplier.fmt1() + "x", factory.videoHoldSpeedMultiplier.fmt1() + "x"),
    PreferenceField("Default Speed", prefs.videoDefaultSpeed.fmt1() + "x", factory.videoDefaultSpeed.fmt1() + "x"),
    PreferenceField("Brightness", prefs.videoBrightnessLevel.pct(), factory.videoBrightnessLevel.pct()),
    PreferenceField("Autoplay Next", prefs.videoAutoplayNext.onOff(), factory.videoAutoplayNext.onOff()),
    PreferenceField("Trailer Autoplay", prefs.trailerAutoplay.onOff(), factory.trailerAutoplay.onOff()),
    PreferenceField("Cinema Mode", prefs.cinemaModeEnabled.onOff(), factory.cinemaModeEnabled.onOff()),
    PreferenceField("Remember Brightness", prefs.videoRememberBrightness.onOff(), factory.videoRememberBrightness.onOff()),
    PreferenceField("Auto Skip Intro", prefs.videoAutoSkipIntro.onOff(), factory.videoAutoSkipIntro.onOff()),
    PreferenceField("Auto Skip Outro", prefs.videoAutoSkipOutro.onOff(), factory.videoAutoSkipOutro.onOff()),
    PreferenceField("Remember Muted", prefs.videoRememberMuted.onOff(), factory.videoRememberMuted.onOff()),
    PreferenceField("Default Muted", prefs.videoMuted.onOff(), factory.videoMuted.onOff()),
    PreferenceField("Gesture Indicator Side", prefs.videoGestureIndicatorSide.enumDisplay(), factory.videoGestureIndicatorSide.enumDisplay()),
    PreferenceField("Seek Duration", prefs.videoSeekDurationMs.millisToSeconds(), factory.videoSeekDurationMs.millisToSeconds()),
    PreferenceField("Controls Timeout", prefs.videoControlsTimeoutMs.millisToSeconds(), factory.videoControlsTimeoutMs.millisToSeconds()),
    PreferenceField("Swipe Seek Max", prefs.videoSwipeSeekMaxMs.millisToSeconds(), factory.videoSwipeSeekMaxMs.millisToSeconds()),
    PreferenceField("Audio Delay", prefs.audioDelayMs.toString() + " ms", factory.audioDelayMs.toString() + " ms"),
    PreferenceField("Trickplay", prefs.trickplayEnabled.onOff(), factory.trickplayEnabled.onOff()),
    PreferenceField("Trickplay On Seek Gesture", prefs.trickplayOnSeekGesture.onOff(), factory.trickplayOnSeekGesture.onOff()),
    PreferenceField("Episode Browser", prefs.videoEpisodeBrowserEnabled.onOff(), factory.videoEpisodeBrowserEnabled.onOff()),
    PreferenceField("Playback Metadata", prefs.videoShowPlaybackMetadata.onOff(), factory.videoShowPlaybackMetadata.onOff()),
    PreferenceField("Background Video Audio", prefs.backgroundVideoAudioEnabled.onOff(), factory.backgroundVideoAudioEnabled.onOff()),
    PreferenceField("Autoplay Countdown (s)", prefs.autoPlayCountdownSec.toString(), factory.autoPlayCountdownSec.toString()),
    PreferenceField("Keep Screen On", prefs.keepScreenOnDuringVideo.onOff(), factory.keepScreenOnDuringVideo.onOff()),
    PreferenceField("Incognito Mode", prefs.incognitoModeEnabled.onOff(), factory.incognitoModeEnabled.onOff()),
    PreferenceField("Clock In Player", prefs.showClockInPlayer.onOff(), factory.showClockInPlayer.onOff()),
    PreferenceField("Time Remaining", prefs.showTimeRemaining.onOff(), factory.showTimeRemaining.onOff()),
    PreferenceField("Pause On Focus Loss", prefs.pauseOnAudioFocusLoss.onOff(), factory.pauseOnAudioFocusLoss.onOff()),
    PreferenceField("Duck On Transient Loss", prefs.duckOnTransientFocusLoss.onOff(), factory.duckOnTransientFocusLoss.onOff()),
    PreferenceField("TV Zoom", prefs.tvZoomModePercent.pct(), factory.tvZoomModePercent.pct()),
    PreferenceField("Segment Behaviors", prefs.segmentBehaviors.toSummary(), factory.segmentBehaviors.toSummary()),
)

private fun Map<*, SegmentBehavior>.toSummary(): String =
    entries.joinToString(", ") { "${it.key}: ${it.value.enumDisplay()}" }

private fun audioFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Default Speed", prefs.audioDefaultSpeed.fmt1() + "x", factory.audioDefaultSpeed.fmt1() + "x"),
    PreferenceField("Visualizer", prefs.audioVisualizerEnabled.onOff(), factory.audioVisualizerEnabled.onOff()),
    PreferenceField("Gapless", prefs.audioGaplessEnabled.onOff(), factory.audioGaplessEnabled.onOff()),
    PreferenceField("Crossfade", prefs.audioCrossfadeDurationMs.millisToSeconds(), factory.audioCrossfadeDurationMs.millisToSeconds()),
    PreferenceField("Normalization Enabled", prefs.audioNormalizationEnabled.onOff(), factory.audioNormalizationEnabled.onOff()),
    PreferenceField("Normalization Mode", prefs.audioNormalizationMode.enumDisplay(), factory.audioNormalizationMode.enumDisplay()),
    PreferenceField("ReplayGain Pre-Amp (dB)", prefs.replayGainPreAmpDb.fmt1(), factory.replayGainPreAmpDb.fmt1()),
    PreferenceField("Channel Mix Enabled", prefs.channelMixEnabled.onOff(), factory.channelMixEnabled.onOff()),
    PreferenceField("Channel Mix Mode", prefs.channelMixMode.enumDisplay(), factory.channelMixMode.enumDisplay()),
    PreferenceField("Equalizer Enabled", prefs.equalizerEnabled.onOff(), factory.equalizerEnabled.onOff()),
    PreferenceField("Equalizer Settings", prefs.equalizerSettings.summary(), factory.equalizerSettings.summary()),
    PreferenceField("Equalizer Preset", prefs.equalizerPreset.enumDisplay(), factory.equalizerPreset.enumDisplay()),
    PreferenceField("Bass Boost", prefs.bassBoostEnabled.onOff(), factory.bassBoostEnabled.onOff()),
    PreferenceField("Bass Boost Strength", prefs.bassBoostStrength.enumDisplay(), factory.bassBoostStrength.enumDisplay()),
    PreferenceField("Virtualizer", prefs.virtualizerEnabled.onOff(), factory.virtualizerEnabled.onOff()),
    PreferenceField("Virtualizer Strength", prefs.virtualizerStrength.toString(), factory.virtualizerStrength.toString()),
    PreferenceField("Reverb Preset", prefs.reverbPreset.enumDisplay(), factory.reverbPreset.enumDisplay()),
    PreferenceField("Volume Boost", prefs.volumeBoostEnabled.onOff(), factory.volumeBoostEnabled.onOff()),
    PreferenceField("Volume Boost Gain", prefs.volumeBoostGain.toString() + " dB", factory.volumeBoostGain.toString() + " dB"),
    PreferenceField("L/R Balance", prefs.lrBalance.fmt1(), factory.lrBalance.fmt1()),
    PreferenceField("Auto-EQ By Genre", prefs.autoEqByGenre.onOff(), factory.autoEqByGenre.onOff()),
    PreferenceField("Pitch (semitones)", prefs.pitchSemitones.fmt1(), factory.pitchSemitones.fmt1()),
    PreferenceField("Autoplay Next", prefs.audioAutoplayNext.onOff(), factory.audioAutoplayNext.onOff()),
    PreferenceField("Audio Preload Buffer", prefs.audioPreloadBufferSize.enumDisplay(), factory.audioPreloadBufferSize.enumDisplay()),
    PreferenceField("Night Mode Volume", prefs.audioNightModeVolume.pct(), factory.audioNightModeVolume.pct()),
    PreferenceField("Night Mode Gain", prefs.audioNightModeGain.toString(), factory.audioNightModeGain.toString()),
    PreferenceField("Skip Previous Threshold", prefs.audioSkipPreviousThresholdMs.millisToSeconds(), factory.audioSkipPreviousThresholdMs.millisToSeconds()),
    PreferenceField("Night Mode", prefs.nightModeEnabled.onOff(), factory.nightModeEnabled.onOff()),
    PreferenceField("Night Mode Strength", prefs.nightModeStrength.enumDisplay(), factory.nightModeStrength.enumDisplay()),
    PreferenceField("Dialogue Boost", prefs.dialogueBoostEnabled.onOff(), factory.dialogueBoostEnabled.onOff()),
    PreferenceField("Dialogue Boost Strength", prefs.dialogueBoostStrength.enumDisplay(), factory.dialogueBoostStrength.enumDisplay()),
    PreferenceField("Sleep Timer Duration", prefs.sleepTimerDurationMs.millisToMinutes(), factory.sleepTimerDurationMs.millisToMinutes()),
    PreferenceField("Sleep Timer End Of Episode", prefs.sleepTimerEndOfEpisode.onOff(), factory.sleepTimerEndOfEpisode.onOff()),
    PreferenceField("Lyrics Visible", prefs.audioLyricsVisible.onOff(), factory.audioLyricsVisible.onOff()),
)

private fun subtitlesLanguageFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Preferred Subtitle Language", prefs.preferredSubtitleLanguage ?: "System", factory.preferredSubtitleLanguage ?: "System"),
    PreferenceField("Preferred Audio Language", prefs.preferredAudioLanguage ?: "System", factory.preferredAudioLanguage ?: "System"),
    PreferenceField("Forced Subtitles Only", prefs.subtitlesForcedOnly.onOff(), factory.subtitlesForcedOnly.onOff()),
    PreferenceField("Subtitle Preview In Settings", prefs.subtitlePreviewInSettings.onOff(), factory.subtitlePreviewInSettings.onOff()),
    PreferenceField("Subtitle Style", prefs.subtitleStyle.summary(), factory.subtitleStyle.summary()),
    PreferenceField("High Contrast Subtitles", prefs.highContrastSubtitles.onOff(), factory.highContrastSubtitles.onOff()),
    PreferenceField("PGS Direct Play", prefs.pgsSubtitleDirectPlay.onOff(), factory.pgsSubtitleDirectPlay.onOff()),
    PreferenceField("HDR Subtitle Style Enabled", prefs.hdrSubtitleStyleEnabled.onOff(), factory.hdrSubtitleStyleEnabled.onOff()),
    PreferenceField("HDR Subtitle Style", prefs.hdrSubtitleStyle.summary(), factory.hdrSubtitleStyle.summary()),
)

private fun downloadsNetworkFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Wi-Fi Only Downloads", prefs.wifiOnlyDownloads.onOff(), factory.wifiOnlyDownloads.onOff()),
    PreferenceField("Download Connections", prefs.downloadConnections.toString(), factory.downloadConnections.toString()),
    PreferenceField("Max Concurrent Downloads", prefs.maxConcurrentDownloads.toString(), factory.maxConcurrentDownloads.toString()),
    PreferenceField("Download Quality", prefs.downloadQuality.enumDisplay(), factory.downloadQuality.enumDisplay()),
    PreferenceField("Smart Downloads", prefs.smartDownloadsEnabled.onOff(), factory.smartDownloadsEnabled.onOff()),
    PreferenceField("Auto-Download New Episodes", prefs.autoDownloadNewEpisodes.onOff(), factory.autoDownloadNewEpisodes.onOff()),
    PreferenceField("Max Download Storage (GB)", prefs.maxDownloadStorageGb.toString(), factory.maxDownloadStorageGb.toString()),
    PreferenceField("Download Storage Location", prefs.downloadStorageLocation, factory.downloadStorageLocation),
    PreferenceField("Max Cache Size (MB)", prefs.maxCacheSizeMb.toString(), factory.maxCacheSizeMb.toString()),
    PreferenceField("Auto-Delete Cache", prefs.autoDeleteCache.onOff(), factory.autoDeleteCache.onOff()),
    PreferenceField("Manual Offline", prefs.manualOfflineEnabled.onOff(), factory.manualOfflineEnabled.onOff()),
    PreferenceField("Auto Offline", prefs.autoOfflineEnabled.onOff(), factory.autoOfflineEnabled.onOff()),
    PreferenceField("Manual Bandwidth Cap", prefs.manualBandwidthCap.toString(), factory.manualBandwidthCap.toString()),
    PreferenceField("Metered Network Behavior", prefs.meteredNetworkBehavior.enumDisplay(), factory.meteredNetworkBehavior.enumDisplay()),
    PreferenceField("Adaptive Bitrate", prefs.adaptiveBitrateEnabled.onOff(), factory.adaptiveBitrateEnabled.onOff()),
    PreferenceField("Data Saver", prefs.dataSaverEnabled.onOff(), factory.dataSaverEnabled.onOff()),
    PreferenceField("Verbose Network Logging", prefs.verboseNetworkLogging.onOff(), factory.verboseNetworkLogging.onOff()),
    PreferenceField("Network Timeout Preset", prefs.networkTimeoutPreset.enumDisplay(), factory.networkTimeoutPreset.enumDisplay()),
    PreferenceField("Cellular Size Warning (MB)", prefs.cellularDownloadSizeWarningMb.toString(), factory.cellularDownloadSizeWarningMb.toString()),
    PreferenceField("Download Schedule Enabled", prefs.downloadScheduleEnabled.onOff(), factory.downloadScheduleEnabled.onOff()),
    PreferenceField("Schedule Start (h)", prefs.downloadScheduleWindow.startHour.toString(), factory.downloadScheduleWindow.startHour.toString()),
    PreferenceField("Schedule End (h)", prefs.downloadScheduleWindow.endHour.toString(), factory.downloadScheduleWindow.endHour.toString()),
    PreferenceField("Schedule Wi-Fi Only", prefs.downloadScheduleWindow.wifiOnly.onOff(), factory.downloadScheduleWindow.wifiOnly.onOff()),
)

private fun homeDiscoveryFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Home Mode", prefs.homeMode.enumDisplay(), factory.homeMode.enumDisplay()),
    PreferenceField("Home Hero", prefs.homeHeroEnabled.onOff(), factory.homeHeroEnabled.onOff()),
    PreferenceField("Hide Top Header On Scroll", prefs.hideTopHeaderOnScroll.onOff(), factory.hideTopHeaderOnScroll.onOff()),
    PreferenceField("Enabled Home Sections", prefs.enabledHomeSectionTypes.size.toString() + " sections", factory.enabledHomeSectionTypes.size.toString() + " sections"),
    PreferenceField("Home Section Order", prefs.homeSectionOrder.size.toString() + " sections", factory.homeSectionOrder.size.toString() + " sections"),
    PreferenceField("Library Home Overrides", prefs.libraryHomeSectionOverrides.size.toString(), factory.libraryHomeSectionOverrides.size.toString()),
    PreferenceField("Library View Mode", prefs.libraryViewMode.enumDisplay(), factory.libraryViewMode.enumDisplay()),
    PreferenceField("Nav Bar Labels", prefs.navBarShowLabels.onOff(), factory.navBarShowLabels.onOff()),
    PreferenceField("Hide Nav On Scroll", prefs.hideBottomNavOnScroll.onOff(), factory.hideBottomNavOnScroll.onOff()),
    PreferenceField("Nav Item Order", prefs.navItemOrder.size.toString(), factory.navItemOrder.size.toString()),
    PreferenceField("Hidden Nav Items", prefs.hiddenNavItems.size.toString(), factory.hiddenNavItems.size.toString()),
    PreferenceField("Unwatched Badge", prefs.showUnwatchedBadge.onOff(), factory.showUnwatchedBadge.onOff()),
    PreferenceField("Hide Watched Items", prefs.hideWatchedItems.onOff(), factory.hideWatchedItems.onOff()),
    PreferenceField("Watched Checkmark", prefs.showWatchedCheckmark.onOff(), factory.showWatchedCheckmark.onOff()),
    PreferenceField("External Ratings", prefs.showExternalRatings.onOff(), factory.showExternalRatings.onOff()),
    PreferenceField("Merge CW + Next Up", prefs.mergeContinueWatchingAndNextUp.onOff(), factory.mergeContinueWatchingAndNextUp.onOff()),
    PreferenceField("Next Up Max Days", prefs.nextUpMaxDays.toString(), factory.nextUpMaxDays.toString()),
    PreferenceField("Next Up Rewatching", prefs.nextUpRewatching.onOff(), factory.nextUpRewatching.onOff()),
    PreferenceField("Next Up Excluded Series", prefs.nextUpExcludedSeriesIds.size.toString(), factory.nextUpExcludedSeriesIds.size.toString()),
    PreferenceField("Hidden CW Items", prefs.hiddenCwItemIds.size.toString(), factory.hiddenCwItemIds.size.toString()),
    PreferenceField("Pinned Home Sections", prefs.pinnedHomeSections.size.toString(), factory.pinnedHomeSections.size.toString()),
    PreferenceField("Home Layout Presets", prefs.homeLayoutPresets.size.toString(), factory.homeLayoutPresets.size.toString()),
    PreferenceField("CW Click Behavior", prefs.continueWatchingClickBehavior.enumDisplay(), factory.continueWatchingClickBehavior.enumDisplay()),
    PreferenceField("Library Sort Overrides", prefs.defaultLibrarySortOrders.size.toString(), factory.defaultLibrarySortOrders.size.toString()),
    PreferenceField("Library View Overrides", prefs.libraryViewModes.size.toString(), factory.libraryViewModes.size.toString()),
    PreferenceField("Library Filter Overrides", prefs.libraryFilters.size.toString(), factory.libraryFilters.size.toString()),
    PreferenceField("Hide Episode Thumbnails", prefs.hideEpisodeThumbnails.onOff(), factory.hideEpisodeThumbnails.onOff()),
    PreferenceField("Episodes Descending", prefs.episodesDescending.onOff(), factory.episodesDescending.onOff()),
    PreferenceField("Skip Specials", prefs.skipSpecials.onOff(), factory.skipSpecials.onOff()),
    PreferenceField("Clock On Home", prefs.showClockOnHome.onOff(), factory.showClockOnHome.onOff()),
    PreferenceField("Settings In Home Search", prefs.showSettingsInHomeSearch.onOff(), factory.showSettingsInHomeSearch.onOff()),
)

private fun audioCacheFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Audio Caching", prefs.audioCachingEnabled.onOff(), factory.audioCachingEnabled.onOff()),
    PreferenceField("Cache Size (MB)", prefs.audioCacheSizeMb.toString(), factory.audioCacheSizeMb.toString()),
    PreferenceField("Prefetch Lookahead", prefs.audioPrefetchLookahead.toString(), factory.audioPrefetchLookahead.toString()),
    PreferenceField("Prefetch Backfill", prefs.audioPrefetchBackfill.toString(), factory.audioPrefetchBackfill.toString()),
    PreferenceField("Cache Network Policy", prefs.audioCacheNetworkPolicy.enumDisplay(), factory.audioCacheNetworkPolicy.enumDisplay()),
    PreferenceField("Cellular Monthly Cap (MB)", prefs.audioCacheCellularMonthlyCapMb.toString(), factory.audioCacheCellularMonthlyCapMb.toString()),
)

private fun securityFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("PIN Lock", prefs.pinLockEnabled.onOff(), factory.pinLockEnabled.onOff()),
    PreferenceField("PIN Set", if (prefs.pinHash != null) "Yes" else "No", if (factory.pinHash != null) "Yes" else "No"),
    PreferenceField("Biometric Lock", prefs.biometricLockEnabled.onOff(), factory.biometricLockEnabled.onOff()),
    PreferenceField("Use PIN For Player Lock", prefs.usePinForPlayerLock.onOff(), factory.usePinForPlayerLock.onOff()),
    PreferenceField("Auto-Lock Timer", prefs.autoLockTimerMs.millisToSeconds(), factory.autoLockTimerMs.millisToSeconds()),
    PreferenceField("Remote Control", prefs.remoteControlEnabled.onOff(), factory.remoteControlEnabled.onOff()),
)

private fun notificationsFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Notifications Enabled", prefs.notificationPreferences.enabled.onOff(), factory.notificationPreferences.enabled.onOff()),
    PreferenceField("Check Frequency", prefs.notificationPreferences.checkFrequency.enumDisplay(), factory.notificationPreferences.checkFrequency.enumDisplay()),
    PreferenceField("Quiet Hours Enabled", prefs.notificationPreferences.quietHoursEnabled.onOff(), factory.notificationPreferences.quietHoursEnabled.onOff()),
    PreferenceField("Quiet Hours Start", prefs.notificationPreferences.quietHoursStart.toString() + " min", factory.notificationPreferences.quietHoursStart.toString() + " min"),
    PreferenceField("Quiet Hours End", prefs.notificationPreferences.quietHoursEnd.toString() + " min", factory.notificationPreferences.quietHoursEnd.toString() + " min"),
    PreferenceField("Sound", prefs.notificationPreferences.soundEnabled.onOff(), factory.notificationPreferences.soundEnabled.onOff()),
    PreferenceField("Vibrate", prefs.notificationPreferences.vibrateEnabled.onOff(), factory.notificationPreferences.vibrateEnabled.onOff()),
    PreferenceField("Lights", prefs.notificationPreferences.lightsEnabled.onOff(), factory.notificationPreferences.lightsEnabled.onOff()),
    PreferenceField("Max Per Check", prefs.notificationPreferences.maxPerCheck.toString(), factory.notificationPreferences.maxPerCheck.toString()),
    PreferenceField("Per-Library Configs", prefs.notificationPreferences.libraryConfigs.size.toString(), factory.notificationPreferences.libraryConfigs.size.toString()),
)

private fun screensaverFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Image Categories", prefs.dreamImageCategories.size.toString(), factory.dreamImageCategories.size.toString()),
    PreferenceField("Transition Style", prefs.dreamTransitionStyle.enumDisplay(), factory.dreamTransitionStyle.enumDisplay()),
    PreferenceField("Ken Burns", prefs.dreamKenBurnsEnabled.onOff(), factory.dreamKenBurnsEnabled.onOff()),
    PreferenceField("Show Title", prefs.dreamShowTitle.onOff(), factory.dreamShowTitle.onOff()),
    PreferenceField("Slideshow Interval", prefs.dreamSlideshowIntervalMs.millisToSeconds(), factory.dreamSlideshowIntervalMs.millisToSeconds()),
)

private fun newsletterFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Newsletter Enabled", prefs.newsletterEnabled.onOff(), factory.newsletterEnabled.onOff()),
    PreferenceField("Day Of Week", prefs.newsletterDayOfWeek.toString(), factory.newsletterDayOfWeek.toString()),
    PreferenceField("Enabled Sections", prefs.enabledNewsletterSections.size.toString(), factory.enabledNewsletterSections.size.toString()),
    PreferenceField("Section Order", prefs.newsletterSectionOrder.size.toString(), factory.newsletterSectionOrder.size.toString()),
)

private fun syncplayCastingFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("SyncPlay Join Behavior", prefs.syncPlayJoinBehavior.enumDisplay(), factory.syncPlayJoinBehavior.enumDisplay()),
    PreferenceField("SyncPlay Tolerance", prefs.syncPlayToleranceMs.toString() + " ms", factory.syncPlayToleranceMs.toString() + " ms"),
    PreferenceField("SyncPlay Auto-Accept Invites", prefs.syncPlayAutoAcceptInvites.onOff(), factory.syncPlayAutoAcceptInvites.onOff()),
    PreferenceField("Default Casting Strategy", prefs.defaultCastingStrategy.enumDisplay(), factory.defaultCastingStrategy.enumDisplay()),
    PreferenceField("Background Casting", prefs.backgroundCastingEnabled.onOff(), factory.backgroundCastingEnabled.onOff()),
    PreferenceField("Preferred Renderer", prefs.preferredRenderer ?: "Auto", factory.preferredRenderer ?: "Auto"),
    PreferenceField("DVR Pre-Padding (min)", prefs.dvrPrePaddingMinutes.toString(), factory.dvrPrePaddingMinutes.toString()),
    PreferenceField("DVR Post-Padding (min)", prefs.dvrPostPaddingMinutes.toString(), factory.dvrPostPaddingMinutes.toString()),
    PreferenceField("DVR Recording Quality", prefs.dvrRecordingQuality, factory.dvrRecordingQuality),
)

private fun playerEnginesFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> {
    val m = prefs.mpvConfig
    val mf: MpvEngineConfig = factory.mpvConfig
    val v = prefs.libVlcConfig
    val vf = factory.libVlcConfig
    val e = prefs.exoPlayerConfig
    val ef = factory.exoPlayerConfig
    return listOf(
        PreferenceField("MPV Video Output", m.videoOutput.enumDisplay(), mf.videoOutput.enumDisplay()),
        PreferenceField("MPV Scaler", m.scaler.enumDisplay(), mf.scaler.enumDisplay()),
        PreferenceField("MPV Deband", m.deband.onOff(), mf.deband.onOff()),
        PreferenceField("MPV Interpolation", m.interpolation.onOff(), mf.interpolation.onOff()),
        PreferenceField("MPV Audio Output", m.audioOutput.enumDisplay(), mf.audioOutput.enumDisplay()),
        PreferenceField("MPV Audio Fallback", m.audioFallback?.enumDisplay() ?: "Auto", mf.audioFallback?.enumDisplay() ?: "Auto"),
        PreferenceField("MPV Demuxer Max Bytes", m.demuxerMaxBytes.enumDisplay(), mf.demuxerMaxBytes.enumDisplay()),
        PreferenceField("MPV Skip Loop Filter", m.skipLoopFilter.enumDisplay(), mf.skipLoopFilter.enumDisplay()),
        PreferenceField("MPV Frame Drop", m.frameDrop.enumDisplay(), mf.frameDrop.enumDisplay()),
        PreferenceField("MPV HWDEC Override", m.hwdecOverride?.enumDisplay() ?: "Auto", mf.hwdecOverride?.enumDisplay() ?: "Auto"),
        PreferenceField("LibVLC Audio Output", v.audioOutput.enumDisplay(), vf.audioOutput.enumDisplay()),
        PreferenceField("LibVLC Time Stretch", v.audioTimeStretch.onOff(), vf.audioTimeStretch.onOff()),
        PreferenceField("LibVLC Network Caching", v.networkCaching.toString() + " ms", vf.networkCaching.toString() + " ms"),
        PreferenceField("LibVLC Video Output", v.videoOutput.enumDisplay(), vf.videoOutput.enumDisplay()),
        PreferenceField("LibVLC Skip Loop Filter", v.skipLoopFilter.toString(), vf.skipLoopFilter.toString()),
        PreferenceField("LibVLC Skip Frame", v.skipFrame.toString(), vf.skipFrame.toString()),
        PreferenceField("LibVLC Decoder Threads", v.decoderThreads.toString(), vf.decoderThreads.toString()),
        PreferenceField("LibVLC Drop Late Frames", v.dropLateFrames.onOff(), vf.dropLateFrames.onOff()),
        PreferenceField("LibVLC Skip Frames", v.skipFrames.onOff(), vf.skipFrames.onOff()),
        PreferenceField("Exo Scaling Mode", e.videoScalingMode.enumDisplay(), ef.videoScalingMode.enumDisplay()),
        PreferenceField("Exo Frame Rate Strategy", e.frameRateStrategy.enumDisplay(), ef.frameRateStrategy.enumDisplay()),
        PreferenceField("Exo Preferred MIME Types", e.preferredVideoMimeTypes.size.toString(), ef.preferredVideoMimeTypes.size.toString()),
        PreferenceField("Exo Skip Silence", e.skipSilence.onOff(), ef.skipSilence.onOff()),
        PreferenceField("Exo Audio Offload", e.audioOffloadMode.enumDisplay(), ef.audioOffloadMode.enumDisplay()),
        PreferenceField("Exo Back Buffer (ms)", e.backBufferDurationMs.toString(), ef.backBufferDurationMs.toString()),
        PreferenceField("Exo Decoder Fallback", e.enableDecoderFallback.onOff(), ef.enableDecoderFallback.onOff()),
    )
}

private fun experimentalFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Enabled Experimental Features", prefs.enabledExperimentalFeatures.size.toString(), factory.enabledExperimentalFeatures.size.toString()),
    PreferenceField("Show Advanced Settings", prefs.showAdvancedSettings.onOff(), factory.showAdvancedSettings.onOff()),
)

private fun miscAppFields(prefs: UserPreferences, factory: UserPreferences): List<PreferenceField> = listOf(
    PreferenceField("Haptics", prefs.hapticsEnabled.onOff(), factory.hapticsEnabled.onOff()),
    PreferenceField("Self-Update Check", prefs.selfUpdateCheckEnabled.onOff(), factory.selfUpdateCheckEnabled.onOff()),
    PreferenceField("Self-Update Auto Download", prefs.selfUpdateDownloadEnabled.onOff(), factory.selfUpdateDownloadEnabled.onOff()),
    PreferenceField("App Language", prefs.appLanguage ?: "System", factory.appLanguage ?: "System"),
    PreferenceField("User Data Sync", prefs.userDataSyncEnabled.onOff(), factory.userDataSyncEnabled.onOff()),
    PreferenceField("Share Media Option", prefs.showShareMediaOption.onOff(), factory.showShareMediaOption.onOff()),
    PreferenceField("Hide Search History", prefs.hideSearchHistory.onOff(), factory.hideSearchHistory.onOff()),
    PreferenceField("Android TV Watch Next", prefs.androidTvWatchNextEnabled.onOff(), factory.androidTvWatchNextEnabled.onOff()),
    PreferenceField("Prefer Audio Description", prefs.preferAudioDescription.onOff(), factory.preferAudioDescription.onOff()),
)

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

/**
 * Ordered presentation registry for every [PreferenceResetCategory]. The UI
 * iterates this list; each entry knows how to render its icon, name, and
 * current-vs-factory fields for a given [UserPreferences] snapshot.
 */
val PreferenceCategoryViews: List<PreferenceCategoryView> = listOf(
    PreferenceCategoryView(PreferenceResetCategory.APPEARANCE, Tabler.Outline.Palette, Res.string.factory_reset_cat_appearance) { p, f -> appearanceFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.PLAYBACK, Tabler.Outline.PlayerPlay, Res.string.factory_reset_cat_playback) { p, f -> playbackFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.AUDIO, Tabler.Outline.Volume, Res.string.factory_reset_cat_audio) { p, f -> audioFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.SUBTITLES_LANGUAGE, Tabler.Outline.Subtitles, Res.string.factory_reset_cat_subtitles_language) { p, f -> subtitlesLanguageFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.DOWNLOADS_NETWORK, Tabler.Outline.Download, Res.string.factory_reset_cat_downloads_network) { p, f -> downloadsNetworkFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.HOME_DISCOVERY, Tabler.Outline.Home, Res.string.factory_reset_cat_home_discovery) { p, f -> homeDiscoveryFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.AUDIO_CACHE, Tabler.Outline.Music, Res.string.factory_reset_cat_audio_cache) { p, f -> audioCacheFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.SECURITY, Tabler.Outline.ShieldLock, Res.string.factory_reset_cat_security) { p, f -> securityFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.NOTIFICATIONS, Tabler.Outline.Bell, Res.string.factory_reset_cat_notifications) { p, f -> notificationsFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.SCREENSAVER, Tabler.Outline.Moon, Res.string.factory_reset_cat_screensaver) { p, f -> screensaverFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.NEWSLETTER, Tabler.Outline.PlayerTrackNext, Res.string.factory_reset_cat_newsletter) { p, f -> newsletterFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.SYNCPLAY_CASTING, Tabler.Outline.ScreenShare, Res.string.factory_reset_cat_syncplay_casting) { p, f -> syncplayCastingFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.PLAYER_ENGINES, Tabler.Outline.Cpu, Res.string.factory_reset_cat_player_engines) { p, f -> playerEnginesFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.EXPERIMENTAL, Tabler.Outline.Flask, Res.string.factory_reset_cat_experimental) { p, f -> experimentalFields(p, f) },
    PreferenceCategoryView(PreferenceResetCategory.MISC_APP, Tabler.Outline.Settings, Res.string.factory_reset_cat_misc_app) { p, f -> miscAppFields(p, f) },
)
