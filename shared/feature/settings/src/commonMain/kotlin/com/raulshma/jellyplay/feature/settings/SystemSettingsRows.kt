package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_system
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_admin_dashboard
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_categories
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discord_presence_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dream_dim_after
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dream_dim_percent
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dream_max_parental_rating
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_ended_cmd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_idle_cmd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_idle_ended_cmd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_play_cmd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hooks_stop_cmd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_idle_ambient_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_idle_ambient_timeout
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_ken_burns
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_setup_wizard
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_slideshow_interval
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_transition_style
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_admin_dashboard_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_admin_dashboard_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_discord_presence_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_ended_cmd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_idle_cmd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_idle_ended_cmd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_play_cmd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hooks_stop_cmd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_enabled_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_timeout_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_idle_ambient_timeout_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_categories_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_categories_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_dim_after_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_dim_percent_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_ken_burns_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_ken_burns_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_max_parental_rating_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_show_title_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_show_title_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_slideshow_interval_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_slideshow_interval_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_transition_style_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_screensaver_transition_style_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_setup_wizard_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_setup_wizard_title

/**
 * The system domain's fused row declarations — the feature-side single home
 * of every system row's presentation, ordering, and capability (the
 * [AppearanceRows] template). Each [SettingsRow] replaces the trio the domain
 * used to declare per row: the `SettingsSearchBinding` entry, the
 * `SettingsRowRecord` entry, and the `SettingsScreenIds` holder constant
 * (all retired).
 *
 * The SEMANTICS stay two-homed by design: the dream/desktop-shell rows'
 * keywords, advanced flag, platform rule and route kind live on the
 * datastore-side [ScreensaverPreferenceSpecs]; the leading navigation pair
 * (admin dashboard, setup wizard) stays a HAND-MAINTAINED residual. The five
 * screen groups (core / screensaver / idle-ambient / discord-presence /
 * hooks) partition the one ordered declaration list — the split happens at
 * the group decoration, the list itself stays whole. The projection
 * ([List.toSearchItems]/[List.asRowGroup]) fails fast at catalog init on any
 * row-spec drift; search results, catalog order, group membership and
 * per-gate visibility are byte-identical to the retired declarations.
 */
internal object SystemRows {

    // -- The main screen's System group — the leading navigation pair (admin dashboard, setup wizard), HAND-MAINTAINED residuals. --

    // ── RESIDUAL rows (navigation facts, spec-less stores).
    val AdminDashboard = SettingsRow(
        id = "admin_dashboard",
        icon = Tabler.Outline.Shield,
        titleRes = Res.string.settings_admin_dashboard,
        searchTitleRes = Res.string.ss_admin_dashboard_title,
        searchSubtitleRes = Res.string.ss_admin_dashboard_subtitle,
        keywords = listOf("admin", "dashboard", "sessions", "server", "management"),
        route = Route.AdminDashboard,
    )

    val SetupWizard = SettingsRow(
        id = "setup_wizard",
        icon = Tabler.Outline.Wand,
        titleRes = Res.string.settings_setup_wizard,
        searchTitleRes = Res.string.ss_setup_wizard_title,
        searchSubtitleRes = Res.string.ss_setup_wizard_subtitle,
        keywords = listOf("setup", "wizard", "onboarding", "configure", "initial"),
        route = Route.Onboarding,
    )

    // -- The TV dream group's eight rows — spec-backed by [ScreensaverPreferenceSpecs] — in catalog order. --

    val ScreensaverShowTitle = SettingsRow(
        id = "screensaver_show_title",
        icon = Tabler.Outline.Typography,
        titleRes = Res.string.settings_show_title,
        searchTitleRes = Res.string.ss_screensaver_show_title_title,
        searchSubtitleRes = Res.string.ss_screensaver_show_title_subtitle,
    )

    val ScreensaverCategories = SettingsRow(
        id = "screensaver_categories",
        icon = Tabler.Outline.Folders,
        titleRes = Res.string.settings_categories,
        searchTitleRes = Res.string.ss_screensaver_categories_title,
        searchSubtitleRes = Res.string.ss_screensaver_categories_subtitle,
    )

