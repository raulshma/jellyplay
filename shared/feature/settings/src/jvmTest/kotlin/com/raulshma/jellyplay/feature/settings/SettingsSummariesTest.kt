package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DownloadPreferences
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.ExternalPlayerApp
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeScreenPreferences
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HdrFormatPreference
import com.raulshma.jellyplay.core.model.LanguagePreferences
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.PerformanceLevel
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.PinnedSectionType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.StoragePreferences
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_performance_level_performance
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_suffix
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dynamic_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_token
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the landing-page summary builders (SettingsScreen.kt) — plain policy
 * functions emitting renderable parts, so no composer is needed here. The
 * fake part renderer maps resource identity to its stable `key` label, which
 * keeps the pins honest about WHICH resource every token resolves through
 * while leaving the actual policy under test — which prefs appear, their
 * order, the ", " separator, the all-defaults shape and the contrast-suffix
 * casing — fully observable. ADR-0017 decision 3 extended the same discipline
 * to every hub level (root entrances + the playback/appearance/home hub
 * leaves); one test per fold family below.
 */
class SettingsSummariesTest {

    /** Fake renderer: literals pass through, resources render as labels. */
    private fun renderPart(part: SettingsSummaryPart): String = when (part) {
        is SettingsSummaryPart.Literal -> part.text
        is SettingsSummaryPart.Token -> part.resource.key
        is SettingsSummaryPart.Formatted -> "${part.resource.key}(${part.arg})"
        is SettingsSummaryPart.Formatted2 -> "${part.resource.key}(${part.arg1}, ${part.arg2})"
        is SettingsSummaryPart.Plural -> "${part.resource.key}:${part.count}"
    }

    private fun render(parts: List<SettingsSummaryPart>): String =
        joinSummaryTokens(parts.map(::renderPart))

    private fun renderAppearance(preferences: SettingsScreenPreferences): String =
        render(appearanceSummaryParts(preferences))

    private fun renderExperimental(preferences: SettingsScreenPreferences): String =
        render(experimentalSummaryParts(preferences))

    // --------------------------------------------------------------- appearance

    @Test
    fun `appearance defaults - theme first, dynamic token, nothing else`() {
        assertEquals(
            "System, settings_dynamic_token",
            renderAppearance(SettingsScreenPreferences()),
        )
    }

    @Test
    fun `appearance order - theme, dynamic, oled, contrast, performance`() {
        val everythingOn = SettingsScreenPreferences(
            themeMode = ThemeMode.DARK,
            dynamicTheming = true,
            oledMode = true,
            contrastLevel = ContrastLevel.HIGH,
            performanceLevel = PerformanceLevel.PERFORMANCE,
        )
        assertEquals(
            "Dark, settings_dynamic_token, settings_oled_token, " +
                "settings_contrast_suffix(High), core_performance_level_performance",
            renderAppearance(everythingOn),
        )
    }

    @Test
    fun `appearance parts are exactly the enabled tokens in order`() {
        val everythingOn = SettingsScreenPreferences(
            themeMode = ThemeMode.DARK,
            dynamicTheming = true,
            oledMode = true,
            contrastLevel = ContrastLevel.HIGH,
            performanceLevel = PerformanceLevel.PERFORMANCE,
        )
        assertEquals(
            listOf(
                SettingsSummaryPart.Literal("Dark"),
                SettingsSummaryPart.Token(Res.string.settings_dynamic_token),
                SettingsSummaryPart.Token(Res.string.settings_oled_token),
                SettingsSummaryPart.Formatted(Res.string.settings_contrast_suffix, "High"),
                SettingsSummaryPart.Token(CoreUiRes.string.core_performance_level_performance),
            ),
            appearanceSummaryParts(everythingOn),
        )
    }

    @Test
    fun `balanced level surfaces its own face - the legacy boolean alone would not`() {
        val balanced = SettingsScreenPreferences(performanceLevel = PerformanceLevel.BALANCED)
        assertEquals("System, settings_dynamic_token, core_performance_level_balanced", renderAppearance(balanced))
    }

