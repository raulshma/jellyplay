package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.DownloadPolicyDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.SeriesDownloadPolicyEntity
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.AutoDownloadSuggestion
import com.raulshma.jellyplay.core.model.TimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the gh-181 policy/suggestion surface of [DownloadRepositoryImpl]: the
 * per-series override CRUD, the Ask-mode suggestion dedupe (only NEW ids are
 * upserted and counted), and the dead-state cleanup — a series whose last
 * download row is gone loses its override and its pending prompts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DownloadRepositoryImplPolicyTest {

    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val downloadPolicyDao: DownloadPolicyDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)
    private val detailFeed: DetailFeed = mockk(relaxed = true)
    private val preferencesStore: DownloadsStore = mockk(relaxed = true)

    private val fakeTimeSource = object : TimeSource {
        override fun nowEpochMillis(): Long = 1_700_000_000_123L
        override fun nowElapsedRealtimeMillis(): Long = 1_700_000_000_123L
        override fun today(zone: java.time.ZoneId): java.time.LocalDate = java.time.LocalDate.now(zone)
    }

    @Before
    fun setup() {
        every { database.downloadPolicyDao() } returns downloadPolicyDao
        every { preferencesStore.downloads } returns MutableStateFlow(DownloadsSlice())
    }

    private fun repository() = DownloadRepositoryImpl(
        downloadDao = downloadDao,
        offlineMediaDao = offlineMediaDao,
        playbackStateDao = playbackStateDao,
        syncBaselineDao = syncBaselineDao,
        database = database,
        mediaRepository = MediaRepositoryAccess { detailFeed },
        episodeCatalogue = mockk(relaxed = true),
        imageUrlProvider = mockk(relaxed = true),
        downloadsStore = preferencesStore,
        storagePolicy = mockk(relaxed = true),
        downloadEnqueuer = mockk(relaxed = true),
        timeSource = fakeTimeSource,
        writer = mockk(relaxed = true),
        downloadDelegate = mockk(relaxed = true),
    )

    private fun suggestion(episodeId: String, seriesId: String = "s1", seasonId: String = "season-1", number: Int = 1) =
        AutoDownloadSuggestion(
            episodeId = episodeId,
            seriesId = seriesId,
            seriesName = "Series",
            seasonId = seasonId,
            seasonName = "Season 1",
            seasonNumber = 1,
            episodeNumber = number,
            name = "Episode $number",
        )

    // ── Per-series policy ─────────────────────────────────────────────

    @Test
    fun `setSeriesPolicy upserts the mode name`() = runTest {
        repository().setSeriesPolicy("s1", AutoDownloadMode.ASK)

        coVerify(exactly = 1) {
            downloadPolicyDao.upsertPolicy(SeriesDownloadPolicyEntity(seriesId = "s1", mode = "ASK"))
        }
    }

    @Test
    fun `clearing a series policy also drops its stale suggestions`() = runTest {
        repository().setSeriesPolicy("s1", null)

        coVerify(exactly = 1) { downloadPolicyDao.deletePolicyBySeriesId("s1") }
        coVerify(exactly = 1) { downloadPolicyDao.deleteSuggestionsBySeriesId("s1") }
    }

    @Test
    fun `a NEVER override drops the series' stale suggestions too`() = runTest {
        repository().setSeriesPolicy("s1", AutoDownloadMode.NEVER)

        // The override row stays (it means NEVER, not inherit) but the
        // prompts recorded under a previous ASK/INSTANT state are dead.
        coVerify(exactly = 0) { downloadPolicyDao.deletePolicyBySeriesId("s1") }
        coVerify(exactly = 1) { downloadPolicyDao.deleteSuggestionsBySeriesId("s1") }
    }

    @Test
    fun `seriesPolicies maps rows and drops unparseable mode names`() = runTest {
        coEvery { downloadPolicyDao.getPoliciesBySeriesIds(listOf("s1", "s2")) } returns listOf(
            SeriesDownloadPolicyEntity("s1", "INSTANT"),
            SeriesDownloadPolicyEntity("s2", "BOGUS"),
        )

        val policies = repository().seriesPolicies(listOf("s1", "s2"))

        assertEquals(mapOf("s1" to AutoDownloadMode.INSTANT), policies)
    }

    // ── Ask-mode suggestions ──────────────────────────────────────────

    @Test
    fun `recording suggestions dedupes known ids and counts only fresh rows`() = runTest {
        coEvery { downloadPolicyDao.getKnownSuggestionIds(listOf("ep-1", "ep-2")) } returns listOf("ep-1")

        val recorded = repository().recordAutoDownloadSuggestions(
            listOf(suggestion("ep-1"), suggestion("ep-2", number = 2)),
        )

        assertEquals(1, recorded)
        coVerify(exactly = 1) {
            downloadPolicyDao.upsertSuggestions(match { rows -> rows.size == 1 && rows.single().episodeId == "ep-2" })
        }
    }

    @Test
    fun `recording an empty batch is a no-op`() = runTest {
        val recorded = repository().recordAutoDownloadSuggestions(emptyList())

        assertEquals(0, recorded)
        coVerify(exactly = 0) { downloadPolicyDao.getKnownSuggestionIds(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.upsertSuggestions(any()) }
    }

    @Test
    fun `dismissing writes the tombstone, not a delete`() = runTest {
        repository().dismissAutoDownloadSuggestions(listOf("ep-1", "ep-2"))

        // Tombstone semantics: the rows survive with dismissedAt set, so the
        // dedupe query keeps their ids and a later pass cannot re-ask.
        coVerify(exactly = 1) { downloadPolicyDao.dismissSuggestions(listOf("ep-1", "ep-2"), any()) }
    }

    // ── Dead-state cleanup (the delete/sweep paths' hook, tested directly —
    // the shared deletion choreography it rides is mock-hostile: real Room
    // transaction machinery over a mocked database) ──────────────────────

    @Test
    fun `a series with no downloads left loses its policy and suggestions`() = runTest {
        coEvery { downloadDao.getDownloadsForSeries("s1") } returns emptyList()

        repository().maybeClearSeriesAutoDownloadState("s1")

        coVerify(exactly = 1) { downloadPolicyDao.deletePolicyBySeriesId("s1") }
        coVerify(exactly = 1) { downloadPolicyDao.deleteSuggestionsBySeriesId("s1") }
    }

    @Test
    fun `a series with downloads remaining keeps its policy state`() = runTest {
        coEvery { downloadDao.getDownloadsForSeries("s1") } returns listOf(mockk(relaxed = true))

        repository().maybeClearSeriesAutoDownloadState("s1")

        coVerify(exactly = 0) { downloadPolicyDao.deletePolicyBySeriesId(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.deleteSuggestionsBySeriesId(any()) }
    }
}
