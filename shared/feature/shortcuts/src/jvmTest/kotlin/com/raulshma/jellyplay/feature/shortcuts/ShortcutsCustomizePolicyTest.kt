package com.raulshma.jellyplay.feature.shortcuts

import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.feature.shortcuts.customize.ShortcutsCustomizePolicy
import io.mockk.mockk
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The editor's compose-free per-surface view fold (pinned rows, available
 * pool, capacity/fullness), the one-formula pin flags, capacity gates, prune,
 * and pickability.
 */
class ShortcutsCustomizePolicyTest {

    private val labelRes = mockk<StringResource>()

    private val catalog = DefaultShortcutCatalog()

    private fun screenDef(id: String, target: String) =
        ShortcutDefinition(id, ShortcutTargetKind.SCREEN, target)

    private fun sections(
        definitions: List<ShortcutDefinition>,
        headerOrder: List<String>,
        overflowOrder: List<String>,
    ) = ShortcutsCustomizePolicy.sections(
        definitions = definitions,
        headerOrder = headerOrder,
        overflowOrder = overflowOrder,
        catalog = catalog,
    )

    @Test
    fun `sections split pinned rows per surface order and the rest into the pools`() {
        val definitions = listOf(screenDef("a", "Downloads"), screenDef("b", "Favorites"), screenDef("c", "Settings"))
        val sections = sections(
            definitions = definitions,
            headerOrder = listOf("b"),
            overflowOrder = listOf("a", "c"),
        )
        assertEquals(listOf("b"), sections.header.pinnedRows.map { it.definition.id })
        assertEquals(listOf("a", "c"), sections.overflow.pinnedRows.map { it.definition.id })
        // Every definition is pinned somewhere → the pools hold only
        // other-surface pins (the "also in" quick-pin rows), nothing nowhere-pinned.
        assertEquals(listOf("a", "c"), sections.header.availableRows.map { it.definition.id })
        assertTrue(sections.header.availableRows.all { it.pinnedOverflow })
        assertEquals(listOf("b"), sections.overflow.availableRows.map { it.definition.id })
        assertTrue(sections.overflow.availableRows.all { it.pinnedHeader })
        // "b" is header-only; "c" is overflow-only — cross-surface pins read false.
        assertFalse(sections.header.pinnedRows.single().pinnedOverflow)
        assertTrue(sections.overflow.pinnedRows.first { it.definition.id == "c" }.pinnedHeader.not())
        assertTrue(sections.overflow.pinnedRows.first { it.definition.id == "a" }.pinnedHeader.not())
        // The tab switch is one lookup — the index reads the named views.
        assertEquals(sections.header, sections[ShortcutSurface.HEADER])
        assertEquals(sections.overflow, sections[ShortcutSurface.OVERFLOW])
    }

    @Test
    fun `a nowhere-pinned definition appears in both pools with all flags false`() {
        val definitions = listOf(screenDef("a", "Downloads"), screenDef("free", "Favorites"))
        val sections = sections(
            definitions = definitions,
            headerOrder = listOf("a"),
            overflowOrder = emptyList(),
        )
        assertTrue(sections.header.availableRows.map { it.definition.id }.contains("free"))
        assertTrue(sections.overflow.availableRows.map { it.definition.id }.contains("free"))
        val headerFace = sections.header.availableRows.first { it.definition.id == "free" }
        val overflowFace = sections.overflow.availableRows.first { it.definition.id == "free" }
        // One pin formula → the definition's face is identical in every list.
        assertEquals(headerFace, overflowFace)
        assertFalse(headerFace.pinnedHeader)
        assertFalse(headerFace.pinnedOverflow)
    }

    @Test
    fun `orphan order ids and unknown targets are skipped in rows`() {
        val definitions = listOf(screenDef("a", "Downloads"))
        val sections = sections(
            definitions = definitions,
            headerOrder = listOf("ghost"),
            overflowOrder = listOf("a"),
        )
        assertTrue(sections.header.pinnedRows.isEmpty())
        assertEquals(1, sections.overflow.pinnedRows.size)
        // Every catalog entry is known here — a synthetic unknown target proves the null path.
        val unknownSections = sections(
            definitions = listOf(screenDef("x", "NotARealScreen")),
            headerOrder = listOf("x"),
            overflowOrder = emptyList(),
        )
        assertNull(unknownSections.header.pinnedRows.single().entry)
    }

    @Test
    fun `available pools are the definitions not pinned to the surface, carrying the other surface's pin`() {
        val definitions = listOf(
            screenDef("a", "Downloads"),
            screenDef("b", "Favorites"),
            screenDef("c", "Settings"),
            screenDef("d", "Playlists"),
        )
        val sections = sections(
            definitions = definitions,
            headerOrder = listOf("b"),
            overflowOrder = listOf("a", "c"),
        )
        // The header tab's pool: everything NOT on the header, in definition
        // order; the menu pins ride along for the "also in" badge.
        assertEquals(listOf("a", "c", "d"), sections.header.availableRows.map { it.definition.id })
        assertTrue(sections.header.availableRows.all { !it.pinnedHeader })
        assertEquals(listOf(true, true, false), sections.header.availableRows.map { it.pinnedOverflow })
        // The menu tab's pool mirrors it: the header pin rides along.
        assertEquals(listOf("b", "d"), sections.overflow.availableRows.map { it.definition.id })
        assertTrue(sections.overflow.availableRows.all { !it.pinnedOverflow })
        assertEquals(listOf(true, false), sections.overflow.availableRows.map { it.pinnedHeader })
    }

