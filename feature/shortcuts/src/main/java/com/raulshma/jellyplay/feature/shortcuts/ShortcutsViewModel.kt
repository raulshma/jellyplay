package com.raulshma.jellyplay.feature.shortcuts

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
    MEDIA("Media & Browsing"),
    PERSONAL("Personal Library"),
    SYSTEM("System & Administration")
}

data class ShortcutItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: Route,
    val category: ShortcutCategory,
    val requiresAdmin: Boolean = false,
)

data class ShortcutsUiState(
    val categories: Map<ShortcutCategory, List<ShortcutItem>> = emptyMap(),
)

@HiltViewModel
class ShortcutsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : JellyPlayViewModel() {

    private val allShortcuts = listOf(
        // Media & Browsing
        ShortcutItem(
            title = "Home",
            description = "Main dashboard for movies, shows, and music.",
            icon = Tabler.Outline.Home,
            route = Route.Home,
            category = ShortcutCategory.MEDIA,
        ),
        ShortcutItem(
            title = "Library",
            description = "Browse all your media libraries and directories.",
            icon = Tabler.Outline.Music,
            route = Route.Library,
            category = ShortcutCategory.MEDIA,
        ),
        ShortcutItem(
            title = "Music Browse",
            description = "Explore music artists, albums, tracks, and playlists.",
            icon = Tabler.Outline.Disc,
            route = Route.MusicBrowse,
            category = ShortcutCategory.MEDIA,
        ),
        ShortcutItem(
            title = "Live TV",
            description = "Watch active TV channels and broadcasts.",
            icon = Tabler.Outline.DeviceTv,
            route = Route.LiveTv,
            category = ShortcutCategory.MEDIA,
        ),
        ShortcutItem(
            title = "TV Guide",
            description = "View the TV schedule guide and upcoming programs.",
            icon = Tabler.Outline.Calendar,
            route = Route.LiveTvGuide,
            category = ShortcutCategory.MEDIA,
        ),
        ShortcutItem(
            title = "Search",
            description = "Find media files, artists, actors, or playlists.",
            icon = Tabler.Outline.Search,
            route = Route.Search,
            category = ShortcutCategory.MEDIA,
        ),

        // Personal Library
        ShortcutItem(
            title = "Downloads",
            description = "Manage downloaded content and offline playback.",
            icon = Tabler.Outline.Download,
            route = Route.Downloads,
            category = ShortcutCategory.PERSONAL,
        ),
        ShortcutItem(
            title = "Favorites",
            description = "Access all your liked items and bookmarked media.",
            icon = Tabler.Outline.Heart,
            route = Route.Favorites,
            category = ShortcutCategory.PERSONAL,
        ),
        ShortcutItem(
            title = "SyncPlay",
            description = "Synchronize playback to watch together with friends.",
            icon = Tabler.Outline.Users,
            route = Route.SyncPlay,
            category = ShortcutCategory.PERSONAL,
        ),
        ShortcutItem(
            title = "Requests",
            description = "Request movies and TV shows via Seerr.",
            icon = Tabler.Outline.Inbox,
            route = Route.Requests,
            category = ShortcutCategory.PERSONAL,
        ),
        ShortcutItem(
            title = "Newsletters",
            description = "Read community and updates newsletters.",
            icon = Tabler.Outline.Mail,
            route = Route.Newsletter,
            category = ShortcutCategory.PERSONAL,
        ),

        // System & Administration
        ShortcutItem(
            title = "Admin Dashboard",
            description = "Monitor system health, activity, and tasks.",
            icon = Tabler.Outline.Shield,
            route = Route.AdminDashboard,
            category = ShortcutCategory.SYSTEM,
            requiresAdmin = true,
        ),
        ShortcutItem(
            title = "Server Config",
            description = "Configure JellyPlay connection settings.",
            icon = Tabler.Outline.Server,
            route = Route.ServerManagement(),
            category = ShortcutCategory.SYSTEM,
            requiresAdmin = true,
        ),
        ShortcutItem(
            title = "User Management",
            description = "Switch users or edit server profiles.",
            icon = Tabler.Outline.User,
            route = Route.UserManagement(),
            category = ShortcutCategory.SYSTEM,
            requiresAdmin = true,
        ),
        ShortcutItem(
            title = "Watch History",
            description = "Check watch progress heatmap and stats.",
            icon = Tabler.Outline.ChartBar,
            route = Route.WatchProgressHeatmap,
            category = ShortcutCategory.SYSTEM,
        ),
        ShortcutItem(
            title = "Setup Wizard",
            description = "Run the initial server configuration wizard.",
            icon = Tabler.Outline.Wand,
            route = Route.Onboarding,
            category = ShortcutCategory.SYSTEM,
        ),
        ShortcutItem(
            title = "Settings",
            description = "Customize appearance, playback, and audio.",
            icon = Tabler.Outline.Settings,
            route = Route.Settings,
            category = ShortcutCategory.SYSTEM,
        ),
        ShortcutItem(
            title = "About",
            description = "Learn more about JellyPlay version and licenses.",
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
                categories = filteredList.groupBy { it.category }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ShortcutsUiState()
        )
}
