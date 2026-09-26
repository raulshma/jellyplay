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
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.settings.PreferenceSliceSnapshot
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.HasDisplayName
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.SubtitleStyle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
// The diff rows' label sources: the existing settings-search titles (ss_*)
// plus the diff_* residue — extension accessors on Res.string, imported in bulk.
import com.raulshma.jellyplay.feature.settings.generated.resources.*

/**
 * Presentation model for a single preference field shown on the Factory Reset
 * / Import Preview screens: its localized label plus the formatted current and
 * baseline values. [changed] is precomputed by
 * [PreferenceCategoryView.changedFields] so the UI never needs to re-derive
 * equality.
 *
 * The label is RESOLVED TEXT — carried in the [PreferenceDiffSnapshot] the
 * ViewModels build (one `getString` per distinct row resource per snapshot
 * generation), because the rendering components take plain strings and the
 * diff lambdas run outside composition. The label SOURCES are the declared
 * [DiffField] resources: the settings-search row titles where a row has one,
 * plus `diff_*` resources added to the default locale for the residue.
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
 * The snapshot pair one diff renders: the [PreferenceSliceSnapshot] values
 * plus the label resolver for this snapshot generation. The ViewModels expose
 * this (current vs factory / current vs incoming); the registry lambdas below
 * consume it opaquely.
 */
@Immutable
class PreferenceDiffSnapshot(
    val slices: PreferenceSliceSnapshot,
    val label: (StringResource) -> String,
)

/**
 * One declared diff row of a category: the localized label source, the slice
 * read, and the value formatter. The per-category lists below are the ONE
 * declaration of "what the reset/import review surfaces per category" — they
 * replaced ~246 hand-written label+format rows that re-listed the fields off
 * the retired `UserPreferences` aggregate.
 *
 * @param labelRes an EXISTING localized string resource — a settings-search
 *   row title (`ss_*`) where the preference has one — so the review screens
 *   render the same wording the settings screens use.
 * @param read the field off [PreferenceSliceSnapshot]; the same slice field
 *   the corresponding screen consumes.
 * @param format the value rendering, ported verbatim from the former rows.
 */
class DiffField<V>(
    val labelRes: StringResource,
    private val read: (PreferenceSliceSnapshot) -> V,
    private val format: (V) -> String,
) {
    /** The formatted value for [slices] — the diff compares these strings. */
    fun value(slices: PreferenceSliceSnapshot): String = format(read(slices))
}

/**
 * Presentation bundle for one [PreferenceResetCategory]: icon, display name,
 * and the ordered list of user-facing diff rows. "User-facing" means the
 * fields a user would recognize from the corresponding settings screen —
 * internal bookkeeping keys (migration flags, recall slots) are intentionally
 * omitted even though the store's reset key list still covers them.
 *
 * Callers pass the current and baseline [PreferenceDiffSnapshot]s once (the
 * [FactoryResetViewModel] / [ImportPreviewViewModel] expose them) so labels
 * aren't re-resolved per field; use [changedFields] for the diff subset and
 * [totalFields] for the full count.
 */
@Immutable
class PreferenceCategoryView(
    val category: PreferenceResetCategory,
    val icon: ImageVector,
    val displayNameRes: StringResource,
    internal val diffFields: List<DiffField<*>>,
) {
    /** The label resources this category resolves — the snapshot's label-table workload. */
    val labelResources: List<StringResource> = diffFields.map { it.labelRes }

    /** Rows whose current value differs from the baseline. */
    fun changedFields(prefs: PreferenceDiffSnapshot, baseline: PreferenceDiffSnapshot): List<PreferenceField> =
        render(prefs, baseline).filter { it.changed }

    /** Total rows surfaced for this category (for "X of Y changed"). */
    fun totalFields(prefs: PreferenceDiffSnapshot, baseline: PreferenceDiffSnapshot): Int =
        diffFields.size

    /** All rows rendered, changed or not — the full review list behind [changedFields]. */
    internal fun fields(prefs: PreferenceDiffSnapshot, baseline: PreferenceDiffSnapshot): List<PreferenceField> =
        render(prefs, baseline)

    private fun render(prefs: PreferenceDiffSnapshot, baseline: PreferenceDiffSnapshot): List<PreferenceField> =
        diffFields.map { field ->
            PreferenceField(
                label = prefs.label(field.labelRes),
                currentValue = field.value(prefs.slices),
                factoryValue = field.value(baseline.slices),
            )
        }
}

// ---------------------------------------------------------------------------
// Formatting helpers (value text is ported verbatim from the former rows;
// only the LABELS are new-localized).
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

private fun Float.fmt1(): String = formatOneDecimal(this.toDouble())
private fun Float.pct(): String = "${(this * 100).toInt()}%"
private fun Long.millisToSeconds(): String = "${this / 1000.0}s"
private fun Long.millisToMinutes(): String = "${this / 60_000.0}m"
private fun Int.sections(): String = "$this sections"