    @Test
    fun `capacity and fullness reflect the surface caps`() {
        val many = (1..13).map { screenDef("s$it", "Downloads$it") }
        val sections = sections(
            definitions = many,
            headerOrder = (1..4).map { "s$it" },
            overflowOrder = (1..12).map { "s$it" },
        )
        assertTrue(sections.header.full)
        assertTrue(sections.overflow.full)
        assertEquals(4, sections.header.capacity)
        assertEquals(12, sections.overflow.capacity)
        // One slot short of a cap: full flips false for that surface only.
        val sections2 = sections(
            definitions = many,
            headerOrder = (1..3).map { "s$it" },
            overflowOrder = (1..12).map { "s$it" },
        )
        assertFalse(sections2.header.full)
        assertTrue(sections2.overflow.full)
    }

    @Test
    fun `rowFor finds a definition wherever it sits`() {
        val definitions = listOf(
            screenDef("a", "Downloads"),
            screenDef("b", "Favorites"),
            screenDef("c", "Settings"),
            screenDef("d", "Playlists"),
        )
        val sections = sections(
            definitions = definitions,
            headerOrder = listOf("b"),
            overflowOrder = listOf("a"),
        )
        // Pinned header, pinned overflow, and the available pools.
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
            supportedActions = ShortcutAction.entries.toSet(),
        )
        assertEquals(listOf("a"), kept.map { it.id })
    }

    @Test
    fun `capability prune drops unsupported action definitions, never screen ones`() {
        val definitions = listOf(
            ShortcutDefinition("s", ShortcutTargetKind.ACTION, ShortcutAction.SURPRISE_ME.id),
            ShortcutDefinition("p", ShortcutTargetKind.ACTION, ShortcutAction.PLAY_ON.id),
            ShortcutDefinition("o", ShortcutTargetKind.ACTION, ShortcutAction.TOGGLE_OFFLINE.id),
            screenDef("a", "Downloads"),
        )
        val kept = ShortcutsCustomizePolicy.prune(
            definitions = definitions,
            catalog = catalog,
            supportedActions = setOf(ShortcutAction.SURPRISE_ME, ShortcutAction.TOGGLE_OFFLINE),
        )
        // PLAY_ON is outside the set (the desktop shell's shape); screens never gate.
        assertEquals(listOf("s", "o", "a"), kept.map { it.id })
        assertEquals(definitions, ShortcutsCustomizePolicy.prune(definitions, catalog, ShortcutAction.entries.toSet()))
    }

    @Test
    fun `executable gates only action entries outside the supported set`() {
        val surpriseEntry = catalog.entries.first { it.isAction }
        val screenEntry = catalog.entries.first { !it.isAction }
        assertTrue(ShortcutsCustomizePolicy.executable(surpriseEntry, setOf(ShortcutAction.SURPRISE_ME)))
        assertFalse(ShortcutsCustomizePolicy.executable(surpriseEntry, setOf(ShortcutAction.TOGGLE_OFFLINE)))
        assertTrue(ShortcutsCustomizePolicy.executable(screenEntry, emptySet()))
    }

    @Test
    fun `quick-pin keeps the other surface's existing pin and always pins the tapped one`() {
        // Tapping header: pins header, preserves an existing overflow pin.
        assertEquals(
            ShortcutsCustomizePolicy.QuickPinTargets(header = true, overflow = true),
            ShortcutsCustomizePolicy.quickPinTargets(ShortcutSurface.HEADER, pinnedHeader = false, pinnedOverflow = true),
        )
        assertEquals(
            ShortcutsCustomizePolicy.QuickPinTargets(header = true, overflow = false),
            ShortcutsCustomizePolicy.quickPinTargets(ShortcutSurface.HEADER, pinnedHeader = false, pinnedOverflow = false),
        )
        // Tapping menu mirrors it: pins overflow, preserves an existing header pin.
        assertEquals(
            ShortcutsCustomizePolicy.QuickPinTargets(header = true, overflow = true),
            ShortcutsCustomizePolicy.quickPinTargets(ShortcutSurface.OVERFLOW, pinnedHeader = true, pinnedOverflow = false),
        )
        assertEquals(
            ShortcutsCustomizePolicy.QuickPinTargets(header = false, overflow = true),
            ShortcutsCustomizePolicy.quickPinTargets(ShortcutSurface.OVERFLOW, pinnedHeader = false, pinnedOverflow = false),
        )
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
