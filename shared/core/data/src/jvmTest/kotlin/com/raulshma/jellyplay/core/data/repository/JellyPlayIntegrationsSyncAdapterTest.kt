package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SecureKeyValueStorage
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adapter tests for the `integrations` namespace (the settings-backup wave):
 * the three preference stores over a temp-file DataStore plus a temp-file
 * DataStore mirror — the allowlist contract (only the stores' [SeerrPreferencesStore.SyncKeys]-
 * style keys ever snapshot or apply; hostile/stale names are inert), the
 * mirror-based dirty cycle, the raw-string ↔ JSON kind translation, the
 * JsonNull-resets rule, and the value-only contract ([ProfileSyncAdapter.deletedKeys]
 * stays empty).
 */
class JellyPlayIntegrationsSyncAdapterTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var mirrorStore: DataStore<Preferences>
    private lateinit var seerrStore: SeerrPreferencesStore
    private lateinit var arrStore: ArrPreferencesStore
    private lateinit var subtitleStore: SubtitleProviderPreferencesStore
    private lateinit var adapter: JellyPlayIntegrationsSyncAdapter

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = PreferenceDataStoreFactory.createWithPath(scope = scope) {
                val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-integrations-adapter-test").apply { mkdirs() }
                File(dir, "prefs-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
            }
            dataStore.edit { it.clear() }
            mirrorStore = PreferenceDataStoreFactory.createWithPath(scope = scope) {
                val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-integrations-adapter-test").apply { mkdirs() }
                File(dir, "mirror-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
            }
            val secureStorage = FakeSecureKeyValueStorage()
            seerrStore = SeerrPreferencesStore(
                dataStore = dataStore,
                secureCredentialsStore = SeerrSecureCredentialsStore(secureStorage),
                externalScope = scope,
            )
            arrStore = ArrPreferencesStore(
                dataStore = dataStore,
                secureCredentialsStore = ArrSecureCredentialsStore(secureStorage),
                externalScope = scope,
            )
            subtitleStore = SubtitleProviderPreferencesStore(
                dataStore = dataStore,
                secureCredentialsStore = SubtitleProviderSecureCredentialsStore(secureStorage),
            )
            adapter = JellyPlayIntegrationsSyncAdapter(
                seerrPreferencesStore = seerrStore,
                arrPreferencesStore = arrStore,
                subtitleProviderPreferencesStore = subtitleStore,
                mirrorStore = mirrorStore,
            )
        }
    }

    @AfterTest
    fun teardown() {
        scope.cancel()
    }

    /** Minimal in-memory stand-in for the OS-encrypted key-value store. */
    private class FakeSecureKeyValueStorage : SecureKeyValueStorage {
        val raw = mutableMapOf<String, String>()
        override fun getString(key: String, defValue: String?): String? = raw[key] ?: defValue
        override fun putString(key: String, value: String?) {
            if (value == null) raw.remove(key) else raw[key] = value
        }

        override fun remove(key: String) {
            raw.remove(key)
        }
    }

    private suspend fun seed(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    /**
     * Bounded wait for the Seerr store's Eagerly-cached projection to settle
     * on [expected] (the cw-adapter-test idiom): the projection may lag the
     * DataStore edit by a dispatch, and no amount of raw-read assertion
     * ordering can force it.
     */
    private suspend fun awaitSeerrEnabled(expected: Boolean) {
        withTimeoutOrNull(5_000) {
            seerrStore.preferences.first { it.enabled == expected }
        } ?: error("seerr preferences never settled on enabled=$expected")
    }

    // ------------------------------------------------------------------
    // snapshot: only stored allowlisted keys, with inferred wire kinds
    // ------------------------------------------------------------------

    @Test
    fun snapshot_seedsAllThreeStores_withInferredKinds() = runBlocking {
        seerrStore.setEnabled(true)
        seerrStore.setServerUrl("https://seerr.local")
        arrStore.setUseSeerrDiscovery(false)
        arrStore.setPollIntervalSeconds(120)
        subtitleStore.setWyzieEnabled(true)

        val snapshot = adapter.snapshot()

        assertEquals(
            mapOf(
                "seerr_server_url" to JsonPrimitive("https://seerr.local"),
                "seerr_enabled" to JsonPrimitive(true),
                "arr_use_seerr_discovery" to JsonPrimitive(false),
                "arr_poll_interval_seconds" to JsonPrimitive(120),
                "subtitle_wyzie_enabled" to JsonPrimitive(true),
            ),
            snapshot,
        )
    }

    @Test
    fun snapshot_freshStores_isEmpty() = runBlocking {
        assertTrue(adapter.snapshot().isEmpty())
    }

    // ------------------------------------------------------------------
    // the allowlist: hostile/stale/secret-ish names are inert on BOTH faces
    // ------------------------------------------------------------------

    @Test
    fun snapshot_neverLeavesTheAllowlist() = runBlocking {
        seerrStore.setEnabled(true)
        // Stale or hostile rows sharing the DataStore: secrets never live in
        // this store (the encrypted stores own them), but a row naming one
        // must not sync either. A reserved mirror key must stay device-local.
        seed("seerr_api_key", "leak-me")
        seed("arr_manual_servers", "[{\"apiKey\":\"leak-me\"}]")
        seed("subtitle_opensubtitles_credentials", "leak-me")
        seed("jpsync.mirror.integrations.seerr_enabled", "true")

        val snapshot = adapter.snapshot()

        val allowlist = seerrStore.SyncKeys + arrStore.SyncKeys + subtitleStore.SyncKeys
        assertTrue(snapshot.keys.all { it in allowlist })
        assertEquals(setOf("seerr_enabled"), snapshot.keys)
        assertTrue("seerr_api_key" !in allowlist)
    }

    @Test
    fun applyRemote_nonAllowlistedKey_isIgnored() = runBlocking {
        adapter.applyRemote(
            mapOf(
                "seerr_api_key" to JsonPrimitive("hostile"),
                "arr_manual_servers" to JsonPrimitive("[]"),
                "jpsync.device.id" to JsonPrimitive("hostile"),
            ),
        )

        val prefs = dataStore.data.first()
        assertNull(prefs[stringPreferencesKey("seerr_api_key")])
        assertNull(prefs[stringPreferencesKey("arr_manual_servers")])
        assertNull(prefs[stringPreferencesKey("jpsync.device.id")])
    }

    // ------------------------------------------------------------------
    // dirty / synced: the mirror cycle
    // ------------------------------------------------------------------

    @Test
    fun dirtyUntilMarkSynced_thenClean_editRedirties() = runBlocking {
        seerrStore.setEnabled(true)
        val snapshot = adapter.snapshot()

        assertTrue(adapter.dirtyValues(snapshot).isNotEmpty()) // no mirror yet

        adapter.markSynced(snapshot)
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())

        seerrStore.setEnabled(false)
        assertEquals(
            mapOf("seerr_enabled" to JsonPrimitive(false)),
            adapter.dirtyValues(adapter.snapshot()),
        )
    }

    // ------------------------------------------------------------------
    // applyRemote: adopt into the owning store's kind; coercion rules
    // ------------------------------------------------------------------

    @Test
    fun applyRemote_adoptsValues_withStoreKinds() = runBlocking {
        adapter.applyRemote(
            mapOf(
                "seerr_enabled" to JsonPrimitive(true),
                "seerr_streaming_region" to JsonPrimitive("DE"),
                "arr_poll_interval_seconds" to JsonPrimitive(90),
                "subtitle_wyzie_enabled" to JsonPrimitive(true),
            ),
        )

        val prefs = dataStore.data.first()
        assertEquals(true, prefs[booleanPreferencesKey("seerr_enabled")])
        assertEquals("DE", prefs[stringPreferencesKey("seerr_streaming_region")])
        assertEquals(90, prefs[intPreferencesKey("arr_poll_interval_seconds")])
        assertEquals(true, prefs[booleanPreferencesKey("subtitle_wyzie_enabled")])
        // The stores' own read projections agree.
        val poll = withTimeoutOrNull(5_000) {
            arrStore.preferences.first { it.pollIntervalSeconds == 90 }
        } ?: error("arr preferences never settled on pollIntervalSeconds=90")
        awaitSeerrEnabled(true)
    }

    @Test
    fun applyRemote_pollIntervalClampsToTheFloor() = runBlocking {
        adapter.applyRemote(mapOf("arr_poll_interval_seconds" to JsonPrimitive(5)))

        assertEquals(15, dataStore.data.first()[intPreferencesKey("arr_poll_interval_seconds")])
    }

    @Test
    fun applyRemote_uncoercibleValue_skipsTheKey() = runBlocking {
        seerrStore.setEnabled(true)

        adapter.applyRemote(mapOf("seerr_enabled" to JsonPrimitive("not-a-bool")))

        assertEquals(true, dataStore.data.first()[booleanPreferencesKey("seerr_enabled")])
    }

    // ------------------------------------------------------------------
    // JsonNull (and deleteRemote) RESET — the value-only convention
    // ------------------------------------------------------------------

    @Test
    fun applyRemote_jsonNull_resetsKeyToDefault() = runBlocking {
        seerrStore.setEnabled(true)

        adapter.applyRemote(mapOf("seerr_enabled" to JsonNull))

        // The reset WRITES the default (never removes — absence doesn't roam,
        // so a removal would re-adopt the server's row on a later cycle).
        assertEquals(false, dataStore.data.first()[booleanPreferencesKey("seerr_enabled")])
        // The store's reader agrees.
        awaitSeerrEnabled(false)
    }

    @Test
    fun applyRemote_jsonNull_resetsEveryOwningStore_byWritingTheDefault() = runBlocking {
        arrStore.setUseSeerrDiscovery(false)
        subtitleStore.setWyzieEnabled(true)

        adapter.applyRemote(
            mapOf(
                "arr_use_seerr_discovery" to JsonNull,
                "subtitle_wyzie_enabled" to JsonNull,
            ),
        )

        // Fanned out by owner (the same routing values take): each key's
        // default WRITTEN, never removed.
        val prefs = dataStore.data.first()
        assertEquals(true, prefs[booleanPreferencesKey("arr_use_seerr_discovery")])
        assertEquals(false, prefs[booleanPreferencesKey("subtitle_wyzie_enabled")])
        // The owning stores' projections agree.
        assertTrue(arrStore.preferences.first().useSeerrDiscovery)
        assertFalse(subtitleStore.preferences.first().wyzieEnabled)
    }

    @Test
    fun deleteRemote_resetsKeys_andClearsTheirMirrorEntries() = runBlocking {
        seerrStore.setEnabled(true)
        subtitleStore.setWyzieEnabled(true)
        adapter.markSynced(adapter.snapshot())

        adapter.deleteRemote(setOf("seerr_enabled", "weird"))

        // The reset writes the default (never removes).
        assertEquals(false, dataStore.data.first()[booleanPreferencesKey("seerr_enabled")])
        assertTrue(subtitleStore.preferences.first().wyzieEnabled) // untouched
        assertNull(
            mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey(NAMESPACE, "seerr_enabled"))],
        )
        assertNull(
            mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey(NAMESPACE, "weird"))],
        )
    }

    @Test
    fun deletedKeys_stayEmpty_valueOnlyNamespace() = runBlocking {
        seerrStore.setEnabled(true)
        adapter.markSynced(adapter.snapshot())

        // A local reset (disconnect rewrites defaults; here: the raw default
        // write) is a VALUE change, never a tombstone...
        seerrStore.setEnabled(false)
        assertTrue(adapter.deletedKeys().isEmpty())

        // ...and so is a key removal (back to defaults, not "deleted everywhere").
        dataStore.edit { it.remove(booleanPreferencesKey("seerr_enabled")) }
        assertTrue(adapter.deletedKeys().isEmpty())
    }

    // ------------------------------------------------------------------
    // disconnect: resets WRITE their defaults so they roam (the value-only
    // adapter never pushes an absence) — the server's rows can't re-adopt
    // the old profile over the disconnect
    // ------------------------------------------------------------------

    @Test
    fun disconnect_roamsValueResets_readoptPassKeepsTheProfileDisconnected() = runBlocking {
        // A connected profile, synced clean: the server now holds these rows.
        seerrStore.setServerUrl("https://seerr.local")
        seerrStore.setUsername("alice")
        seerrStore.setEmail("alice@seerr.local")
        seerrStore.setEnabled(true)
        adapter.markSynced(adapter.snapshot())

        // Disconnect resets the identity keys BY WRITING their blank
        // defaults — the resets must read as dirty values (a removal would
        // be invisible to the value-only adapter and the server's
        // still-standing rows would re-adopt next cycle).
        seerrStore.disconnect()
        val pushed = adapter.dirtyValues(adapter.snapshot())
        assertTrue("seerr_server_url" in pushed)
        assertTrue("seerr_username" in pushed)
        assertTrue("seerr_email" in pushed)
        assertTrue(adapter.deletedKeys().isEmpty()) // resets are values, not tombstones

        // Engine-style next cycle: the pushed resets are confirmed (the
        // server's rows are now the blanks), then one adopt pass re-applies
        // the server's rows — the old profile must NOT come back.
        adapter.markSynced(pushed)
        adapter.applyRemote(adapter.snapshot())

        val prefs = seerrStore.preferences.first()
        assertTrue(prefs.serverUrl.isBlank())
        assertTrue(prefs.username.isBlank())
        assertTrue(prefs.email.isBlank())
        assertFalse(seerrStore.isConnected.first()) // blank serverUrl → false
    }

    private companion object {
        const val NAMESPACE = "integrations"
    }
}
