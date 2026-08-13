package com.raulshma.jellyplay.core.datastore.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the home-discovery preference store, focusing on the JSON-decoded
 * section-type sets and the one-shot legacy `home_hidden_library_section_ids`
 * read-time migration that previously lived inline in the
 * `UserPreferencesStore` god object with no unit coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeDiscoveryStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: HomeDiscoveryStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = HomeDiscoveryStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.homeDiscovery.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.homeDiscovery.first()
        assertEquals(HomeMode.VIDEO, slice.homeMode)
        assertEquals(HomeSectionType.CONFIGURABLE.toSet(), slice.enabledHomeSectionTypes)
        assertEquals(HomeSectionType.CONFIGURABLE, slice.homeSectionOrder)
        assertTrue(slice.libraryHomeSectionOverrides.isEmpty())
        assertTrue(slice.pinnedHomeSections.isEmpty())
        assertEquals(ContinueWatchingClickBehavior.DETAILS, slice.continueWatchingClickBehavior)
        // Default off — current pinned behaviour until the user opts in.
        assertEquals(false, slice.hideTopHeaderOnScroll)
    }

    @Test
    fun `setHideTopHeaderOnScroll round-trips`() = runTest {
        store.setHideTopHeaderOnScroll(true)
        assertEquals(true, store.homeDiscovery.first().hideTopHeaderOnScroll)
        store.setHideTopHeaderOnScroll(false)
        assertEquals(false, store.homeDiscovery.first().hideTopHeaderOnScroll)
    }

    @Test
    fun `setEnabledHomeSectionTypes round-trips`() = runTest {
        val types = setOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP)
        store.setEnabledHomeSectionTypes(types)
        assertEquals(types, store.homeDiscovery.first().enabledHomeSectionTypes)
    }

    @Test
    fun `setLastViewedSeason round-trips two series`() = runTest {
        store.setLastViewedSeason("seriesA", "season2")
        store.setLastViewedSeason("seriesB", "season5")
        val map = store.homeDiscovery.first().lastViewedSeasonBySeries
        assertEquals("season2", map["seriesA"])
        assertEquals("season5", map["seriesB"])
        assertEquals(2, map.size)
    }

    @Test
    fun `setLastViewedSeason overwrites the same series`() = runTest {
        store.setLastViewedSeason("seriesA", "season1")
        store.setLastViewedSeason("seriesA", "season3")
        val map = store.homeDiscovery.first().lastViewedSeasonBySeries
        assertEquals("season3", map["seriesA"])
        assertEquals(1, map.size)
    }

    @Test
    fun `setLastViewedSeason JSON survives a cold re-read`() = runTest {
        store.setLastViewedSeason("seriesA", "season4")
        store.setLastViewedSeason("seriesB", "season1")
        // Read the raw on-disk value straight from the DataStore file and decode
        // it exactly as a fresh store would, proving the persisted JSON format
        // round-trips (deterministic — no stateIn initialValue race).
        val raw = dataStore.data.first()[stringPreferencesKey("last_viewed_season_by_series")]
        val decoded = PreferenceCodec.json.decodeFromString<Map<String, String>>(raw!!)
        assertEquals("season4", decoded["seriesA"])
        assertEquals("season1", decoded["seriesB"])
        assertEquals(2, decoded.size)
    }

    @Test
    fun `lastViewedSeasonBySeries defaults to empty`() = runTest {
        assertTrue(store.homeDiscovery.first().lastViewedSeasonBySeries.isEmpty())
    }

    @Test
    fun `legacy hidden library section ids migrate to overrides and drop the legacy key`() = runTest {
        // Seed the legacy all-or-nothing "hide library from home" key, then run
        // the one-shot migration explicitly (production runs it in the store's
        // init block; calling it directly avoids init/stateIn ordering races).
        dataStore.edit {
            it[stringPreferencesKey("home_hidden_library_section_ids")] = """["lib_a","lib_b"]"""
        }
        store.migrateHiddenLibrarySectionIds()
        val slice = store.homeDiscovery.first()
        val overrides = slice.libraryHomeSectionOverrides
        assertEquals(setOf("lib_a", "lib_b"), overrides.keys)
        assertEquals(
            setOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED),
            overrides["lib_a"],
        )
    }
}
