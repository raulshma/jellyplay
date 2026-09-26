package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.AdjustmentsHorizontal
import com.composables.icons.tabler.outline.ArrowsHorizontal
import com.composables.icons.tabler.outline.Filter
import com.composables.icons.tabler.outline.ListNumbers
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_language_subtitles
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_audio_order
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rules
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_selection_preset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_subtitle_order
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_track_audio_order_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_track_audio_order_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_track_rules_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_track_rules_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_track_selection_preset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_track_selection_preset_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_track_subtitle_order_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_track_subtitle_order_title

/**
 * The single-source row ids of the language screen's track-selection group.
 * Same contract as [LanguageSettingsIds]: every consumer references
 * these constants, and the values are the persisted deep-link/recents
 * contract.
 */
internal object TrackSelectionIds {
    const val TRACK_SELECTION_PRESET = "track_selection_preset"
    const val TRACK_AUDIO_LANGUAGES = "track_audio_languages_ordered"
    const val TRACK_SUBTITLE_LANGUAGES = "track_subtitle_languages_ordered"
    const val TRACK_RULES = "track_selection_rules"
}

/**
 * Settings-search items for the language screen's "Track Selection" group
 * (preset picker, ordered audio/subtitle language editors, advanced
 * rules). A separate declaration list from [LanguageSettingsSearchItems] so
 * the language.general/language.subtitles split line stays untouched; the
 * rows always render, so every id declares [RowAdmission.Always].
 */
internal val TrackSelectionRowRecords = listOf(
    SettingsRowRecord(
        id = TrackSelectionIds.TRACK_SELECTION_PRESET,
        titleRes = Res.string.settings_track_selection_preset,
        searchTitleRes = Res.string.ss_track_selection_preset_title,
        searchSubtitleRes = Res.string.ss_track_selection_preset_subtitle,
        keywords = listOf("preset", "subbed", "dubbed", "language", "track", "automatic"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.AdjustmentsHorizontal,
    ),
    SettingsRowRecord(
        id = TrackSelectionIds.TRACK_AUDIO_LANGUAGES,
        titleRes = Res.string.settings_track_audio_order,
        searchTitleRes = Res.string.ss_track_audio_order_title,
        searchSubtitleRes = Res.string.ss_track_audio_order_subtitle,
        keywords = listOf("audio", "language", "order", "priority", "fallback"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.ListNumbers,
    ),
    SettingsRowRecord(
        id = TrackSelectionIds.TRACK_SUBTITLE_LANGUAGES,
        titleRes = Res.string.settings_track_subtitle_order,
        searchTitleRes = Res.string.ss_track_subtitle_order_title,
        searchSubtitleRes = Res.string.ss_track_subtitle_order_subtitle,
        keywords = listOf("subtitle", "language", "order", "priority", "fallback"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.ArrowsHorizontal,
    ),
    SettingsRowRecord(
        id = TrackSelectionIds.TRACK_RULES,
        titleRes = Res.string.settings_track_rules,
        searchTitleRes = Res.string.ss_track_rules_title,
        searchSubtitleRes = Res.string.ss_track_rules_subtitle,
        keywords = listOf("rule", "regex", "anime", "signs", "songs", "per series", "advanced"),
        route = Route.LanguageSettings(),
        icon = Tabler.Outline.Filter,
    ))

/** The catalog projection of `TrackSelectionRowRecords`: the search faces + the shared category. */
internal val TrackSelectionSearchItems: List<SettingsSearchItem> = TrackSelectionRowRecords.toSearchItems(CoreUiRes.string.ss_cat_language_subtitles)


/** Every track-selection row renders unconditionally (the strict-count default never fires). */
internal val TrackSelectionRowAdmissions: Map<String, RowAdmission> = mapOf(
    TrackSelectionIds.TRACK_SELECTION_PRESET to RowAdmission.Always,
    TrackSelectionIds.TRACK_AUDIO_LANGUAGES to RowAdmission.Always,
    TrackSelectionIds.TRACK_SUBTITLE_LANGUAGES to RowAdmission.Always,
    TrackSelectionIds.TRACK_RULES to RowAdmission.Always,
)
