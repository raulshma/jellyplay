package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.model.arr.ArrSeriesResolution
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManageSeriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var mediaRepository: MediaRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var viewModel: ManageSeriesViewModel

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        arrRepository = mockk(relaxed = true)
        // The relaxed context mock returns "" for getString, which empties
        // every localized error. Reconstruct the canonical form here.
        every { context.getString(R.string.detail_manage_series_load_error) } returns "Couldn't load series from Jellyfin."
        viewModel = ManageSeriesViewModel(context, mediaRepository, arrRepository)
    }

    private fun ep(
        id: Int,
        season: Int = 1,
        episode: Int = 1,
        title: String = "Ep $id",
        hasFile: Boolean = false,
        monitored: Boolean = true,
        episodeFileId: Int = 0,
        fileSizeBytes: Long? = null,
        absoluteEpisodeNumber: Int? = null,
    ) = ArrSeriesEpisode(
        id = id,
        seasonNumber = season,
        episodeNumber = episode,
        absoluteEpisodeNumber = absoluteEpisodeNumber,
        title = title,
        hasFile = hasFile,
        monitored = monitored,
        episodeFileId = episodeFileId,
        fileSizeBytes = fileSizeBytes,
    )

    private fun stubSeriesDetail(seriesId: String, providerIds: Map<String, String> = mapOf("tvdb" to "123")) {
        coEvery { mediaRepository.getMediaDetail(seriesId) } returns Result.success(
            MediaDetail(
                item = MediaItem(id = seriesId, name = "Show", mediaType = MediaType.SERIES),
                providerIds = providerIds,
            ),
        )
        coEvery { arrRepository.resolveSonarrSeries(123) } returns Result.success(
            ArrSeriesResolution(serverId = "sonarr-1", seriesId = 55, title = "Show", monitored = true),
        )
    }

    // ── load() happy path ───────────────────────────────────────────────

    @Test
    fun `load resolves tvdb then Sonarr then episodes`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(ep(1, hasFile = true), ep(2, hasFile = false))
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)

        viewModel.load("s1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals("Show", state.series?.title)
        assertEquals(eps, state.episodesBySeason[1])
    }

    @Test
    fun `load without tvdb id surfaces error`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1", providerIds = emptyMap())

        viewModel.load("s1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNotNull(state.error)
        assertEquals(false, state.episodesBySeason.containsKey(1))
    }

    @Test
    fun `load detail failure surfaces error`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("s1") } returns Result.failure(RuntimeException("boom"))

        viewModel.load("s1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("Couldn't load series from Jellyfin.", state.error)
    }

    @Test
    fun `load Sonarr resolution failure surfaces error`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        coEvery { arrRepository.resolveSonarrSeries(123) } returns Result.failure(RuntimeException("no sonarr"))

        viewModel.load("s1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNotNull(state.error)
    }

    @Test
    fun `load episodes failure surfaces error and clears loading`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.failure(RuntimeException("eps boom"))

        viewModel.load("s1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("eps boom", state.error)
    }

    @Test
    fun `load dedupes concurrent reload for same series`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(listOf(ep(1)))

        viewModel.load("s1")
        viewModel.load("s1") // deduped while first is in flight
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("s1") }
    }

    // ── season grouping & default expansion ─────────────────────────────

    @Test
    fun `seasons grouped with specials sorted last`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        // Episodes across seasons 0 (specials), 1, 2 — deliberately out of order.
        val eps = listOf(
            ep(10, season = 2, episode = 1),
            ep(1, season = 0, episode = 1),
            ep(5, season = 1, episode = 1),
        )
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)

        viewModel.load("s1")
        advanceUntilIdle()

        // Season keys sorted 1, 2, 0 (specials last).
        assertEquals(listOf(1, 2, 0), viewModel.uiState.value.episodesBySeason.keys.toList())
    }

    @Test
    fun `default expanded season is first non-specials season with missing monitored episodes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            stubSeriesDetail("s1")
            // Season 1 fully downloaded; season 2 has a missing monitored episode.
            val eps = listOf(
                ep(1, season = 1, hasFile = true, monitored = true),
                ep(2, season = 2, hasFile = false, monitored = true),
            )
            coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)

            viewModel.load("s1")
            advanceUntilIdle()

            assertEquals(setOf(2), viewModel.uiState.value.expandedSeasons)
        }

    @Test
    fun `default expanded season falls back to first non-specials season when none missing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            stubSeriesDetail("s1")
            val eps = listOf(
                ep(1, season = 1, hasFile = true),
                ep(2, season = 2, hasFile = true),
                ep(3, season = 0, hasFile = false, monitored = true),
            )
            coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)

            viewModel.load("s1")
            advanceUntilIdle()

            assertEquals(setOf(1), viewModel.uiState.value.expandedSeasons)
        }

    // ── toggleEpisodeMonitored ──────────────────────────────────────────

    @Test
    fun `toggleEpisodeMonitored optimistically flips then refreshes`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(ep(1, monitored = false))
        coEvery { arrRepository.getSonarrEpisodes(123) } returnsMany listOf(
            Result.success(eps),
            Result.success(listOf(ep(1, monitored = true))),
        )
        coEvery { arrRepository.monitorSonarrEpisodes(123, listOf(1), true) } returns Result.success(Unit)

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.toggleEpisodeMonitored(eps.first())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.episodesBySeason[1]!!.first().monitored)
        coVerify(exactly = 1) { arrRepository.monitorSonarrEpisodes(123, listOf(1), true) }
    }

    @Test
    fun `toggleEpisodeMonitored failure reverts optimistic flip`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(ep(1, monitored = false))
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)
        coEvery { arrRepository.monitorSonarrEpisodes(123, listOf(1), true) } returns Result.failure(RuntimeException("boom"))

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.toggleEpisodeMonitored(eps.first())
        advanceUntilIdle()

        // Reverted back to the original monitored=false.
        assertFalse(viewModel.uiState.value.episodesBySeason[1]!!.first().monitored)
        assertNotNull(viewModel.uiState.value.userMessage)
    }

    // ── searchEpisode ───────────────────────────────────────────────────

    @Test
    fun `searchEpisode success sets searching message and clears actionTarget`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(ep(1, title = "Pilot"))
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)
        coEvery { arrRepository.searchSonarrEpisodes(123, listOf(1)) } returns Result.success(Unit)

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.searchEpisode(eps.first())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.actionTarget)
        assertTrue(state.userMessage!!.contains("Pilot"))
        coVerify(exactly = 1) { arrRepository.searchSonarrEpisodes(123, listOf(1)) }
    }

    @Test
    fun `searchEpisode failure sets error message`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(ep(1, title = "Pilot"))
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)
        coEvery { arrRepository.searchSonarrEpisodes(123, listOf(1)) } returns Result.failure(RuntimeException("nope"))

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.searchEpisode(eps.first())
        advanceUntilIdle()

        assertEquals("nope", viewModel.uiState.value.userMessage)
    }

    // ── delete flow ─────────────────────────────────────────────────────

    @Test
    fun `requestDeleteEpisode stages pending episode`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(ep(1, hasFile = true, episodeFileId = 99))
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.requestDeleteEpisode(eps.first())
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.pendingDeleteEpisode?.id)
    }

    @Test
    fun `cancelDeleteEpisode clears pending`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(ep(1, hasFile = true, episodeFileId = 99))
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.requestDeleteEpisode(eps.first())
        viewModel.cancelDeleteEpisode()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingDeleteEpisode)
    }

    @Test
    fun `confirmDeleteEpisode deletes file then refreshes`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(ep(1, hasFile = true, episodeFileId = 99, title = "Pilot"))
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)
        coEvery { arrRepository.deleteSonarrEpisodeFile(123, 99) } returns Result.success(Unit)

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.requestDeleteEpisode(eps.first())
        viewModel.confirmDeleteEpisode()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingDeleteEpisode)
        assertNull(viewModel.uiState.value.actionTarget)
        assertTrue(viewModel.uiState.value.userMessage!!.contains("Pilot"))
        coVerify(exactly = 1) { arrRepository.deleteSonarrEpisodeFile(123, 99) }
    }

    @Test
    fun `confirmDeleteEpisode failure sets error message`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(ep(1, hasFile = true, episodeFileId = 99))
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)
        coEvery { arrRepository.deleteSonarrEpisodeFile(123, 99) } returns Result.failure(RuntimeException("denied"))

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.requestDeleteEpisode(eps.first())
        viewModel.confirmDeleteEpisode()
        advanceUntilIdle()

        assertEquals("denied", viewModel.uiState.value.userMessage)
    }

    // ── season-level actions ────────────────────────────────────────────

    @Test
    fun `toggleSeasonMonitor monitors all when any unmonitored`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(ep(1, monitored = false), ep(2, monitored = true))
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)
        coEvery { arrRepository.monitorSonarrEpisodes(123, listOf(1, 2), true) } returns Result.success(Unit)

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.toggleSeasonMonitor(1)
        advanceUntilIdle()

        coVerify(exactly = 1) { arrRepository.monitorSonarrEpisodes(123, listOf(1, 2), true) }
    }

    @Test
    fun `toggleSeasonMonitor unmonitors all when all monitored`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(ep(1, monitored = true), ep(2, monitored = true))
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)
        coEvery { arrRepository.monitorSonarrEpisodes(123, listOf(1, 2), false) } returns Result.success(Unit)

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.toggleSeasonMonitor(1)
        advanceUntilIdle()

        coVerify(exactly = 1) { arrRepository.monitorSonarrEpisodes(123, listOf(1, 2), false) }
    }

    @Test
    fun `toggleSeasonMonitor on empty season is no-op`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(emptyList())

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.toggleSeasonMonitor(1)
        advanceUntilIdle()

        coVerify(exactly = 0) { arrRepository.monitorSonarrEpisodes(any(), any(), any()) }
    }

    @Test
    fun `searchSeason success sets searching message`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(listOf(ep(1)))
        coEvery { arrRepository.searchMonitoredSonarrSeason(123, 1) } returns Result.success(Unit)

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.searchSeason(1)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.actionTarget)
        assertTrue(viewModel.uiState.value.userMessage!!.contains("season 1"))
        coVerify(exactly = 1) { arrRepository.searchMonitoredSonarrSeason(123, 1) }
    }

    // ── series-level actions ────────────────────────────────────────────

    @Test
    fun `refreshSeries success clears actionTarget and sets message`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(listOf(ep(1)))
        coEvery { arrRepository.refreshSonarrSeries(123) } returns Result.success(Unit)

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.refreshSeries()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.actionTarget)
        coVerify(exactly = 1) { arrRepository.refreshSonarrSeries(123) }
    }

    @Test
    fun `refreshAndScan fires both refresh and rescan`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(listOf(ep(1)))
        coEvery { arrRepository.refreshSonarrSeries(123) } returns Result.success(Unit)
        coEvery { arrRepository.rescanSonarrSeries(123) } returns Result.success(Unit)

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.refreshAndScan()
        advanceUntilIdle()

        coVerify(exactly = 1) { arrRepository.refreshSonarrSeries(123) }
        coVerify(exactly = 1) { arrRepository.rescanSonarrSeries(123) }
        assertNull(viewModel.uiState.value.actionTarget)
    }

    @Test
    fun `searchSeries success sets message`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(listOf(ep(1)))
        coEvery { arrRepository.searchSonarrSeries(123) } returns Result.success(Unit)

        viewModel.load("s1")
        advanceUntilIdle()

        viewModel.searchSeries()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.userMessage)
        coVerify(exactly = 1) { arrRepository.searchSonarrSeries(123) }
    }

    @Test
    fun `series actions before load complete are no-ops`() = runTest(mainDispatcherRule.testDispatcher) {
        // Without load(), tvdbId is null — every action short-circuits.
        viewModel.refreshSeries()
        viewModel.refreshAndScan()
        viewModel.searchSeries()
        viewModel.toggleSeasonMonitor(1)
        advanceUntilIdle()

        coVerify(exactly = 0) { arrRepository.refreshSonarrSeries(any()) }
        coVerify(exactly = 0) { arrRepository.searchSonarrSeries(any()) }
    }

    // ── refresh() ───────────────────────────────────────────────────────

    @Test
    fun `refresh before load is no-op`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 0) { arrRepository.getSonarrEpisodes(any()) }
    }

    // ── expand toggles & message/error clearing ─────────────────────────

    @Test
    fun `toggleSeasonExpanded adds then removes season`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.toggleSeasonExpanded(2)
        assertTrue(viewModel.uiState.value.expandedSeasons.contains(2))

        viewModel.toggleSeasonExpanded(2)
        assertFalse(viewModel.uiState.value.expandedSeasons.contains(2))
    }

    @Test
    fun `clearUserMessage nulls the message`() {
        viewModel.toggleSeasonExpanded(1) // ensure state is live
        viewModel.clearUserMessage()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun `clearError nulls the error`() {
        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    // ── ManageSeriesUiState derived helpers ─────────────────────────────

    @Test
    fun `seasonStats computes total downloaded and monitored counts`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(
            ep(1, hasFile = true, monitored = true),
            ep(2, hasFile = false, monitored = true),
            ep(3, hasFile = true, monitored = false),
        )
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)

        viewModel.load("s1")
        advanceUntilIdle()

        val stats = viewModel.uiState.value.seasonStats(1)
        assertEquals(3, stats.total)
        assertEquals(2, stats.downloaded)
        assertEquals(2, stats.monitored)
    }

    @Test
    fun `totalStorageBytes sums downloaded episode sizes across seasons`() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubSeriesDetail("s1")
        val eps = listOf(
            ep(1, season = 1, hasFile = true, episodeFileId = 10, fileSizeBytes = 1_000L),
            ep(2, season = 2, hasFile = true, episodeFileId = 20, fileSizeBytes = 2_500L),
            ep(3, season = 2, hasFile = false), // not downloaded → no file size
        )
        coEvery { arrRepository.getSonarrEpisodes(123) } returns Result.success(eps)

        viewModel.load("s1")
        advanceUntilIdle()

        assertEquals(3_500L, viewModel.uiState.value.totalStorageBytes)
    }
}
