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
}
