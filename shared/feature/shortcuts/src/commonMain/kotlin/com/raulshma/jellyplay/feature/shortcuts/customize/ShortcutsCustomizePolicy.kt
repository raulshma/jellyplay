package com.raulshma.jellyplay.feature.shortcuts.customize

import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutLimits
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalog
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogEntry
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogTarget
import com.raulshma.jellyplay.feature.shortcuts.ShortcutCategory

/**
 * Compose-free editor policy (the `ShortcutListPolicy` precedent): the
 * per-surface editor view — pinned rows, available rows, capacity/fullness —
 * the pin membership behind every row's flags, and the prune fold that drops
 * definitions whose target left the catalog. Everything pure, everything
 * unit-testable.
 *
 * The pin flags carry ONE formula everywhere
 * ([EditorRow.pinnedHeader] / [EditorRow.pinnedOverflow] = "is this
 * definition in that surface's stored order"): the two former
 * section-relative forms (`surface == HEADER || id in otherOrder` for pinned
 * sections vs `surface == OVERFLOW && id in otherOrder` for available pools)
 * were two context-baked simplifications of the same membership question —
 * a pinned row is in its own surface's order by construction, an available
 * row is out of it by construction — so the direct membership form preserves
 * every call site's visible behavior while making the faces of one definition
 * identical in every list it appears in.
 */
object ShortcutsCustomizePolicy {

    /** One editor row: the definition, its catalog entry (null = unknown target), and its pins. */
    data class EditorRow(
        val definition: ShortcutDefinition,
        val entry: com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogEntry?,
        val pinnedHeader: Boolean,
        val pinnedOverflow: Boolean,
    )

    /**
     * One surface's editor view — everything the surface's tab renders,
     * computed once from the stored slice: the pinned rows in the surface's
     * stored order, the quick-pin pool in stored definition order, and the
     * capacity/fullness gate the pin toggles enforce.
     */
    data class SurfaceEditorView(
        val surface: ShortcutSurface,
        /** Definitions pinned to this surface, in the stored order. */
        val pinnedRows: List<EditorRow>,
        /** Definitions NOT pinned to this surface, in stored definition order — the quick-pin pool. */
        val availableRows: List<EditorRow>,
    ) {
        val capacity: Int
            get() = when (surface) {
                ShortcutSurface.HEADER -> ShortcutLimits.HEADER_MAX
                ShortcutSurface.OVERFLOW -> ShortcutLimits.OVERFLOW_MAX
            }

        val full: Boolean get() = pinnedRows.size >= capacity
    }

    /**
     * Both surfaces' views, indexed by [get] — the screen's tab switch is one
     * lookup, and a future surface joins the pair, not a fifth parallel list.
     */
    data class EditorSections(
        val header: SurfaceEditorView,
        val overflow: SurfaceEditorView,
    ) {
        operator fun get(surface: ShortcutSurface): SurfaceEditorView = when (surface) {
            ShortcutSurface.HEADER -> header
            ShortcutSurface.OVERFLOW -> overflow
        }

        /**
         * The row with [id], wherever it currently sits (pinned on either
         * surface, either surface's available pool) — the edit sheet's
         * summary-line lookup. A definition's face is identical in every
         * section (the one pin formula — see the object KDoc), so the first
         * hit is the answer.
         */
        fun rowFor(id: String): EditorRow? =
            header.pinnedRows.firstOrNull { it.definition.id == id }
                ?: overflow.pinnedRows.firstOrNull { it.definition.id == id }
                ?: header.availableRows.firstOrNull { it.definition.id == id }
                ?: overflow.availableRows.firstOrNull { it.definition.id == id }

        companion object {
            /** The pre-first-emission sections — every surface empty (the VM's initial UiState). */
            fun empty(): EditorSections = EditorSections(
                SurfaceEditorView(ShortcutSurface.HEADER, emptyList(), emptyList()),
                SurfaceEditorView(ShortcutSurface.OVERFLOW, emptyList(), emptyList()),
            )
        }
    }

