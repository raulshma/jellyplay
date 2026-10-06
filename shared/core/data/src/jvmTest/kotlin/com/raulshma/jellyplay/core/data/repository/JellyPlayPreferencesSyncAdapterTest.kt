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
            it[stringPreferencesKey("jpsync.mirror.theme")] = "\"dark\""
            it[stringPreferencesKey("normal")] = "value"
        }

        val snapshot = adapter.snapshot()

        assertTrue(snapshot.containsKey("normal"))
        assertNull(snapshot["jpsync.mirror.theme"])
    }

}
