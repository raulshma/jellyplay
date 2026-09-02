package com.raulshma.jellyplay.feature.shortcuts

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewModelScope
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.Res
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_category_library
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_category_services
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_category_system
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
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_live_tv_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_live_tv_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_newsletters_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_newsletters_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_playlists_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_playlists_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_requests_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_requests_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_seerr_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_seerr_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_server_mgmt_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_server_mgmt_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_settings_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_settings_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_setup_wizard_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_setup_wizard_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_switch_user_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_switch_user_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_syncplay_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_syncplay_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_upcoming_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_upcoming_title
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_watch_history_description
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_item_watch_history_title
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.StringResource

enum class ShortcutCategory(val displayNameRes: StringResource) {
    LIBRARY(Res.string.shortcuts_category_library),
    SERVICES(Res.string.shortcuts_category_services),
    SYSTEM(Res.string.shortcuts_category_system),
}

data class ShortcutItem(
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val icon: ImageVector,
    val route: Route,
    val category: ShortcutCategory,
    val requiresAdmin: Boolean = false,
)

@Immutable
data class ShortcutsUiState(
    val categories: Map<ShortcutCategory, List<ShortcutItem>> = emptyMap(),
)

class ShortcutsViewModel(
    private val authRepository: AuthRepository,
) : JellyPlayViewModel() {

    private val allShortcuts = listOf(
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_downloads_title,
            descriptionRes = Res.string.shortcuts_item_downloads_description,
            icon = Tabler.Outline.Download,
            route = Route.Downloads,
            category = ShortcutCategory.LIBRARY,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_favorites_title,
            descriptionRes = Res.string.shortcuts_item_favorites_description,
            icon = Tabler.Outline.Heart,
            route = Route.Favorites,
            category = ShortcutCategory.LIBRARY,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_watch_history_title,
            descriptionRes = Res.string.shortcuts_item_watch_history_description,
            icon = Tabler.Outline.ChartBar,
            route = Route.WatchProgressHeatmap,
            category = ShortcutCategory.LIBRARY,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_live_tv_title,
            descriptionRes = Res.string.shortcuts_item_live_tv_description,
            icon = Tabler.Outline.DeviceTv,
            route = Route.LiveTv,
            category = ShortcutCategory.LIBRARY,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_playlists_title,
            descriptionRes = Res.string.shortcuts_item_playlists_description,
            icon = Tabler.Outline.Playlist,
            route = Route.Playlists,
            category = ShortcutCategory.LIBRARY,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_syncplay_title,
            descriptionRes = Res.string.shortcuts_item_syncplay_description,
            icon = Tabler.Outline.Users,
            route = Route.SyncPlay,
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_requests_title,
            descriptionRes = Res.string.shortcuts_item_requests_description,
            icon = Tabler.Outline.Inbox,
            route = Route.Requests,
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_newsletters_title,
            descriptionRes = Res.string.shortcuts_item_newsletters_description,
            icon = Tabler.Outline.Mail,
            route = Route.Newsletter,
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_seerr_title,
            descriptionRes = Res.string.shortcuts_item_seerr_description,
            icon = Tabler.Outline.Puzzle,
            route = Route.SeerrSettings(),
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_activity_queue_title,
            descriptionRes = Res.string.shortcuts_item_activity_queue_description,
            icon = Tabler.Outline.Database,
            route = Route.ArrQueue,
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_upcoming_title,
            descriptionRes = Res.string.shortcuts_item_upcoming_description,
            icon = Tabler.Outline.CalendarEvent,
            route = Route.UpcomingCalendar,
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_arr_title,
            descriptionRes = Res.string.shortcuts_item_arr_description,
            icon = Tabler.Outline.Movie,
            route = Route.ArrSettings(),
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_settings_title,
            descriptionRes = Res.string.shortcuts_item_settings_description,
            icon = Tabler.Outline.Settings,
            route = Route.Settings,
            category = ShortcutCategory.SYSTEM,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_server_mgmt_title,
            descriptionRes = Res.string.shortcuts_item_server_mgmt_description,
            icon = Tabler.Outline.Server,
            route = Route.ServerManagement(),
            category = ShortcutCategory.SYSTEM,
            requiresAdmin = true,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_switch_user_title,
            descriptionRes = Res.string.shortcuts_item_switch_user_description,
            icon = Tabler.Outline.User,
            route = Route.UserManagement(),
            category = ShortcutCategory.SYSTEM,
            requiresAdmin = true,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_admin_title,
            descriptionRes = Res.string.shortcuts_item_admin_description,
            icon = Tabler.Outline.Shield,
            route = Route.AdminDashboard,
            category = ShortcutCategory.SYSTEM,
            requiresAdmin = true,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_setup_wizard_title,
            descriptionRes = Res.string.shortcuts_item_setup_wizard_description,
            icon = Tabler.Outline.Wand,
            route = Route.Onboarding,
            category = ShortcutCategory.SYSTEM,
        ),
        ShortcutItem(
            titleRes = Res.string.shortcuts_item_about_title,
            descriptionRes = Res.string.shortcuts_item_about_description,
            icon = Tabler.Outline.InfoCircle,
            route = Route.About,
            category = ShortcutCategory.SYSTEM,
        ),
    )

    val uiState: StateFlow<ShortcutsUiState> = authRepository.currentUser
        .map { user ->
            val isAdmin = user?.isAdmin == true
            val filteredList = allShortcuts.filter { !it.requiresAdmin || isAdmin }
            ShortcutsUiState(
                categories = filteredList.groupBy { it.category },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ShortcutsUiState(),
        )
}