    /**
     * The sections fold: each surface's order drives its pinned rows; the
     * remainder of the definitions is its quick-pin pool. A definition pinned
     * only to the other surface appears in the pool with that pin carried in
     * [EditorRow.pinnedHeader] / [EditorRow.pinnedOverflow] for the "also in"
     * badge — which is what lets a header-only pin be added to the menu
     * without editing it.
     */
    fun sections(
        definitions: List<ShortcutDefinition>,
        headerOrder: List<String>,
        overflowOrder: List<String>,
        catalog: ShortcutCatalog,
    ): EditorSections = EditorSections(
        header = view(ShortcutSurface.HEADER, headerOrder, definitions, headerOrder, overflowOrder, catalog),
        overflow = view(ShortcutSurface.OVERFLOW, overflowOrder, definitions, headerOrder, overflowOrder, catalog),
    )

    /** The per-surface fold behind [sections]: order rows + not-in-order pool. */
    private fun view(
        surface: ShortcutSurface,
        order: List<String>,
        definitions: List<ShortcutDefinition>,
        headerOrder: List<String>,
        overflowOrder: List<String>,
        catalog: ShortcutCatalog,
    ): SurfaceEditorView {
        val byId = definitions.associateBy { it.id }
        val pinnedRows = order.mapNotNull { id ->
            byId[id]?.let { editorRow(it, headerOrder, overflowOrder, catalog) }
        }
        val availableRows = definitions
            .filter { it.id !in order }
            .map { editorRow(it, headerOrder, overflowOrder, catalog) }
        return SurfaceEditorView(surface, pinnedRows, availableRows)
    }

    /** The row face — [ShortcutDefinition] + catalog entry + the ONE pin formula. */
    private fun editorRow(
        definition: ShortcutDefinition,
        headerOrder: List<String>,
        overflowOrder: List<String>,
        catalog: ShortcutCatalog,
    ): EditorRow = EditorRow(
        definition = definition,
        entry = catalog.entryFor(definition.kind, definition.targetId),
        pinnedHeader = definition.id in headerOrder,
        pinnedOverflow = definition.id in overflowOrder,
    )

    /**
     * The quick-pin's surface set: pinning from [surface] is ADDITIVE for the
     * other one — a row already pinned there keeps that pin (the "also in"
     * badge promises it stays put), so a quick-pin only ever turns a pin ON.
     */
    fun quickPinTargets(
        surface: ShortcutSurface,
        pinnedHeader: Boolean,
        pinnedOverflow: Boolean,
    ): QuickPinTargets = QuickPinTargets(
        header = surface == ShortcutSurface.HEADER || pinnedHeader,
        overflow = surface == ShortcutSurface.OVERFLOW || pinnedOverflow,
    )

    data class QuickPinTargets(val header: Boolean, val overflow: Boolean)

    /**
     * The capability-aware prune: drops definitions whose target has no
     * catalog entry (an app update removed/renamed a destination) and
     * definitions pointing at a shell action outside [supportedActions]
     * (the shell cannot execute them — a stored pin predating a capability
     * change must not resurface). Screen targets are never capability-gated.
     */
    fun prune(
        definitions: List<ShortcutDefinition>,
        catalog: ShortcutCatalog,
        supportedActions: Set<ShortcutAction>,
    ): List<ShortcutDefinition> = definitions.filter { definition ->
        val entry = catalog.entryFor(definition.kind, definition.targetId) ?: return@filter false
        val action = (entry.target as? ShortcutCatalogTarget.Action)?.action ?: return@filter true
        action in supportedActions
    }

    /** Whether the target picker may offer [entry] — admin entries hide for non-admins. */
    fun pickable(entry: ShortcutCatalogEntry, isAdmin: Boolean): Boolean =
        !entry.requiresAdmin || isAdmin

    /**
     * Whether the target picker may offer [entry] on this shell: an ACTION
     * entry whose action is outside [supportedActions] never renders — the
     * shell cannot execute it, and a pinned pill that dead-ends is the exact
     * defect the capability surface exists to prevent. Screen entries are
     * always offerable.
     */
    fun executable(entry: ShortcutCatalogEntry, supportedActions: Set<ShortcutAction>): Boolean =
        (entry.target as? ShortcutCatalogTarget.Action)?.action?.let { it in supportedActions } ?: true
}
