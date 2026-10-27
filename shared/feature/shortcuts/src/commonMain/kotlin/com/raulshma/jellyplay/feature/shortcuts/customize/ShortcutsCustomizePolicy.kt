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
 * pinned/unpinned section fold over the stored slice, the capacity gates the
 * pin toggles enforce, and the prune fold that drops definitions whose
 * target left the catalog. Everything pure, everything unit-testable.
 */
object ShortcutsCustomizePolicy {

    /** One editor row: the definition, its catalog entry (null = unknown target), and its pins. */
    data class EditorRow(
        val definition: ShortcutDefinition,
        val entry: com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogEntry?,
        val pinnedHeader: Boolean,
        val pinnedOverflow: Boolean,
    )

    data class EditorSections(
        /** Definitions pinned to the home header, in the stored order. */
        val headerRows: List<EditorRow>,
        /** Definitions pinned to the overflow menu, in the stored order. */
        val overflowRows: List<EditorRow>,
        /** Defined but unpinned rows, in the stored definition order. */
        val unpinnedRows: List<EditorRow>,
        /** Definitions not pinned to the header, in stored definition order. */
        val availableHeaderRows: List<EditorRow>,
        /** Definitions not pinned to the overflow menu, in stored definition order. */
        val availableOverflowRows: List<EditorRow>,
    ) {
        val headerCapacity: Int get() = ShortcutLimits.HEADER_MAX
        val overflowCapacity: Int get() = ShortcutLimits.OVERFLOW_MAX
        val headerFull: Boolean get() = headerRows.size >= headerCapacity
        val overflowFull: Boolean get() = overflowRows.size >= overflowCapacity

        /**
         * The row with [id], wherever it currently sits (pinned, unpinned,
         * either surface's available pool) — the edit sheet's summary-line
         * lookup. A definition's face is identical in every section, so the
         * first hit is the answer.
         */
        fun rowFor(id: String): EditorRow? =
            headerRows.firstOrNull { it.definition.id == id }
                ?: overflowRows.firstOrNull { it.definition.id == id }
                ?: unpinnedRows.firstOrNull { it.definition.id == id }
                ?: availableHeaderRows.firstOrNull { it.definition.id == id }
                ?: availableOverflowRows.firstOrNull { it.definition.id == id }
    }

    /** The sections fold: order lists drive the pinned rows; the remainder is unpinned. */
    fun sections(definitions: List<ShortcutDefinition>, headerOrder: List<String>, overflowOrder: List<String>, catalog: ShortcutCatalog): EditorSections {
        val byId = definitions.associateBy { it.id }
        fun rowsFor(order: List<String>, otherOrder: List<String>, surface: ShortcutSurface): List<EditorRow> = order.mapNotNull { id ->
            val definition = byId[id] ?: return@mapNotNull null
            EditorRow(
                definition = definition,
                entry = catalog.entryFor(definition.kind, definition.targetId),
                pinnedHeader = surface == ShortcutSurface.HEADER || id in otherOrder,
                pinnedOverflow = surface == ShortcutSurface.OVERFLOW || id in otherOrder,
            )
        }
        val headerRows = rowsFor(headerOrder, overflowOrder, ShortcutSurface.HEADER)
        val overflowRows = rowsFor(overflowOrder, headerOrder, ShortcutSurface.OVERFLOW)
        val pinnedIds = headerOrder + overflowOrder
        val unpinnedRows = definitions.filter { it.id !in pinnedIds }.map { definition ->
            EditorRow(definition, catalog.entryFor(definition.kind, definition.targetId), pinnedHeader = false, pinnedOverflow = false)
        }
        return EditorSections(
            headerRows = headerRows,
            overflowRows = overflowRows,
            unpinnedRows = unpinnedRows,
            availableHeaderRows = availableRows(definitions, headerOrder, overflowOrder, ShortcutSurface.HEADER, catalog),
            availableOverflowRows = availableRows(definitions, overflowOrder, headerOrder, ShortcutSurface.OVERFLOW, catalog),
        )
    }

    /**
     * The per-surface "available" fold behind the editor's tabs: every
     * definition NOT pinned to [surface], in stored definition order — the
     * quick-pin pool. A definition pinned only to the other surface appears
     * here (with that pin carried in [EditorRow.pinnedHeader] /
     * [EditorRow.pinnedOverflow] for the "also in" badge), which is what lets
     * a header-only pin be added to the menu without editing it.
     */
    fun availableRows(
        definitions: List<ShortcutDefinition>,
        surfaceOrder: List<String>,
        otherOrder: List<String>,
        surface: ShortcutSurface,
        catalog: ShortcutCatalog,
    ): List<EditorRow> = definitions
        .filter { it.id !in surfaceOrder }
        .map { definition ->
            EditorRow(
                definition = definition,
                entry = catalog.entryFor(definition.kind, definition.targetId),
                pinnedHeader = surface == ShortcutSurface.OVERFLOW && definition.id in otherOrder,
                pinnedOverflow = surface == ShortcutSurface.HEADER && definition.id in otherOrder,
            )
        }

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
