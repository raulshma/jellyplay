package com.raulshma.jellyplay.widget

import android.content.Context
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.WidgetConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the library-recommendations widget worker's data contract:
 *
 *  - a failed/best-effort session restore or a missing server is NOT an
 *    error: the worker succeeds and leaves the last good widget snapshot
 *    untouched (no persist, no launcher notify).
 *  - an empty fetch keeps the existing snapshot too.
 *  - the FAVORITES source maps favorites (capped at MAX_ITEMS) into
 *    [LibraryWidgetItem]s, preferring the series id for poster lookup and
 *    blanking out poster urls that the repository could not build.
 *  - the SIMILAR_TO_RECENT source seeds from the first continue-watching
 *    item and only falls back when the similar fetch yields nothing usable.
 *  - a thrown error is retried, except permanent (401/403/404) failures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LibraryRecommendationsWidgetWorkerTest {

    private val context: Context = org.robolectric.RuntimeEnvironment.getApplication()
    private val workerParameters: WorkerParameters = mockk(relaxed = true)
    private val widgetDataStore: WidgetDataStore = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)

    private val currentServer = MutableStateFlow<ServerInfo?>(null)
    private val continueWatching = MutableStateFlow<List<MediaItem>>(emptyList())
    private val widgetConfig = MutableStateFlow(WidgetConfig())

    @Before
    fun setUp() {
        every { authRepository.currentServer } returns currentServer
        every { widgetDataStore.widgetConfig } returns widgetConfig
        every { widgetDataStore.continueWatching } returns continueWatching
        // The persist helper reads the current snapshot/version before writing.
        every { widgetDataStore.libraryWidgetItems } returns MutableStateFlow(emptyList())
        every { widgetDataStore.libraryWidgetVersion } returns MutableStateFlow(0L)
        coEvery { authRepository.restoreSession() } returns Result.success(Unit)
    }

    private fun createWorker() = LibraryRecommendationsWidgetWorker(
        appContext = context,
        params = workerParameters,
        widgetDataStore = widgetDataStore,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        authRepository = authRepository,
    )

    private fun mediaItem(
        id: String,
        name: String = "Item $id",
        seriesId: String? = null,
        isFavorite: Boolean = false,
    ) = MediaItem(
        id = id,
        name = name,
        mediaType = if (seriesId != null) MediaType.SERIES else MediaType.MOVIE,
        seriesId = seriesId,
        isFavorite = isFavorite,
    )

    private fun persistedItems() = slot<List<LibraryWidgetItem>>()

    private fun verifyPersistedOnce(): List<LibraryWidgetItem> {
        val items = persistedItems()
        coVerify(exactly = 1) {
            widgetDataStore.setLibraryWidgetItems(capture(items), any(), any())
        }
        return items.captured
    }

    @Test
    fun `no restored server succeeds without touching the persisted snapshot`() = runTest {
        currentServer.value = null

        val result = createWorker().doWork()

        assertTrue(result is WorkResult.Success)
        coVerify(exactly = 0) { widgetDataStore.setLibraryWidgetItems(any(), any(), any()) }
    }

    @Test
    fun `favorites source persists favorites capped at nine`() = runTest {
        currentServer.value = server()
        widgetConfig.value = WidgetConfig(librarySource = LibraryRecommendationsSource.FAVORITES)
        val favorites = (1..11).map { mediaItem("fav-$it", isFavorite = true) }
        coEvery {
            mediaRepository.getFavorites(any(), any(), any())
        } returns Result.success(
            SearchResult(items = favorites, totalRecordCount = favorites.size, startIndex = 0),
        )

        val result = createWorker().doWork()

        assertTrue(result is WorkResult.Success)
        val persisted = verifyPersistedOnce()
        assertEquals(9, persisted.size)
        assertEquals("fav-1", persisted.first().itemId)
        assertEquals("fav-9", persisted.last().itemId)
        assertTrue(persisted.first().isFavorite)
    }

    @Test
    fun `poster lookup prefers the series id and blanks unbuildable urls`() = runTest {
        currentServer.value = server()
        widgetConfig.value = WidgetConfig(librarySource = LibraryRecommendationsSource.FAVORITES)
        coEvery {
            mediaRepository.getFavorites(any(), any(), any())
        } returns Result.success(
            SearchResult(
                items = listOf(
                    mediaItem("ep-1", seriesId = "series-9"),
                    mediaItem("movie-1"),
                ),
                totalRecordCount = 2,
                startIndex = 0,
            ),
        )
        every { playbackRepository.getImageUrl(eq("series-9"), any(), any()) } returns "https://img/series-9"
        // Blank default for "movie-1" (relaxed mock) — must yield a null poster.

        createWorker().doWork()

        val persisted = verifyPersistedOnce()
        assertEquals("https://img/series-9", persisted[0].posterUrl)
        assertNull(persisted[1].posterUrl)
        coVerify(exactly = 1) { playbackRepository.getImageUrl(eq("series-9"), any(), any()) }
        coVerify(exactly = 1) { playbackRepository.getImageUrl(eq("movie-1"), any(), any()) }
    }

    @Test
    fun `empty fetch keeps the existing snapshot`() = runTest {
        currentServer.value = server()
        widgetConfig.value = WidgetConfig(librarySource = LibraryRecommendationsSource.FAVORITES)
        coEvery {
            mediaRepository.getFavorites(any(), any(), any())
        } returns Result.success(SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0))

        val result = createWorker().doWork()

        assertTrue(result is WorkResult.Success)
        coVerify(exactly = 0) { widgetDataStore.setLibraryWidgetItems(any(), any(), any()) }
    }

    @Test
    fun `similar-to-recent seeds from the first continue-watching item`() = runTest {
        currentServer.value = server()
        widgetConfig.value = WidgetConfig(librarySource = LibraryRecommendationsSource.SIMILAR_TO_RECENT)
        continueWatching.value = listOf(mediaItem("seed-1"))
        coEvery {
            mediaRepository.getSimilarItems(eq("seed-1"), any())
        } returns Result.success(listOf(mediaItem("similar-1"), mediaItem("similar-2")))

        val result = createWorker().doWork()

        assertTrue(result is WorkResult.Success)
        val persisted = verifyPersistedOnce()
        assertEquals(listOf("similar-1", "similar-2"), persisted.map { it.itemId })
    }

    @Test
    fun `a generic fetch error is retried`() = runTest {
        currentServer.value = server()
        widgetConfig.value = WidgetConfig(librarySource = LibraryRecommendationsSource.FAVORITES)
        coEvery {
            mediaRepository.getFavorites(any(), any(), any())
        } throws RuntimeException("connection reset")

        val result = createWorker().doWork()

        assertTrue(result is WorkResult.Retry)
    }

    @Test
    fun `a permanent 401 error is not retried`() = runTest {
        currentServer.value = server()
        widgetConfig.value = WidgetConfig(librarySource = LibraryRecommendationsSource.FAVORITES)
        coEvery {
            mediaRepository.getFavorites(any(), any(), any())
        } throws RuntimeException("HTTP 401 Unauthorized")

        val result = createWorker().doWork()

        assertTrue(result is WorkResult.Failure)
    }

    @Test
    fun `restoreSession failure does not fail the worker`() = runTest {
        currentServer.value = server()
        widgetConfig.value = WidgetConfig(librarySource = LibraryRecommendationsSource.FAVORITES)
        coEvery { authRepository.restoreSession() } returns Result.failure(RuntimeException("db locked"))
        coEvery {
            mediaRepository.getFavorites(any(), any(), any())
        } returns Result.failure(RuntimeException("db locked"))

        val result = createWorker().doWork()

        // The failed favorites fetch falls through the getOrDefault empty
        // path, which deliberately keeps the last good widget snapshot.
        assertTrue(result is WorkResult.Success)
        coVerify(exactly = 0) { widgetDataStore.setLibraryWidgetItems(any(), any(), any()) }
    }

    private fun server() = ServerInfo(
        id = "server-1",
        name = "Home",
        address = "https://server.local",
    )
}
