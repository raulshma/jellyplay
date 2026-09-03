package com.raulshma.jellyplay.widget

import android.content.Context
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import com.raulshma.jellyplay.core.model.WidgetConfig
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the seerr-recommendations widget worker's data contract:
 *
 *  - no configured Seerr server leaves the cached snapshot intact (success,
 *    no fetch, no persist) so the widget never blanks during the window
 *    before the user configures a server.
 *  - the TRENDING source feeds the widget; entries without a poster are
 *    dropped before the persist, and the result is capped at nine.
 *  - discovery sources route by [SeerrWidgetSource]; the upcoming variants
 *    gate on today's ISO date for their release-window filter.
 *  - items map into [com.raulshma.jellyplay.core.model.SeerrWidgetItem]s
 *    with the TMDB CDN urls built from the poster/backdrop paths.
 *  - a thrown error is retried, except permanent (401/403/404) failures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SeerrRecommendationsWidgetWorkerTest {

    private val context: Context = org.robolectric.RuntimeEnvironment.getApplication()
    private val workerParameters: WorkerParameters = mockk(relaxed = true)
    private val widgetDataStore: WidgetDataStore = mockk(relaxed = true)
    private val seerrPreferencesStore: SeerrPreferencesStore = mockk(relaxed = true)
    private val seerrRepository: SeerrRepository = mockk(relaxed = true)

    private val seerrPreferences = MutableStateFlow(SeerrPreferences())
    private val widgetConfig = MutableStateFlow(WidgetConfig())

    @Before
    fun setUp() {
        every { seerrPreferencesStore.preferences } returns seerrPreferences
        every { widgetDataStore.widgetConfig } returns widgetConfig
        // The persist helper reads the current snapshot/version before writing.
        every { widgetDataStore.seerrWidgetItems } returns MutableStateFlow(emptyList())
        every { widgetDataStore.seerrWidgetVersion } returns MutableStateFlow(0L)
    }

    private fun createWorker() = SeerrRecommendationsWidgetWorker(
        appContext = context,
        params = workerParameters,
        widgetDataStore = widgetDataStore,
        seerrPreferencesStore = seerrPreferencesStore,
        seerrRepository = seerrRepository,
    )

    private fun searchItem(id: Int, mediaType: String = "movie", posterPath: String? = "/p$id.jpg") =
        SeerrSearchItem(
            id = id,
            mediaType = mediaType,
            title = "Title $id",
            releaseDate = "2024-05-01",
            posterPath = posterPath,
            backdropPath = "/b$id.jpg",
            voteAverage = 8.3f,
        )

    private fun configureSeerr(serverUrl: String = "https://seerr.local", region: String = "DE") {
        seerrPreferences.value = SeerrPreferences(serverUrl = serverUrl, discoverRegion = region)
    }

    @Test
    fun `no configured server succeeds without fetching or persisting`() = runTest {
        configureSeerr(serverUrl = "")

        val result = createWorker().doWork()

        assertTrue(result is WorkResult.Success)
        coVerify(exactly = 0) { seerrRepository.getTrending(any()) }
        coVerify(exactly = 0) { widgetDataStore.setSeerrWidgetItems(any(), any(), any()) }
    }

    @Test
    fun `trending source persists poster-bearing items capped at nine`() = runTest {
        configureSeerr()
        val noPoster = searchItem(100, posterPath = null)
        coEvery { seerrRepository.getTrending(any()) } returns Result.success(
            SeerrSearchResponse(results = (1..11).map { searchItem(it) } + noPoster),
        )

        val result = createWorker().doWork()

        assertTrue(result is WorkResult.Success)
        val items = slot<List<com.raulshma.jellyplay.core.model.SeerrWidgetItem>>()
        coVerify(exactly = 1) { widgetDataStore.setSeerrWidgetItems(capture(items), any(), any()) }
        val persisted = items.captured
        assertEquals(9, persisted.size)
        assertEquals(1, persisted.first().tmdbId)
        assertEquals(9, persisted.last().tmdbId)
        // The poster-less 11th item is dropped, not carried.
        assertTrue(persisted.none { it.tmdbId == 100 })
    }

    @Test
    fun `items map titles subtitles and built TMDB urls`() = runTest {
        configureSeerr()
        coEvery { seerrRepository.getTrending(any()) } returns Result.success(
            SeerrSearchResponse(
                results = listOf(
                    searchItem(1),
                    searchItem(2, mediaType = "tv"),
                    // No poster path → dropped by the worker's poster filter.
                    SeerrSearchItem(id = 3, mediaType = "movie", title = null, name = "Named"),
                ),
            ),
        )

        createWorker().doWork()

        val items = slot<List<com.raulshma.jellyplay.core.model.SeerrWidgetItem>>()
        coVerify(exactly = 1) { widgetDataStore.setSeerrWidgetItems(capture(items), any(), any()) }
        assertEquals(2, items.captured.size)
        val movie = items.captured[0]
        assertEquals("Title 1", movie.title)
        assertEquals("Movie", movie.subtitle)
        assertEquals(2024, movie.year)
        assertEquals("https://image.tmdb.org/t/p/w500/p1.jpg", movie.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/b1.jpg", movie.backdropUrl)
        val tv = items.captured[1]
        assertEquals("TV Series", tv.subtitle)
    }

    @Test
    fun `discover sources route by config`() = runTest {
        configureSeerr()
        widgetConfig.value = WidgetConfig(seerrSource = SeerrWidgetSource.POPULAR_TV)
        coEvery { seerrRepository.getDiscoverTv(any(), any()) } returns Result.success(
            SeerrSearchResponse(results = listOf(searchItem(7, mediaType = "tv"))),
        )

        val result = createWorker().doWork()

        assertTrue(result is WorkResult.Success)
        coVerify(exactly = 1) { seerrRepository.getDiscoverTv(page = 1, firstAirDateGte = null) }
        coVerify(exactly = 0) { seerrRepository.getTrending(any()) }
    }

    @Test
    fun `upcoming tv gates on today's ISO date`() = runTest {
        configureSeerr()
        widgetConfig.value = WidgetConfig(seerrSource = SeerrWidgetSource.UPCOMING_TV)
        coEvery { seerrRepository.getDiscoverTv(any(), any()) } returns Result.success(
            SeerrSearchResponse(results = listOf(searchItem(8, mediaType = "tv"))),
        )

        createWorker().doWork()

        coVerify {
            seerrRepository.getDiscoverTv(page = eq(1), firstAirDateGte = match { date ->
                Regex("""\d{4}-\d{2}-\d{2}""").matches(date)
            })
        }
    }

    @Test
    fun `an empty fetch keeps the existing snapshot`() = runTest {
        configureSeerr()
        coEvery { seerrRepository.getTrending(any()) } returns Result.success(SeerrSearchResponse())

        val result = createWorker().doWork()

        assertTrue(result is WorkResult.Success)
        coVerify(exactly = 0) { widgetDataStore.setSeerrWidgetItems(any(), any(), any()) }
    }

    @Test
    fun `a generic fetch error is retried and a permanent one is not`() = runTest {
        configureSeerr()
        coEvery { seerrRepository.getTrending(any()) } throws RuntimeException("connection reset")

        assertTrue(createWorker().doWork() is WorkResult.Retry)

        coEvery { seerrRepository.getTrending(any()) } throws RuntimeException("403 Forbidden")

        assertTrue(createWorker().doWork() is WorkResult.Failure)
    }

    @Test
    fun `a failed fetch falls back to the cached snapshot`() = runTest {
        configureSeerr()
        coEvery { seerrRepository.getTrending(any()) } returns Result.failure(RuntimeException("offline"))

        val result = createWorker().doWork()

        assertTrue(result is WorkResult.Success)
        coVerify(exactly = 0) { widgetDataStore.setSeerrWidgetItems(any(), any(), any()) }
    }
}
