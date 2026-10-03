package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_language_subtitles
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
 * The language domain's fused row declarations — the feature-side single home
 * of every language/subtitle row's presentation, ordering, and capability
 * (the [AppearanceRows] template), plus the screen's "Track Selection" group
 * ([TrackSelectionRows]). Each [SettingsRow] replaces the trio the domain
 * used to declare per row: the `SettingsSearchBinding` entry, the
 * `SettingsRowRecord` entry, and the `LanguageSettingsIds` /
 * `TrackSelectionIds` holder constants (all retired).
 *
 * Every row is a HAND-MAINTAINED residual (the knobs live in spec-less
 * stores) and carries its full hand search faces; the gates state the shipped
 * quirks explicitly — the per-app display-language row rides the
 * `AppLocaleSetter` seam's capability, high-contrast subtitles is declared
 * advanced yet always shown, and the HDR font-size row compounds advanced
 * with the HDR-style toggle. The projection
 * ([List.toSearchItems]/[List.asRowGroup]) fails fast at catalog init on any
 * drift; search results, catalog order, group membership and per-gate
 * visibility are byte-identical to the retired declarations.
 */
internal object LanguageRows {

    // -- The screen's leading "Language" trio — the split line of the general/subtitles decoration (pinned by `SettingsCatalogScreenContractTest`). --

    val AppLanguage = SettingsRow(
        id = "app_language",
        icon = Tabler.Outline.Language,
        titleRes = Res.string.settings_display_language,
        searchTitleRes = Res.string.ss_app_language_title,
        searchSubtitleRes = Res.string.ss_app_language_subtitle,
        keywords = listOf("language", "display", "interface", "locale", "ui language", "app language"),
        route = Route.LanguageSettings(),
        platforms = platformsForCapability(settingsCapabilities.supportsAppLocaleOverride),
        gate = RowAdmission.Platform(RowAdmissionCapability.AppLocaleOverride),
    )

    val AudioLanguage = SettingsRow(
        id = "audio_language",
        icon = Tabler.Outline.Language,
        titleRes = Res.string.settings_audio_language,
        searchTitleRes = Res.string.ss_audio_language_title,
        searchSubtitleRes = Res.string.ss_audio_language_subtitle,
        keywords = listOf("language", "audio track", "speech", "default language"),
        route = Route.LanguageSettings(),
    )

    val SubtitleLanguage = SettingsRow(
        id = "subtitle_language",
        icon = Tabler.Outline.Subtitles,
        titleRes = Res.string.settings_subtitle_language,
        searchTitleRes = Res.string.ss_subtitle_language_title,
        searchSubtitleRes = Res.string.ss_subtitle_language_subtitle,
        keywords = listOf("subtitles", "language", "cc", "captions"),
        route = Route.LanguageSettings(),
    )

    // -- The "Subtitles" group's twelve rows — every id declares its gate (the strict shape) — in catalog order. --

    val SubtitleFontSize = SettingsRow(
        id = "subtitle_font_size",
        icon = Tabler.Outline.Typography,
        titleRes = Res.string.settings_font_size,
        searchTitleRes = Res.string.ss_subtitle_font_size_title,
        searchSubtitleRes = Res.string.ss_subtitle_font_size_subtitle,
        keywords = listOf("subtitle size", "font size", "text size", "bigger"),
        route = Route.LanguageSettings(),
        gate = RowAdmission.Always,
    )

    val SubtitleForcedOnly = SettingsRow(
        id = "subtitle_forced_only",
        icon = Tabler.Outline.TextSize,
        titleRes = Res.string.settings_forced_subtitles,
        searchTitleRes = Res.string.ss_subtitle_forced_only_title,
        searchSubtitleRes = Res.string.ss_subtitle_forced_only_subtitle,
        keywords = listOf("forced", "subtitles", "foreign", "parts", "native"),
        route = Route.LanguageSettings(),
        gate = RowAdmission.Always,
    )

