package com.raulshma.jellyplay.feature.shortcuts

import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.shortcuts.customize.ShortcutsCustomizePolicy
import io.mockk.mockk
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The editor's compose-free section fold, capacity gates, prune, and pickability. */
class ShortcutsCustomizePolicyTest {

    private val labelRes = mockk<StringResource>()

    private val catalog = DefaultShortcutCatalog()

    private fun screenDef(id: String, target: String) =
        ShortcutDefinition(id, ShortcutTargetKind.SCREEN, target)

    @Test
    fun `sections split pinned rows by surface order and the rest unpinned`() {
        val definitions = listOf(screenDef("a", "Downloads"), screenDef("b", "Favorites"), screenDef("c", "Settings"))
        val sections = ShortcutsCustomizePolicy.sections(
            definitions = definitions,
            headerOrder = listOf("b"),
            overflowOrder = listOf("a", "c"),
            catalog = catalog,
        )
        assertEquals(listOf("b"), sections.headerRows.map { it.definition.id })
        assertEquals(listOf("a", "c"), sections.overflowRows.map { it.definition.id })
        assertTrue(sections.unpinnedRows.isEmpty())
        // "b" is header-only; "c" is overflow-only — cross-surface pins read false.
        assertFalse(sections.headerRows.single().pinnedOverflow)
        assertTrue(sections.overflowRows.first { it.definition.id == "c" }.pinnedHeader.not())
        assertTrue(sections.overflowRows.first { it.definition.id == "a" }.pinnedHeader.not())
    }

    @Test
    fun `orphan order ids and unknown targets are skipped in rows`() {
        val definitions = listOf(screenDef("a", "Downloads"))
        val sections = ShortcutsCustomizePolicy.sections(
            definitions = definitions,
            headerOrder = listOf("ghost"),
            overflowOrder = listOf("a"),
            catalog = catalog,
        )
        assertTrue(sections.headerRows.isEmpty())
        assertEquals(1, sections.overflowRows.size)
        // Every catalog entry is known here — a synthetic unknown target proves the null path.
        val unknownSections = ShortcutsCustomizePolicy.sections(
            definitions = listOf(screenDef("x", "NotARealScreen")),
            headerOrder = listOf("x"),
            overflowOrder = emptyList(),
            catalog = catalog,
        )
        assertNull(unknownSections.headerRows.single().entry)
    }

    @Test
    fun `available rows are the definitions not pinned to the surface, carrying the other surface's pin`() {
        val definitions = listOf(
            screenDef("a", "Downloads"),
            screenDef("b", "Favorites"),
            screenDef("c", "Settings"),
            screenDef("d", "Playlists"),
        )
        val sections = ShortcutsCustomizePolicy.sections(
            definitions = definitions,
            headerOrder = listOf("b"),
            overflowOrder = listOf("a", "c"),
            catalog = catalog,
        )
        // The header tab's pool: everything NOT on the header, in definition
        // order; the menu pins ride along for the "also in" badge.
        assertEquals(listOf("a", "c", "d"), sections.availableHeaderRows.map { it.definition.id })
        assertTrue(sections.availableHeaderRows.all { !it.pinnedHeader })
        assertEquals(listOf(true, true, false), sections.availableHeaderRows.map { it.pinnedOverflow })
        // The menu tab's pool mirrors it: the header pin rides along.
        assertEquals(listOf("b", "d"), sections.availableOverflowRows.map { it.definition.id })
        assertTrue(sections.availableOverflowRows.all { !it.pinnedOverflow })
        assertEquals(listOf(true, false), sections.availableOverflowRows.map { it.pinnedHeader })
    }

    @Test
    fun `capacity gates reflect the surface caps`() {
        val many = (1..13).map { screenDef("s$it", "Downloads$it") }
        val sections = ShortcutsCustomizePolicy.sections(
            definitions = many,
            headerOrder = (1..4).map { "s$it" },
            overflowOrder = (1..12).map { "s$it" },
            catalog = catalog,
        )
        assertTrue(sections.headerFull)
        assertTrue(sections.overflowFull)
        assertEquals(4, sections.headerCapacity)
        assertEquals(12, sections.overflowCapacity)
    }

    @Test
    fun `rowFor finds a definition wherever it sits`() {
        val definitions = listOf(
            screenDef("a", "Downloads"),
            screenDef("b", "Favorites"),
            screenDef("c", "Settings"),
            screenDef("d", "Playlists"),
        )
        val sections = ShortcutsCustomizePolicy.sections(
            definitions = definitions,
            headerOrder = listOf("b"),
            overflowOrder = listOf("a"),
            catalog = catalog,
        )
        // Pinned header, pinned overflow, unpinned, and the available pools.
        assertEquals("b", sections.rowFor("b")?.definition?.id)
        assertEquals("a", sections.rowFor("a")?.definition?.id)
        assertEquals("d", sections.rowFor("d")?.definition?.id)
        assertNull(sections.rowFor("ghost"))
    }

    @Test
    fun `prune drops definitions whose target left the catalog`() {
        val kept = ShortcutsCustomizePolicy.prune(
            definitions = listOf(screenDef("a", "Downloads"), screenDef("x", "NotARealScreen")),
            catalog = catalog,
        )
        assertEquals(listOf("a"), kept.map { it.id })
    }

    @Test
    fun `admin entries are pickable only for admins`() {
        val adminEntry = catalog.entries.first { it.requiresAdmin }
        val plainEntry = catalog.entries.first { !it.requiresAdmin }
        assertFalse(ShortcutsCustomizePolicy.pickable(adminEntry, isAdmin = false))
        assertTrue(ShortcutsCustomizePolicy.pickable(adminEntry, isAdmin = true))
        assertTrue(ShortcutsCustomizePolicy.pickable(plainEntry, isAdmin = false))
    }

}
