package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.HomeSectionType
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Adapter tests for the `homelayout/` namespace (ADR 0011's home-layout
 * roaming): a real [HomeDiscoveryStore] over a temp-file DataStore plus a
 * temp-file DataStore mirror — the canonical wire keys, the store's OWN
 * setters as the adoption path (the section algebra stays hers), the
 * malformed-value tolerance, and the no-tombstone stance (a cleared layout
 * domain means "back to default here", never "delete everywhere").
 */
class JellyPlayHomeLayoutSyncAdapterTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var homeStore: HomeDiscoveryStore
    private lateinit var mirrorStore: DataStore<Preferences>
    private lateinit var adapter: JellyPlayHomeLayoutSyncAdapter

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = PreferenceDataStoreFactory.createWithPath(scope = scope) {
                val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-homelayout-adapter-test").apply { mkdirs() }
                File(dir, "home-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
            }
            dataStore.edit { it.clear() }
            val identityStore = ServerIdentityStore(dataStore, scope)
            homeStore = HomeDiscoveryStore(dataStore, scope, identityStore)
            mirrorStore = PreferenceDataStoreFactory.createWithPath(scope = scope) {
                val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-homelayout-adapter-test").apply { mkdirs() }
                File(dir, "homelayout-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
            }
            adapter = JellyPlayHomeLayoutSyncAdapter(
                homeDiscoveryStore = homeStore,
                mirrorStore = mirrorStore,
            )
            identityStore.setActiveUser(USER)
            settle { true }
        }
    }

    @AfterTest
    fun teardown() {
        scope.cancel()
    }

    /**
     * Bounded wait for the Eagerly-cached [HomeDiscoveryStore.homeDiscovery]
     * projection to satisfy [predicate] — the adapter reads the StateFlow's
     * `.value`, so a write must land there before any assertion. The test
     * bodies run [runBlocking] (real time — the projection's upstream hops
     * through the DataStore's own dispatcher, which no amount of virtual-time
     * advancing can order against), so this bound is a real 5s.
     */
    private suspend fun settle(predicate: (com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice) -> Boolean) {
        withTimeoutOrNull(5_000) {
            homeStore.homeDiscovery.first(predicate)
        } ?: error("homeDiscovery never settled")
    }

    // ------------------------------------------------------------------
    // snapshot: the canonical wire keys, one per layout domain
    // ------------------------------------------------------------------

    @Test
    fun snapshot_carriesEveryCanonicalKey() = runBlocking {
        val snapshot = adapter.snapshot()

        assertEquals(
            setOf(
                "home_enabled_section_types",
                "home_section_order",
                "home_library_section_overrides",
                "pinned_home_sections",
                "home_discover_rows",
                "home_layout_presets",
            ),
            snapshot.keys,
        )
    }

    // ------------------------------------------------------------------
    // dirty / synced: the mirror cycle
    // ------------------------------------------------------------------

    @Test
    fun dirtyUntilMarkSynced_thenClean() = runBlocking {
        homeStore.setHomeSectionOrder(listOf(HomeSectionType.NEXT_UP, HomeSectionType.CONTINUE_WATCHING))
        // The store normalizes the persisted order to a full permutation of
        // the configurable set — the pulled head is what must settle.
        settle { it.homeSectionOrder.take(2) == listOf(HomeSectionType.NEXT_UP, HomeSectionType.CONTINUE_WATCHING) }
        val snapshot = adapter.snapshot()

        assertTrue(adapter.dirtyValues(snapshot).isNotEmpty()) // no mirror yet

        adapter.markSynced(snapshot)
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
    }

    // ------------------------------------------------------------------
    // applyRemote: adoption rides the store's own setters
    // ------------------------------------------------------------------

    @Test
    fun applyRemote_adoptsOrderAndEnabledSetThroughTheStore() = runBlocking {
        adapter.applyRemote(
            mapOf(
                "home_section_order" to buildJsonArray {
                    add(JsonPrimitive("NEXT_UP"))
                    add(JsonPrimitive("LATEST_MEDIA"))
                    add(JsonPrimitive("CONTINUE_WATCHING"))
                },
                "home_enabled_section_types" to buildJsonArray {
                    add(JsonPrimitive("NEXT_UP"))
                    add(JsonPrimitive("DISCOVER"))
                },
            ),
        )
        settle {
            it.homeSectionOrder.take(3) == listOf(
                HomeSectionType.NEXT_UP,
                HomeSectionType.LATEST_MEDIA,
                HomeSectionType.CONTINUE_WATCHING,
            ) && it.enabledHomeSectionTypes == setOf(HomeSectionType.NEXT_UP, HomeSectionType.DISCOVER)
        }

        val slice = homeStore.homeDiscovery.value
        // The store's own normalization keeps the persisted order a full
        // permutation of the configurable set — the pulled head must be
        // those three, in the pulled order, with the rest appended.
        assertEquals(
            listOf(HomeSectionType.NEXT_UP, HomeSectionType.LATEST_MEDIA, HomeSectionType.CONTINUE_WATCHING),
            slice.homeSectionOrder.take(3),
        )
        assertEquals(
            HomeSectionType.CONFIGURABLE.toSet(),
            slice.homeSectionOrder.toSet(),
        )
        assertEquals(setOf(HomeSectionType.NEXT_UP, HomeSectionType.DISCOVER), slice.enabledHomeSectionTypes)
        adapter.markSynced(adapter.snapshot())
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
    }

    @Test
    fun applyRemote_unknownKey_andMalformedValue_skipCleanly() = runBlocking {
        homeStore.setHomeSectionOrder(listOf(HomeSectionType.NEXT_UP))
        settle { it.homeSectionOrder.first() == HomeSectionType.NEXT_UP }

        adapter.applyRemote(
            mapOf(
                // Not one of the domains — never touches the store.
                "home_something_new" to JsonPrimitive(true),
                // A garbage order value decodes to null — the local layout
                // survives (the prefs-adapter tolerance rule).
                "home_section_order" to JsonPrimitive("garbage"),
                // Defensive: tombstones never roam for this namespace.
                "home_enabled_section_types" to JsonNull,
            ),
        )

        assertEquals(HomeSectionType.NEXT_UP, homeStore.homeDiscovery.value.homeSectionOrder.first())
    }

    // ------------------------------------------------------------------
    // no tombstones: a cleared domain is a default-value write, never a
    // cross-device delete
    // ------------------------------------------------------------------

    @Test
    fun deletedKeys_stayEmpty_evenAfterLocalChanges() = runBlocking {
        homeStore.setEnabledHomeSectionTypes(setOf(HomeSectionType.FAVORITES))
        settle { it.enabledHomeSectionTypes == setOf(HomeSectionType.FAVORITES) }
        adapter.markSynced(adapter.snapshot())

        homeStore.setEnabledHomeSectionTypes(emptySet())
        settle { it.enabledHomeSectionTypes.isEmpty() }

        assertTrue(adapter.snapshot().isNotEmpty()) // domains always carry a value
        assertTrue(adapter.deletedKeys().isEmpty())
    }

    private companion object {
        const val USER = "user-1"
    }
}
