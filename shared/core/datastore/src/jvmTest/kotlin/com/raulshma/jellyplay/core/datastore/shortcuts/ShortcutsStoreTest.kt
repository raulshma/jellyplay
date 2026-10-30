package com.raulshma.jellyplay.core.datastore.shortcuts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutLimits
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the user-shortcuts preference store: round-trips, the write-side
 * normalization invariants, per-user namespacing (raw `u_<userId>::` grammar
 * pinned), pre-login skip, and the dynamic category-reset participation.
 */
class ShortcutsStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var identityStore: ServerIdentityStore
    private lateinit var store: ShortcutsStore

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            identityStore = ServerIdentityStore(dataStore, scope)
            store = ShortcutsStore(dataStore, scope, identityStore)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.shortcuts.first()
        }
    }

    private fun screen(id: String, target: String = "Screen_$id") =
        ShortcutDefinition(id = id, kind = ShortcutTargetKind.SCREEN, targetId = target)

    private suspend fun activate(userId: String) {
        identityStore.setActiveUser(userId)
    }

    // ------------------------------------------------------------------
    // Defaults
    // ------------------------------------------------------------------

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.shortcuts.first()
        assertTrue(slice.definitions.isEmpty())
        assertTrue(slice.headerOrder.isEmpty())
        assertTrue(slice.overflowOrder.isEmpty())
        assertTrue(slice.isUnconfigured)
    }

    // ------------------------------------------------------------------
    // Shipped-default adoption (seedDefaultsIfUnconfigured)
    // ------------------------------------------------------------------

    @Test
    fun `seedDefaultsIfUnconfigured seeds an unconfigured user`() = runTest {
        activate("user-1")
        store.seedDefaultsIfUnconfigured(
            definitions = listOf(screen("default:Downloads", "Downloads")),
            headerOrder = emptyList(),
            overflowOrder = listOf("default:Downloads"),
        )
        val slice = store.shortcuts.first()
        assertEquals(listOf("default:Downloads"), slice.definitions.map { it.id })
        assertEquals(listOf("default:Downloads"), slice.overflowOrder)
        assertFalse(slice.isUnconfigured)
    }

    @Test
    fun `seedDefaultsIfUnconfigured never clobbers a configured user`() = runTest {
        activate("user-1")
        store.addShortcut(screen("mine"), pinHeader = true)
        store.seedDefaultsIfUnconfigured(
            definitions = listOf(screen("default:Downloads", "Downloads")),
            headerOrder = emptyList(),
            overflowOrder = listOf("default:Downloads"),
        )
        val slice = store.shortcuts.first()
        assertEquals(listOf("mine"), slice.definitions.map { it.id })
        assertEquals(listOf("mine"), slice.headerOrder)
        assertTrue(slice.overflowOrder.isEmpty())
    }

    @Test
    fun `seedDefaultsIfUnconfigured normalizes like every write`() = runTest {
        activate("user-1")
        store.seedDefaultsIfUnconfigured(
            definitions = listOf(screen("a")),
            headerOrder = emptyList(),
            overflowOrder = listOf("a", "ghost", "a"),
        )
        val slice = store.shortcuts.first()
        assertEquals(listOf("a"), slice.overflowOrder)
    }

    // ------------------------------------------------------------------
    // Round-trips
    // ------------------------------------------------------------------

    @Test
    fun `setShortcutsConfig round-trips`() = runTest {
        activate("user-1")
        store.setShortcutsConfig(
            definitions = listOf(screen("a"), screen("b", "Downloads")),
            headerOrder = listOf("b"),
            overflowOrder = listOf("a", "b"),
        )
        val slice = store.shortcuts.first()
        assertEquals(listOf(screen("a"), screen("b", "Downloads")), slice.definitions)
        assertEquals(listOf("b"), slice.headerOrder)
        assertEquals(listOf("a", "b"), slice.overflowOrder)
        assertFalse(slice.isUnconfigured)
    }

    @Test
    fun `restore round-trips through the same encoding`() = runTest {
        activate("user-1")
        store.restore(
            ShortcutsSlice(
                definitions = listOf(screen("a", "LiveTv")),
                headerOrder = listOf("a"),
                overflowOrder = emptyList(),
            ),
        )
        assertEquals(listOf("a"), store.shortcuts.first().headerOrder)
        assertEquals("LiveTv", store.shortcuts.first().definitions.single().targetId)
    }

    // ------------------------------------------------------------------
    // Normalization invariants
    // ------------------------------------------------------------------

    @Test
    fun `normalize dedupes ids first-wins and drops blanks`() {
        val normalized = store.normalize(
            definitions = listOf(
                screen("a", "One"),
                ShortcutDefinition("", ShortcutTargetKind.SCREEN, "Ghost"),
                ShortcutDefinition(" ", ShortcutTargetKind.SCREEN, "Blank"),
                screen("a", "Duplicate"),
                screen("c", "Three"),
            ),
            headerOrder = emptyList(),
            overflowOrder = emptyList(),
        )
        assertEquals(listOf(screen("a", "One"), screen("c", "Three")), normalized.definitions)
    }

    @Test
    fun `normalize dedupes by target keeping the first definition`() {
        val normalized = store.normalize(
            definitions = listOf(screen("a", "Downloads"), screen("b", "Downloads")),
            headerOrder = emptyList(),
            overflowOrder = emptyList(),
        )
        assertEquals(listOf("a"), normalized.definitions.map { it.id })
    }

    @Test
    fun `normalize drops orphan order ids, dedupes and clamps`() {
        val definitions = (1..20).map { screen("s$it") }
        val normalized = store.normalize(
            definitions = definitions,
            headerOrder = listOf("s3", "ghost", "s1", "s3", "s2", "s4", "s5", "s6"),
            overflowOrder = (1..20).map { "s$it" } + (1..20).map { "s$it" },
        )
        // Header: ghosts dropped, dupes dropped, clamped to 4.
        assertEquals(listOf("s3", "s1", "s2", "s4"), normalized.headerOrder)
        // Overflow: deduped then clamped to 12.
        assertEquals(12, normalized.overflowOrder.size)
        assertTrue(normalized.overflowOrder.distinct().size == normalized.overflowOrder.size)
        assertEquals((1..12).map { "s$it" }, normalized.overflowOrder)
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    @Test
    fun `addShortcut appends and pins`() = runTest {
        activate("user-1")
        store.addShortcut(screen("a"), pinOverflow = true)
        store.addShortcut(screen("b", "Downloads"), pinHeader = true, pinOverflow = true)
        val slice = store.shortcuts.first()
        assertEquals(listOf("a", "b"), slice.overflowOrder)
        assertEquals(listOf("b"), slice.headerOrder)
    }

    @Test
    fun `addShortcut no-ops on a duplicate target`() = runTest {
        activate("user-1")
        store.addShortcut(screen("a"))
        store.addShortcut(screen("b", target = screen("a").targetId))
        assertEquals(1, store.shortcuts.first().definitions.size)
    }

    @Test
    fun `addShortcut respects surface capacity`() = runTest {
        activate("user-1")
        repeat(6) { index -> store.addShortcut(screen("h$index", "Header$index"), pinHeader = true) }
        assertEquals(ShortcutLimits.HEADER_MAX, store.shortcuts.first().headerOrder.size)
        repeat(14) { index -> store.addShortcut(screen("o$index", "Overflow$index"), pinOverflow = true) }
        assertEquals(ShortcutLimits.OVERFLOW_MAX, store.shortcuts.first().overflowOrder.size)
    }

    @Test
    fun `updateShortcut replaces icon and label in place`() = runTest {
        activate("user-1")
        store.addShortcut(screen("a"), pinOverflow = true)
        store.updateShortcut(ShortcutDefinition("a", ShortcutTargetKind.SCREEN, screen("a").targetId, iconId = "wand", labelOverride = "Magic"))
        val slice = store.shortcuts.first()
        assertEquals("wand", slice.definitions.single().iconId)
        assertEquals("Magic", slice.definitions.single().labelOverride)
        // Placement untouched.
        assertEquals(listOf("a"), slice.overflowOrder)
    }

    @Test
    fun `setShortcutSurfaces pins and unpins`() = runTest {
        activate("user-1")
        store.addShortcut(screen("a"))
        store.setShortcutSurfaces("a", header = true, overflow = true)
        val pinned = store.shortcuts.first()
        assertEquals(listOf("a"), pinned.headerOrder)
        assertEquals(listOf("a"), pinned.overflowOrder)
        store.setShortcutSurfaces("a", header = false, overflow = true)
        val unpinned = store.shortcuts.first()
        assertTrue(unpinned.headerOrder.isEmpty())
        assertEquals(listOf("a"), unpinned.overflowOrder)
    }

    @Test
    fun `moveShortcut reorders within its surface only`() = runTest {
        activate("user-1")
        listOf("a", "b", "c").forEach { store.addShortcut(screen(it), pinHeader = true, pinOverflow = true) }
        store.moveShortcut("c", ShortcutSurface.HEADER, up = true)
        val slice = store.shortcuts.first()
        assertEquals(listOf("a", "c", "b"), slice.headerOrder)
        // Overflow order untouched by a header move.
        assertEquals(listOf("a", "b", "c"), slice.overflowOrder)
        // Edge no-ops.
        store.moveShortcut("a", ShortcutSurface.HEADER, up = true)
        assertEquals(listOf("a", "c", "b"), store.shortcuts.first().headerOrder)
    }

    @Test
    fun `removeShortcut drops definition and both pins`() = runTest {
        activate("user-1")
        store.addShortcut(screen("a"), pinHeader = true, pinOverflow = true)
        store.addShortcut(screen("b", "Downloads"), pinOverflow = true)
        store.removeShortcut("a")
        val slice = store.shortcuts.first()
        assertEquals(listOf("Downloads"), slice.definitions.map { it.targetId })
        assertTrue(slice.headerOrder.isEmpty())
        assertEquals(listOf("b"), slice.overflowOrder)
    }

    @Test
    fun `resetShortcuts returns to the unconfigured default`() = runTest {
        activate("user-1")
        store.addShortcut(screen("a"), pinHeader = true, pinOverflow = true)
        store.resetShortcuts()
        assertTrue(store.shortcuts.first().isUnconfigured)
    }

    // ------------------------------------------------------------------
    // Per-user namespacing
    // ------------------------------------------------------------------

    @Test
    fun `users never see each other's config`() = runTest {
        activate("user-1")
        store.addShortcut(screen("mine", "Favorites"), pinOverflow = true)
        activate("user-2")
        assertTrue(store.shortcuts.first().isUnconfigured)
        store.addShortcut(screen("theirs", "Playlists"), pinHeader = true)
        activate("user-1")
        val user1 = store.shortcuts.first()
        assertEquals(listOf("mine"), user1.overflowOrder)
        assertTrue(user1.headerOrder.isEmpty())
    }

    @Test
    fun `namespaced raw keys follow the u_userid-canonical grammar`() = runTest {
        activate("user-7")
        store.addShortcut(screen("a"), pinOverflow = true)
        val raw = dataStore.data.first()
        assertNotNull(raw[stringPreferencesKey("u_user-7::shortcuts_definitions")])
        assertNotNull(raw[stringPreferencesKey("u_user-7::shortcuts_overflow_order")])
        // The canonical flat names never exist — these keys shipped namespaced.
        assertNull(raw[stringPreferencesKey("shortcuts_definitions")])
    }

    @Test
    fun `pre-login writes are skipped`() = runTest {
        // No active user.
        store.addShortcut(screen("a"), pinOverflow = true)
        assertTrue(store.shortcuts.first().isUnconfigured)
    }

    // ------------------------------------------------------------------
    // Corrupt-state robustness
    // ------------------------------------------------------------------

    @Test
    fun `malformed json degrades to defaults`() = runTest {
        activate("user-1")
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("u_user-1::shortcuts_definitions")] = "{not json"
            prefs[stringPreferencesKey("u_user-1::shortcuts_header_order")] = "]["
        }
        val slice = store.shortcuts.first()
        assertTrue(slice.definitions.isEmpty())
        assertTrue(slice.headerOrder.isEmpty())
        assertTrue(slice.isUnconfigured)
    }

    // ------------------------------------------------------------------
    // Reset participation
    // ------------------------------------------------------------------

    @Test
    fun `removeDynamicResetKeys strips every user's shortcuts keys for HOME_DISCOVERY only`() = runTest {
        activate("user-1")
        store.addShortcut(screen("a"), pinOverflow = true)
        activate("user-2")
        store.addShortcut(screen("b"), pinOverflow = true)

        // Other categories no-op (checked BEFORE the strip so the keys exist).
        dataStore.edit { prefs -> store.removeDynamicResetKeys(PreferenceResetCategory.PLAYBACK, prefs) }
        assertTrue(dataStore.data.first().asMap().keys.any { it.name == "u_user-1::shortcuts_definitions" })

        // Production shape: the reset machinery strips inside a DataStore edit.
        dataStore.edit { prefs -> store.removeDynamicResetKeys(PreferenceResetCategory.HOME_DISCOVERY, prefs) }
        assertTrue(dataStore.data.first().asMap().keys.none { it.name.startsWith("u_") })
    }

    @Test
    fun `resetKeysFor lists the canonical keys for HOME_DISCOVERY`() {
        val keys = store.resetKeysFor(PreferenceResetCategory.HOME_DISCOVERY).map { it.name }
        assertEquals(
            listOf("shortcuts_definitions", "shortcuts_header_order", "shortcuts_overflow_order"),
            keys,
        )
        assertTrue(store.resetKeysFor(PreferenceResetCategory.PLAYBACK).isEmpty())
    }
}
