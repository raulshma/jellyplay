package com.raulshma.jellyplay.core.datastore.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import com.raulshma.jellyplay.core.model.WidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises the widget cache sink: Library/Seerr widget item list round-trips
 * (including version + updated-at stamps), the continue-watching payload, and
 * the corrupt-JSON degrade — a malformed blob must fall back to the empty
 * placeholder, never throw into the AppWidget render path.
 */
class WidgetDataStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: WidgetDataStore

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = WidgetDataStore(dataStore, scope)
            // Drain the eagerly-collected snapshots so the cleared state is
            // observed before each test writes + reads.
            store.libraryWidgetItems.first()
            store.seerrWidgetItems.first()
            store.continueWatching.first()
        }
    }

    @Test
    fun `library widget items round-trip with version and updatedAt`() = runTest {
        val items = listOf(
            LibraryWidgetItem(
                itemId = "item-1",
                name = "Dune",
                mediaType = MediaType.MOVIE,
                year = 2021,
                communityRating = 8.0f,
                isFavorite = true,
                seedItemName = "Arrival",
            ),
            LibraryWidgetItem(
                itemId = "item-2",
                name = "Breaking Bad",
                mediaType = MediaType.SERIES,
                seriesName = "Breaking Bad",
                posterUrl = "http://img/poster.jpg",
            ),
        )

        store.setLibraryWidgetItems(items, version = 7, updatedAtMs = 1_720_000_000_000)

        assertEquals(items, store.libraryWidgetItems.first())
        assertEquals(7L, store.libraryWidgetVersion.first())
        assertEquals(1_720_000_000_000L, store.libraryWidgetUpdatedAtMs.first())
    }

    @Test
    fun `seerr widget items round-trip with version and updatedAt`() = runTest {
        val items = listOf(
            SeerrWidgetItem(
                tmdbId = 42,
                mediaType = "movie",
                title = "The Hitchhiker's Guide",
                subtitle = "Don't panic",
                year = 2005,
                voteAverage = 7.5f,
                overview = "A towel is the most important item.",
                posterUrl = "http://img/tmdb.jpg",
            ),
            SeerrWidgetItem(tmdbId = 99, mediaType = "tv", title = "Show"),
        )

        store.setSeerrWidgetItems(items, version = 3, updatedAtMs = 555L)

        assertEquals(items, store.seerrWidgetItems.first())
        assertEquals(3L, store.seerrWidgetVersion.first())
        assertEquals(555L, store.seerrWidgetUpdatedAtMs.first())
    }

    @Test
    fun `library widget items overwrite the previous payload`() = runTest {
        store.setLibraryWidgetItems(listOf(LibraryWidgetItem("a", "Old", MediaType.MOVIE)), 1, 1L)
        val next = listOf(LibraryWidgetItem("b", "New", MediaType.SERIES))

        store.setLibraryWidgetItems(next, 2, 2L)

        assertEquals(next, store.libraryWidgetItems.first())
        assertEquals(2L, store.libraryWidgetVersion.first())
    }

    @Test
    fun `continue watching round-trips`() = runTest {
        val items = listOf(
            MediaItem(id = "cw-1", name = "Episode 4", mediaType = MediaType.EPISODE, seriesName = "Show"),
        )

        store.setContinueWatching(items)

        assertEquals(items, store.continueWatching.first())
    }

    @Test
    fun `corrupt library blob degrades to empty list`() = runTest {
        // Seed a valid payload first so the corrupt write is a real transition,
        // then scribble garbage over the items key directly.
        store.setLibraryWidgetItems(listOf(LibraryWidgetItem("a", "Old", MediaType.MOVIE)), 1, 1L)
        dataStore.edit { it[stringPreferencesKey("library_widget_items")] = "}{not json" }

        assertEquals(emptyList(), store.libraryWidgetItems.first())
    }

    @Test
    fun `corrupt seerr blob degrades to empty list`() = runTest {
        store.setSeerrWidgetItems(listOf(SeerrWidgetItem(1, "movie", "Old")), 1, 1L)
        dataStore.edit { it[stringPreferencesKey("seerr_widget_items")] = "[{\"tmdbId\": }" }

        assertEquals(emptyList(), store.seerrWidgetItems.first())
    }

    @Test
    fun `corrupt continue-watching blob degrades to empty list`() = runTest {
        store.setContinueWatching(listOf(MediaItem("x", "Old", mediaType = MediaType.MOVIE)))
        dataStore.edit { it[stringPreferencesKey("continue_watching")] = "not-json-at-all" }

        assertEquals(emptyList(), store.continueWatching.first())
    }

    @Test
    fun `widget last refresh timestamp round-trips`() = runTest {
        assertEquals(0L, store.widgetLastRefreshMs.first())

        store.setWidgetLastRefreshMs(1_234_567_890L)

        assertEquals(1_234_567_890L, store.widgetLastRefreshMs.first())
    }

    // ── widget config (legacy global + per-widget) ───────────────────

    @Test
    fun `legacy widget config round-trips`() = runTest {
        val config = WidgetConfig(continueWatchingItemCount = 5)

        store.setWidgetConfig(config)

        assertEquals(config, store.widgetConfig.first())
    }

    @Test
    fun `corrupt legacy config blob degrades to the default config`() = runTest {
        store.setWidgetConfig(WidgetConfig(continueWatchingItemCount = 5))
        dataStore.edit { it[stringPreferencesKey("widget_config")] = "{broken" }

        assertEquals(WidgetConfig(), store.widgetConfig.first())
    }

    @Test
    fun `per-widget config round-trips and resolves by widget id`() = runTest {
        val configA = WidgetConfig(continueWatchingItemCount = 4)
        val configB = WidgetConfig(continueWatchingItemCount = 8)

        store.setWidgetConfigForId(1, configA)
        store.setWidgetConfigForId(2, configB)

        assertEquals(configA, store.getWidgetConfigForId(1).first())
        assertEquals(configB, store.getWidgetConfigForId(2).first())
    }

    @Test
    fun `per-widget lookup falls back to the legacy config when the id has none`() = runTest {
        val legacy = WidgetConfig(continueWatchingItemCount = 6)
        store.setWidgetConfig(legacy)
        store.setWidgetConfigForId(1, WidgetConfig(continueWatchingItemCount = 4))

        assertEquals(legacy, store.getWidgetConfigForId(2).first())
    }

    @Test
    fun `per-widget lookup with neither entry falls back to the default config`() = runTest {
        assertEquals(WidgetConfig(), store.getWidgetConfigForId(99).first())
    }

    @Test
    fun `corrupt per-widget blob falls back to the legacy config`() = runTest {
        val legacy = WidgetConfig(continueWatchingItemCount = 6)
        store.setWidgetConfig(legacy)
        store.setWidgetConfigForId(1, WidgetConfig(continueWatchingItemCount = 4))
        dataStore.edit { it[stringPreferencesKey("widget_configs")] = "not-json" }

        // Per-widget decode fails → null → legacy config is used.
        assertEquals(legacy, store.getWidgetConfigForId(1).first())
    }

    @Test
    fun `removeWidgetConfigForId drops only that widget's entry`() = runTest {
        val configA = WidgetConfig(continueWatchingItemCount = 4)
        val configB = WidgetConfig(continueWatchingItemCount = 8)
        store.setWidgetConfigForId(1, configA)
        store.setWidgetConfigForId(2, configB)

        store.removeWidgetConfigForId(1)

        assertEquals(WidgetConfig(), store.getWidgetConfigForId(1).first(), "falls back to default after removal")
        assertEquals(configB, store.getWidgetConfigForId(2).first())
    }

    @Test
    fun `getWidgetConfigForIdSync serves the per-widget config once the snapshot is warm`() = runTest {
        val configA = WidgetConfig(continueWatchingItemCount = 4)
        store.setWidgetConfigForId(1, configA)
        store.setWidgetConfig(WidgetConfig(continueWatchingItemCount = 6))

        // The eager snapshot StateFlow warms asynchronously on the store scope;
        // poll (bounded) until it has materialized, then the sync accessor must
        // serve per-widget[1].
        val deadline = System.currentTimeMillis() + 5_000
        var synced = store.getWidgetConfigForIdSync(1)
        while (synced != configA && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
            synced = store.getWidgetConfigForIdSync(1)
        }
        assertEquals(configA, synced)
    }

    @Test
    fun `getWidgetConfigForIdSync falls back to the legacy config for unknown ids`() = runTest {
        val legacy = WidgetConfig(continueWatchingItemCount = 6)
        store.setWidgetConfigForId(1, WidgetConfig(continueWatchingItemCount = 4))
        store.setWidgetConfig(legacy)

        val deadline = System.currentTimeMillis() + 5_000
        var synced = store.getWidgetConfigForIdSync(2)
        while (synced != legacy && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
            synced = store.getWidgetConfigForIdSync(2)
        }
        assertEquals(legacy, synced)
    }

    @Test
    fun `continueWatchingSnapshot returns the persisted payload on a cold store`() = runTest {
        val items = listOf(MediaItem("cw-cold", "Persisted", mediaType = MediaType.MOVIE))
        // Seed through one store instance, then observe through a FRESH store
        // whose eager snapshot has not warmed yet — the sync accessor must do
        // its one bounded disk read and render the persisted payload instead
        // of the empty placeholder.
        store.setContinueWatching(items)
        val coldStore = WidgetDataStore(dataStore, scope)

        assertEquals(items, coldStore.continueWatchingSnapshot())
    }
}
