package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adapter tests against a real temp-file DataStore: dirty semantics,
 * kind-preserving remote application, mirror bookkeeping.
 */
class JellyPlayPreferencesSyncAdapterTest {

    private fun newDataStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ) {
            val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-adapter-test").apply { mkdirs() }
            File(dir, "$name.preferences_pb").absolutePath.toPath()
        }

    private suspend fun DataStore<Preferences>.reset() = edit { it.clear() }

    @Test
    fun dirtyValues_neverSyncedKeyIsDirty_syncedKeyIsClean() = runTest {
        val store = newDataStore("dirty-${System.nanoTime()}").apply { reset() }
        val adapter = JellyPlayPreferencesSyncAdapter(store)

        store.edit { it[stringPreferencesKey("theme")] = "dark" }
        assertTrue(adapter.dirtyValues(adapter.snapshot()).containsKey("theme")) // no mirror yet

        adapter.markSynced(mapOf("theme" to JsonPrimitive("dark")))
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())

        store.edit { it[stringPreferencesKey("theme")] = "light" }
        assertEquals(mapOf("theme" to JsonPrimitive("light")), adapter.dirtyValues(adapter.snapshot()))
    }

    @Test
    fun applyRemote_preservesLocalIntKind() = runTest {
        val store = newDataStore("kind-int-${System.nanoTime()}").apply { reset() }
        val adapter = JellyPlayPreferencesSyncAdapter(store)
        store.edit { it[intPreferencesKey("volume")] = 30 }

        adapter.applyRemote(mapOf("volume" to JsonPrimitive(80)))

        // Must be readable as INT — a Long write here would crash int-key readers.
        assertEquals(80, store.data.first()[intPreferencesKey("volume")])
    }

    @Test
    fun applyRemote_preservesLocalFloatKind() = runTest {
        val store = newDataStore("kind-float-${System.nanoTime()}").apply { reset() }
        val adapter = JellyPlayPreferencesSyncAdapter(store)
        store.edit { it[floatPreferencesKey("speed")] = 1.5f }

        adapter.applyRemote(mapOf("speed" to JsonPrimitive(2.0)))

        assertEquals(2.0f, store.data.first()[floatPreferencesKey("speed")]!!)
    }

    @Test
    fun applyRemote_newKeyTakesIncomingKind() = runTest {
        val store = newDataStore("kind-new-${System.nanoTime()}").apply { reset() }
        val adapter = JellyPlayPreferencesSyncAdapter(store)

        adapter.applyRemote(mapOf("enabled" to JsonPrimitive(true), "label" to JsonPrimitive("x")))

        assertEquals(true, store.data.first()[booleanPreferencesKey("enabled")])
        assertEquals("x", store.data.first()[stringPreferencesKey("label")])
    }

    @Test
    fun reservedAndByteArrayKeys_neverSync() = runTest {
        val store = newDataStore("reserved-${System.nanoTime()}").apply { reset() }
        val adapter = JellyPlayPreferencesSyncAdapter(store)
        store.edit {
            it[stringPreferencesKey(JpsyncReservation.MIRROR_ROOT + "theme")] = "\"dark\""
            it[stringPreferencesKey("normal")] = "value"
        }

        val snapshot = adapter.snapshot()

        assertTrue(snapshot.containsKey("normal"))
        assertNull(snapshot[JpsyncReservation.MIRROR_ROOT + "theme"])
    }

    @Test
    fun excludedKeysAndPrefixes_neverSync() = runTest {
        val store = newDataStore("excluded-${System.nanoTime()}").apply { reset() }
        val adapter = JellyPlayPreferencesSyncAdapter(
            store,
            excludedPrefixes = listOf("dream"),
            excludedKeys = setOf("pin_hash", "device_id"),
        )
        store.edit {
            it[stringPreferencesKey("pin_hash")] = "secret"
            it[stringPreferencesKey("device_id")] = "dev-1"
            it[stringPreferencesKey("dream_enabled")] = "true"
            it[stringPreferencesKey("theme_mode")] = "DARK"
        }

        val snapshot = adapter.snapshot()

        // Secrets and identity must never leave the device; prefix-excluded
        // namespaces stay per-device.
        assertNull(snapshot["pin_hash"])
        assertNull(snapshot["device_id"])
        assertNull(snapshot["dream_enabled"])
        assertEquals(JsonPrimitive("DARK"), snapshot["theme_mode"])
    }

    @Test
    fun applyRemoteAndMarkSynced_dropExcludedAndReservedEntries() = runTest {
        val store = newDataStore("excluded-inbound-${System.nanoTime()}").apply { reset() }
        val adapter = JellyPlayPreferencesSyncAdapter(
            store,
            excludedPrefixes = listOf("dream"),
            excludedKeys = setOf("pin_hash", "device_id"),
        )
        store.edit { it[stringPreferencesKey("pin_hash")] = "local-secret" }

        // A server still holding pre-exclusion leaked rows (or a hostile one)
        // must not re-write secrets, identity, per-device namespaces, or
        // mirror state back onto this device.
        adapter.applyRemote(
            mapOf(
                "pin_hash" to JsonPrimitive("attacker-hash"),
                "device_id" to JsonPrimitive("attacker-device"),
                "dream_enabled" to JsonPrimitive(true),
                JpsyncReservation.MIRROR_ROOT + "theme" to JsonPrimitive("\"dark\""),
                "theme_mode" to JsonPrimitive("LIGHT"),
            ),
        )
        adapter.markSynced(
            mapOf(
                "pin_hash" to JsonPrimitive("attacker-hash"),
                "theme_mode" to JsonPrimitive("LIGHT"),
            ),
        )

        val prefs = store.data.first()
        assertEquals("local-secret", prefs[stringPreferencesKey("pin_hash")])
        assertNull(prefs[booleanPreferencesKey("dream_enabled")])
        assertNull(prefs[stringPreferencesKey("device_id")])
        assertNull(prefs[stringPreferencesKey(JpsyncReservation.MIRROR_ROOT + "theme")])
        assertEquals("LIGHT", prefs[stringPreferencesKey("theme_mode")])
        assertNull(prefs[stringPreferencesKey(JpsyncReservation.MIRROR_ROOT + "pin_hash")])
        assertEquals("\"LIGHT\"", prefs[stringPreferencesKey(JpsyncReservation.MIRROR_ROOT + "theme_mode")])
    }

    @Test
    fun deleteRemote_removesValueOfAnyKind_andItsMirror() = runTest {
        // The inbound tombstone face (a namespace reset's tombstone batch, or
        // another device's roaming delete): the local value AND its mirror
        // entry go — the adopted delete never resurrects nor re-reads dirty.
        val store = newDataStore("delete-remote-${System.nanoTime()}").apply { reset() }
        val adapter = JellyPlayPreferencesSyncAdapter(store)
        store.edit {
            it[stringPreferencesKey("theme")] = "dark"
            it[intPreferencesKey("volume")] = 30
            it[stringPreferencesKey(JpsyncReservation.MIRROR_ROOT + "theme")] = "\"dark\""
            it[stringPreferencesKey(JpsyncReservation.MIRROR_ROOT + "volume")] = "30"
        }
        // The cursors the sync wiring parks under the reserved prefix are
        // untouchable (a hostile/stale tombstone cannot wipe identity or
        // cursor state).
        store.edit { it[stringPreferencesKey(JpsyncReservation.cursorKey("delta", "user-1"))] = "42" }

        adapter.deleteRemote(setOf("theme", "volume", "pin_hash"))

        val prefs = store.data.first()
        assertNull(prefs[stringPreferencesKey("theme")])
        assertNull(prefs[intPreferencesKey("volume")])
        assertNull(prefs[stringPreferencesKey(JpsyncReservation.MIRROR_ROOT + "theme")])
        assertNull(prefs[stringPreferencesKey(JpsyncReservation.MIRROR_ROOT + "volume")])
        assertEquals("42", prefs[stringPreferencesKey(JpsyncReservation.cursorKey("delta", "user-1"))])
        // Nothing re-reads as deleted or dirty afterwards.
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
        assertTrue(adapter.deletedKeys().isEmpty())
    }

}
