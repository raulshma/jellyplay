package com.raulshma.jellyplay.feature.shortcuts

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewModelScope
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class ShortcutCategory(val displayName: String) {
    LIBRARY("Library"),
    SERVICES("Services"),
    SYSTEM("System"),
}

data class ShortcutItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: Route,
    val category: ShortcutCategory,
    val requiresAdmin: Boolean = false,
)

@Immutable
data class ShortcutsUiState(
    val categories: Map<ShortcutCategory, List<ShortcutItem>> = emptyMap(),
)

@HiltViewModel
class ShortcutsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : JellyPlayViewModel() {

    private val allShortcuts = listOf(
        ShortcutItem(
            title = "Downloads",
            description = "Offline content and download management.",
            icon = Tabler.Outline.Download,
            route = Route.Downloads,
            category = ShortcutCategory.LIBRARY,
        ),
        ShortcutItem(
            title = "Favorites",
            description = "Liked and bookmarked media.",
            icon = Tabler.Outline.Heart,
            route = Route.Favorites,
            category = ShortcutCategory.LIBRARY,
        ),
        ShortcutItem(
            title = "Watch History",
            description = "Progress heatmap and viewing stats.",
            icon = Tabler.Outline.ChartBar,
            route = Route.WatchProgressHeatmap,
            category = ShortcutCategory.LIBRARY,
        ),
        ShortcutItem(
            title = "TV Guide",
            description = "Live TV schedule and programs.",
            icon = Tabler.Outline.Calendar,
            route = Route.LiveTvGuide,
            category = ShortcutCategory.LIBRARY,
        ),
        ShortcutItem(
            title = "SyncPlay",
            description = "Watch together with synchronized playback.",
            icon = Tabler.Outline.Users,
            route = Route.SyncPlay,
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            title = "Requests",
            description = "Request movies and TV shows.",
            icon = Tabler.Outline.Inbox,
            route = Route.Requests,
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            title = "Newsletters",
            description = "Latest newsletters and updates.",
            icon = Tabler.Outline.Mail,
            route = Route.Newsletter,
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            title = "Seerr",
            description = "Configure Seerr integration.",
            icon = Tabler.Outline.Puzzle,
            route = Route.SeerrSettings(),
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            title = "Activity Queue",
            description = "Combined Radarr/Sonarr download queue and management.",
            icon = Tabler.Outline.Database,
            route = Route.ArrQueue,
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            title = "Radarr / Sonarr",
            description = "Direct *arr server connections and overrides.",
            icon = Tabler.Outline.Movie,
            route = Route.ArrSettings(),
            category = ShortcutCategory.SERVICES,
        ),
        ShortcutItem(
            title = "Settings",
            description = "Appearance, playback, audio, and app options.",
            icon = Tabler.Outline.Settings,
            route = Route.Settings,
            category = ShortcutCategory.SYSTEM,
        ),
        ShortcutItem(
            title = "Server Mgmt",
            description = "Server connection and configuration.",
            icon = Tabler.Outline.Server,
            route = Route.ServerManagement(),
            category = ShortcutCategory.SYSTEM,
            requiresAdmin = true,
        ),
        ShortcutItem(
            title = "Switch User",
            description = "Manage server users and profiles.",
            icon = Tabler.Outline.User,
            route = Route.UserManagement(),
            category = ShortcutCategory.SYSTEM,
            requiresAdmin = true,
        ),
        ShortcutItem(
            title = "Admin",
            description = "Dashboard, health, activity, and tasks.",
            icon = Tabler.Outline.Shield,
            route = Route.AdminDashboard,
            category = ShortcutCategory.SYSTEM,
            requiresAdmin = true,
        ),
        ShortcutItem(
            title = "Setup Wizard",
            description = "Run the initial configuration flow.",
            icon = Tabler.Outline.Wand,
            route = Route.Onboarding,
            category = ShortcutCategory.SYSTEM,
        ),
        ShortcutItem(
            title = "About",
            description = "Version, licenses, and app information.",
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