    val ScreensaverSlideshowInterval = SettingsRow(
        id = "screensaver_slideshow_interval",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_slideshow_interval,
        searchTitleRes = Res.string.ss_screensaver_slideshow_interval_title,
        searchSubtitleRes = Res.string.ss_screensaver_slideshow_interval_subtitle,
    )

    val ScreensaverKenBurns = SettingsRow(
        id = "screensaver_ken_burns",
        icon = Tabler.Outline.Movie,
        titleRes = Res.string.settings_ken_burns,
        searchTitleRes = Res.string.ss_screensaver_ken_burns_title,
        searchSubtitleRes = Res.string.ss_screensaver_ken_burns_subtitle,
    )

    val ScreensaverTransitionStyle = SettingsRow(
        id = "screensaver_transition_style",
        icon = Tabler.Outline.ArrowsHorizontal,
        titleRes = Res.string.settings_transition_style,
        searchTitleRes = Res.string.ss_screensaver_transition_style_title,
        searchSubtitleRes = Res.string.ss_screensaver_transition_style_subtitle,
    )

    val ScreensaverMaxParentalRating = SettingsRow(
        id = "screensaver_max_parental_rating",
        icon = Tabler.Outline.Shield,
        titleRes = Res.string.settings_dream_max_parental_rating,
        searchSubtitleRes = Res.string.ss_screensaver_max_parental_rating_subtitle,
    )

    val ScreensaverDimAfter = SettingsRow(
        id = "screensaver_dim_after",
        icon = Tabler.Outline.Hourglass,
        titleRes = Res.string.settings_dream_dim_after,
        searchSubtitleRes = Res.string.ss_screensaver_dim_after_subtitle,
    )

    val ScreensaverDimPercent = SettingsRow(
        id = "screensaver_dim_percent",
        icon = Tabler.Outline.Sun,
        titleRes = Res.string.settings_dream_dim_percent,
        searchSubtitleRes = Res.string.ss_screensaver_dim_percent_subtitle,
    )

    // -- The desktop idle "Ready to play" ambient screen pair — their own screen group beside the TV dream group. --

    val IdleAmbientEnabled = SettingsRow(
        id = "idle_ambient_enabled",
        icon = Tabler.Outline.Moon,
        titleRes = Res.string.settings_idle_ambient_enabled,
        searchTitleRes = Res.string.ss_idle_ambient_enabled_title,
        searchSubtitleRes = Res.string.ss_idle_ambient_enabled_subtitle,
        platforms = platformsForCapability(settingsCapabilities.supportsIdleAmbientScreen),
    )

    val IdleAmbientTimeout = SettingsRow(
        id = "idle_ambient_timeout",
        icon = Tabler.Outline.Stopwatch,
        titleRes = Res.string.settings_idle_ambient_timeout,
        searchTitleRes = Res.string.ss_idle_ambient_timeout_title,
        searchSubtitleRes = Res.string.ss_idle_ambient_timeout_subtitle,
        platforms = platformsForCapability(settingsCapabilities.supportsIdleAmbientScreen),
    )

    // -- The desktop Discord Rich Presence toggle (feature 4.2) — its own capability-gated group. --

    val DiscordPresenceEnabled = SettingsRow(
        id = "discord_presence_enabled",
        icon = Tabler.Outline.BrandDiscord,
        titleRes = Res.string.settings_discord_presence_enabled,
        searchSubtitleRes = Res.string.ss_discord_presence_enabled_subtitle,
    )

    // -- The desktop playback-event shell-hook rows (feature 4.3) — the master toggle plus the five commands, one group. --

    val HooksEnabled = SettingsRow(
        id = "hooks_enabled",
        icon = Tabler.Outline.Terminal2,
        titleRes = Res.string.settings_hooks_enabled,
        searchSubtitleRes = Res.string.ss_hooks_enabled_subtitle,
    )

    val HooksPlayCmd = SettingsRow(
        id = "hooks_play_cmd",
        icon = Tabler.Outline.Terminal2,
        titleRes = Res.string.settings_hooks_play_cmd,
        searchSubtitleRes = Res.string.ss_hooks_play_cmd_subtitle,
    )

    val HooksStopCmd = SettingsRow(
        id = "hooks_stop_cmd",
        icon = Tabler.Outline.Terminal2,
        titleRes = Res.string.settings_hooks_stop_cmd,
        searchSubtitleRes = Res.string.ss_hooks_stop_cmd_subtitle,
    )

