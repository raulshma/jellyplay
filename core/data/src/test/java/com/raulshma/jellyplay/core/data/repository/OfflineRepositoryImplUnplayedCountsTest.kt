package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaWithPlayback
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.dao.UnplayedCountRow
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.OFFLINE_WATCHED_THRESHOLD
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the unwatched-count wiring in [OfflineRepositoryImpl]: the per-series
 * `IN (...)` lookup is chunked below SQLite's host-variable cap (a whole
 * downloaded library of series exceeds 999 variables), and the threshold
 * handed to the DAO is the model's [OFFLINE_WATCHED_THRESHOLD] on the stored
 * 0–100 percent scale — the same fact `isFinishedOffline` compares against,
 * so the badge SQL and the display normalization can't drift apart.
 */
class OfflineRepositoryImplUnplayedCountsTest {

    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)

    private lateinit var repository: OfflineRepositoryImpl

    @Before
    fun setup() {
        repository = OfflineRepositoryImpl(offlineMediaDao, playbackStateDao, syncBaselineDao, downloadDao, database)
    }

    private fun seriesRow(id: String) = OfflineMediaWithPlayback(
        media = OfflineMediaEntity(id = id, name = id, mediaType = MediaType.SERIES.name),
        playbackPositionTicks = null,
        playedPercentage = null,
        isPlayed = null,
        isFavorite = null,
        lastPlayedDate = null,
    )

    @Test
    fun `per-series unplayed counts chunk past the SQLite host-variable cap`() = runTest {
        val seriesIds = (0 until 901).map { "series-$it" }
        coEvery { offlineMediaDao.getTopLevelItems() } returns flowOf(seriesIds.map(::seriesRow))
        coEvery { downloadDao.getSeriesSizeAggregatesFlow(any()) } returns flowOf(emptyList())
        val capturedChunks = mutableListOf<List<String>>()
        coEvery { offlineMediaDao.getUnplayedEpisodeCountsBySeriesFlow(any(), any()) } answers {
            val chunk: List<String> = firstArg()
            capturedChunks.add(chunk)
            flowOf(chunk.map { UnplayedCountRow(groupedId = it, unplayedCount = 1) })
        }

        val items = repository.getOfflineLibrary().first()

        assertEquals(901, items.size)
        assertTrue(items.all { it.unplayedEpisodeCount == 1 })
        // 901 ids → one full chunk plus the remainder, merged into one map.
        assertEquals(listOf(900, 1), capturedChunks.map { it.size })
    }

    @Test
    fun `counts query receives the model threshold on the percent scale`() = runTest {
        coEvery { offlineMediaDao.getTopLevelItems() } returns flowOf(listOf(seriesRow("series-1")))
        coEvery { downloadDao.getSeriesSizeAggregatesFlow(any()) } returns flowOf(emptyList())
        val thresholds = mutableListOf<Double>()
        coEvery { offlineMediaDao.getUnplayedEpisodeCountsBySeriesFlow(any(), any()) } answers {
            thresholds.add(secondArg())
            flowOf(emptyList())
        }

        repository.getOfflineLibrary().first()

        // Exactly one derivation, owned by core/model — not re-derived here.
        assertEquals(listOf(OFFLINE_WATCHED_THRESHOLD * 100), thresholds)
    }
}
