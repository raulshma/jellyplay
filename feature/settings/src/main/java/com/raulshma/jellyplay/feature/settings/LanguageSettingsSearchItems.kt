package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * Settings-search items for the "Language & Subtitles" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to LanguageSettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val LanguageSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "app_language",
        titleRes = R.string.ss_app_language_title,
        subtitleRes = R.string.ss_app_language_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("language", "display", "interface", "locale", "ui language", "app language"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Language
    ),
    SettingsSearchItem(
        id = "audio_language",
        titleRes = R.string.ss_audio_language_title,
        subtitleRes = R.string.ss_audio_language_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("language", "audio track", "speech", "default language"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Language
    ),
    SettingsSearchItem(
        id = "subtitle_language",
        titleRes = R.string.ss_subtitle_language_title,
        subtitleRes = R.string.ss_subtitle_language_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("subtitles", "language", "cc", "captions"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Subtitles
    ),
    SettingsSearchItem(
        id = "subtitle_font_size",
        titleRes = R.string.ss_subtitle_font_size_title,
        subtitleRes = R.string.ss_subtitle_font_size_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("subtitle size", "font size", "text size", "bigger"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Typography
    ),
    SettingsSearchItem(
        id = "subtitle_forced_only",
        titleRes = R.string.ss_subtitle_forced_only_title,
        subtitleRes = R.string.ss_subtitle_forced_only_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("forced", "subtitles", "foreign", "parts", "native"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.TextSize
    ),
    SettingsSearchItem(
        id = "pgs_direct_play",
        titleRes = R.string.ss_pgs_direct_play_title,
        subtitleRes = R.string.ss_pgs_direct_play_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("pgs", "subtitle", "direct play", "picture", "image subtitle", "bluray"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Photo,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "hdr_subtitle_style",
        titleRes = R.string.ss_hdr_subtitle_style_title,
        subtitleRes = R.string.ss_hdr_subtitle_style_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("hdr", "subtitle", "style", "dolby vision", "hdr10", "brightness"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Sun,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "subtitle_color",
        titleRes = R.string.ss_subtitle_color_title,
        subtitleRes = R.string.ss_subtitle_color_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("subtitle color", "text color", "yellow subtitles", "white"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Palette,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "subtitle_background",
        titleRes = R.string.ss_subtitle_background_title,
        subtitleRes = R.string.ss_subtitle_background_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("subtitle background", "opacity", "transparency", "box"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Background,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "subtitle_edge_style",
        titleRes = R.string.ss_subtitle_edge_style_title,
        subtitleRes = R.string.ss_subtitle_edge_style_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("edge style", "shadow", "outline", "border"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.BorderAll,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "subtitle_sync_offset",
        titleRes = R.string.ss_subtitle_sync_offset_title,
        subtitleRes = R.string.ss_subtitle_sync_offset_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("sync", "offset", "delay", "lagging subtitles"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "subtitle_vertical_position",
        titleRes = R.string.ss_subtitle_vertical_position_title,
        subtitleRes = R.string.ss_subtitle_vertical_position_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("position", "height", "vertical", "bottom", "margin"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.ArrowBarDown,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "high_contrast_subtitles",
        titleRes = R.string.ss_high_contrast_subtitles_title,
        subtitleRes = R.string.ss_high_contrast_subtitles_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("subtitle", "high", "contrast", "accessibility", "visibility"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Contrast2,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "subtitle_tester",
        titleRes = R.string.ss_subtitle_tester_title,
        subtitleRes = R.string.ss_subtitle_tester_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("subtitle", "tester", "preview", "sample", "test", "style"),
        route = Route.SubtitleTester,
        icon = Tabler.Outline.EyeCheck
    ),
    SettingsSearchItem(
        id = "hdr_subtitle_font_size",
        titleRes = R.string.ss_hdr_subtitle_font_size_title,
        subtitleRes = R.string.ss_hdr_subtitle_font_size_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_language_subtitles,
        keywords = listOf("hdr", "subtitle", "font size", "text", "dolby vision"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Typography,
        isAdvanced = true
    ),
)
