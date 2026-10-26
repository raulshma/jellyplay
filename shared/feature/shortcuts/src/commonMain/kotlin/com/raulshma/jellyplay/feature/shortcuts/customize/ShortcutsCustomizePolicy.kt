package com.raulshma.jellyplay.feature.shortcuts.customize

import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutLimits
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalog
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
    ) {
        val headerCapacity: Int get() = ShortcutLimits.HEADER_MAX
        val overflowCapacity: Int get() = ShortcutLimits.OVERFLOW_MAX
        val headerFull: Boolean get() = headerRows.size >= headerCapacity
        val overflowFull: Boolean get() = overflowRows.size >= overflowCapacity
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
        return EditorSections(headerRows, overflowRows, unpinnedRows)
    }

    /**
     * The prune fold: the definitions whose target has no catalog entry (an
     * app update removed/renamed a destination) — dropped on the editor's
     * next write so stale rows never resurface.
     */
    fun prune(definitions: List<ShortcutDefinition>, catalog: ShortcutCatalog): List<ShortcutDefinition> =
        definitions.filter { catalog.entryFor(it.kind, it.targetId) != null }

    /** Whether the target picker may offer [entry] — admin entries hide for non-admins. */
    fun pickable(entry: com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogEntry, isAdmin: Boolean): Boolean =
        !entry.requiresAdmin || isAdmin
}
