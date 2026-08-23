package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineLibraryViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var offlineRepository: OfflineRepository
    private lateinit var userDataMutator: UserDataMutator
    private lateinit var viewModel: OfflineLibraryViewModel

    /** Backing flow behind getOfflineLibrary so tests can push list changes. */
    private lateinit var libraryFlow: MutableStateFlow<List<OfflineMediaItem>>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        offlineRepository = mockk(relaxed = true)
        userDataMutator = mockk(relaxed = true)
        libraryFlow = MutableStateFlow(emptyList())
        every { offlineRepository.getOfflineLibrary() } returns libraryFlow
        viewModel = OfflineLibraryViewModel(offlineRepository, userDataMutator)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Subscribes [OfflineLibraryViewModel.offlineLibrary] (WhileSubscribed —
     * the pipeline stays cold until a collector exists) and settles. The filter
     * pipeline hops to Dispatchers.Default (real thread), so after the virtual
     * scheduler drains we give the hop a beat and drain again.
     */
    private fun TestScope.collectAndSettle(): kotlinx.coroutines.Job {
        val job = launch { viewModel.offlineLibrary.collect {} } // keep upstream hot
        settle()
        return job
    }

    private fun TestScope.settle() {
        advanceUntilIdle()
        // withContext(Dispatchers.Default) hop may resume after the virtual
        // queue drained; give the (tiny, in-memory) hop a few beats and drain
        // again — same idiom the FavoritesViewModelTest uses for its Default-
        // dispatcher projections.
        repeat(5) {
            Thread.sleep(20)
            advanceUntilIdle()
        }
    }

    // ── Filter tabs ───────────────────────────────────────────────────────

    @Test
    fun videos_tab_keeps_series_and_movies_only() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            offline("ser", mediaType = MediaType.SERIES),
            offline("mov", mediaType = MediaType.MOVIE),
            offline("ep", mediaType = MediaType.EPISODE),
            offline("aud", mediaType = MediaType.AUDIO),
        )
        val job = collectAndSettle()
        viewModel.setFilter(OfflineLibraryFilter.VIDEOS)
        settle()

        assertEquals(listOf("ser", "mov"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    @Test
    fun music_tab_keeps_audio_music_and_album() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            offline("aud", mediaType = MediaType.AUDIO),
            offline("mus", mediaType = MediaType.MUSIC),
            offline("alb", mediaType = MediaType.ALBUM),
            offline("mov", mediaType = MediaType.MOVIE),
        )
        val job = collectAndSettle()
        viewModel.setFilter(OfflineLibraryFilter.MUSIC)
        settle()

        assertEquals(listOf("aud", "mus", "alb"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    @Test
    fun all_tab_keeps_everything() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            offline("ser", mediaType = MediaType.SERIES),
            offline("aud", mediaType = MediaType.AUDIO),
        )
        val job = collectAndSettle()

        assertEquals(listOf("ser", "aud"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    // ── Search query ──────────────────────────────────────────────────────

    @Test
    fun query_under_two_chars_does_not_filter() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(offline("a", name = "Alpha"))
        val job = collectAndSettle()
        viewModel.setQuery("A")
        settle()

        assertEquals(listOf("a"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    @Test
    fun query_matches_name_series_and_season_case_insensitively() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            offline("n1", name = "Dune"),
            offline("n2", name = "Unrelated", seriesName = "DUNE Saga"),
            offline("n3", name = "Unrelated", seasonName = "dune season"),
            offline("n4", name = "Unrelated", seriesName = "Other"),
        )
        val job = collectAndSettle()
        viewModel.setQuery("dune")
        settle()

        assertEquals(listOf("n1", "n2", "n3"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    // ── Sort ──────────────────────────────────────────────────────────────

    @Test
    fun sort_recent_orders_by_created_at_descending() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            offline("old", createdAt = 100L),
            offline("new", createdAt = 300L),
            offline("mid", createdAt = 200L),
        )
        val job = collectAndSettle()
        viewModel.setSort(OfflineLibrarySort.RECENT)
        settle()

        assertEquals(listOf("new", "mid", "old"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    @Test
    fun sort_name_is_case_insensitive_ascending() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            offline("b", name = "banana"),
            offline("a", name = "Apple"),
            offline("c", name = "cherry"),
        )
        val job = collectAndSettle()
        viewModel.setSort(OfflineLibrarySort.NAME)
        settle()

        assertEquals(listOf("a", "b", "c"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    @Test
    fun sort_rating_descends_with_null_rated_last() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            offline("low", rating = 5.5f),
            offline("null", rating = null),
            offline("high", rating = 9.1f),
        )
        val job = collectAndSettle()
        viewModel.setSort(OfflineLibrarySort.RATING)
        settle()

        assertEquals(listOf("high", "low", "null"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    @Test
    fun sort_size_descends_by_total_bytes() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            offline("small", totalBytes = 10L),
            offline("big", totalBytes = 900L),
            offline("mid", totalBytes = 100L),
        )
        val job = collectAndSettle()
        viewModel.setSort(OfflineLibrarySort.SIZE)
        settle()

        assertEquals(listOf("big", "mid", "small"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    // ── Storage summary + loading ─────────────────────────────────────────

    @Test
    fun storage_summary_sums_bytes_and_counts_items() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            offline("a", totalBytes = 100L),
            offline("b", totalBytes = 250L),
        )
        val job = launch { viewModel.storageSummary.collect {} }
        advanceUntilIdle()

        val summary = viewModel.storageSummary.value
        assertEquals(350L, summary.totalBytes)
        assertEquals(2, summary.itemCount)
        job.cancel()
    }

    @Test
    fun first_emission_clears_isLoading() = runTest(mainDispatcher) {
        assertTrue(viewModel.isLoading)
        libraryFlow.value = listOf(offline("a"))
        val job = collectAndSettle()

        assertFalse(viewModel.isLoading)
        job.cancel()
    }

    // ── Quick-action routing ──────────────────────────────────────────────

    @Test
    fun markItemPlayed_routes_through_userDataMutator() = runTest(mainDispatcher) {
        viewModel.markItemPlayed(mediaItem("i1"), played = true)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            userDataMutator.setPlayed("i1", true, UserDataMutator.FlipMode.Silent, emptyList(), null)
        }

        viewModel.markItemPlayed(mediaItem("i1"), played = false)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            userDataMutator.setPlayed("i1", false, UserDataMutator.FlipMode.Silent, emptyList(), null)
        }
    }

    @Test
    fun toggleFavorite_routes_through_userDataMutator() = runTest(mainDispatcher) {
        viewModel.toggleFavorite(mediaItem("i1"))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            userDataMutator.setFavorite("i1", UserDataMutator.FlipMode.Silent, emptyList(), null)
        }
    }

    @Test
    fun delete_routes_series_to_series_delete_and_items_to_item_delete() = runTest(mainDispatcher) {
        viewModel.delete(mediaItem("s1", mediaType = MediaType.SERIES))
        advanceUntilIdle()
        coVerify(exactly = 1) { offlineRepository.deleteOfflineSeries("s1") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem(any()) }

        viewModel.delete(mediaItem("m1", mediaType = MediaType.MOVIE))
        advanceUntilIdle()
        coVerify(exactly = 1) { offlineRepository.deleteOfflineItem("m1") }
        coVerify(exactly = 1) { offlineRepository.deleteOfflineSeries(any()) }
    }

    private fun offline(
        id: String,
        name: String = "Item $id",
        mediaType: MediaType = MediaType.MOVIE,
        rating: Float? = null,
        totalBytes: Long = 0L,
        createdAt: Long = 0L,
        seriesName: String? = null,
        seasonName: String? = null,
    ) = OfflineMediaItem(
        id = id,
        name = name,
        mediaType = mediaType,
        communityRating = rating,
        totalSizeBytes = totalBytes,
        createdAt = createdAt,
        seriesName = seriesName,
        seasonName = seasonName,
    )

    private fun mediaItem(id: String, mediaType: MediaType = MediaType.MOVIE) = MediaItem(
        id = id,
        name = "Item $id",
        mediaType = mediaType,
    )
}
