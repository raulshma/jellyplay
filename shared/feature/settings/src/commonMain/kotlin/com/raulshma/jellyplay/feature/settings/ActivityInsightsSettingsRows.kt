package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_activity_insights
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_activity_queue
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_browse_favorites
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_requests
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_upcoming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_watch_history_heatmap
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_activity_queue_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_activity_queue_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_favorites_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_favorites_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_requests_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_requests_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_upcoming_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_upcoming_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_watch_progress_heatmap_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_watch_progress_heatmap_title

/**
 * The activity/insights domain's fused row declarations — the feature-side
 * single home of the main screen's activity-group rows (the [AppearanceRows]
 * template). Each [SettingsRow] replaces the trio the domain used to declare
 * per row: the `SettingsSearchBinding` entry, the `SettingsRowRecord` entry,
 * and the `SettingsScreenIds` holder constant (all retired).
 *
 * HAND-MAINTAINED residuals: navigation facts whose knobs live in stores
 * without spec machinery — they carry their full hand search faces. The
 * projection ([List.toSearchItems]) is the pure hand-projection; search
 * results, catalog order and group membership are byte-identical to the
 * retired declarations.
 */
internal object ActivityInsightsRows {

    // -- The main screen's "Activity & Insights" group — navigation facts whose knobs live in spec-less stores — in catalog order. --

    val Favorites = SettingsRow(
        id = "favorites",
        icon = Tabler.Outline.Heart,
        titleRes = Res.string.settings_browse_favorites,
        searchTitleRes = Res.string.ss_favorites_title,
        searchSubtitleRes = Res.string.ss_favorites_subtitle,
        keywords = listOf("favorites", "favourite", "liked", "collection", "heart"),
        route = Route.Favorites,
    )

    val WatchProgressHeatmap = SettingsRow(
        id = "watch_progress_heatmap",
        icon = Tabler.Outline.ChartBar,
        titleRes = Res.string.settings_watch_history_heatmap,
        searchTitleRes = Res.string.ss_watch_progress_heatmap_title,
        searchSubtitleRes = Res.string.ss_watch_progress_heatmap_subtitle,
        keywords = listOf("watch", "history", "heatmap", "progress", "activity", "stats"),
        route = Route.WatchProgressHeatmap,
    )

    val ActivityQueue = SettingsRow(
        id = "activity_queue",
        icon = Tabler.Outline.Database,
        titleRes = Res.string.settings_activity_queue,
        searchTitleRes = Res.string.ss_activity_queue_title,
        searchSubtitleRes = Res.string.ss_activity_queue_subtitle,
        keywords = listOf("activity", "queue", "download", "radarr", "sonarr", "arr", "import"),
        route = Route.ArrQueue,
    )

    val Upcoming = SettingsRow(
        id = "upcoming",
        icon = Tabler.Outline.CalendarEvent,
        titleRes = Res.string.settings_upcoming,
        searchTitleRes = Res.string.ss_upcoming_title,
        searchSubtitleRes = Res.string.ss_upcoming_subtitle,
        keywords = listOf("upcoming", "calendar", "schedule", "new", "episodes", "soon"),
        route = Route.UpcomingCalendar,
    )

    val Requests = SettingsRow(
        id = "requests",
        icon = Tabler.Outline.Inbox,
        titleRes = Res.string.settings_requests,
        searchTitleRes = Res.string.ss_requests_title,
        searchSubtitleRes = Res.string.ss_requests_subtitle,
        keywords = listOf("requests", "seerr", "jellyseerr", "pending", "approve"),
        route = Route.Requests,
    )

    /**
     * Every fused activityInsights row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = ActivityInsightsRowsList
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = emptyMap()

private val activityCategory = CoreUiRes.string.ss_cat_activity_insights

internal val ActivityInsightsRowsList: List<SettingsRow> = listOf(
    ActivityInsightsRows.Favorites,
    ActivityInsightsRows.WatchProgressHeatmap,
    ActivityInsightsRows.ActivityQueue,
    ActivityInsightsRows.Upcoming,
    ActivityInsightsRows.Requests,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val ActivityInsightsGroup =
    ActivityInsightsRowsList.asRowGroup("activityInsights", emptyList(), searchRoutes, activityCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val ActivityInsightsSearchItems: List<SettingsSearchItem> = ActivityInsightsGroup.items
