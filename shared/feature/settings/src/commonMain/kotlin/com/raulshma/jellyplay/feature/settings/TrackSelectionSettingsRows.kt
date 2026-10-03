package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AdjustmentsHorizontal
import com.composables.icons.tabler.outline.ArrowsHorizontal
import com.composables.icons.tabler.outline.Filter
import com.composables.icons.tabler.outline.ListNumbers
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
 * The track-selection domain's fused row declarations — the feature-side
 * single home of the language screen's "Track Selection" group (the
 * [AppearanceRows] template). Each [SettingsRow] replaces the trio the domain
 * used to declare per row: the `SettingsSearchBinding` entry, the
 * `SettingsRowRecord` entry, and the `TrackSelectionIds` holder constant
 * (all retired).
 *
 * HAND-MAINTAINED residuals: the track-selection knobs live in the spec-less
 * track-selection store — the rows carry their full hand search faces and
 * always render ([RowAdmission.Always] derived from their non-advanced
 * flags). The projection ([List.toSearchItems]) is the pure
 * hand-projection; search results, catalog order and group membership are
 * byte-identical to the retired declarations.
 */
internal object TrackSelectionRows {

    // -- The language screen's "Track Selection" group's four rows — every row always renders — in catalog order. --

    val TrackSelectionPreset = SettingsRow(
        id = "track_selection_preset",
        icon = Tabler.Outline.AdjustmentsHorizontal,
        titleRes = Res.string.settings_track_selection_preset,
        searchTitleRes = Res.string.ss_track_selection_preset_title,
        searchSubtitleRes = Res.string.ss_track_selection_preset_subtitle,
        keywords = listOf("preset", "subbed", "dubbed", "language", "track", "automatic"),
        route = Route.LanguageSettings(),
        gate = RowAdmission.Always,
    )

    val TrackAudioLanguages = SettingsRow(
        id = "track_audio_languages_ordered",
        icon = Tabler.Outline.ListNumbers,
        titleRes = Res.string.settings_track_audio_order,
        searchTitleRes = Res.string.ss_track_audio_order_title,
        searchSubtitleRes = Res.string.ss_track_audio_order_subtitle,
        keywords = listOf("audio", "language", "order", "priority", "fallback"),
        route = Route.LanguageSettings(),
        gate = RowAdmission.Always,
    )

    val TrackSubtitleLanguages = SettingsRow(
        id = "track_subtitle_languages_ordered",
        icon = Tabler.Outline.ArrowsHorizontal,
        titleRes = Res.string.settings_track_subtitle_order,
        searchTitleRes = Res.string.ss_track_subtitle_order_title,
        searchSubtitleRes = Res.string.ss_track_subtitle_order_subtitle,
        keywords = listOf("subtitle", "language", "order", "priority", "fallback"),
        route = Route.LanguageSettings(),
        gate = RowAdmission.Always,
    )

    val TrackRules = SettingsRow(
        id = "track_selection_rules",
        icon = Tabler.Outline.Filter,
        titleRes = Res.string.settings_track_rules,
        searchTitleRes = Res.string.ss_track_rules_title,
        searchSubtitleRes = Res.string.ss_track_rules_subtitle,
        keywords = listOf("rule", "regex", "anime", "signs", "songs", "per series", "advanced"),
        route = Route.LanguageSettings(),
        gate = RowAdmission.Always,
    )

    /**
     * Every fused trackSelection row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = TrackSelectionRowsList
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = emptyMap()

private val trackSelectionCategory = CoreUiRes.string.ss_cat_language_subtitles

internal val TrackSelectionRowsList: List<SettingsRow> = listOf(
    TrackSelectionRows.TrackSelectionPreset,
    TrackSelectionRows.TrackAudioLanguages,
    TrackSelectionRows.TrackSubtitleLanguages,
    TrackSelectionRows.TrackRules,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val LanguageTrackSelectionGroup =
    TrackSelectionRowsList.asRowGroup("language.trackSelection", emptyList(), searchRoutes, trackSelectionCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val TrackSelectionSearchItems: List<SettingsSearchItem> = LanguageTrackSelectionGroup.items
