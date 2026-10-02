package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.SeriesEpisodeDownloads
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Wiring invariants for the [HomeSheetsFactory] construction seam — the same
 * "adds no behavioural seam" contract [HomeRefresherFactoryTest] pins for its
 * twin: [create] must wire the factory-owned collaborators into BOTH built
 * holders. One behaviour per holder proves the wiring (the holders' own
 * invariants are pinned by their dedicated suites):
 *  - a series-download request driven through the factory-built holder lands
 *    on the factory's [EpisodeCatalogue]/[SeriesEpisodeDownloads] and its
 *    snapshot reaches the holder's state;
 *  - a series-delete request driven through the factory-built holder lands on
 *    the factory's [OfflineRepository] and its slices reach the state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeSheetsFactoryTest {

    private lateinit var episodeCatalogue: EpisodeCatalogue
    private lateinit var seriesDownloads: SeriesEpisodeDownloads
    private lateinit var downloadIntake: DownloadIntake
    private lateinit var userMessageBus: UserMessageBus
    private lateinit var offlineRepository: OfflineRepository
    private var sheetsScope: CoroutineScope? = null

    @BeforeTest
    fun setUp() {
        episodeCatalogue = mockk(relaxed = true)
        seriesDownloads = mockk(relaxed = true)
        downloadIntake = mockk(relaxed = true)
        userMessageBus = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
    }

    @AfterTest
    fun stopSheets() {
        sheetsScope?.cancel()
    }

    private fun TestScope.createSheets(): HomeSheets {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        sheetsScope = scope
        return HomeSheetsFactory(
            episodeCatalogue = episodeCatalogue,
            seriesDownloads = seriesDownloads,
            downloadIntake = downloadIntake,
            userMessageBus = userMessageBus,
            offlineRepository = offlineRepository,
        ).create(scope)
    }

    private fun series(id: String = "s1") = MediaItem(id = id, name = "Series", mediaType = MediaType.SERIES)

    @Test
    fun create_wiresCatalogueAndDownloadReads_intoTheDownloadHolder() = runTest {
        val snapshot = EpisodeCatalogueSnapshot(
            seriesId = "s1",
            seasons = listOf(MediaItem(id = "season1", name = "S1", mediaType = MediaType.SEASON)),
            episodesBySeason = mapOf("season1" to listOf(MediaItem(id = "e1", name = "E1", mediaType = MediaType.EPISODE))),
            fetchedSeasonIds = setOf("season1"),
            sortedEpisodes = listOf(MediaItem(id = "e1", name = "E1", mediaType = MediaType.EPISODE)),
            epoch = 1L,
        )
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1") } returns Result.success(snapshot)
        coEvery { seriesDownloads.getDownloadedEpisodeIdsForSeries("s1") } returns setOf("e1")
        val sheets = createSheets()

        sheets.seriesDownload.requestSeriesDownload(series())
        runCurrent()

        val state = sheets.seriesDownload.state.value!!
        assertEquals("s1", state.seriesId)
        assertEquals(setOf("e1"), state.downloadedEpisodeIds)
        assertEquals(listOf("season1"), state.seasons.map { it.id })
    }

    @Test
    fun create_wiresOfflineRepository_intoTheDeleteHolder() = runTest {
        every { offlineRepository.getSeasonsForSeries("s1") } returns flowOf(
            listOf(OfflineMediaItem(id = "season1", name = "S1", mediaType = MediaType.SEASON)),
        )
        coEvery { offlineRepository.getEpisodesForSeries("s1") } returns listOf(
            OfflineMediaItem(
                id = "e1",
                name = "E1",
                mediaType = MediaType.EPISODE,
                seasonId = "season1",
                totalSizeBytes = 10L,
            ),
        )
        val sheets = createSheets()

        sheets.seriesDelete.requestSeriesDelete(series())
        runCurrent()

        val state = sheets.seriesDelete.state.value!!
        assertEquals("s1", state.seriesId)
        assertEquals(listOf("season1"), state.seasons.map { it.id })
        assertEquals(mapOf("e1" to 10L), state.episodeSizeBytes)
    }
}