    val PgsDirectPlay = SettingsRow(
        id = "pgs_direct_play",
        icon = Tabler.Outline.Photo,
        titleRes = Res.string.settings_pgs_direct_play,
        searchTitleRes = Res.string.ss_pgs_direct_play_title,
        searchSubtitleRes = Res.string.ss_pgs_direct_play_subtitle,
        keywords = listOf("pgs", "subtitle", "direct play", "picture", "image subtitle", "bluray"),
        route = Route.LanguageSettings(),
        isAdvanced = true,
        gate = RowAdmission.Advanced,
    )

    val HdrSubtitleStyle = SettingsRow(
        id = "hdr_subtitle_style",
        icon = Tabler.Outline.Sun,
        titleRes = Res.string.settings_hdr_subtitle_style,
        searchTitleRes = Res.string.ss_hdr_subtitle_style_title,
        searchSubtitleRes = Res.string.ss_hdr_subtitle_style_subtitle,
        keywords = listOf("hdr", "subtitle", "style", "dolby vision", "hdr10", "brightness"),
        route = Route.LanguageSettings(),
        isAdvanced = true,
        gate = RowAdmission.Advanced,
    )

    val SubtitleColor = SettingsRow(
        id = "subtitle_color",
        icon = Tabler.Outline.Palette,
        titleRes = Res.string.settings_subtitle_text_color,
        searchTitleRes = Res.string.ss_subtitle_color_title,
        searchSubtitleRes = Res.string.ss_subtitle_color_subtitle,
        keywords = listOf("subtitle color", "text color", "yellow subtitles", "white"),
        route = Route.LanguageSettings(),
        isAdvanced = true,
        gate = RowAdmission.Advanced,
    )

    val SubtitleBackground = SettingsRow(
        id = "subtitle_background",
        icon = Tabler.Outline.Background,
        titleRes = Res.string.settings_subtitle_background,
        searchTitleRes = Res.string.ss_subtitle_background_title,
        searchSubtitleRes = Res.string.ss_subtitle_background_subtitle,
        keywords = listOf("subtitle background", "opacity", "transparency", "box"),
        route = Route.LanguageSettings(),
        isAdvanced = true,
        gate = RowAdmission.Advanced,
    )

    val SubtitleEdgeStyle = SettingsRow(
        id = "subtitle_edge_style",
        icon = Tabler.Outline.BorderAll,
        titleRes = Res.string.settings_subtitle_edge_style,
        searchTitleRes = Res.string.ss_subtitle_edge_style_title,
        searchSubtitleRes = Res.string.ss_subtitle_edge_style_subtitle,
        keywords = listOf("edge style", "shadow", "outline", "border"),
        route = Route.LanguageSettings(),
        isAdvanced = true,
        gate = RowAdmission.Advanced,
    )

    val SubtitleSyncOffset = SettingsRow(
        id = "subtitle_sync_offset",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_subtitle_sync_offset,
        searchTitleRes = Res.string.ss_subtitle_sync_offset_title,
        searchSubtitleRes = Res.string.ss_subtitle_sync_offset_subtitle,
        keywords = listOf("sync", "offset", "delay", "lagging subtitles"),
        route = Route.LanguageSettings(),
        isAdvanced = true,
        gate = RowAdmission.Advanced,
    )

    val SubtitleVerticalPosition = SettingsRow(
        id = "subtitle_vertical_position",
        icon = Tabler.Outline.ArrowBarDown,
        titleRes = Res.string.settings_subtitle_vertical_position,
        searchTitleRes = Res.string.ss_subtitle_vertical_position_title,
        searchSubtitleRes = Res.string.ss_subtitle_vertical_position_subtitle,
        keywords = listOf("position", "height", "vertical", "bottom", "margin"),
        route = Route.LanguageSettings(),
        isAdvanced = true,
        gate = RowAdmission.Advanced,
    )

