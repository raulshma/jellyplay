package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_language_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
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
internal val LanguageSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = LanguageSettingsIds.APP_LANGUAGE,
        titleRes = Res.string.ss_app_language_title,
        subtitleRes = Res.string.ss_app_language_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("language", "display", "interface", "locale", "ui language", "app language"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Language,
        platforms = platformsForCapability(settingsCapabilities.supportsAppLocaleOverride),
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.AUDIO_LANGUAGE,
        titleRes = Res.string.ss_audio_language_title,
        subtitleRes = Res.string.ss_audio_language_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("language", "audio track", "speech", "default language"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Language
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.SUBTITLE_LANGUAGE,
        titleRes = Res.string.ss_subtitle_language_title,
        subtitleRes = Res.string.ss_subtitle_language_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("subtitles", "language", "cc", "captions"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Subtitles
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.SUBTITLE_FONT_SIZE,
        titleRes = Res.string.ss_subtitle_font_size_title,
        subtitleRes = Res.string.ss_subtitle_font_size_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("subtitle size", "font size", "text size", "bigger"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Typography
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.SUBTITLE_FORCED_ONLY,
        titleRes = Res.string.ss_subtitle_forced_only_title,
        subtitleRes = Res.string.ss_subtitle_forced_only_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("forced", "subtitles", "foreign", "parts", "native"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.TextSize
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.PGS_DIRECT_PLAY,
        titleRes = Res.string.ss_pgs_direct_play_title,
        subtitleRes = Res.string.ss_pgs_direct_play_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("pgs", "subtitle", "direct play", "picture", "image subtitle", "bluray"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Photo,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.HDR_SUBTITLE_STYLE,
        titleRes = Res.string.ss_hdr_subtitle_style_title,
        subtitleRes = Res.string.ss_hdr_subtitle_style_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("hdr", "subtitle", "style", "dolby vision", "hdr10", "brightness"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Sun,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.SUBTITLE_COLOR,
        titleRes = Res.string.ss_subtitle_color_title,
        subtitleRes = Res.string.ss_subtitle_color_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("subtitle color", "text color", "yellow subtitles", "white"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Palette,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.SUBTITLE_BACKGROUND,
        titleRes = Res.string.ss_subtitle_background_title,
        subtitleRes = Res.string.ss_subtitle_background_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("subtitle background", "opacity", "transparency", "box"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Background,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.SUBTITLE_EDGE_STYLE,
        titleRes = Res.string.ss_subtitle_edge_style_title,
        subtitleRes = Res.string.ss_subtitle_edge_style_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("edge style", "shadow", "outline", "border"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.BorderAll,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.SUBTITLE_SYNC_OFFSET,
        titleRes = Res.string.ss_subtitle_sync_offset_title,
        subtitleRes = Res.string.ss_subtitle_sync_offset_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("sync", "offset", "delay", "lagging subtitles"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.SUBTITLE_VERTICAL_POSITION,
        titleRes = Res.string.ss_subtitle_vertical_position_title,
        subtitleRes = Res.string.ss_subtitle_vertical_position_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("position", "height", "vertical", "bottom", "margin"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.ArrowBarDown,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.HIGH_CONTRAST_SUBTITLES,
        titleRes = Res.string.ss_high_contrast_subtitles_title,
        subtitleRes = Res.string.ss_high_contrast_subtitles_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("subtitle", "high", "contrast", "accessibility", "visibility"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Contrast2,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.SUBTITLE_TESTER,
        titleRes = Res.string.ss_subtitle_tester_title,
        subtitleRes = Res.string.ss_subtitle_tester_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("subtitle", "tester", "preview", "sample", "test", "style"),
        route = Route.SubtitleTester,
        icon = Tabler.Outline.EyeCheck
    ),
    SettingsSearchItem(
        id = LanguageSettingsIds.HDR_SUBTITLE_FONT_SIZE,
        titleRes = Res.string.ss_hdr_subtitle_font_size_title,
        subtitleRes = Res.string.ss_hdr_subtitle_font_size_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_language_subtitles,
        keywords = listOf("hdr", "subtitle", "font size", "text", "dolby vision"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Typography,
        isAdvanced = true
    ),
)

/**
 * The subtitles group's per-id declared row admissions — the single gate both
 * `languageSubtitlesScreenRowTotal` and LanguageSettingsScreen's emission `if`s
 * read. Every id is declared, so the total counts strictly (the
 * notifications/security `?: false` shape): the tester/font-size/forced-only
 * trio always render ([RowAdmission.Always]) — as does high-contrast
 * subtitles, declared `isAdvanced` yet shown in every mode (the shipped quirk,
 * stated explicitly) — the seven style rows ride [RowAdmission.Advanced] (the
 * screen's advanced structural block carries that gate), and the HDR font-size
 * row additionally rides the HDR-style toggle ([RowAdmission.All]).
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
