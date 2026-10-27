package com.raulshma.jellyplay.feature.shortcuts.customize

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsSlice
import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalog
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.shortcuts.DefaultShortcutCatalog
import com.raulshma.jellyplay.feature.shortcuts.ShortcutCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The shortcuts-customization editor's ViewModel. Reads the per-user
 * [ShortcutsSlice] straight from [com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsStore]
 * (the home dock / overflow menu read the same slice — the editor's preview
 * can never drift from what the surfaces render); every write funnels through
 * the [PreferencesEditor] seam (`editor.edit { shortcuts.<command> }`), so the
 * store's normalization invariants and the edit-failure reporting apply to
 * every editor action for free.
 */
class ShortcutsCustomizeViewModel(
    private val editor: PreferencesEditor,
    private val shortcutsStore: com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsStore,
    private val catalog: DefaultShortcutCatalog,
    authRepository: AuthRepository,
) : JellyPlayViewModel() {

    @Immutable
    data class UiState(
        val slice: ShortcutsSlice = ShortcutsSlice(),
        /** The pinned/unpinned section fold — the screen's list content. */
        val sections: ShortcutsCustomizePolicy.EditorSections =
            ShortcutsCustomizePolicy.EditorSections(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
        /** The target picker's rows, admin-filtered, grouped by category in declaration order. */
        val pickerByCategory: Map<ShortcutCategory, List<PickerItem>> = emptyMap(),
    )

    /** One target-picker row: the catalog item plus whether it's already defined. */
    @Immutable
    data class PickerItem(
        val entry: com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogEntry,
        val category: ShortcutCategory,
        /** True when a definition already points at this target (the picker disables it). */
        val alreadyDefined: Boolean,
    )

    val uiState: StateFlow<UiState> = combine(
        shortcutsStore.shortcuts,
        authRepository.currentUser,
    ) { slice, user ->
        val isAdmin = user?.isAdmin == true
        val definedTargets = slice.definitions.map { it.targetKey }.toSet()
        UiState(
            slice = slice,
            sections = ShortcutsCustomizePolicy.sections(
                definitions = slice.definitions,
                headerOrder = slice.headerOrder,
                overflowOrder = slice.overflowOrder,
                catalog = catalog,
            ),
            pickerByCategory = catalog.items.asSequence()
                .filter { ShortcutsCustomizePolicy.pickable(it.entry, isAdmin) }
                .groupBy { it.category }
                .mapValues { (_, items) ->
                    items.map { item ->
                        PickerItem(
                            entry = item.entry,
                            category = item.category,
                            alreadyDefined = item.entry.storageKey in definedTargets,
                        )
                    }
                },
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    // ------------------------------------------------------------------
    // Commands — every write through editor.edit; the store normalizes.
    // ------------------------------------------------------------------

    fun addShortcut(
        kind: ShortcutTargetKind,
        targetId: String,
        iconId: String,
        labelOverride: String?,
        pinHeader: Boolean,
        pinOverflow: Boolean,
    ) {
        val id = shortcutsStore.newShortcutId()
        editor.edit {
            shortcuts.addShortcut(
                ShortcutDefinition(
                    id = id,
                    kind = kind,
                    targetId = targetId,
                    iconId = iconId,
                    labelOverride = labelOverride?.takeIf { it.isNotBlank() },
                ),
                pinHeader = pinHeader,
                pinOverflow = pinOverflow,
            )
        }
    }

    fun updateShortcut(definition: ShortcutDefinition) {
        editor.edit {
            shortcuts.updateShortcut(
                definition.copy(labelOverride = definition.labelOverride?.takeIf { it.isNotBlank() }),
            )
        }
    }

    fun setSurfaces(id: String, header: Boolean, overflow: Boolean) {
        editor.edit { shortcuts.setShortcutSurfaces(id, header, overflow) }
    }

    fun move(id: String, surface: com.raulshma.jellyplay.core.model.ShortcutSurface, up: Boolean) {
        editor.edit { shortcuts.moveShortcut(id, surface, up) }
    }

    fun remove(id: String) {
        editor.edit { shortcuts.removeShortcut(id) }
    }

    fun resetAll() {
        editor.edit { shortcuts.resetShortcuts() }
    }

    /**
     * Drops definitions whose target left the catalog — or targets a shell
     * action outside [supportedActions] (this shell cannot execute it) — run
     * once when the editor opens so stale rows (an app update removed/renamed
     * a destination; a pin predating a capability change) never resurface,
     * without blocking the read path.
     */
    fun pruneUnknownTargets(supportedActions: Set<ShortcutAction> = ShortcutAction.entries.toSet()) {
        viewModelScope.launch {
            val slice = shortcutsStore.shortcuts.value
            if (slice.definitions.isEmpty()) return@launch
            val kept = ShortcutsCustomizePolicy.prune(slice.definitions, catalog, supportedActions)
            if (kept.size != slice.definitions.size) {
                editor.edit {
                    shortcuts.setShortcutsConfig(
                        definitions = kept,
                        headerOrder = slice.headerOrder,
                        overflowOrder = slice.overflowOrder,
                    )
                }
            }
        }
    }
}