    @Test
    fun `appearance disabled prefs drop out - lone token carries no separator`() {
        val minimal = SettingsScreenPreferences(
            themeMode = ThemeMode.LIGHT,
            dynamicTheming = false,
            contrastLevel = ContrastLevel.DEFAULT,
        )
        assertEquals("Light", renderAppearance(minimal))
    }

    @Test
    fun `appearance theme mode is title-cased from the enum name`() {
        val scheduled = SettingsScreenPreferences(themeMode = ThemeMode.SCHEDULED, dynamicTheming = false)
        assertEquals("Scheduled", renderAppearance(scheduled))
    }

    @Test
    fun `contrast suffix title-cases the enum name`() {
        val medium = SettingsScreenPreferences(contrastLevel = ContrastLevel.MEDIUM)
        assertEquals(
            "System, settings_dynamic_token, settings_contrast_suffix(Medium)",
            renderAppearance(medium),
        )
    }

    // ------------------------------------------------------------- experimental

    @Test
    fun `experimental defaults - early-access placeholder, no plural`() {
        assertEquals(
            "settings_early_access_features",
            renderExperimental(SettingsScreenPreferences()),
        )
    }

    @Test
    fun `experimental plural carries the enabled feature count`() {
        val threeEnabled = SettingsScreenPreferences(
            enabledExperimentalFeatures = setOf(
                ExperimentalFeature.HOME_CARD_CLIPPING,
                ExperimentalFeature.MEDIA_CARD_PEEK,
                ExperimentalFeature.DIRECT_ARR_INTEGRATION,
            ),
        )
        assertEquals(
            "settings_features_enabled:3",
            renderExperimental(threeEnabled),
        )
    }

    @Test
    fun `experimental single feature still uses the plural`() {
        val oneEnabled = SettingsScreenPreferences(
            enabledExperimentalFeatures = setOf(ExperimentalFeature.MEDIA_CARD_PEEK),
        )
        assertEquals(
            "settings_features_enabled:1",
            renderExperimental(oneEnabled),
        )
    }

    // ---------------------------------------------------------------- playback
    // The root Playback entrance (PlaybackSettingsRows.playbackSummaryParts).

    @Test
    fun `playback root defaults - the speed face alone`() {
        assertEquals(
            "settings_default_speed_value(1x)",
            render(playbackSummaryParts(PlaybackPreferences())),
        )
    }

    @Test
    fun `playback root order - speed, mode, quality, hdr - each only off auto`() {
        val pinned = PlaybackPreferences(
            videoDefaultSpeed = 1.5f,
            playbackMode = PlaybackMode.FORCE_DIRECT_PLAY,
            streamingQuality = StreamingQuality.UHD_4K,
            hdrFormatPreference = HdrFormatPreference.DOLBY_VISION_ONLY,
        )
        assertEquals(
            "settings_default_speed_value(1.5x), Force Direct Play, 4K, settings_hdr_format_dv_only",
            render(playbackSummaryParts(pinned)),
        )
    }

    // ------------------------------------------------------ the playback hub
    // The eight leaf entrances (PlaybackSettingsRows.playback*SummaryParts).

    @Test
    fun `playback basics - speed face, aspect only off auto`() {
        assertEquals(
            "settings_default_speed_value(1x)",
            render(playbackBasicsSummaryParts(PlaybackPreferences())),
        )
        assertEquals(
            "settings_default_speed_value(1.25x), 16:9",
            render(playbackBasicsSummaryParts(PlaybackPreferences(videoDefaultSpeed = 1.25f, videoDefaultAspectRatio = "16:9"))),
        )
    }

