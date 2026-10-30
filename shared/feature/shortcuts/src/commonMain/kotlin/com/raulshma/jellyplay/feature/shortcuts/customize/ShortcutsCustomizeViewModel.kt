package com.raulshma.jellyplay.feature.shortcuts.customize

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsSlice
import com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsStore
import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalog
import com.raulshma.jellyplay.core.ui.navigation.defaultShortcutsFor
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
 * The permissive capability set — every [ShortcutAction] — for hosts that
 * genuinely arm the whole vocabulary (the Android shell's shape) and for the
 * VM tests' all-actions lane. Deliberately NOT a default anywhere: a caller
 * must name it, so a shell that cannot execute an action never silently
 * inherits an "all actions" editor.
 */
val ALL_SHELL_ACTIONS: Set<ShortcutAction> = ShortcutAction.entries.toSet()

/**
 * The shortcuts-customization editor's ViewModel. Reads the per-user
 * [ShortcutsSlice] straight from [com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsStore]
 * (the home dock / overflow menu read the same slice — the editor's preview
 * can never drift from what the surfaces render); every write funnels through
 * the [PreferencesEditor] seam (`editor.edit { shortcuts.<command> }`), so the
 * store's normalization invariants and the edit-failure reporting apply to
 * every editor action for free.
 *
 * Initialization is the ONE editor-bootstrap sequence, owned here instead of
 * a composition effect: adopt the shipped layout, then prune stale targets —
 * in that documented order, in one coroutine (see [init]). The shell's
 * executable-action capability set ([supportedActions]) is an explicit,
 * non-defaulted construction input captured once and applied to BOTH
 * consumers — the on-open prune and the target picker's rows — because a
 * defaulted "all actions" set is exactly the silent-dead-end-pill defect for
 * a future caller that forgets to pass the shell's real set.
 */
class ShortcutsCustomizeViewModel(
    private val editor: PreferencesEditor,
    private val shortcutsStore: ShortcutsStore,
    private val catalog: DefaultShortcutCatalog,
    authRepository: AuthRepository,
    /**
     * The shell's executable shortcut actions (the shell feature's
     * ShortcutActionSurface.supportedActions vocabulary, passed as plain
     * data — no feature→shell edge). Required on purpose: every action
     * outside the set is pruned from stored configs on open and filtered
     * from the picker, so an action the shell cannot execute is never
     * offered or resurrected. Tests pass [ALL_SHELL_ACTIONS] for the
     * permissive "this shell can run them all" shape.
     */
    private val supportedActions: Set<ShortcutAction>,
) : JellyPlayViewModel() {

    init {
        launch {
            // ── Editor bootstrap: adopt THEN prune, in this order, in one
            // coroutine so the order is real (not a race between two
            // launchers). Adoption lands the shipped layout as an
            // unconfigured user's starting config (without it the editor
            // would open on empty lists — see [adoptShippedDefaults]); the
            // prune then runs against the POST-adopt store: an empty slice
            // would early-return and skip the stale-target sweep.
            adoptShippedDefaults()
            pruneUnknownTargets()
        }
    }

    @Immutable
    data class UiState(
        val slice: ShortcutsSlice = ShortcutsSlice(),
        /** The per-surface editor views — the screen's list content. */
        val sections: ShortcutsCustomizePolicy.EditorSections = ShortcutsCustomizePolicy.EditorSections.empty(),
        /**
         * The target picker's rows, admin- AND capability-filtered (the
         * shell's executable-action set is [ShortcutsCustomizeViewModel.supportedActions]),
         * grouped by category in declaration order.
         */
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
                // Capability filter lives HERE (store-side with the admin one)
                // so the picker's rows arrive in UiState already filtered — a
                // shell that cannot execute an action never renders its row.
                .filter { ShortcutsCustomizePolicy.executable(it.entry, supportedActions) }
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

    fun move(id: String, surface: ShortcutSurface, up: Boolean) {
        editor.edit { shortcuts.moveShortcut(id, surface, up) }
    }

    fun remove(id: String) {
        editor.edit { shortcuts.removeShortcut(id) }
    }

    fun resetAll() {
        editor.edit {
            shortcuts.resetShortcuts()
            seedShippedDefaults()
        }
    }

    /**
     * Adopts the shipped default layout as the editor's starting config. An
     * unconfigured user's stored slice is empty by design (the surfaces render
     * the code-defined defaults), so without this adoption the editor would
     * open on empty lists — and the user's first edit (a header pin, say)
     * would persist "definitions exist, other surface never chosen" and
     * collapse the untouched overflow menu to its Settings guard rail. Runs
     * on open and again after a reset; a no-op for a configured user
     * ([ShortcutsStore.seedDefaultsIfUnconfigured] reads its guard inside the
     * atomic edit). Targets missing from the catalog drop at adoption —
     * seeding never introduces an unknown row.
     *
     * Suspends until the write lands: [PreferencesEditor.edit] fires on the
     * application scope, and the init's adopt→prune ordering depends on the
     * adoption being observable before the prune reads the store.
     */
    private suspend fun adoptShippedDefaults() {
        editor.edit { seedShippedDefaults() }.join()
    }

    private suspend fun PreferencesEditScope.seedShippedDefaults() {
        fun defaultRows(surface: ShortcutSurface) =
            defaultShortcutsFor(surface).filter { catalog.entryFor(it.kind, it.targetId) != null }
        val header = defaultRows(ShortcutSurface.HEADER)
        val overflow = defaultRows(ShortcutSurface.OVERFLOW)
        shortcuts.seedDefaultsIfUnconfigured(
            definitions = header + overflow,
            headerOrder = header.map { it.id },
            overflowOrder = overflow.map { it.id },
        )
    }

    /**
     * Drops definitions whose target left the catalog — or targets a shell
     * action outside the captured [supportedActions] set (this shell cannot
     * execute them) — run once in [init], after the adoption, so stale rows
     * (an app update removed/renamed a destination; a pin predating a
     * capability change) never resurface, without blocking the read path.
     * Suspends until a pruning write lands (the bootstrap's ordering), so the
     * editor's first frame always reflects the post-prune slice.
     */
    private suspend fun pruneUnknownTargets() {
        val slice = shortcutsStore.shortcuts.value
        if (slice.definitions.isEmpty()) return
        val kept = ShortcutsCustomizePolicy.prune(slice.definitions, catalog, supportedActions)
        if (kept.size != slice.definitions.size) {
            editor.edit {
                shortcuts.setShortcutsConfig(
                    definitions = kept,
                    headerOrder = slice.headerOrder,
                    overflowOrder = slice.overflowOrder,
                )
            }.join()
        }
    }
}
