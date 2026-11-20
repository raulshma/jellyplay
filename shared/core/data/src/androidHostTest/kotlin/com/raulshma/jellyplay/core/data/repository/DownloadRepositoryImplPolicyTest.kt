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
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.TimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the gh-181 policy/suggestion surface of [DownloadRepositoryImpl] plus
 * its ADR-0015 extension: the per-series arm CRUD, the Ask-mode suggestion
 * dedupe (only NEW ids are upserted and counted), the zero-rows un-arm rule
 * and its watch-boundary verb split ([DownloadRepository.deleteDownload]'s
 * keepSeriesArm), the armed-series reads the check's universe union and
 * the Downloads screen's armed list consume, the arm verbs
 * ([SeriesDownloadPolicyStore.armSeries] and the manual entries'
 * [SeriesDownloadPolicyStore.manualArmSeries]), and the NEVER tombstone's
 * survival of the zero-rows clear (gh-225 follow-up).
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
        coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns null
        repository().setSeriesPolicy("s1", AutoDownloadMode.ASK)

        coVerify(exactly = 1) {
            downloadPolicyDao.upsertPolicy(SeriesDownloadPolicyEntity(seriesId = "s1", mode = "ASK"))
        }
    }

    @Test
    fun `setSeriesPolicy carries over the cached arm display name`() = runTest {
        // An upsert replaces the whole row — a sheet re-confirm must not blank
        // the display name a download backfilled into the arm row.
        coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns
            SeriesDownloadPolicyEntity("s1", "INSTANT", "Series One")

        repository().setSeriesPolicy("s1", AutoDownloadMode.NEVER)

        coVerify(exactly = 1) {
            downloadPolicyDao.upsertPolicy(SeriesDownloadPolicyEntity("s1", "NEVER", "Series One"))
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

    // ── ADR-0015: the zero-rows un-arm rule (one home on the store) ──────

    // The zero-rows clear runs inside one withTransaction (atomic against a
    // concurrent NEVER write), so these tests execute it through the module's
    // inline transaction stub like the deleteDownload suite below.

    @Test
    fun `clearSeriesArmIfNoDownloads clears arm and prompts at zero rows`() = runTest {
        stubTransactionInline()
        coEvery { downloadDao.getDownloadsForSeries("s1") } returns emptyList()

        repository().clearSeriesArmIfNoDownloads("s1")

        coVerify(exactly = 1) { downloadPolicyDao.deletePolicyBySeriesId("s1") }
        coVerify(exactly = 1) { downloadPolicyDao.deleteSuggestionsBySeriesId("s1") }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    @Test
    fun `clearSeriesArmIfNoDownloads is a no-op while download rows remain`() = runTest {
        stubTransactionInline()
        coEvery { downloadDao.getDownloadsForSeries("s1") } returns listOf(mockk(relaxed = true))

        repository().clearSeriesArmIfNoDownloads("s1")

        coVerify(exactly = 0) { downloadPolicyDao.deletePolicyBySeriesId(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.deleteSuggestionsBySeriesId(any()) }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    @Test
    fun `clearSeriesArmIfNoDownloads keeps a NEVER tombstone at zero rows`() = runTest {
        stubTransactionInline()
        coEvery { downloadDao.getDownloadsForSeries("s1") } returns emptyList()
        coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns
            SeriesDownloadPolicyEntity("s1", "NEVER", "Series One")

        repository().clearSeriesArmIfNoDownloads("s1")

        // The tombstone ROW survives hygiene: the follow-up arr auto-arm sync
        // re-arms any series with NO policy row, so an erased NEVER would be
        // resurrected. Its pending prompts do not — the same setSeriesPolicy
        // (NEVER) invariant: prompts recorded under a policy that no longer
        // wants them are dead.
        coVerify(exactly = 0) { downloadPolicyDao.deletePolicyBySeriesId(any()) }
        coVerify(exactly = 1) { downloadPolicyDao.deleteSuggestionsBySeriesId("s1") }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    @Test
    fun `clearSeriesArmIfNoDownloads still clears FOLLOW_GLOBAL and override rows at zero rows`() = runTest {
        stubTransactionInline()
        coEvery { downloadDao.getDownloadsForSeries(any()) } returns emptyList()
        coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns
            SeriesDownloadPolicyEntity("s1", "FOLLOW_GLOBAL", "Series One")
        coEvery { downloadPolicyDao.getPolicyBySeriesId("s2") } returns
            SeriesDownloadPolicyEntity("s2", "INSTANT", "Series Two")

        val repo = repository()
        repo.clearSeriesArmIfNoDownloads("s1")
        repo.clearSeriesArmIfNoDownloads("s2")

        coVerify(exactly = 1) { downloadPolicyDao.deletePolicyBySeriesId("s1") }
        coVerify(exactly = 1) { downloadPolicyDao.deletePolicyBySeriesId("s2") }
        coVerify(exactly = 2) { downloadPolicyDao.deleteSuggestionsBySeriesId(any()) }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    // ── gh-225 follow-up: the manual arm verb ─────────────────────────────

    @Test
    fun `armSeries inserts a FOLLOW_GLOBAL arm when no row exists`() = runTest {
        coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns null

        repository().armSeries("s1", "Series One")

        coVerify(exactly = 1) {
            downloadPolicyDao.insertPolicyIfAbsent(
                SeriesDownloadPolicyEntity("s1", "FOLLOW_GLOBAL", "Series One"),
            )
        }
        coVerify(exactly = 0) { downloadPolicyDao.upsertPolicy(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.updatePolicySeriesName(any(), any()) }
    }

    @Test
    fun `armSeries never clobbers an existing override or changes its mode`() = runTest {
        for (mode in listOf(AutoDownloadMode.INSTANT, AutoDownloadMode.ASK, AutoDownloadMode.NEVER)) {
            coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns
                SeriesDownloadPolicyEntity("s1", mode.name, "Series One")

            repository().armSeries("s1", "Series One")
        }

        // Insert-if-absent + name backfill only — an explicit sheet choice
        // (incl. the NEVER tombstone) is never demoted to a plain arm.
        coVerify(exactly = 0) { downloadPolicyDao.insertPolicyIfAbsent(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.upsertPolicy(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.updatePolicySeriesName(any(), any()) }
    }

    @Test
    fun `armSeries backfills a blank arm display name`() = runTest {
        coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns
            SeriesDownloadPolicyEntity("s1", "FOLLOW_GLOBAL", "")

        repository().armSeries("s1", "Series One")

        coVerify(exactly = 0) { downloadPolicyDao.insertPolicyIfAbsent(any()) }
        coVerify(exactly = 1) { downloadPolicyDao.updatePolicySeriesName("s1", "Series One") }
    }

    @Test
    fun `armSeries does not overwrite a set arm display name`() = runTest {
        coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns
            SeriesDownloadPolicyEntity("s1", "FOLLOW_GLOBAL", "Series One")

        repository().armSeries("s1", "Renamed")

        coVerify(exactly = 0) { downloadPolicyDao.insertPolicyIfAbsent(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.updatePolicySeriesName(any(), any()) }
    }

    // manualArmSeries runs its read + flip + insert inside one withTransaction
    // (atomic against a concurrent sweep/pass), so these tests ride the module's
    // inline transaction stub like the deleteDownload suite below — without it
    // the relaxed database mock never executes the transaction body.

    @Test
    fun `manualArmSeries inserts a FOLLOW_GLOBAL arm when no row exists`() = runTest {
        stubTransactionInline()
        coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns null

        repository().manualArmSeries("s1", "Series One")

        coVerify(exactly = 1) {
            downloadPolicyDao.insertPolicyIfAbsent(
                SeriesDownloadPolicyEntity("s1", "FOLLOW_GLOBAL", "Series One"),
            )
        }
        coVerify(exactly = 0) { downloadPolicyDao.upsertPolicy(any()) }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    @Test
    fun `manualArmSeries flips a raw NEVER tombstone back to FOLLOW_GLOBAL`() = runTest {
        stubTransactionInline()
        coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns
            SeriesDownloadPolicyEntity("s1", "NEVER", "Series One")

        repository().manualArmSeries("s1", "Series One")

        // The re-arm rides the one upsert body, carrying the cached name;
        // FOLLOW_GLOBAL keeps prompts (no suggestion delete), and the
        // insert-if-absent arm no-ops on the now-existing row.
        coVerify(exactly = 1) {
            downloadPolicyDao.upsertPolicy(SeriesDownloadPolicyEntity("s1", "FOLLOW_GLOBAL", "Series One"))
        }
        coVerify(exactly = 0) { downloadPolicyDao.insertPolicyIfAbsent(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.deleteSuggestionsBySeriesId(any()) }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    @Test
    fun `manualArmSeries backfills a blank name when flipping a tombstone`() = runTest {
        stubTransactionInline()
        coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns
            SeriesDownloadPolicyEntity("s1", "NEVER", "")

        repository().manualArmSeries("s1", "Series One")

        coVerify(exactly = 1) {
            downloadPolicyDao.upsertPolicy(SeriesDownloadPolicyEntity("s1", "FOLLOW_GLOBAL", ""))
        }
        coVerify(exactly = 1) { downloadPolicyDao.updatePolicySeriesName("s1", "Series One") }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    @Test
    fun `manualArmSeries never demotes an explicit INSTANT or ASK override`() = runTest {
        stubTransactionInline()
        for (mode in listOf(AutoDownloadMode.INSTANT, AutoDownloadMode.ASK)) {
            coEvery { downloadPolicyDao.getPolicyBySeriesId("s1") } returns
                SeriesDownloadPolicyEntity("s1", mode.name, "Series One")

            repository().manualArmSeries("s1", "Series One")
        }

        coVerify(exactly = 0) { downloadPolicyDao.insertPolicyIfAbsent(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.upsertPolicy(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.updatePolicySeriesName(any(), any()) }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    // ── ADR-0015: the delete verb's un-arm split ──────────────────────────
    // The shared deletion choreography runs inline via the module's KMP
    // withTransaction helper stub (the OfflineRepositoryImplArtworkTest
    // idiom), so the full deleteDownload path is exercisable over mocks.

    private fun stubTransactionInline() {
        mockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    private fun seriesEpisodeEntity(seriesId: String?) = DownloadEntity(
        id = "dl-1",
        mediaItemId = "ep-1",
        name = "Episode",
        mediaType = "EPISODE",
        downloadPath = "",
        downloadUrl = "https://stream/ep-1",
        totalSizeBytes = 0L,
        downloadedBytes = 0L,
        status = DownloadStatus.PENDING.name,
        seriesId = seriesId,
    )

    @Test
    fun `deleteDownload default un-arms the series when its last row goes`() = runTest {
        stubTransactionInline()
        coEvery { downloadDao.getDownloadById("dl-1") } returns seriesEpisodeEntity(seriesId = "s1")
        coEvery { downloadDao.getDownloadsForSeries("s1") } returns emptyList()

        repository().deleteDownload("dl-1")

        coVerify(exactly = 1) { downloadPolicyDao.deletePolicyBySeriesId("s1") }
        coVerify(exactly = 1) { downloadPolicyDao.deleteSuggestionsBySeriesId("s1") }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    @Test
    fun `deleteDownload keepSeriesArm=true keeps the arm through watch-boundary hygiene`() = runTest {
        stubTransactionInline()
        coEvery { downloadDao.getDownloadById("dl-1") } returns seriesEpisodeEntity(seriesId = "s1")

        repository().deleteDownload("dl-1", keepSeriesArm = true)

        // The ADR-0015 watch-boundary contract: the deletion runs (the row's
        // cascade is the deletionCore's) but the arm + prompts survive so the
        // series stays in the check's universe at zero rows.
        coVerify(exactly = 0) { downloadPolicyDao.deletePolicyBySeriesId(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.deleteSuggestionsBySeriesId(any()) }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    @Test
    fun `deleteDownload of a series-less row never consults the arm table`() = runTest {
        stubTransactionInline()
        coEvery { downloadDao.getDownloadById("dl-1") } returns seriesEpisodeEntity(seriesId = null)

        repository().deleteDownload("dl-1")

        coVerify(exactly = 0) { downloadPolicyDao.deletePolicyBySeriesId(any()) }
        coVerify(exactly = 0) { downloadPolicyDao.deleteSuggestionsBySeriesId(any()) }
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    // ── ADR-0015: the armed-series reads ──────────────────────────────

    @Test
    fun `armedSeriesIds and observeArmedSeries surface parseable arms with raw modes`() = runTest {
        every { downloadPolicyDao.observePolicies() } returns flowOf(
            listOf(
                SeriesDownloadPolicyEntity("s1", "FOLLOW_GLOBAL", "Series One"),
                SeriesDownloadPolicyEntity("s2", "NEVER"),
                // A name this build cannot parse must stay inert: it joins
                // neither the universe nor the armed list (fail-safe).
                SeriesDownloadPolicyEntity("s3", "FUTURE_MODE"),
            ),
        )
        val repo = repository()

        assertEquals(listOf("s1", "s2"), repo.armedSeriesIds())

        val arms = repo.observeArmedSeries().first()
        assertEquals(
            listOf(
                SeriesDownloadArm("s1", AutoDownloadMode.FOLLOW_GLOBAL, "Series One"),
                SeriesDownloadArm("s2", AutoDownloadMode.NEVER, ""),
            ),
            arms,
        )
    }
}
