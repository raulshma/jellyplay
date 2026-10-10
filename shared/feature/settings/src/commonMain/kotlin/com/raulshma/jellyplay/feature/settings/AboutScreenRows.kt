package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_about
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_about
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_app_info
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_about_version_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_about_version_title

/**
 * The about domain's fused row declaration — the feature-side single home of
 * the about screen's row (the [AppearanceRows] template). The one
 * [SettingsRow] replaces the trio the domain used to declare: the
 * `SettingsSearchBinding` entry, the `SettingsRowRecord` entry, and the
 * `AboutScreenIds` holder constant (all retired).
 *
 * HAND-MAINTAINED residual: the about row is a navigation fact whose knob
 * lives in a store without spec machinery — it carries its full hand search
 * face. The projection ([List.toSearchItems]) is the pure hand-projection;
 * search results, catalog order and group membership are byte-identical to
 * the retired declarations.
 */
internal object AboutRows {

    // -- The "About" group's single row — in catalog order. --

    val AboutVersion = SettingsRow(
        id = "about_version",
        icon = Tabler.Outline.InfoCircle,
        titleRes = Res.string.settings_app_info,
        searchTitleRes = Res.string.ss_about_version_title,
        searchSubtitleRes = Res.string.ss_about_version_subtitle,
        keywords = listOf("about", "version", "licenses", "open source", "developer"),
        route = Route.About,
    )

    /**
     * Every fused about row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = AboutRowsList
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = emptyMap()

private val aboutCategory = CoreUiRes.string.ss_cat_about

internal val AboutRowsList: List<SettingsRow> = listOf(
    AboutRows.AboutVersion,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val AboutGroup =
    AboutRowsList.asRowGroup("about", emptyList(), searchRoutes, aboutCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val AboutSearchItems: List<SettingsSearchItem> = AboutGroup.items

// -- The domain's root-screen entrance declaration --

/** The about domain's root-screen entrance — the ONE ordered declaration that drives both the settings root's `item_about` section emission (icon/title/route id) and its entrance-step index (spliced into [SETTINGS_ENTRANCE_SECTIONS] at this render position). */
internal val AboutEntrance = SettingsEntranceSectionRow(
    key = "item_about",
    rowId = "about",
    icon = Tabler.Outline.InfoCircle,
    titleRes = Res.string.settings_about,
)
