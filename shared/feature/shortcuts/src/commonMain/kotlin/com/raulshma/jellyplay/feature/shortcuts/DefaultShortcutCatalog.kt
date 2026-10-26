package com.raulshma.jellyplay.feature.shortcuts

import androidx.compose.runtime.Immutable
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Activity
import com.composables.icons.tabler.outline.Apps
import com.composables.icons.tabler.outline.CalendarEvent
import com.composables.icons.tabler.outline.Cast
import com.composables.icons.tabler.outline.ChartBar
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Inbox
import com.composables.icons.tabler.outline.Library
import com.composables.icons.tabler.outline.Mail
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Music
import com.composables.icons.tabler.outline.Playlist
import com.composables.icons.tabler.outline.Puzzle
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Server
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Shield
import com.composables.icons.tabler.outline.Sparkles
import com.composables.icons.tabler.outline.User
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.Wand
import com.composables.icons.tabler.outline.WifiOff
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

/**
 * The ONE registration site for user-shortcut targets: every screen and shell
 * action a shortcut may point at, with its label, default icon, category, and
 * gating flags (the `NavDestinationRegistry` "facts registered exactly once"
 * rule). The Shortcuts catalog screen, the shortcuts-customization editor's
 * target picker, and the resolve policy's lookups all read from here — a
 * new pickable target is one [items] row, never a per-surface copy.
 *
 * Screen ids ride the `Route.navKey` registry vocabulary where the route is a
 * registered nav destination; parameterized/extra routes carry explicit
 * literal keys (never class names — the persisted vocabulary must survive
 * renames and obfuscation). Shell actions carry their [ShortcutAction] ids.
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

class DefaultShortcutCatalog : ShortcutCatalog {

    val items: List<CatalogItem> = listOf(
        // ── Library ──────────────────────────────────────────────────────
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.Downloads, Route.Downloads.navKey),
                labelRes = Res.string.shortcuts_item_downloads_title,
                descriptionRes = Res.string.shortcuts_item_downloads_description,
                defaultIcon = Tabler.Outline.Download,
            ),
            ShortcutCategory.LIBRARY,
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
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.WatchProgressHeatmap, Route.WatchProgressHeatmap.navKey),
                labelRes = Res.string.shortcuts_item_watch_history_title,
                descriptionRes = Res.string.shortcuts_item_watch_history_description,
                defaultIcon = Tabler.Outline.ChartBar,
            ),
            ShortcutCategory.LIBRARY,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.LiveTv, Route.LiveTv.navKey),
                labelRes = Res.string.shortcuts_item_live_tv_title,
                descriptionRes = Res.string.shortcuts_item_live_tv_description,
                defaultIcon = Tabler.Outline.DeviceTv,
                hiddenOffline = true,
            ),
            ShortcutCategory.LIBRARY,
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
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.Library, Route.Library.navKey),
                labelRes = Res.string.shortcuts_item_library_title,
                descriptionRes = Res.string.shortcuts_item_library_description,
                defaultIcon = Tabler.Outline.Library,
            ),
            ShortcutCategory.LIBRARY,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.Search, Route.Search.navKey),
                labelRes = Res.string.shortcuts_item_search_title,
                descriptionRes = Res.string.shortcuts_item_search_description,
                defaultIcon = Tabler.Outline.Search,
            ),
            ShortcutCategory.LIBRARY,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.MusicBrowse, Route.MusicBrowse.navKey),
                labelRes = Res.string.shortcuts_item_music_title,
                descriptionRes = Res.string.shortcuts_item_music_description,
                defaultIcon = Tabler.Outline.Music,
            ),
            ShortcutCategory.LIBRARY,
        ),

        // ── Services ─────────────────────────────────────────────────────
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.SyncPlay, Route.SyncPlay.navKey),
                labelRes = Res.string.shortcuts_item_syncplay_title,
                descriptionRes = Res.string.shortcuts_item_syncplay_description,
                defaultIcon = Tabler.Outline.Users,
            ),
            ShortcutCategory.SERVICES,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.Requests, Route.Requests.navKey),
                labelRes = Res.string.shortcuts_item_requests_title,
                descriptionRes = Res.string.shortcuts_item_requests_description,
                defaultIcon = Tabler.Outline.Inbox,
            ),
            ShortcutCategory.SERVICES,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.Newsletter, Route.Newsletter.navKey),
                labelRes = Res.string.shortcuts_item_newsletters_title,
                descriptionRes = Res.string.shortcuts_item_newsletters_description,
                defaultIcon = Tabler.Outline.Mail,
            ),
            ShortcutCategory.SERVICES,
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
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.ArrQueue, Route.ArrQueue.navKey),
                labelRes = Res.string.shortcuts_item_activity_queue_title,
                descriptionRes = Res.string.shortcuts_item_activity_queue_description,
                defaultIcon = Tabler.Outline.Database,
            ),
            ShortcutCategory.SERVICES,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.UpcomingCalendar, Route.UpcomingCalendar.navKey),
                labelRes = Res.string.shortcuts_item_upcoming_title,
                descriptionRes = Res.string.shortcuts_item_upcoming_description,
                defaultIcon = Tabler.Outline.CalendarEvent,
            ),
            ShortcutCategory.SERVICES,
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
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.Settings, Route.Settings.navKey),
                labelRes = Res.string.shortcuts_item_settings_title,
                descriptionRes = Res.string.shortcuts_item_settings_description,
                defaultIcon = Tabler.Outline.Settings,
            ),
            ShortcutCategory.SYSTEM,
        ),
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.Shortcuts, Route.Shortcuts.navKey),
                labelRes = Res.string.shortcuts_item_shortcuts_title,
                descriptionRes = Res.string.shortcuts_item_shortcuts_description,
                defaultIcon = Tabler.Outline.Apps,
            ),
            ShortcutCategory.SYSTEM,
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
        CatalogItem(
            ShortcutCatalogEntry(
                target = ShortcutCatalogTarget.Screen(Route.AdminDashboard, Route.AdminDashboard.navKey),
                labelRes = Res.string.shortcuts_item_admin_title,
                descriptionRes = Res.string.shortcuts_item_admin_description,
                defaultIcon = Tabler.Outline.Shield,
                requiresAdmin = true,
            ),
            ShortcutCategory.SYSTEM,
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
