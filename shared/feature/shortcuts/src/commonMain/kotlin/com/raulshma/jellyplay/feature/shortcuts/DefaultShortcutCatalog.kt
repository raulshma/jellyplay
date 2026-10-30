package com.raulshma.jellyplay.feature.shortcuts

import androidx.compose.runtime.Immutable
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Cast
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Playlist
import com.composables.icons.tabler.outline.Puzzle
import com.composables.icons.tabler.outline.Server
import com.composables.icons.tabler.outline.Sparkles
import com.composables.icons.tabler.outline.User
import com.composables.icons.tabler.outline.Wand
import com.composables.icons.tabler.outline.WifiOff
import com.raulshma.jellyplay.core.ui.navigation.NAV_DESTINATION_BY_ROUTE
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalog
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogEntry
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogTarget
import com.raulshma.jellyplay.core.ui.navigation.navKey
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.Res
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_action_offline_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_action_play_on_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_action_surprise_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_about_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_about_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_activity_queue_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_activity_queue_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_admin_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_admin_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_arr_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_arr_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_downloads_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_downloads_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_favorites_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_favorites_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_library_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_library_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_live_tv_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_live_tv_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_music_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_music_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_newsletters_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_newsletters_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_playlists_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_playlists_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_requests_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_requests_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_search_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_search_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_seerr_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_seerr_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_server_mgmt_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_server_mgmt_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_settings_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_settings_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_setup_wizard_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_setup_wizard_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_shortcuts_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_shortcuts_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_switch_user_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_switch_user_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_syncplay_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_syncplay_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_upcoming_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_upcoming_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_watch_history_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_watch_history_title
import org.jetbrains.compose.resources.StringResource

/**
 * The ONE registration site for user-shortcut targets: every screen and shell
 * action a shortcut may point at, with its label, category, and
 * shortcut-specific gating. Where a target is also a registered nav
 * destination, the destination facts (icon, offline gating) are NOT copied
 * here — they derive from the `NavDestinationRegistry` row (see
 * [registeredScreenItem]), the "facts registered exactly once" rule. The
 * Shortcuts catalog screen, the shortcuts-customization editor's target
 * picker, and the resolve policy's lookups all read from here — a new
 * pickable target is one [items] row, never a per-surface copy.
 *
 * Screen ids ride the `Route.navKey` registry vocabulary where the route is a
 * registered nav destination; parameterized/extra routes carry explicit
 * literal keys (never class names — the persisted vocabulary must survive
 * renames and obfuscation). Shell actions carry their [ShortcutAction] ids.
 *
 * Labels deliberately stay shortcut-local (not the registry's English rail
 * labels — those are rail chrome; these are localized resources).
 *
 * Shipped-default overflow rows ([ShortcutAction.SURPRISE_ME], SyncPlay,
 * Downloads, [ShortcutAction.TOGGLE_OFFLINE], [ShortcutAction.PLAY_ON],
 * Shortcuts, Settings) MUST stay registered here or the unconfigured default
 * menu silently loses them — `DefaultShortcutCatalogContractTest` pins the
 * pairing.
 */
@Immutable
data class CatalogItem(
    val entry: ShortcutCatalogEntry,
    val category: ShortcutCategory,
)

/**
 * The catalog row for a route that has a [NAV_DESTINATIONS] registry row:
 * the icon and the offline gating derive from that row (a shortcut pill and
 * its nav-bar tab render the same icon; both offline gates flip together —
 * a shortcut can never reach where navigation may not), so the row carries
 * only the shortcut-specific facts: the localized label, the category, the
 * optional description, and admin gating (a shortcut-side concern — the
 * registry has no admin concept). Fails loudly for an unregistered route:
 * catalog-only targets register standalone rows instead.
 */
