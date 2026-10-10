package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_integrations
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.withHighlightSettingId
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_subtitles_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_integration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_integration_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_integrations_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_integrations_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_seerr_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_seerr_settings_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_provider_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_provider_settings_title

/**
 * The integrations domain's fused row declarations — the feature-side single
 * home of the integrations hub's rows (the [AppearanceRows] template). Each
 * [SettingsRow] replaces the trio the domain used to declare per row: the
 * `SettingsSearchBinding` entry, the `SettingsRowRecord` entry, and the
 * `IntegrationsScreenIds` holder constant (all retired).
 *
 * HAND-MAINTAINED residuals: navigation facts whose knobs live in stores
 * without spec machinery — they carry their full hand search faces. The
 * projection ([List.toSearchItems]) is the pure hand-projection; search
 * results, catalog order and group membership are byte-identical to the
 * retired declarations.
 */
internal object IntegrationsRows {

    // -- The integrations hub's three rows (Seerr, the hub itself, the subtitle-provider drill-in) — in catalog order. --

    val SeerrSettings = SettingsRow(
        id = "seerr_settings",
        icon = Tabler.Outline.Puzzle,
        titleRes = Res.string.settings_seerr_integration,
        subtitleRes = Res.string.settings_seerr_integration_subtitle,
        searchTitleRes = Res.string.ss_seerr_settings_title,
        searchSubtitleRes = Res.string.ss_seerr_settings_subtitle,
        keywords = listOf("seerr", "jellyseerr", "request", "movies", "shows", "approve", "integration"),
        route = Route.SeerrSettings(),
    )

    val Integrations = SettingsRow(
        id = "integrations",
        icon = Tabler.Outline.Plug,
        titleRes = Res.string.settings_integrations,
        searchTitleRes = Res.string.ss_integrations_title,
        searchSubtitleRes = Res.string.ss_integrations_subtitle,
        keywords = listOf("integrations", "jellyseerr", "overseerr", "arr", "sonarr", "radarr", "request"),
        route = Route.Integrations().withHighlightSettingId("integrations"),
    )

    val SubtitleProviderSettings = SettingsRow(
        id = "subtitle_provider_settings",
        icon = Tabler.Outline.Language,
        titleRes = Res.string.settings_integrations_subtitles,
        subtitleRes = Res.string.settings_integrations_subtitles_subtitle,
        searchTitleRes = Res.string.ss_subtitle_provider_settings_title,
        searchSubtitleRes = Res.string.ss_subtitle_provider_settings_subtitle,
        keywords = listOf("subtitle", "provider", "opensubtitles", "opensubtitles.com", "tvsubs", "manager", "extensions"),
        route = Route.Integrations().withHighlightSettingId("subtitle_provider_settings"),
        isAdvanced = true,
        // Advanced-tagged yet always rendered: the hub's hand-built rows carry
        // explicit index/count, so the legacy tag the screen never honored gets
        // the explicit Always override (the appearance HapticsEnabled shape).
    )

    /**
     * Every fused integrations row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = IntegrationsRowsList
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = emptyMap()

private val integrationsCategory = CoreUiRes.string.ss_cat_integrations

internal val IntegrationsRowsList: List<SettingsRow> = listOf(
    IntegrationsRows.SeerrSettings,
    IntegrationsRows.Integrations,
    IntegrationsRows.SubtitleProviderSettings,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val IntegrationsGroup =
    IntegrationsRowsList.asRowGroup("integrations", emptyList(), searchRoutes, integrationsCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val IntegrationsSearchItems: List<SettingsSearchItem> = IntegrationsGroup.items

// -- The domain's root-screen entrance declaration --

/** The integrations domain's root-screen entrance — the ONE ordered declaration that drives both the settings root's `item_integrations` section emission (icon/title/route id) and its entrance-step index (spliced into [SETTINGS_ENTRANCE_SECTIONS] at this render position). */
internal val IntegrationsEntrance = SettingsEntranceSectionRow(
    key = "item_integrations",
    rowId = "integrations",
    icon = Tabler.Outline.Plug,
    titleRes = Res.string.settings_integrations,
)
