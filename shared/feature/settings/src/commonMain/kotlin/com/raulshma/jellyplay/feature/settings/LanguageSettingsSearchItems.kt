package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_language_subtitles
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_language
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_display_language
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_font_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_forced_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_font_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_subtitle_style
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_high_contrast_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_open_subtitle_tester
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pgs_direct_play
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_background
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_edge_style
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_language
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_sync_offset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_text_color
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_vertical_position
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_app_language_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_app_language_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_language_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_audio_language_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hdr_subtitle_font_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hdr_subtitle_font_size_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hdr_subtitle_style_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hdr_subtitle_style_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_high_contrast_subtitles_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_high_contrast_subtitles_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pgs_direct_play_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pgs_direct_play_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_background_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_background_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_color_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_color_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_edge_style_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_edge_style_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_font_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_font_size_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_forced_only_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_forced_only_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_language_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_language_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_sync_offset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_sync_offset_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_tester_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_tester_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_vertical_position_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_vertical_position_title

/**
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object LanguageSettingsIds {
    const val APP_LANGUAGE = "app_language"
    const val AUDIO_LANGUAGE = "audio_language"
    const val SUBTITLE_LANGUAGE = "subtitle_language"
    const val SUBTITLE_FONT_SIZE = "subtitle_font_size"
    const val SUBTITLE_FORCED_ONLY = "subtitle_forced_only"
    const val PGS_DIRECT_PLAY = "pgs_direct_play"
    const val HDR_SUBTITLE_STYLE = "hdr_subtitle_style"
    const val SUBTITLE_COLOR = "subtitle_color"
    const val SUBTITLE_BACKGROUND = "subtitle_background"
    const val SUBTITLE_EDGE_STYLE = "subtitle_edge_style"
    const val SUBTITLE_SYNC_OFFSET = "subtitle_sync_offset"
    const val SUBTITLE_VERTICAL_POSITION = "subtitle_vertical_position"
    const val HIGH_CONTRAST_SUBTITLES = "high_contrast_subtitles"
    const val SUBTITLE_TESTER = "subtitle_tester"
    const val HDR_SUBTITLE_FONT_SIZE = "hdr_subtitle_font_size"
}

/**
 * Settings-search items for the "Language & Subtitles" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to LanguageSettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val LanguageSettingsRowRecords = listOf(
    SettingsRowRecord(
        id = LanguageSettingsIds.APP_LANGUAGE,
        titleRes = Res.string.settings_display_language,
        searchTitleRes = Res.string.ss_app_language_title,
        searchSubtitleRes = Res.string.ss_app_language_subtitle,
        keywords = listOf("language", "display", "interface", "locale", "ui language", "app language"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Language,
        platforms = platformsForCapability(settingsCapabilities.supportsAppLocaleOverride),
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.AUDIO_LANGUAGE,
        titleRes = Res.string.settings_audio_language,
        searchTitleRes = Res.string.ss_audio_language_title,
        searchSubtitleRes = Res.string.ss_audio_language_subtitle,
        keywords = listOf("language", "audio track", "speech", "default language"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Language
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.SUBTITLE_LANGUAGE,
        titleRes = Res.string.settings_subtitle_language,
        searchTitleRes = Res.string.ss_subtitle_language_title,
        searchSubtitleRes = Res.string.ss_subtitle_language_subtitle,
        keywords = listOf("subtitles", "language", "cc", "captions"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Subtitles
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.SUBTITLE_FONT_SIZE,
        titleRes = Res.string.settings_font_size,
        searchTitleRes = Res.string.ss_subtitle_font_size_title,
        searchSubtitleRes = Res.string.ss_subtitle_font_size_subtitle,
        keywords = listOf("subtitle size", "font size", "text size", "bigger"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Typography
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.SUBTITLE_FORCED_ONLY,
        titleRes = Res.string.settings_forced_subtitles,
        searchTitleRes = Res.string.ss_subtitle_forced_only_title,
        searchSubtitleRes = Res.string.ss_subtitle_forced_only_subtitle,
        keywords = listOf("forced", "subtitles", "foreign", "parts", "native"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.TextSize
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.PGS_DIRECT_PLAY,
        titleRes = Res.string.settings_pgs_direct_play,
        searchTitleRes = Res.string.ss_pgs_direct_play_title,
        searchSubtitleRes = Res.string.ss_pgs_direct_play_subtitle,
        keywords = listOf("pgs", "subtitle", "direct play", "picture", "image subtitle", "bluray"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Photo,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.HDR_SUBTITLE_STYLE,
        titleRes = Res.string.settings_hdr_subtitle_style,
        searchTitleRes = Res.string.ss_hdr_subtitle_style_title,
        searchSubtitleRes = Res.string.ss_hdr_subtitle_style_subtitle,
        keywords = listOf("hdr", "subtitle", "style", "dolby vision", "hdr10", "brightness"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Sun,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.SUBTITLE_COLOR,
        titleRes = Res.string.settings_subtitle_text_color,
        searchTitleRes = Res.string.ss_subtitle_color_title,
        searchSubtitleRes = Res.string.ss_subtitle_color_subtitle,
        keywords = listOf("subtitle color", "text color", "yellow subtitles", "white"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Palette,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.SUBTITLE_BACKGROUND,
        titleRes = Res.string.settings_subtitle_background,
        searchTitleRes = Res.string.ss_subtitle_background_title,
        searchSubtitleRes = Res.string.ss_subtitle_background_subtitle,
        keywords = listOf("subtitle background", "opacity", "transparency", "box"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Background,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.SUBTITLE_EDGE_STYLE,
        titleRes = Res.string.settings_subtitle_edge_style,
        searchTitleRes = Res.string.ss_subtitle_edge_style_title,
        searchSubtitleRes = Res.string.ss_subtitle_edge_style_subtitle,
        keywords = listOf("edge style", "shadow", "outline", "border"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.BorderAll,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.SUBTITLE_SYNC_OFFSET,
        titleRes = Res.string.settings_subtitle_sync_offset,
        searchTitleRes = Res.string.ss_subtitle_sync_offset_title,
        searchSubtitleRes = Res.string.ss_subtitle_sync_offset_subtitle,
        keywords = listOf("sync", "offset", "delay", "lagging subtitles"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.SUBTITLE_VERTICAL_POSITION,
        titleRes = Res.string.settings_subtitle_vertical_position,
        searchTitleRes = Res.string.ss_subtitle_vertical_position_title,
        searchSubtitleRes = Res.string.ss_subtitle_vertical_position_subtitle,
        keywords = listOf("position", "height", "vertical", "bottom", "margin"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.ArrowBarDown,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.HIGH_CONTRAST_SUBTITLES,
        titleRes = Res.string.settings_high_contrast_subtitles,
        searchTitleRes = Res.string.ss_high_contrast_subtitles_title,
        searchSubtitleRes = Res.string.ss_high_contrast_subtitles_subtitle,
        keywords = listOf("subtitle", "high", "contrast", "accessibility", "visibility"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Contrast2,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.SUBTITLE_TESTER,
        titleRes = Res.string.settings_open_subtitle_tester,
        searchTitleRes = Res.string.ss_subtitle_tester_title,
        searchSubtitleRes = Res.string.ss_subtitle_tester_subtitle,
        keywords = listOf("subtitle", "tester", "preview", "sample", "test", "style"),
        route = Route.SubtitleTester,
        icon = Tabler.Outline.EyeCheck
    ),
    SettingsRowRecord(
        id = LanguageSettingsIds.HDR_SUBTITLE_FONT_SIZE,
        titleRes = Res.string.settings_hdr_font_size,
        searchTitleRes = Res.string.ss_hdr_subtitle_font_size_title,
        searchSubtitleRes = Res.string.ss_hdr_subtitle_font_size_subtitle,
        keywords = listOf("hdr", "subtitle", "font size", "text", "dolby vision"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Typography,
        isAdvanced = true
    ))

/** The catalog projection of `LanguageSettingsRowRecords`: the search faces + the shared category. */
internal val LanguageSettingsSearchItems: List<SettingsSearchItem> = LanguageSettingsRowRecords.toSearchItems(CoreUiRes.string.ss_cat_language_subtitles)