private fun registeredScreenItem(
    route: Route,
    labelRes: StringResource,
    category: ShortcutCategory,
    descriptionRes: StringResource? = null,
    requiresAdmin: Boolean = false,
): CatalogItem {
    val destination = requireNotNull(NAV_DESTINATION_BY_ROUTE[route]) {
        "No NAV_DESTINATIONS row for ${route::class.simpleName}: a shortcut target on a registered " +
            "route derives its facts from the registry; catalog-only targets register standalone rows."
    }
    return CatalogItem(
        ShortcutCatalogEntry(
            target = ShortcutCatalogTarget.Screen(route, route.navKey),
            labelRes = labelRes,
            descriptionRes = descriptionRes,
            defaultIcon = destination.icon,
            requiresAdmin = requiresAdmin,
            hiddenOffline = destination.hiddenOffline,
        ),
        category,
    )
}

class DefaultShortcutCatalog : ShortcutCatalog {

    val items: List<CatalogItem> = listOf(
        // ── Library ──────────────────────────────────────────────────────
        registeredScreenItem(
            route = Route.Downloads,
            labelRes = Res.string.shortcuts_item_downloads_title,
            descriptionRes = Res.string.shortcuts_item_downloads_description,
            category = ShortcutCategory.LIBRARY,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.Favorites, Route.Favorites.navKey),
                labelRes = Res.string.shortcuts_item_favorites_title,
                descriptionRes = Res.string.shortcuts_item_favorites_description,
                defaultIcon = Tabler.Outline.Heart,
            ),
            ShortcutCategory.LIBRARY,
        ),
        registeredScreenItem(
            route = Route.WatchProgressHeatmap,
            labelRes = Res.string.shortcuts_item_watch_history_title,
            descriptionRes = Res.string.shortcuts_item_watch_history_description,
            category = ShortcutCategory.LIBRARY,
        ),
        registeredScreenItem(
            route = Route.LiveTv,
            labelRes = Res.string.shortcuts_item_live_tv_title,
            descriptionRes = Res.string.shortcuts_item_live_tv_description,
            category = ShortcutCategory.LIBRARY,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.Playlists, Route.Playlists.navKey),
                labelRes = Res.string.shortcuts_item_playlists_title,
                descriptionRes = Res.string.shortcuts_item_playlists_description,
                defaultIcon = Tabler.Outline.Playlist,
            ),
            ShortcutCategory.LIBRARY,
        ),
        registeredScreenItem(
            route = Route.Library,
            labelRes = Res.string.shortcuts_item_library_title,
            descriptionRes = Res.string.shortcuts_item_library_description,
            category = ShortcutCategory.LIBRARY,
        ),
        registeredScreenItem(
            route = Route.Search,
            labelRes = Res.string.shortcuts_item_search_title,
            descriptionRes = Res.string.shortcuts_item_search_description,
            category = ShortcutCategory.LIBRARY,
        ),
        registeredScreenItem(
            route = Route.MusicBrowse,
            labelRes = Res.string.shortcuts_item_music_title,
            descriptionRes = Res.string.shortcuts_item_music_description,
            category = ShortcutCategory.LIBRARY,
        ),

        // ── Services ─────────────────────────────────────────────────────
        registeredScreenItem(
            route = Route.SyncPlay,
            labelRes = Res.string.shortcuts_item_syncplay_title,
            descriptionRes = Res.string.shortcuts_item_syncplay_description,
            category = ShortcutCategory.SERVICES,
        ),
        registeredScreenItem(
            route = Route.Requests,
            labelRes = Res.string.shortcuts_item_requests_title,
            descriptionRes = Res.string.shortcuts_item_requests_description,
            category = ShortcutCategory.SERVICES,
        ),
        registeredScreenItem(
            route = Route.Newsletter,
            labelRes = Res.string.shortcuts_item_newsletters_title,
            descriptionRes = Res.string.shortcuts_item_newsletters_description,
            category = ShortcutCategory.SERVICES,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.SeerrSettings(), "SeerrSettings"),
                labelRes = Res.string.shortcuts_item_seerr_title,
                descriptionRes = Res.string.shortcuts_item_seerr_description,
                defaultIcon = Tabler.Outline.Puzzle,
            ),
            ShortcutCategory.SERVICES,
        ),
        registeredScreenItem(
            route = Route.ArrQueue,
            labelRes = Res.string.shortcuts_item_activity_queue_title,
            descriptionRes = Res.string.shortcuts_item_activity_queue_description,
            category = ShortcutCategory.SERVICES,
        ),
        registeredScreenItem(
            route = Route.UpcomingCalendar,
            labelRes = Res.string.shortcuts_item_upcoming_title,
            descriptionRes = Res.string.shortcuts_item_upcoming_description,
            category = ShortcutCategory.SERVICES,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.ArrSettings(), "ArrSettings"),
                labelRes = Res.string.shortcuts_item_arr_title,
                descriptionRes = Res.string.shortcuts_item_arr_description,
                defaultIcon = Tabler.Outline.Movie,
            ),
            ShortcutCategory.SERVICES,
        ),

        // ── System ───────────────────────────────────────────────────────
        registeredScreenItem(
            route = Route.Settings,
            labelRes = Res.string.shortcuts_item_settings_title,
            descriptionRes = Res.string.shortcuts_item_settings_description,
            category = ShortcutCategory.SYSTEM,
        ),
        registeredScreenItem(
            route = Route.Shortcuts,
            labelRes = Res.string.shortcuts_item_shortcuts_title,
            descriptionRes = Res.string.shortcuts_item_shortcuts_description,
            category = ShortcutCategory.SYSTEM,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.ServerManagement(), "ServerManagement"),
                labelRes = Res.string.shortcuts_item_server_mgmt_title,
                descriptionRes = Res.string.shortcuts_item_server_mgmt_description,
                defaultIcon = Tabler.Outline.Server,
                requiresAdmin = true,
            ),
            ShortcutCategory.SYSTEM,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.UserManagement(), "UserManagement"),
                labelRes = Res.string.shortcuts_item_switch_user_title,
                descriptionRes = Res.string.shortcuts_item_switch_user_description,
                defaultIcon = Tabler.Outline.User,
                requiresAdmin = true,
            ),
            ShortcutCategory.SYSTEM,
        ),
        registeredScreenItem(
            route = Route.AdminDashboard,
            labelRes = Res.string.shortcuts_item_admin_title,
            descriptionRes = Res.string.shortcuts_item_admin_description,
            category = ShortcutCategory.SYSTEM,
            requiresAdmin = true,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.Onboarding, Route.Onboarding.navKey),
                labelRes = Res.string.shortcuts_item_setup_wizard_title,
                descriptionRes = Res.string.shortcuts_item_setup_wizard_description,
                defaultIcon = Tabler.Outline.Wand,
            ),
            ShortcutCategory.SYSTEM,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.About, Route.About.navKey),
                labelRes = Res.string.shortcuts_item_about_title,
                descriptionRes = Res.string.shortcuts_item_about_description,
                defaultIcon = Tabler.Outline.InfoCircle,
            ),
            ShortcutCategory.SYSTEM,
        ),

        // ── Shell actions ────────────────────────────────────────────────
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Action(ShortcutAction.SURPRISE_ME),
                labelRes = Res.string.shortcuts_action_surprise_title,
                defaultIcon = Tabler.Outline.Sparkles,
            ),
            ShortcutCategory.ACTIONS,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Action(ShortcutAction.PLAY_ON),
                labelRes = Res.string.shortcuts_action_play_on_title,
                defaultIcon = Tabler.Outline.Cast,
            ),
            ShortcutCategory.ACTIONS,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Action(ShortcutAction.TOGGLE_OFFLINE),
                labelRes = Res.string.shortcuts_action_offline_title,
                defaultIcon = Tabler.Outline.WifiOff,
            ),
            ShortcutCategory.ACTIONS,
        ),
    )

    override val entries: List<ShortcutCatalogEntry> get() = items.map { it.entry }
}