private fun Map<*, SegmentBehavior>.segmentSummary(): String =
    entries.joinToString(", ") { "${it.key}: ${it.value.enumDisplay()}" }

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
    "Preset bands: ${bandLevels.joinToString(",") { formatSignedInt(it) }}"

// ---------------------------------------------------------------------------
// Declared per-category diff rows. Each row: label resource (existing ss_*
// search-item title where one exists, else a diff_* default-locale string),
// the slice read, and the formatter. Order matches the former hand-written
// rows; `PreferenceCategoryPresentationTest` pins both the diff behavior and
// the row coverage.
// ---------------------------------------------------------------------------

private val appearanceDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_theme_mode_title, { it.appearance.themeMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_dynamic_theming_title, { it.appearance.dynamicTheming }, Boolean::onOff),
    DiffField(Res.string.ss_oled_mode_title, { it.appearance.oledMode }, Boolean::onOff),
    DiffField(Res.string.ss_contrast_title, { it.appearance.contrastLevel }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_style_accent_title, { it.appearance.accentColorSwatch }, ::identity),
    DiffField(Res.string.ss_theme_style_title, { it.appearance.colorStyle }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_performance_mode_title, { it.appearance.performanceMode }, Boolean::onOff),
    DiffField(Res.string.ss_reduce_motion_title, { it.appearance.reduceMotionEnabled }, Boolean::onOff),
    DiffField(Res.string.diff_synthwave_mode, { it.appearance.themeVariant == "synthwave" }, Boolean::onOff),
    DiffField(Res.string.diff_soothing_mode, { it.appearance.themeVariant == "soothing" }, Boolean::onOff),
    DiffField(Res.string.diff_monochrome_mode, { it.appearance.themeVariant == "monochrome" }, Boolean::onOff),
    DiffField(Res.string.ss_theme_music_title, { it.appearance.backdropThemeMusicEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_blue_light_filter_title, { it.appearance.blueLightFilterEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_blue_light_strength_title, { it.appearance.blueLightFilterStrength }, Float::pct),
    DiffField(Res.string.ss_date_format_title, { it.appearance.dateFormatPreference }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_font_scale_title, { it.appearance.appFontScale }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_scheduled_start_title, { it.appearance.scheduledThemeStartHour }, ::hour),
    DiffField(Res.string.ss_scheduled_end_title, { it.appearance.scheduledThemeEndHour }, ::hour),
    DiffField(Res.string.ss_color_blind_mode_title, { it.appearance.colorBlindMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_hand_mode_title, { it.appearance.handMode }, { v -> v.enumDisplay() }),
)

private val playbackDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_player_engine_title, { it.playback.preferredPlayer }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_streaming_quality_title, { it.playback.streamingQuality }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_cellular_streaming_quality_title, { it.playback.cellularStreamingQuality }, { v -> v.enumDisplay() }),
    DiffField(Res.string.diff_playback_mode, { it.playback.playbackMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_decoder_title, { it.playback.decoderMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_audio_passthrough_title, { it.playback.audioPassthrough }, Boolean::onOff),
    DiffField(Res.string.ss_frame_rate_matching_title, { it.playback.frameRateMatching }, Boolean::onOff),
    DiffField(Res.string.ss_orientation_title, { it.videoPlayer.videoDefaultOrientation }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_default_aspect_title, { it.videoPlayer.videoDefaultAspectRatio }, ::identity),
    DiffField(Res.string.ss_preload_buffer_title, { it.videoPlayer.videoPreloadBufferSize }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_gestures_title, { it.videoPlayer.videoGesturesEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_pass_out_protection_title, { it.videoPlayer.videoPassOutProtectionHours }, Int::toString),
    DiffField(Res.string.ss_skip_back_on_resume_title, { it.videoPlayer.videoSkipBackOnResumeMs }, Long::millisToSeconds),
    DiffField(Res.string.diff_hold_to_speed, { it.videoPlayer.videoHoldSpeedEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_hold_speed_multiplier_title, { it.videoPlayer.videoHoldSpeedMultiplier }, ::speedMultiplier),
    DiffField(Res.string.ss_default_speed_title, { it.videoPlayer.videoDefaultSpeed }, ::speedMultiplier),
    DiffField(Res.string.ss_default_brightness_level_title, { it.videoPlayer.videoBrightnessLevel }, Float::pct),
    DiffField(Res.string.ss_video_autoplay_next_title, { it.videoPlayer.videoAutoplayNext }, Boolean::onOff),
    DiffField(Res.string.ss_autoplay_trailers_title, { it.videoPlayer.trailerAutoplay }, Boolean::onOff),
    DiffField(Res.string.ss_cinema_mode_title, { it.videoPlayer.cinemaModeEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_remember_brightness_title, { it.videoPlayer.videoRememberBrightness }, Boolean::onOff),
    DiffField(Res.string.diff_auto_skip_intro, { it.videoPlayer.videoAutoSkipIntro }, Boolean::onOff),
    DiffField(Res.string.diff_auto_skip_outro, { it.videoPlayer.videoAutoSkipOutro }, Boolean::onOff),
    DiffField(Res.string.diff_remember_muted, { it.videoPlayer.videoRememberMuted }, Boolean::onOff),
    DiffField(Res.string.diff_default_muted, { it.videoPlayer.videoMuted }, Boolean::onOff),
    DiffField(Res.string.ss_gesture_indicator_side_title, { it.videoPlayer.videoGestureIndicatorSide }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_seek_duration_title, { it.videoPlayer.videoSeekDurationMs }, Long::millisToSeconds),
    DiffField(Res.string.ss_controls_timeout_title, { it.videoPlayer.videoControlsTimeoutMs }, Long::millisToSeconds),
    DiffField(Res.string.ss_swipe_seek_range_title, { it.videoPlayer.videoSwipeSeekMaxMs }, Long::millisToSeconds),
    DiffField(Res.string.ss_audio_delay_title, { it.audio.audioDelayMs }, ::milliseconds),
    DiffField(Res.string.ss_trickplay_preview_title, { it.videoPlayer.trickplayEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_trickplay_on_gestures_title, { it.videoPlayer.trickplayOnSeekGesture }, Boolean::onOff),
    DiffField(Res.string.ss_episode_browser_title, { it.videoPlayer.videoEpisodeBrowserEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_playback_metadata_title, { it.videoPlayer.videoShowPlaybackMetadata }, Boolean::onOff),
    DiffField(Res.string.ss_background_audio_title, { it.playback.backgroundVideoAudioEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_autoplay_countdown_title, { it.playback.autoPlayCountdownSec }, Int::toString),
    DiffField(Res.string.ss_keep_screen_on_title, { it.playback.keepScreenOnDuringVideo }, Boolean::onOff),
    DiffField(Res.string.ss_incognito_mode_title, { it.videoPlayer.incognitoModeEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_show_clock_player_title, { it.videoPlayer.showClockInPlayer }, Boolean::onOff),
    DiffField(Res.string.ss_show_time_remaining_title, { it.videoPlayer.showTimeRemaining }, Boolean::onOff),
    DiffField(Res.string.ss_pause_on_focus_loss_title, { it.playback.pauseOnAudioFocusLoss }, Boolean::onOff),
    DiffField(Res.string.ss_duck_on_transient_focus_loss_title, { it.playback.duckOnTransientFocusLoss }, Boolean::onOff),
    DiffField(Res.string.ss_tv_zoom_mode_title, { it.videoPlayer.tvZoomModePercent }, Float::pct),
    DiffField(Res.string.diff_segment_behaviors, { it.videoPlayer.segmentBehaviors }, Map<*, SegmentBehavior>::segmentSummary),
)

private val audioDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_audio_default_speed_title, { it.audio.audioDefaultSpeed }, ::speedMultiplier),
    DiffField(Res.string.ss_audio_visualizer_title, { it.audio.audioVisualizerEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_gapless_playback_title, { it.audio.audioGaplessEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_crossfade_title, { it.audio.audioCrossfadeDurationMs }, Long::millisToSeconds),
    DiffField(Res.string.ss_volume_normalization_title, { it.audio.audioNormalizationEnabled }, Boolean::onOff),
    DiffField(Res.string.diff_audio_normalization_mode, { it.audio.audioNormalizationMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_replaygain_preamp_title, { it.audio.replayGainPreAmpDb }, Float::fmt1),
    DiffField(Res.string.ss_channel_mixing_title, { it.audio.channelMixEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_channel_mix_mode_title, { it.audio.channelMixMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_equalizer_title, { it.audioEffects.equalizerEnabled }, Boolean::onOff),
    DiffField(Res.string.diff_equalizer_settings, { it.audioEffects.equalizerSettings }, EqualizerSettings::summary),
    DiffField(Res.string.ss_equalizer_preset_title, { it.audioEffects.equalizerPreset }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_bass_boost_title, { it.audioEffects.bassBoostEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_bass_boost_strength_title, { it.audioEffects.bassBoostStrength }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_virtualizer_title, { it.audioEffects.virtualizerEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_virtualizer_strength_title, { it.audioEffects.virtualizerStrength }, Int::toString),
    DiffField(Res.string.ss_reverb_title, { it.audioEffects.reverbPreset }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_volume_boost_title, { it.audioEffects.volumeBoostEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_volume_boost_gain_title, { it.audioEffects.volumeBoostGain }, ::decibels),
    DiffField(Res.string.ss_lr_balance_title, { it.audioEffects.lrBalance }, Float::fmt1),
    DiffField(Res.string.ss_auto_eq_by_genre_title, { it.audioEffects.autoEqByGenre }, Boolean::onOff),
    DiffField(Res.string.ss_pitch_shift_title, { it.audioEffects.pitchSemitones }, Float::fmt1),
    DiffField(Res.string.ss_audio_autoplay_next_title, { it.audio.audioAutoplayNext }, Boolean::onOff),
    DiffField(Res.string.ss_audio_preload_buffer_title, { it.audio.audioPreloadBufferSize }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_night_mode_volume_title, { it.audio.audioNightModeVolume }, Float::pct),
    DiffField(Res.string.ss_night_mode_gain_title, { it.audio.audioNightModeGain }, Int::toString),
    DiffField(Res.string.ss_audio_skip_prev_threshold_title, { it.audio.audioSkipPreviousThresholdMs }, Long::millisToSeconds),
    DiffField(Res.string.ss_night_mode_title, { it.audioEffects.nightModeEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_night_mode_strength_title, { it.audioEffects.nightModeStrength }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_dialogue_boost_title, { it.audioEffects.dialogueBoostEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_dialogue_boost_strength_title, { it.audioEffects.dialogueBoostStrength }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_sleep_timer_title, { it.audio.sleepTimerDurationMs }, Long::millisToMinutes),
    DiffField(Res.string.diff_sleep_timer_end_of_episode, { it.audio.sleepTimerEndOfEpisode }, Boolean::onOff),
    DiffField(Res.string.diff_lyrics_visible, { it.audio.audioLyricsVisible }, Boolean::onOff),
)

private val subtitlesLanguageDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_subtitle_language_title, { it.subtitle.preferredSubtitleLanguage }, ::systemFallback),
    DiffField(Res.string.ss_audio_language_title, { it.subtitle.preferredAudioLanguage }, ::systemFallback),
    DiffField(Res.string.ss_subtitle_forced_only_title, { it.subtitle.subtitlesForcedOnly }, Boolean::onOff),
    DiffField(Res.string.diff_subtitle_preview, { it.subtitle.subtitlePreviewInSettings }, Boolean::onOff),
    DiffField(Res.string.diff_subtitle_style, { it.subtitle.subtitleStyle }, SubtitleStyle::summary),
    DiffField(Res.string.ss_high_contrast_subtitles_title, { it.subtitle.highContrastSubtitles }, Boolean::onOff),
    DiffField(Res.string.ss_pgs_direct_play_title, { it.playback.pgsSubtitleDirectPlay }, Boolean::onOff),
    DiffField(Res.string.ss_hdr_subtitle_style_title, { it.subtitle.hdrSubtitleStyleEnabled }, Boolean::onOff),
    DiffField(Res.string.diff_hdr_subtitle_style, { it.subtitle.hdrSubtitleStyle }, SubtitleStyle::summary),
)

private val downloadsNetworkDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_wifi_only_downloads_title, { it.downloads.wifiOnlyDownloads }, Boolean::onOff),
    DiffField(Res.string.ss_download_connections_title, { it.downloads.downloadConnections }, Int::toString),
    DiffField(Res.string.ss_max_concurrent_downloads_title, { it.downloads.maxConcurrentDownloads }, Int::toString),
    DiffField(Res.string.ss_download_quality_title, { it.downloads.downloadQuality }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_smart_downloads_title, { it.downloads.smartDownloadsEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_auto_download_new_episodes_title, { it.downloads.autoDownloadNewEpisodes }, Boolean::onOff),
    DiffField(Res.string.ss_max_download_storage_limit_title, { it.downloads.maxDownloadStorageGb }, Int::toString),
    DiffField(Res.string.ss_download_storage_location_title, { it.downloads.downloadStorageLocation }, ::identity),
    DiffField(Res.string.ss_max_cache_size_title, { it.networkOffline.maxCacheSizeMb }, Int::toString),
    DiffField(Res.string.ss_auto_delete_cache_title, { it.networkOffline.autoDeleteCache }, Boolean::onOff),
    DiffField(Res.string.ss_offline_mode_title, { it.networkOffline.manualOfflineEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_auto_offline_title, { it.networkOffline.autoOfflineEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_bandwidth_cap_title, { it.networkOffline.manualBandwidthCap }, Long::toString),
    DiffField(Res.string.ss_metered_network_behavior_title, { it.networkOffline.meteredNetworkBehavior }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_adaptive_bitrate_title, { it.networkOffline.adaptiveBitrateEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_data_saver_title, { it.networkOffline.dataSaverEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_verbose_logging_title, { it.networkOffline.verboseNetworkLogging }, Boolean::onOff),
    DiffField(Res.string.ss_network_timeout_title, { it.networkOffline.networkTimeoutPreset }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_cellular_download_warning_title, { it.downloads.cellularDownloadSizeWarningMb }, Int::toString),
    DiffField(Res.string.ss_download_schedule_title, { it.downloads.downloadScheduleEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_download_schedule_start_title, { it.downloads.downloadScheduleWindow.startHour }, Int::toString),
    DiffField(Res.string.ss_download_schedule_end_title, { it.downloads.downloadScheduleWindow.endHour }, Int::toString),
    DiffField(Res.string.ss_download_schedule_wifi_only_title, { it.downloads.downloadScheduleWindow.wifiOnly }, Boolean::onOff),
)

private val homeDiscoveryDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_home_mode_title, { it.homeDiscovery.homeMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_hero_section_title, { it.homeDiscovery.homeHeroEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_hide_top_header_title, { it.homeDiscovery.hideTopHeaderOnScroll }, Boolean::onOff),
    DiffField(Res.string.diff_enabled_home_sections, { it.homeDiscovery.enabledHomeSectionTypes }, { it.size.sections() }),
    DiffField(Res.string.diff_home_section_order, { it.homeDiscovery.homeSectionOrder }, { it.size.sections() }),
    DiffField(Res.string.diff_library_home_overrides, { it.homeDiscovery.libraryHomeSectionOverrides.size }, Int::toString),
    DiffField(Res.string.ss_library_view_mode_title, { it.library.libraryViewMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_nav_labels_title, { it.navigation.navBarShowLabels }, Boolean::onOff),
    DiffField(Res.string.ss_nav_hide_on_scroll_title, { it.navigation.hideBottomNavOnScroll }, Boolean::onOff),
    DiffField(Res.string.diff_nav_item_order, { it.navigation.navItemOrder.size }, Int::toString),
    DiffField(Res.string.diff_hidden_nav_items, { it.navigation.hiddenNavItems.size }, Int::toString),
    DiffField(Res.string.ss_show_unwatched_badge_title, { it.homeDiscovery.showUnwatchedBadge }, Boolean::onOff),
    DiffField(Res.string.ss_hide_watched_items_title, { it.homeDiscovery.hideWatchedItems }, Boolean::onOff),
    DiffField(Res.string.ss_show_watched_checkmark_title, { it.homeDiscovery.showWatchedCheckmark }, Boolean::onOff),
    DiffField(Res.string.ss_show_external_ratings_title, { it.homeDiscovery.showExternalRatings }, Boolean::onOff),
    DiffField(Res.string.ss_merge_continue_next_up_title, { it.homeDiscovery.mergeContinueWatchingAndNextUp }, Boolean::onOff),
    DiffField(Res.string.ss_next_up_max_days_title, { it.homeDiscovery.nextUpMaxDays }, Int::toString),
    DiffField(Res.string.ss_next_up_rewatching_title, { it.homeDiscovery.nextUpRewatching }, Boolean::onOff),
    DiffField(Res.string.diff_next_up_excluded_series, { it.homeDiscovery.nextUpExcludedSeriesIds.size }, Int::toString),
    DiffField(Res.string.diff_hidden_cw_items, { it.homeDiscovery.hiddenCwItemIds.size }, Int::toString),
    DiffField(Res.string.ss_pinned_home_sections_title, { it.homeDiscovery.pinnedHomeSections.size }, Int::toString),
    DiffField(Res.string.ss_home_layout_presets_title, { it.homeDiscovery.homeLayoutPresets.size }, Int::toString),
    DiffField(Res.string.ss_continue_watching_click_title, { it.homeDiscovery.continueWatchingClickBehavior }, { v -> v.enumDisplay() }),
    DiffField(Res.string.diff_library_sort_overrides, { it.library.defaultLibrarySortOrders.size }, Int::toString),
    DiffField(Res.string.diff_library_view_overrides, { it.library.libraryViewModes.size }, Int::toString),
    DiffField(Res.string.diff_library_filter_overrides, { it.library.libraryFilters.size }, Int::toString),
    DiffField(Res.string.ss_hide_episode_thumbnails_title, { it.library.hideEpisodeThumbnails }, Boolean::onOff),
    DiffField(Res.string.diff_episodes_descending, { it.library.episodesDescending }, Boolean::onOff),
    DiffField(Res.string.ss_skip_specials_title, { it.library.skipSpecials }, Boolean::onOff),
    DiffField(Res.string.ss_clock_home_title, { it.homeDiscovery.showClockOnHome }, Boolean::onOff),
    DiffField(Res.string.ss_settings_in_home_search_title, { it.homeDiscovery.showSettingsInHomeSearch }, Boolean::onOff),
)

private val audioCacheDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_audio_caching_enabled_title, { it.audioCache.audioCachingEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_audio_cache_size_title, { it.audioCache.audioCacheSizeMb }, Int::toString),
    DiffField(Res.string.ss_audio_prefetch_lookahead_title, { it.audioCache.audioPrefetchLookahead }, Int::toString),
    DiffField(Res.string.ss_audio_prefetch_backfill_title, { it.audioCache.audioPrefetchBackfill }, Int::toString),
    DiffField(Res.string.ss_audio_cache_network_policy_title, { it.audioCache.audioCacheNetworkPolicy }, { v -> v.enumDisplay() }),
    DiffField(Res.string.diff_audio_cache_cellular_cap, { it.audioCache.audioCacheCellularMonthlyCapMb }, Int::toString),
)

private val securityDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_pin_lock_title, { it.security.pinLockEnabled }, Boolean::onOff),
    DiffField(Res.string.diff_pin_set, { it.security.pinHash }, ::pinSet),
    DiffField(Res.string.ss_biometric_lock_title, { it.security.biometricLockEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_pin_for_player_lock_title, { it.security.usePinForPlayerLock }, Boolean::onOff),
    DiffField(Res.string.ss_auto_lock_timer_title, { it.security.autoLockTimerMs }, Long::millisToSeconds),
    DiffField(Res.string.ss_remote_control_enabled_title, { it.security.remoteControlEnabled }, Boolean::onOff),
)

private val notificationsDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_notifications_enable_title, { it.notification.notificationPreferences.enabled }, Boolean::onOff),
    DiffField(Res.string.ss_notification_check_frequency_title, { it.notification.notificationPreferences.checkFrequency }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_quiet_hours_title, { it.notification.notificationPreferences.quietHoursEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_quiet_start_title, { it.notification.notificationPreferences.quietHoursStart }, ::minutesSuffix),
    DiffField(Res.string.ss_quiet_end_title, { it.notification.notificationPreferences.quietHoursEnd }, ::minutesSuffix),
    DiffField(Res.string.ss_notification_sound_title, { it.notification.notificationPreferences.soundEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_notification_vibrate_title, { it.notification.notificationPreferences.vibrateEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_notification_lights_title, { it.notification.notificationPreferences.lightsEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_max_per_check_title, { it.notification.notificationPreferences.maxPerCheck }, Int::toString),
    DiffField(Res.string.ss_notification_libraries_title, { it.notification.notificationPreferences.libraryConfigs.size }, Int::toString),
)

private val screensaverDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_screensaver_categories_title, { it.screensaver.dreamImageCategories.size }, Int::toString),
    DiffField(Res.string.ss_screensaver_transition_style_title, { it.screensaver.dreamTransitionStyle }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_screensaver_ken_burns_title, { it.screensaver.dreamKenBurnsEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_screensaver_show_title_title, { it.screensaver.dreamShowTitle }, Boolean::onOff),
    DiffField(Res.string.ss_screensaver_slideshow_interval_title, { it.screensaver.dreamSlideshowIntervalMs }, Long::millisToSeconds),
)

private val newsletterDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_newsletter_enabled_title, { it.notification.newsletterEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_newsletter_delivery_day_title, { it.notification.newsletterDayOfWeek }, Int::toString),
    DiffField(Res.string.ss_newsletter_sections_title, { it.notification.enabledNewsletterSections.size }, Int::toString),
    DiffField(Res.string.diff_newsletter_section_order, { it.notification.newsletterSectionOrder.size }, Int::toString),
    DiffField(Res.string.diff_newsletter_last_viewed, { it.notification.newsletterLastViewedMs }, ::lastViewed),
)

private val syncplayCastingDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_syncplay_join_behavior_title, { it.syncPlayCast.syncPlayJoinBehavior }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_syncplay_tolerance_title, { it.syncPlayCast.syncPlayToleranceMs }, ::milliseconds),
    DiffField(Res.string.ss_syncplay_auto_accept_invites_title, { it.syncPlayCast.syncPlayAutoAcceptInvites }, Boolean::onOff),
    DiffField(Res.string.ss_casting_strategy_title, { it.syncPlayCast.defaultCastingStrategy }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_background_casting_title, { it.syncPlayCast.backgroundCastingEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_preferred_renderer_title, { it.syncPlayCast.preferredRenderer }, ::autoFallback),
    DiffField(Res.string.ss_dvr_pre_padding_title, { it.syncPlayCast.dvrPrePaddingMinutes }, Int::toString),
    DiffField(Res.string.ss_dvr_post_padding_title, { it.syncPlayCast.dvrPostPaddingMinutes }, Int::toString),
    DiffField(Res.string.ss_dvr_recording_quality_title, { it.syncPlayCast.dvrRecordingQuality }, ::identity),
)

private val playerEnginesDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_mpv_video_output_title, { it.engine.mpvConfig.videoOutput }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_scaler_title, { it.engine.mpvConfig.scaler }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_debanding_title, { it.engine.mpvConfig.deband }, Boolean::onOff),
    DiffField(Res.string.ss_mpv_interpolation_title, { it.engine.mpvConfig.interpolation }, Boolean::onOff),
    DiffField(Res.string.ss_mpv_audio_output_title, { it.engine.mpvConfig.audioOutput }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_audio_fallback_title, { it.engine.mpvConfig.audioFallback?.enumDisplay() }, ::autoFallback),
    DiffField(Res.string.ss_mpv_buffer_size_title, { it.engine.mpvConfig.demuxerMaxBytes }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_skip_loop_filter_title, { it.engine.mpvConfig.skipLoopFilter }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_frame_drop_title, { it.engine.mpvConfig.frameDrop }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_mpv_hwdec_override_title, { it.engine.mpvConfig.hwdecOverride?.enumDisplay() }, ::autoFallback),
    DiffField(Res.string.ss_vlc_audio_output_title, { it.engine.libVlcConfig.audioOutput }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_vlc_audio_time_stretch_title, { it.engine.libVlcConfig.audioTimeStretch }, Boolean::onOff),
    DiffField(Res.string.ss_vlc_network_caching_title, { it.engine.libVlcConfig.networkCaching }, { ms -> "$ms ms" }),
    DiffField(Res.string.ss_vlc_video_output_title, { it.engine.libVlcConfig.videoOutput }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_vlc_skip_loop_filter_title, { it.engine.libVlcConfig.skipLoopFilter }, Int::toString),
    DiffField(Res.string.diff_vlc_skip_frame, { it.engine.libVlcConfig.skipFrame }, Int::toString),
    DiffField(Res.string.ss_vlc_decoder_threads_title, { it.engine.libVlcConfig.decoderThreads }, Int::toString),
    DiffField(Res.string.ss_vlc_drop_late_frames_title, { it.engine.libVlcConfig.dropLateFrames }, Boolean::onOff),
    DiffField(Res.string.ss_vlc_skip_frames_title, { it.engine.libVlcConfig.skipFrames }, Boolean::onOff),
    DiffField(Res.string.ss_exo_video_scaling_title, { it.engine.exoPlayerConfig.videoScalingMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_exo_frame_rate_strategy_title, { it.engine.exoPlayerConfig.frameRateStrategy }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_exo_preferred_codecs_title, { it.engine.exoPlayerConfig.preferredVideoMimeTypes.size }, Int::toString),
    DiffField(Res.string.ss_exo_skip_silence_title, { it.engine.exoPlayerConfig.skipSilence }, Boolean::onOff),
    DiffField(Res.string.ss_exo_audio_offload_title, { it.engine.exoPlayerConfig.audioOffloadMode }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_exo_back_buffer_title, { it.engine.exoPlayerConfig.backBufferDurationMs }, Int::toString),
    DiffField(Res.string.ss_exo_decoder_fallback_title, { it.engine.exoPlayerConfig.enableDecoderFallback }, Boolean::onOff),
)

private val experimentalDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.diff_experimental_enabled_features, { it.experimental.enabledExperimentalFeatures.size }, Int::toString),
    DiffField(Res.string.diff_show_advanced_settings, { it.appearance.showAdvancedSettings }, Boolean::onOff),
)

private val miscAppDiffFields: List<DiffField<*>> = listOf(
    DiffField(Res.string.ss_haptics_enabled_title, { it.appearance.hapticsEnabled }, Boolean::onOff),
    DiffField(Res.string.diff_self_update_check, { it.experimental.selfUpdateCheckEnabled }, Boolean::onOff),
    DiffField(Res.string.diff_self_update_download, { it.experimental.selfUpdateDownloadEnabled }, Boolean::onOff),
    DiffField(Res.string.diff_update_dismiss_period, { it.experimental.updateDismissPeriod }, { v -> v.enumDisplay() }),
    DiffField(Res.string.ss_app_language_title, { it.experimental.appLanguage }, ::systemFallback),
    DiffField(Res.string.ss_user_data_sync_title, { it.playback.userDataSyncEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_show_share_media_title, { it.experimental.showShareMediaOption }, Boolean::onOff),
    DiffField(Res.string.ss_hide_search_history_title, { it.experimental.hideSearchHistory }, Boolean::onOff),
    DiffField(Res.string.ss_android_tv_watch_next_title, { it.playback.androidTvWatchNextEnabled }, Boolean::onOff),
    DiffField(Res.string.ss_audio_description_title, { it.experimental.preferAudioDescription }, Boolean::onOff),
)

// ---------------------------------------------------------------------------
// Diff-row formatting primitives (label text resolves via [PreferenceDiffSnapshot]).
// ---------------------------------------------------------------------------

private fun <T> identity(value: T): String = value.toString()
private fun hour(hour: Int): String = "$hour:00"
private fun milliseconds(ms: Long): String = "$ms ms"
private fun minutesSuffix(minutes: Int): String = "$minutes min"
private fun decibels(gain: Int): String = "$gain dB"
private fun speedMultiplier(speed: Float): String = speed.fmt1() + "x"
private fun pinSet(pinHash: String?): String = if (pinHash != null) "Yes" else "No"
private fun systemFallback(value: String?): String = value ?: "System"
private fun autoFallback(value: String?): String = value ?: "Auto"
private fun lastViewed(ms: Long): String = if (ms == 0L) "Never" else "${ms}ms"

// ---------------------------------------------------------------------------
// App runtime fields — surfaced only in import preview ("everything").
// Not part of `PreferenceResetCategory`; factory reset intentionally omits them.
// Kept in this file (not a separate module) because `core:datastore` cannot
// depend on `feature:settings` for the presentation registry — the registry
// already lives here and `AppRuntimeState` is a pure model type (no UI dep).
// The call site is the import-preview screen itself (which passes the
// localized none-label), so these five rows keep literal labels until that
// seam moves label resolution screen-side too.
// ---------------------------------------------------------------------------

/**
 * User-facing diff for the `AppRuntimeState` extras carried in a v2 backup.
 * Mirrors the [PreferenceField] shape so import-preview can reuse
 * `PreferenceDiffCategoryItem` / `PreferenceFieldRow`.
 *
 * @param noneLabel localized label for empty/absent values (pass
 * `stringResource(R.string.settings_unknown)` from the composable caller so
 * this pure function does not need a @Composable context and can be used
 * inside `remember`).
 */
fun appRuntimeFields(
    current: AppRuntimeState,
    incoming: AppRuntimeState,
    noneLabel: String = "None",
): List<PreferenceField> = listOf(
    PreferenceField(
        "Favorite Channels",
        current.favoriteChannels.sorted().joinToString(", ").ifEmpty { noneLabel },
        incoming.favoriteChannels.sorted().joinToString(", ").ifEmpty { noneLabel },
    ),
    PreferenceField(
        "Last Live-TV Channel",
        current.liveTvLastChannelId ?: noneLabel,
        incoming.liveTvLastChannelId ?: noneLabel,
    ),
    PreferenceField(
        "Watch Later Playlist",
        current.watchLaterPlaylistId ?: noneLabel,
        incoming.watchLaterPlaylistId ?: noneLabel,
    ),
    PreferenceField(
        "Onboarding Completed",
        current.onboardingCompleted.onOff(),
        incoming.onboardingCompleted.onOff(),
    ),
    PreferenceField(
        "Recent DLNA Devices",
        if (current.recentDlnaDevices.isEmpty()) noneLabel else "${current.recentDlnaDevices.size} devices",
        if (incoming.recentDlnaDevices.isEmpty()) noneLabel else "${incoming.recentDlnaDevices.size} devices",
    ),
)

/**
 * Resolves the diff rows' label resources once per snapshot generation. The
 * ViewModels call this when building a [PreferenceDiffSnapshot]; the map key
 * fallback (`toString`) exists only for a resource-system failure and is never
 * hit in production.
 */
suspend fun resolveDiffLabels(resources: List<StringResource>): (StringResource) -> String {
    val resolved = resources.distinct().associateWith { res ->
        runCatchingRethrowingCancellation { getString(res) }.getOrElse { res.toString() }
    }
    return { res -> resolved[res] ?: res.toString() }
}

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

/**
 * Ordered presentation registry for every [PreferenceResetCategory]. The UI
 * iterates this list; each entry renders its icon, name, and
 * current-vs-baseline rows for the [PreferenceDiffSnapshot] pair the
 * ViewModel exposes.
 */
val PreferenceCategoryViews: List<PreferenceCategoryView> = listOf(
    PreferenceCategoryView(PreferenceResetCategory.APPEARANCE, Tabler.Outline.Palette, Res.string.factory_reset_cat_appearance, appearanceDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.PLAYBACK, Tabler.Outline.PlayerPlay, Res.string.factory_reset_cat_playback, playbackDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.AUDIO, Tabler.Outline.Volume, Res.string.factory_reset_cat_audio, audioDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.SUBTITLES_LANGUAGE, Tabler.Outline.Subtitles, Res.string.factory_reset_cat_subtitles_language, subtitlesLanguageDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.DOWNLOADS_NETWORK, Tabler.Outline.Download, Res.string.factory_reset_cat_downloads_network, downloadsNetworkDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.HOME_DISCOVERY, Tabler.Outline.Home, Res.string.factory_reset_cat_home_discovery, homeDiscoveryDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.AUDIO_CACHE, Tabler.Outline.Music, Res.string.factory_reset_cat_audio_cache, audioCacheDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.SECURITY, Tabler.Outline.ShieldLock, Res.string.factory_reset_cat_security, securityDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.NOTIFICATIONS, Tabler.Outline.Bell, Res.string.factory_reset_cat_notifications, notificationsDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.SCREENSAVER, Tabler.Outline.Moon, Res.string.factory_reset_cat_screensaver, screensaverDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.NEWSLETTER, Tabler.Outline.PlayerTrackNext, Res.string.factory_reset_cat_newsletter, newsletterDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.SYNCPLAY_CASTING, Tabler.Outline.ScreenShare, Res.string.factory_reset_cat_syncplay_casting, syncplayCastingDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.PLAYER_ENGINES, Tabler.Outline.Cpu, Res.string.factory_reset_cat_player_engines, playerEnginesDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.EXPERIMENTAL, Tabler.Outline.Flask, Res.string.factory_reset_cat_experimental, experimentalDiffFields),
    PreferenceCategoryView(PreferenceResetCategory.MISC_APP, Tabler.Outline.Settings, Res.string.factory_reset_cat_misc_app, miscAppDiffFields),
)
