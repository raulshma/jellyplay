package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Activity
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Broadcast
import com.composables.icons.tabler.outline.Exchange
import com.composables.icons.tabler.outline.Graph
import com.composables.icons.tabler.outline.Inbox
import com.composables.icons.tabler.outline.LayoutRows
import com.composables.icons.tabler.outline.News
import com.composables.icons.tabler.outline.Plant
import com.composables.icons.tabler.outline.PlugConnected
import com.composables.icons.tabler.outline.Radar
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Stars
import com.composables.icons.tabler.outline.Sparkles
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_system
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_analytics
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_plugin_entry
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_anime_markers
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_bookmarks
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_custom_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_events
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_messages
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_newsletter
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_push
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_ratings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_recommendations
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_seerr_bridge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_seasonal_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_transcodes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_feature_user_ratings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_features_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sum_plugin_sync_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sum_plugin_sync_on
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_analytics_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_anime_markers_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_bookmarks_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_custom_rows_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_events_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_messages_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_newsletter_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_push_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_ratings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_recommendations_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_seerr_bridge_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_seasonal_rows_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_transcodes_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_feature_user_ratings_subtitle

/**
 * The "Server plugin" section's per-feature toggle rows — one
 * switch per USER-FACING capability key, each emitted ONLY where the
 * capabilities probe currently exposes it (the section itself hides whenever
 * the plugin is UNAVAILABLE/UNKNOWN — never dead switches). The meta keys
 * (settings-sync, device-profiles, admin-defaults, config-backup) have no row
 * here: they are probe-governed infrastructure, deliberately unlisted.
 *
 * The knob behind every row is an ordinary synced preference
 * (`pluginFeature.<featureKey>.enabled`, absent = enabled) read and written
 * through [com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate] —
 * the residual-row shape (no datastore spec): hand search faces + `route =
 * Route.JellyPlayPluginSettings`, so a search hit deep-links into the plugin
 * screen with the row id baked in as the highlight target (ADR-0017 moved
 * the root's inline plugin section onto that screen).
 */
internal object JellyPlayFeatureRows {

    /** One row plus the wire feature key (+ admin-context flag) it switches. */
    internal data class Declared(
        val row: SettingsRow,
        val featureKey: String,
        /** Emitted only for admin users (the ActiveDevicesRow gating idiom). */
        val adminOnly: Boolean = false,
    )

    val Events = SettingsRow(
        id = "jellyplay_feature_events",
        icon = Tabler.Outline.Broadcast,
        titleRes = Res.string.settings_jellyplay_feature_events,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_events_subtitle,
        keywords = listOf("events", "live", "broadcast", "notifications", "sse", "jellyplay", "plugin"),
        route = Route.JellyPlayPluginSettings(),
    )

    val Messages = SettingsRow(
        id = "jellyplay_feature_messages",
        icon = Tabler.Outline.Inbox,
        titleRes = Res.string.settings_jellyplay_feature_messages,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_messages_subtitle,
        keywords = listOf("messages", "inbox", "unread", "jellyplay", "plugin"),
        route = Route.JellyPlayPluginSettings(),
    )

    val SeerrBridge = SettingsRow(
        id = "jellyplay_feature_seerr_bridge",
        icon = Tabler.Outline.Exchange,
        titleRes = Res.string.settings_jellyplay_feature_seerr_bridge,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_seerr_bridge_subtitle,
        keywords = listOf("seerr", "bridge", "proxy", "via server", "jellyplay", "plugin", "seerr-bridge"),
        route = Route.JellyPlayPluginSettings(),
    )

    val Ratings = SettingsRow(
        id = "jellyplay_feature_ratings",
        icon = Tabler.Outline.Star,
        titleRes = Res.string.settings_jellyplay_feature_ratings,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_ratings_subtitle,
        keywords = listOf("ratings", "mdblist", "scores", "detail", "jellyplay", "plugin"),
        route = Route.JellyPlayPluginSettings(),
    )

