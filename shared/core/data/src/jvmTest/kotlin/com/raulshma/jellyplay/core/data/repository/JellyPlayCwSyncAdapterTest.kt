package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
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
import kotlin.test.assertTrue

/**
 * Adapter tests for the `cw/` namespace (ADR 0011's continue-watching
 * removals): a real [HomeDiscoveryStore] over a temp-file DataStore plus a
 * temp-file DataStore mirror — the `hidden/{itemId}` keying, the mirror-based
 * dirty/deleted detection, and both delete directions (the plan's roaming
 * removals). The store's user activation rides the production
 * [ServerIdentityStore.setActiveUser] seam; the Eagerly-cached
 * [HomeDiscoveryStore.homeDiscovery] StateFlow the adapter reads is settled
 * with bounded waits so its `.value` reads are deterministic.
 */
class JellyPlayCwSyncAdapterTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var homeStore: HomeDiscoveryStore
    private lateinit var mirrorStore: DataStore<Preferences>
    private lateinit var adapter: JellyPlayCwSyncAdapter

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = PreferenceDataStoreFactory.createWithPath(scope = scope) {
                val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-cw-adapter-test").apply { mkdirs() }
                File(dir, "home-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
            }
            dataStore.edit { it.clear() }
            val identityStore = ServerIdentityStore(dataStore, scope)
            homeStore = HomeDiscoveryStore(dataStore, scope, identityStore)
            mirrorStore = PreferenceDataStoreFactory.createWithPath(scope = scope) {
                val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-cw-adapter-test").apply { mkdirs() }
                File(dir, "cw-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
            }
            adapter = JellyPlayCwSyncAdapter(
                homeDiscoveryStore = homeStore,
                mirrorStore = mirrorStore,
            )
            identityStore.setActiveUser(USER)
            settle()
        }
    }

    @AfterTest
    fun teardown() {
        scope.cancel()
    }

    /**
     * Bounded wait for the Eagerly-cached projection to settle on [expected]
     * (the HomeDiscoveryStoreTest reactive-re-derivation idiom): the adapter
     * reads the StateFlow's `.value`, so a write must land there before any
     * assertion. The test bodies run [runBlocking] (real time — the
     * projection's upstream hops through the DataStore's own dispatcher,
     * which no amount of virtual-time advancing can order against), so this
     * bound is a real 5s.
     */
    private suspend fun settle(expected: Set<String> = emptySet()) {
        withTimeoutOrNull(5_000) {
            homeStore.homeDiscovery.first { it.hiddenCwItemIds == expected }
        } ?: error("homeDiscovery never settled on $expected")
    }

    private suspend fun hide(itemId: String) {
        homeStore.hideCwItem(itemId)
        settle(setOf(itemId))
    }

    private fun idsOf(snapshot: Map<String, kotlinx.serialization.json.JsonElement>): Set<String> =
        snapshot.keys.mapNotNull { it.removePrefix("hidden/").takeIf(String::isNotEmpty) }.toSet()

    private suspend fun assertNullMirror(key: String) {
        mirrorStore.edit { } // settle the write side
        assertEquals(
            null,
            mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey("cw", key))],
        )
    }

    // ------------------------------------------------------------------
    // snapshot: hidden/{itemId} keys, `true` primitive values
    // ------------------------------------------------------------------

    @Test
    fun snapshot_keysHiddenItemsWithTruePrimitives() = runBlocking {
        homeStore.hideCwItem("cw-1")
        homeStore.hideCwItem("cw-2")
        settle(setOf("cw-1", "cw-2"))

        val snapshot = adapter.snapshot()

        assertEquals(
            mapOf(
                "hidden/cw-1" to JsonPrimitive(true),
                "hidden/cw-2" to JsonPrimitive(true),
            ),
            snapshot,
        )
    }

    // ------------------------------------------------------------------
    // dirty / synced: the mirror cycle
    // ------------------------------------------------------------------

    @Test
    fun dirtyUntilMarkSynced_thenClean() = runBlocking {
        hide("cw-1")
        val snapshot = adapter.snapshot()

        assertTrue(adapter.dirtyValues(snapshot).isNotEmpty()) // no mirror yet

        adapter.markSynced(snapshot)
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
    }

    // ------------------------------------------------------------------
    // deletes roam: a local un-hide is an outbound tombstone, an inbound
    // tombstone unhides
    // ------------------------------------------------------------------

    @Test
    fun localUnhide_reportsDeletedKey_deleteRemoteClearsIt_noResurrection() = runBlocking {
        hide("cw-1")
        val snapshot = adapter.snapshot()
        adapter.markSynced(snapshot)

        homeStore.unhideCwItem("cw-1")
        settle()

        assertEquals(setOf("hidden/cw-1"), adapter.deletedKeys())

        adapter.deleteRemote(setOf("hidden/cw-1"))
        assertTrue(adapter.deletedKeys().isEmpty())
        assertTrue(adapter.snapshot().isEmpty())
        assertNullMirror("hidden/cw-1")
    }

    @Test
    fun deleteRemote_removesOnlyTheNamedItems() = runBlocking {
        homeStore.hideCwItem("cw-1")
        homeStore.hideCwItem("cw-2")
        settle(setOf("cw-1", "cw-2"))
        adapter.markSynced(adapter.snapshot())

        adapter.deleteRemote(setOf("hidden/cw-1"))
        settle(setOf("cw-2"))

        assertEquals(setOf("cw-2"), idsOf(adapter.snapshot()))
        assertTrue(adapter.deletedKeys().isEmpty())
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
    }

    @Test
    fun deleteRemote_unparseableKey_stillClearsMirror() = runBlocking {
        adapter.markSynced(mapOf("not-a-hidden-key" to JsonPrimitive(true)))

        adapter.deleteRemote(setOf("not-a-hidden-key"))

        assertNullMirror("not-a-hidden-key")
    }

    // ------------------------------------------------------------------
    // applyRemote: adopt hides (and defensive nulls as unhides)
    // ------------------------------------------------------------------

    @Test
    fun applyRemote_adoptsHides_andNullsUnhide() = runBlocking {
        hide("cw-1")

        adapter.applyRemote(
            mapOf(
                "hidden/cw-2" to JsonPrimitive(true),
                // Defensive: the engine routes tombstones through deleteRemote;
                // a null here is treated identically (unhide).
                "hidden/cw-1" to JsonNull,
            ),
        )
        settle(setOf("cw-2"))

        assertEquals(setOf("cw-2"), idsOf(adapter.snapshot()))
    }

    @Test
    fun applyRemote_malformedKeysSkipped() = runBlocking {
        hide("cw-1")

        adapter.applyRemote(
            mapOf(
                "weird" to JsonPrimitive(true), // no hidden/ prefix
                "hidden/" to JsonPrimitive(true), // empty itemId
            ),
        )
        settle(setOf("cw-1"))

        assertEquals(setOf("cw-1"), idsOf(adapter.snapshot()))
    }

    private companion object {
        const val USER = "user-1"
    }
}