    val HighContrastSubtitles = SettingsRow(
        id = "high_contrast_subtitles",
        icon = Tabler.Outline.Contrast2,
        titleRes = Res.string.settings_high_contrast_subtitles,
        searchTitleRes = Res.string.ss_high_contrast_subtitles_title,
        searchSubtitleRes = Res.string.ss_high_contrast_subtitles_subtitle,
        keywords = listOf("subtitle", "high", "contrast", "accessibility", "visibility"),
        route = Route.LanguageSettings(),
        isAdvanced = true,
        gate = RowAdmission.Always,
    )

    val SubtitleTester = SettingsRow(
        id = "subtitle_tester",
        icon = Tabler.Outline.EyeCheck,
        titleRes = Res.string.settings_open_subtitle_tester,
        searchTitleRes = Res.string.ss_subtitle_tester_title,
        searchSubtitleRes = Res.string.ss_subtitle_tester_subtitle,
        keywords = listOf("subtitle", "tester", "preview", "sample", "test", "style"),
        route = Route.SubtitleTester,
        gate = RowAdmission.Always,
    )

    val HdrSubtitleFontSize = SettingsRow(
        id = "hdr_subtitle_font_size",
        icon = Tabler.Outline.Typography,
        titleRes = Res.string.settings_hdr_font_size,
        searchTitleRes = Res.string.ss_hdr_subtitle_font_size_title,
        searchSubtitleRes = Res.string.ss_hdr_subtitle_font_size_subtitle,
        keywords = listOf("hdr", "subtitle", "font size", "text", "dolby vision"),
        route = Route.LanguageSettings(),
        isAdvanced = true,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.WhenOn(LanguageRows.HdrSubtitleStyle.id)),
    )

    /**
     * Every fused language row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = LanguageGeneralRows + LanguageSubtitlesRows
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = emptyMap()

private val languageCategory = CoreUiRes.string.ss_cat_language_subtitles

internal val LanguageGeneralRows: List<SettingsRow> = listOf(
    LanguageRows.AppLanguage,
    LanguageRows.AudioLanguage,
    LanguageRows.SubtitleLanguage,
)

internal val LanguageSubtitlesRows: List<SettingsRow> = listOf(
    LanguageRows.SubtitleFontSize,
    LanguageRows.SubtitleForcedOnly,
    LanguageRows.PgsDirectPlay,
    LanguageRows.HdrSubtitleStyle,
    LanguageRows.SubtitleColor,
    LanguageRows.SubtitleBackground,
    LanguageRows.SubtitleEdgeStyle,
    LanguageRows.SubtitleSyncOffset,
    LanguageRows.SubtitleVerticalPosition,
    LanguageRows.HighContrastSubtitles,
    LanguageRows.SubtitleTester,
    LanguageRows.HdrSubtitleFontSize,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val LanguageGeneralGroup =
    LanguageGeneralRows.asRowGroup("language.general", emptyList(), searchRoutes, languageCategory)

internal val LanguageSubtitlesGroup =
    LanguageSubtitlesRows.asRowGroup("language.subtitles", emptyList(), searchRoutes, languageCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

/** The whole language declaration list — the two screen groups' concatenation (the leading trio then the subtitles half), the pinned catalog order. */
internal val LanguageSettingsSearchItems: List<SettingsSearchItem> = LanguageGeneralGroup.items + LanguageSubtitlesGroup.items

// -- The domain's root-screen entrance declaration --

/** The language domain's root-screen entrance — the ONE ordered declaration that drives both the settings root's `item_language` section emission (icon/title/route id) and its entrance-step index (spliced into [SETTINGS_ENTRANCE_SECTIONS] at this render position). */
internal val LanguageEntrance = SettingsEntranceSectionRow(
    key = "item_language",
    rowId = "language",
    icon = Tabler.Outline.Language,
    titleRes = Res.string.settings_language_subtitles,
)