    val CustomRows = SettingsRow(
        id = "jellyplay_feature_custom_rows",
        icon = Tabler.Outline.LayoutRows,
        titleRes = Res.string.settings_jellyplay_feature_custom_rows,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_custom_rows_subtitle,
        keywords = listOf("custom", "home", "rows", "admin", "jellyplay", "plugin", "custom-rows"),
        route = Route.JellyPlayPluginSettings(),
    )

    val SeasonalRows = SettingsRow(
        id = "jellyplay_feature_seasonal_rows",
        icon = Tabler.Outline.Plant,
        titleRes = Res.string.settings_jellyplay_feature_seasonal_rows,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_seasonal_rows_subtitle,
        keywords = listOf("seasonal", "season", "home", "row", "jellyplay", "plugin", "seasonal-rows"),
        route = Route.JellyPlayPluginSettings(),
    )

    val AnimeMarkers = SettingsRow(
        id = "jellyplay_feature_anime_markers",
        icon = Tabler.Outline.Radar,
        titleRes = Res.string.settings_jellyplay_feature_anime_markers,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_anime_markers_subtitle,
        keywords = listOf("anime", "filler", "recap", "markers", "episodes", "jellyplay", "plugin"),
        route = Route.JellyPlayPluginSettings(),
    )

    val Recommendations = SettingsRow(
        id = "jellyplay_feature_recommendations",
        icon = Tabler.Outline.Sparkles,
        titleRes = Res.string.settings_jellyplay_feature_recommendations,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_recommendations_subtitle,
        keywords = listOf("recommendations", "similar", "more like this", "detail", "jellyplay", "plugin"),
        route = Route.JellyPlayPluginSettings(),
    )

    val UserRatings = SettingsRow(
        id = "jellyplay_feature_user_ratings",
        icon = Tabler.Outline.Stars,
        titleRes = Res.string.settings_jellyplay_feature_user_ratings,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_user_ratings_subtitle,
        keywords = listOf("user", "ratings", "sync", "jellyplay", "plugin", "user-ratings"),
        route = Route.JellyPlayPluginSettings(),
    )

    val Bookmarks = SettingsRow(
        id = "jellyplay_feature_bookmarks",
        icon = Tabler.Outline.Bookmark,
        titleRes = Res.string.settings_jellyplay_feature_bookmarks,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_bookmarks_subtitle,
        keywords = listOf("bookmarks", "reader", "books", "sync", "jellyplay", "plugin"),
        route = Route.JellyPlayPluginSettings(),
    )

    val Newsletter = SettingsRow(
        id = "jellyplay_feature_newsletter",
        icon = Tabler.Outline.News,
        titleRes = Res.string.settings_jellyplay_feature_newsletter,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_newsletter_subtitle,
        keywords = listOf("newsletter", "digest", "entry", "jellyplay", "plugin"),
        route = Route.JellyPlayPluginSettings(),
    )

    val Transcodes = SettingsRow(
        id = "jellyplay_feature_transcodes",
        icon = Tabler.Outline.Activity,
        titleRes = Res.string.settings_jellyplay_feature_transcodes,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_transcodes_subtitle,
        keywords = listOf("transcodes", "transcoding", "admin", "monitor", "dashboard", "jellyplay", "plugin"),
        route = Route.JellyPlayPluginSettings(),
    )

    /**
     * The push toggle (the plugin's `push` key — exposed only where the
     * server admin enabled push). The section renders this row's subtitle
     * DYNAMICALLY from the push repository's state machine (Registered / No
     * distributor app / Server push disabled); this static search subtitle is
     * the search-result face and the pre-registration default.
     */
    val Push = SettingsRow(
        id = "jellyplay_feature_push",
        icon = Tabler.Outline.Bell,
        titleRes = Res.string.settings_jellyplay_feature_push,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_push_subtitle,
        keywords = listOf("push", "notifications", "unifiedpush", "ntfy", "distributor", "jellyplay", "plugin"),
        route = Route.JellyPlayPluginSettings(),
    )

