package com.raulshma.jellyplay.feature.shortcuts

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogTarget
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.Res
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_category_actions
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_category_library
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_category_services
import com.raulshma.jellyplay.feature.shortcuts.generated.resources.shortcuts_category_system
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.StringResource

enum class ShortcutCategory(val displayNameRes: StringResource) {
    LIBRARY(Res.string.shortcuts_category_library),
    SERVICES(Res.string.shortcuts_category_services),
    SYSTEM(Res.string.shortcuts_category_system),
    ACTIONS(Res.string.shortcuts_category_actions),
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
    authRepository: AuthRepository,
    catalog: DefaultShortcutCatalog,
) : JellyPlayViewModel() {

    /**
     * The browsable screen slice of the shared [DefaultShortcutCatalog]: the
     * SCREEN targets (shell actions have no route to navigate to here — they
     * are shortcut-picker/editor material), flattened back to the screen's
     * row model. Registration lives in the catalog; this mapping must stay a
     * pure projection.
     */
    private val allShortcuts: List<ShortcutItem> = catalog.items.mapNotNull { item ->
        val screen = item.entry.target as? ShortcutCatalogTarget.Screen ?: return@mapNotNull null
        ShortcutItem(
            titleRes = item.entry.labelRes,
            descriptionRes = item.entry.descriptionRes ?: return@mapNotNull null,
            icon = item.entry.defaultIcon,
            route = screen.route,
            category = item.category,
            requiresAdmin = item.entry.requiresAdmin,
        )
    }

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