    @Test
    fun `playback controls - the faces only where the leaf's capability gates admit them`() {
        // The gesture/orientation faces ride the SAME capability gates the
        // Controls leaf's rows declare (RowAdmission.Platform): a device
        // without a capability gets no summary term for it, and with neither
        // the static entrance subtitle carries.
        assertEquals(
            "core_gesture_mode_all, core_orientation_sensor_landscape",
            render(playbackControlsSummaryParts(PlaybackPreferences(), supportsTouchGestures = true, supportsScreenOrientation = true)),
        )
        assertEquals(
            "core_gesture_mode_all",
            render(playbackControlsSummaryParts(PlaybackPreferences(), supportsTouchGestures = true, supportsScreenOrientation = false)),
        )
        assertEquals(
            "core_orientation_sensor_landscape",
            render(playbackControlsSummaryParts(PlaybackPreferences(), supportsTouchGestures = false, supportsScreenOrientation = true)),
        )
        // The JVM (desktop) seam: both capabilities off — the leaf renders
        // neither row, the summary is the static subtitle.
        assertEquals(
            "settings_playback_entry_controls_subtitle",
            render(playbackControlsSummaryParts(PlaybackPreferences())),
        )
    }

    @Test
    fun `playback chrome - all-default posture falls back to the static subtitle`() {
        assertEquals(
            "settings_playback_entry_chrome_subtitle",
            render(playbackPlayerChromeSummaryParts(PlaybackPreferences())),
        )
        assertEquals(
            "settings_sum_chrome_chapters_off, settings_sum_chrome_buffer_off",
            render(
                playbackPlayerChromeSummaryParts(
                    PlaybackPreferences(videoShowChapterMarkers = false, videoShowBufferedRange = false),
                ),
            ),
        )
    }