/**
 * The language screen's leading "Language" trio's per-id declared row
 * admissions — full coverage, so `rowTotalFor` and LanguageSettingsScreen's
 * emission `if` read one gate per id: the audio/subtitle language rows always
 * render, and the per-app display-language row only where the
 * `AppLocaleSetter` seam is real (desktop's is a no-op — the
 * [RowAdmission.Platform] gate the screen reads through the same
 * `supportsAppLocaleOverride` flag).
 */
internal val LanguageGeneralRowAdmissions: Map<String, RowAdmission> =
    LanguageSettingsRowRecords.take(SettingsScreenGroups.LANGUAGE_GENERAL_GROUP_SIZE).admissionsByAdvancedFlag() + mapOf(
        LanguageSettingsIds.APP_LANGUAGE to RowAdmission.Platform(RowAdmissionCapability.AppLocaleOverride),
    )


/**
 * The subtitles group's per-id declared row admissions — the single gate both
 * `rowTotalFor` (the screen's "Subtitles" total) and LanguageSettingsScreen's
 * emission `if`s read. Every id is declared, so the total counts strictly
 * (the notifications/security shape): the tester/font-size/forced-only trio
 * always render ([RowAdmission.Always]) — as does high-contrast subtitles,
 * declared `isAdvanced` yet shown in every mode (the shipped quirk, stated
 * explicitly) — the seven style rows ride [RowAdmission.Advanced] (the
 * screen's advanced structural block carries that gate), and the HDR
 * font-size row additionally rides the HDR-style toggle ([RowAdmission.All]).
 */
internal val LanguageSubtitlesRowAdmissions: Map<String, RowAdmission> = mapOf(
    LanguageSettingsIds.SUBTITLE_FONT_SIZE to RowAdmission.Always,
    LanguageSettingsIds.SUBTITLE_FORCED_ONLY to RowAdmission.Always,
    LanguageSettingsIds.PGS_DIRECT_PLAY to RowAdmission.Advanced,
    LanguageSettingsIds.HDR_SUBTITLE_STYLE to RowAdmission.Advanced,
    LanguageSettingsIds.SUBTITLE_COLOR to RowAdmission.Advanced,
    LanguageSettingsIds.SUBTITLE_BACKGROUND to RowAdmission.Advanced,
    LanguageSettingsIds.SUBTITLE_EDGE_STYLE to RowAdmission.Advanced,
    LanguageSettingsIds.SUBTITLE_SYNC_OFFSET to RowAdmission.Advanced,
    LanguageSettingsIds.SUBTITLE_VERTICAL_POSITION to RowAdmission.Advanced,
    LanguageSettingsIds.HIGH_CONTRAST_SUBTITLES to RowAdmission.Always,
    LanguageSettingsIds.SUBTITLE_TESTER to RowAdmission.Always,
    LanguageSettingsIds.HDR_SUBTITLE_FONT_SIZE to RowAdmission.All(
        RowAdmission.Advanced,
        RowAdmission.WhenOn(LanguageSettingsIds.HDR_SUBTITLE_STYLE),
    ),
)