    /**
     * The analytics toggle (the plugin's `analytics` key — an
     * admin-context surface like [Transcodes], so the row is admin-only: the
     * switch switches the admin dashboard's analytics tile and screen).
     */
    val Analytics = SettingsRow(
        id = "jellyplay_feature_analytics",
        icon = Tabler.Outline.Graph,
        titleRes = Res.string.settings_jellyplay_feature_analytics,
        searchSubtitleRes = Res.string.ss_jellyplay_feature_analytics_subtitle,
        keywords = listOf("analytics", "statistics", "plays", "watch time", "dashboard", "admin", "jellyplay", "plugin"),
        route = Route.JellyPlayPluginSettings(),
    )

    /** The whole declaration, in emission order — the section's vocabulary. */
    val declared: List<Declared> = listOf(
        Declared(Events, JellyPlayPluginFeatures.Events),
        Declared(Messages, JellyPlayPluginFeatures.Messages),
        Declared(SeerrBridge, JellyPlayPluginFeatures.SeerrBridge),
        Declared(Ratings, JellyPlayPluginFeatures.Ratings),
        Declared(CustomRows, JellyPlayPluginFeatures.CustomRows),
        Declared(SeasonalRows, JellyPlayPluginFeatures.SeasonalRows),
        Declared(AnimeMarkers, JellyPlayPluginFeatures.AnimeMarkers),
        Declared(Recommendations, JellyPlayPluginFeatures.Recommendations),
        Declared(UserRatings, JellyPlayPluginFeatures.UserRatings),
        Declared(Bookmarks, JellyPlayPluginFeatures.Bookmarks),
        Declared(Newsletter, JellyPlayPluginFeatures.Newsletter),
        Declared(Push, JellyPlayPluginFeatures.Push),
        Declared(Transcodes, JellyPlayPluginFeatures.Transcodes, adminOnly = true),
        Declared(Analytics, JellyPlayPluginFeatures.Analytics, adminOnly = true),
    )

    /** The rows only (the group derivation's input). */
    val all: List<SettingsRow> = declared.map { it.row }
}

private val jellyPlayFeatureSearchRoutes: Map<String, Route> =
    JellyPlayFeatureRows.all.associate { it.id to Route.JellyPlayPluginSettings() }

/** One on-screen group on the plugin screen (probe-gated at emission). */
internal val JellyPlayFeaturesGroup =
    JellyPlayFeatureRows.all.asRowGroup("jellyplay.features", emptyList(), jellyPlayFeatureSearchRoutes, CoreUiRes.string.ss_cat_system)

// ── The domain's root-screen entrance declaration ────────────────────────

/**
 * The JellyPlay companion-plugin domain's root-screen entrance (ADR-0017):
 * the root's former inline plugin section is now ONE screen
 * ([Route.JellyPlayPluginSettings]) behind this entrance — the ONE ordered
 * declaration that drives the root's `item_jellyplay_plugin` section
 * emission and its entrance-step index. The row's probe gate (AVAILABLE)
 * stays at the emission site.
 */
internal val JellyPlayPluginEntrance = SettingsEntranceSectionRow(
    key = "item_jellyplay_plugin",
    rowId = "jellyplay_plugin",
    icon = Tabler.Outline.PlugConnected,
    titleRes = Res.string.settings_jellyplay_plugin_entry,
)

// ── Entrance summary (ADR-0017 decision 3) ──────────────────────────────

/**
 * The root JellyPlay-plugin entrance's summary: the settings-sync posture
 * plus the enabled-feature count (the "%d feature(s) enabled" plural — the
 * experimental fold's resource, one vocabulary for "how much of the plugin is
 * on"). The probe-gated row composes only while the plugin is AVAILABLE (the
 * emission site's gate), so the values are always live when shown; the sync
 * flag and the enabled count ride the root VM's existing plugin flows, passed
 * in as slice values to keep this fold pure. Pinned by SettingsSummariesTest.
 */
internal fun jellyPlayPluginSummaryParts(
    syncEnabled: Boolean,
    enabledFeatureCount: Int,
): List<SettingsSummaryPart> = buildList {
    add(
        SettingsSummaryPart.Token(
            if (syncEnabled) Res.string.settings_sum_plugin_sync_on else Res.string.settings_sum_plugin_sync_off,
        ),
    )
    if (enabledFeatureCount > 0) {
        add(SettingsSummaryPart.Plural(Res.plurals.settings_features_enabled, enabledFeatureCount))
    }
}