    @Test
    fun `playback video quality - all-auto falls back to the static subtitle`() {
        assertEquals(
            "settings_playback_entry_video_quality_subtitle",
            render(playbackVideoQualitySummaryParts(PlaybackPreferences())),
        )
        assertEquals(
            "Force Transcode, 1080p, settings_hdr_format_dv_only",
            render(
                playbackVideoQualitySummaryParts(
                    PlaybackPreferences(
                        playbackMode = PlaybackMode.FORCE_TRANSCODE,
                        streamingQuality = StreamingQuality.FHD_1080P,
                        hdrFormatPreference = HdrFormatPreference.DOLBY_VISION_ONLY,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `playback engines - preferred engine, external app beside the external arm`() {
        assertEquals(
            "ExoPlayer",
            render(playbackEnginesSummaryParts(PlaybackPreferences())),
        )
        assertEquals(
            "External, MX Player",
            render(
                playbackEnginesSummaryParts(
                    PlaybackPreferences(preferredPlayer = PlayerType.EXTERNAL, preferredExternalPlayer = ExternalPlayerApp.MX_PLAYER_FREE),
                ),
            ),
        )
    }

    @Test
    fun `playback segments - behavior posture from the defaults, seek arm when on`() {
        assertEquals(
            "settings_media_segments_summary(1, 2)",
            render(playbackSegmentsSummaryParts(PlaybackPreferences())),
        )
        val seekToo = PlaybackPreferences(
            segmentBehaviors = mapOf(
                MediaSegmentType.INTRO to SegmentBehavior.AUTO_SKIP,
                MediaSegmentType.OUTRO to SegmentBehavior.AUTO_SKIP,
                MediaSegmentType.COMMERCIAL to SegmentBehavior.AUTO_SKIP,
            ),
            skipSegmentsOnSeek = true,
        )
        assertEquals(
            "settings_media_segments_summary(3, 0), settings_skip_segments_on_seek",
            render(playbackSegmentsSummaryParts(seekToo)),
        )
    }

    @Test
    fun `playback syncplay casting - strategy then join behavior`() {
        assertEquals(
            "Always Ask, Always Ask",
            render(playbackSyncPlayCastingSummaryParts(PlaybackPreferences())),
        )
        assertEquals(
            "Prefer DLNA, Never Join",
            render(
                playbackSyncPlayCastingSummaryParts(
                    PlaybackPreferences(
                        defaultCastingStrategy = CastingStrategy.PREFER_DLNA,
                        syncPlayJoinBehavior = SyncPlayJoinBehavior.NEVER_JOIN,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `playback live tv dvr - the recording quality localized label face`() {
        // The summary maps the wire value through the ONE shared
        // recording-quality label mapping ([dvrRecordingQualityLabelRes]) —
        // the same localized faces the leaf's row + picker carry — falling
        // back to the raw stored string only for an unknown wire value.
        assertEquals("settings_dvr_quality_auto", render(playbackLiveTvDvrSummaryParts(PlaybackPreferences())))
        assertEquals(
            "settings_dvr_quality_high",
            render(playbackLiveTvDvrSummaryParts(PlaybackPreferences(dvrRecordingQuality = "HIGH"))),
        )
        assertEquals(
            "UNKNOWN_WIRE_VALUE",
            render(playbackLiveTvDvrSummaryParts(PlaybackPreferences(dvrRecordingQuality = "UNKNOWN_WIRE_VALUE"))),
        )
    }

    // ------------------------------------------------------------------ audio

    @Test
    fun `audio root - speed face only at defaults`() {
        assertEquals(
            "settings_default_speed_value(1x)",
            render(audioSummaryParts(AudioPreferences())),
        )
        val full = AudioPreferences(
            audioDefaultSpeed = 1.25f,
            audioCrossfadeDurationMs = 5_000L,
            equalizerEnabled = true,
        )
        assertEquals(
            "settings_default_speed_value(1.25x), settings_crossfade_on(5), settings_equalizer",
            render(audioSummaryParts(full)),
        )
    }

    // --------------------------------------------------------------- language

    @Test
    fun `language root - both faces fall back to the default label`() {
        assertEquals(
            "settings_audio_label(Default), settings_subtitle_label(Default)",
            render(languageSummaryParts(LanguagePreferences(), "Default")),
        )
        assertEquals(
            "settings_audio_label(eng), settings_subtitle_label(jpn)",
            render(
                languageSummaryParts(
                    LanguagePreferences(preferredAudioLanguage = "eng", preferredSubtitleLanguage = "jpn"),
                    "Default",
                ),
            ),
        )
    }

    // ------------------------------------------------------------------- home

    @Test
    fun `home root - mode, hero state, visible-sections count`() {
        assertEquals(
            "settings_home_mode_video, settings_show_hero_on, settings_home_sections_visible(9, 9)",
            render(homeSummaryParts(HomeScreenPreferences())),
        )
        val tuned = HomeScreenPreferences(
            homeMode = HomeMode.MUSIC,
            homeHeroEnabled = false,
            enabledHomeSectionTypes = HomeSectionType.CONFIGURABLE.take(3).toSet(),
        )
        assertEquals(
            "settings_home_mode_music, settings_show_hero_off, settings_home_sections_visible(3, 9)",
            render(homeSummaryParts(tuned)),
        )
    }

    // ------------------------------------------------------------ performance

    @Test
    fun `performance root - the level face plus the reduce-motion marker`() {
        assertEquals(
            "core_performance_level_standard",
            render(performanceSummaryParts(PerformanceLevel.STANDARD, reduceMotionEnabled = false)),
        )
        assertEquals(
            "core_performance_level_balanced, settings_reduced_motion",
            render(performanceSummaryParts(PerformanceLevel.BALANCED, reduceMotionEnabled = true)),
        )
    }

    // ---------------------------------------------------------------- ambient

    @Test
    fun `ambient tv arm - the dream toggles on-state, static fallback when all off`() {
        assertEquals(
            "settings_show_title, settings_ken_burns",
            render(ambientSummaryParts(isTv = true, idleAmbientEnabled = true, idleAmbientTimeoutMin = 5, dreamShowTitle = true, dreamKenBurnsEnabled = true)),
        )
        assertEquals(
            "settings_ambient_subtitle",
            render(ambientSummaryParts(isTv = true, idleAmbientEnabled = true, idleAmbientTimeoutMin = 5, dreamShowTitle = false, dreamKenBurnsEnabled = false)),
        )
    }

    @Test
    fun `ambient desktop arm - the idle screen posture`() {
        assertEquals(
            "settings_idle_ambient_timeout_minutes(5)",
            render(ambientSummaryParts(isTv = false, idleAmbientEnabled = true, idleAmbientTimeoutMin = 5, dreamShowTitle = true, dreamKenBurnsEnabled = true)),
        )
        assertEquals(
            "settings_off",
            render(ambientSummaryParts(isTv = false, idleAmbientEnabled = false, idleAmbientTimeoutMin = 5, dreamShowTitle = true, dreamKenBurnsEnabled = true)),
        )
        assertEquals(
            "settings_idle_ambient_timeout_off",
            render(ambientSummaryParts(isTv = false, idleAmbientEnabled = true, idleAmbientTimeoutMin = 0, dreamShowTitle = true, dreamKenBurnsEnabled = true)),
        )
    }

    // ------------------------------------------------------- storage/downloads

    @Test
    fun `storage root - cache face always, offline and data-saver markers when on`() {
        assertEquals(
            "settings_cache_subtitle(0)",
            render(storageSummaryParts(StoragePreferences(), cacheSizeMb = 0L)),
        )
        assertEquals(
            "settings_offline_mode, settings_cache_subtitle(512), settings_data_saver_mode",
            render(
                storageSummaryParts(
                    StoragePreferences(manualOfflineEnabled = true, dataSaverEnabled = true),
                    cacheSizeMb = 512L,
                ),
            ),
        )
    }

    @Test
    fun `downloads root - default posture falls back to the static subtitle`() {
        assertEquals(
            "settings_downloads_subtitle",
            render(downloadsSummaryParts(DownloadPreferences())),
        )
        assertEquals(
            "settings_smart_downloads, settings_auto_download_mode_ask",
            render(
                downloadsSummaryParts(
                    DownloadPreferences(smartDownloadsEnabled = true, autoDownloadMode = AutoDownloadMode.ASK),
                ),
            ),
        )
    }

    // ---------------------------------------------------------- notifications

    @Test
    fun `notifications - the disabled face while off, frequency plus quiet hours while on`() {
        assertEquals(
            "settings_disabled",
            render(notificationsSummaryParts(NotificationPreferences())),
        )
        assertEquals(
            "core_check_frequency_every_6_hours",
            render(notificationsSummaryParts(NotificationPreferences(enabled = true))),
        )
        assertEquals(
            "core_check_frequency_every_6_hours, settings_quiet_hours",
            render(notificationsSummaryParts(NotificationPreferences(enabled = true, quietHoursEnabled = true))),
        )
    }

    // --------------------------------------------------------------- security

    @Test
    fun `security - the lock posture as one face`() {
        assertEquals("settings_lock_off", render(securitySummaryParts(pinLockEnabled = false, biometricLockEnabled = false)))
        assertEquals("settings_pin_on", render(securitySummaryParts(pinLockEnabled = true, biometricLockEnabled = false)))
        assertEquals("settings_biometric_on", render(securitySummaryParts(pinLockEnabled = false, biometricLockEnabled = true)))
        assertEquals("settings_pin_biometric_on", render(securitySummaryParts(pinLockEnabled = true, biometricLockEnabled = true)))
    }

    // -------------------------------------------------------- jellyplay plugin

    @Test
    fun `jellyplay plugin - sync posture plus the enabled-feature count`() {
        assertEquals(
            "settings_sum_plugin_sync_off",
            render(jellyPlayPluginSummaryParts(syncEnabled = false, enabledFeatureCount = 0)),
        )
        assertEquals(
            "settings_sum_plugin_sync_on, settings_features_enabled:3",
            render(jellyPlayPluginSummaryParts(syncEnabled = true, enabledFeatureCount = 3)),
        )
    }

    // --------------------------------------------------------- appearance hub

    @Test
    fun `appearance hub theme - localized mode label, variant off standard, oled marker`() {
        assertEquals(
            "settings_theme_follow_system",
            render(appearanceThemeSummaryParts(AppearanceScreenPreferences())),
        )
        assertEquals(
            "settings_theme_always_dark, Monochrome, settings_oled_token",
            render(
                appearanceThemeSummaryParts(
                    AppearanceScreenPreferences(themeMode = ThemeMode.DARK, themeVariant = "monochrome", oledMode = true),
                ),
            ),
        )
    }

    @Test
    fun `appearance hub library views - badge deviations, static fallback at defaults`() {
        assertEquals(
            "settings_appearance_entry_library_views_subtitle",
            render(appearanceLibraryViewsSummaryParts(AppearanceScreenPreferences())),
        )
        assertEquals(
            "settings_sum_quality_badges_off, settings_sum_new_badges_off",
            render(
                appearanceLibraryViewsSummaryParts(
                    AppearanceScreenPreferences(showQualityBadges = false, showNewBadges = false),
                ),
            ),
        )
    }

    @Test
    fun `appearance hub detail - visible toggles only, never the tuning values`() {
        assertEquals(
            "settings_appearance_entry_detail_subtitle",
            render(appearanceDetailSummaryParts(AppearanceScreenPreferences())),
        )
        assertEquals(
            "settings_sum_detail_hide_watched, settings_sum_detail_confirm_played",
            render(
                appearanceDetailSummaryParts(
                    AppearanceScreenPreferences(hideWatchedEpisodes = true, confirmMarkPlayed = true),
                ),
            ),
        )
    }

    @Test
    fun `appearance hub navigation - the leaf's hide-on-scroll posture`() {
        assertEquals(
            "settings_nav_auto_hide",
            render(appearanceNavigationSummaryParts(NavigationCustomizationPreferences())),
        )
        assertEquals(
            "settings_nav_pinned",
            render(
                appearanceNavigationSummaryParts(
                    NavigationCustomizationPreferences(hideBottomNavOnScroll = false),
                ),
            ),
        )
    }

    // -------------------------------------------------------------- home hub

    @Test
    fun `home hub display - mode, hero state, clock chip while on`() {
        assertEquals(
            "settings_home_mode_video, settings_show_hero_on",
            render(homeDisplaySummaryParts(HomeScreenPreferences())),
        )
        assertEquals(
            "settings_home_mode_music, settings_show_hero_off, settings_show_clock_on",
            render(
                homeDisplaySummaryParts(
                    HomeScreenPreferences(homeMode = HomeMode.MUSIC, homeHeroEnabled = false, showClockOnHome = true),
                ),
            ),
        )
    }

    @Test
    fun `home hub rows layout - merge plus pinned count, static fallback at defaults`() {
        assertEquals(
            "settings_home_entry_rows_subtitle",
            render(homeRowsLayoutSummaryParts(HomeScreenPreferences())),
        )
        assertEquals(
            "settings_merge_continue_next_up_on, settings_sum_pinned_sections:2",
            render(
                homeRowsLayoutSummaryParts(
                    HomeScreenPreferences(
                        mergeContinueWatchingAndNextUp = true,
                        pinnedHomeSections = listOf(
                            PinnedHomeSection(PinnedSectionType.FAVORITES, "src-1", "Favorites"),
                            PinnedHomeSection(PinnedSectionType.COLLECTION, "src-2", "Mine"),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `home hub cards - the badge quartet's on-count, static fallback when all off`() {
        assertEquals(
            "settings_sum_badges_on:4",
            render(homeCardsSummaryParts(HomeScreenPreferences())),
        )
        assertEquals(
            "settings_home_entry_cards_subtitle",
            render(
                homeCardsSummaryParts(
                    HomeScreenPreferences(
                        showUnwatchedBadge = false,
                        showWatchedCheckmark = false,
                        showQualityBadges = false,
                        showNewBadges = false,
                    ),
                ),
            ),
        )
    }

    // ------------------------------------------------------ the shared faces

    @Test
    fun `default speed face - the row faces' long-standing shape`() {
        assertEquals("1x", defaultSpeedFace(1.0f))
        assertEquals("1.5x", defaultSpeedFace(1.5f))
        assertEquals("1.25x", defaultSpeedFace(1.25f))
    }
}