    val HooksEndedCmd = SettingsRow(
        id = "hooks_ended_cmd",
        icon = Tabler.Outline.Terminal2,
        titleRes = Res.string.settings_hooks_ended_cmd,
        searchSubtitleRes = Res.string.ss_hooks_ended_cmd_subtitle,
    )

    val HooksIdleCmd = SettingsRow(
        id = "hooks_idle_cmd",
        icon = Tabler.Outline.Terminal2,
        titleRes = Res.string.settings_hooks_idle_cmd,
        searchSubtitleRes = Res.string.ss_hooks_idle_cmd_subtitle,
    )

    val HooksIdleEndedCmd = SettingsRow(
        id = "hooks_idle_ended_cmd",
        icon = Tabler.Outline.Terminal2,
        titleRes = Res.string.settings_hooks_idle_ended_cmd,
        searchSubtitleRes = Res.string.ss_hooks_idle_ended_cmd_subtitle,
    )

    /**
     * Every fused system row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = SystemCoreRows + SystemScreensaverRows + SystemIdleAmbientRows + SystemDiscordPresenceRows + SystemHooksRows
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val systemSearchRoutes: Map<String, Route> = mapOf(
    ScreensaverPreferenceSpecs.ROUTE_SETTINGS to Route.Settings,
)

private val systemSpecEntries: List<PreferenceSearchSpec> = ScreensaverPreferenceSpecs.searchEntries

private val systemCategory = CoreUiRes.string.ss_cat_system

internal val SystemCoreRows: List<SettingsRow> = listOf(
    SystemRows.AdminDashboard,
    SystemRows.SetupWizard,
)

internal val SystemScreensaverRows: List<SettingsRow> = listOf(
    SystemRows.ScreensaverShowTitle,
    SystemRows.ScreensaverCategories,
    SystemRows.ScreensaverSlideshowInterval,
    SystemRows.ScreensaverKenBurns,
    SystemRows.ScreensaverTransitionStyle,
    SystemRows.ScreensaverMaxParentalRating,
    SystemRows.ScreensaverDimAfter,
    SystemRows.ScreensaverDimPercent,
)

internal val SystemIdleAmbientRows: List<SettingsRow> = listOf(
    SystemRows.IdleAmbientEnabled,
    SystemRows.IdleAmbientTimeout,
)

internal val SystemDiscordPresenceRows: List<SettingsRow> = listOf(
    SystemRows.DiscordPresenceEnabled,
)

internal val SystemHooksRows: List<SettingsRow> = listOf(
    SystemRows.HooksEnabled,
    SystemRows.HooksPlayCmd,
    SystemRows.HooksStopCmd,
    SystemRows.HooksEndedCmd,
    SystemRows.HooksIdleCmd,
    SystemRows.HooksIdleEndedCmd,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val SystemCoreGroup =
    SystemCoreRows.asRowGroup("system.core", emptyList(), systemSearchRoutes, systemCategory)

internal val SystemScreensaverGroup =
    SystemScreensaverRows.asRowGroup("system.screensaver", specEntriesFor(SystemScreensaverRows, systemSpecEntries), systemSearchRoutes, systemCategory)

internal val SystemIdleAmbientGroup =
    SystemIdleAmbientRows.asRowGroup("system.idleAmbient", specEntriesFor(SystemIdleAmbientRows, systemSpecEntries), systemSearchRoutes, systemCategory)

internal val SystemDiscordPresenceGroup =
    SystemDiscordPresenceRows.asRowGroup("system.discordPresence", specEntriesFor(SystemDiscordPresenceRows, systemSpecEntries), systemSearchRoutes, systemCategory)

internal val SystemHooksGroup =
    SystemHooksRows.asRowGroup("system.hooks", specEntriesFor(SystemHooksRows, systemSpecEntries), systemSearchRoutes, systemCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

/** The whole System declaration list — the five screen groups' concatenation, the pinned catalog order (the navigation pair, the dream rows, the desktop-shell rows). */
internal val SystemSearchItems: List<SettingsSearchItem> = SystemCoreGroup.items + SystemScreensaverGroup.items + SystemIdleAmbientGroup.items + SystemDiscordPresenceGroup.items + SystemHooksGroup.items
