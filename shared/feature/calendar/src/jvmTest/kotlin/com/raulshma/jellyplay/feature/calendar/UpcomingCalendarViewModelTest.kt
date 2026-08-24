package com.raulshma.jellyplay.feature.calendar

import androidx.compose.runtime.snapshots.Snapshot
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrDownloadSummary
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadResult
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.model.arr.ArrSeriesResolution
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem
import com.raulshma.jellyplay.core.model.seerr.SeerrCurrentUser
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrSettings
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestCount
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestListResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrSeasonDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceServer
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrSettings
import com.raulshma.jellyplay.core.model.seerr.SeerrStatusResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.TmdbImageUrls
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext

/**
 * UpcomingCalendarViewModel coverage (downloads/syncplay conveyor test style,
 * hand-rolled fakes over the repository interfaces — no mocking library):
 * the feature-disabled refresh gate, the month-collector window swap, the
 * enrichment semaphore + append-only poster map, and stableRowId identity.
 *
 * The VM does real work in `init` (month collector + snapshotFlow enrichment
 * watcher + refresh), so Main is a [StandardTestDispatcher] and every
 * construction site is followed by [advanceUntilIdle]. Enrichment fans out on
 * [Dispatchers.Default] (production behavior, kept verbatim), so those tests
 * poll on real time via [awaitReal].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingCalendarViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var arr: FakeArrRepository
    private lateinit var seerr: FakeSeerrRepository
    private lateinit var experimentalStore: ExperimentalStore

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        arr = FakeArrRepository()
        seerr = FakeSeerrRepository()
        // Real store over an in-memory DataStore — no tmpdir file (concurrent
        // Gradle builds on one machine would fight over the fixed
        // TestDataStoreProvider path). Store scope runs Unconfined so the
        // Eagerly-shared experimental slice is current right after writes.
        experimentalStore = ExperimentalStore(
            dataStore = FakePreferencesDataStore(),
            externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun enableDirectArr() {
        experimentalStore.setEnabledExperimentalFeatures(setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION))
    }

    private fun newViewModel() = UpcomingCalendarViewModel(
        arrRepository = arr,
        seerrRepository = seerr,
        experimentalStore = experimentalStore,
    )

    private fun item(
        tmdbId: Int?,
        title: String = "Item $tmdbId",
        mediaType: com.raulshma.jellyplay.core.model.arr.ArrMediaType =
            com.raulshma.jellyplay.core.model.arr.ArrMediaType.MOVIE,
    ) = ArrCalendarItem(tmdbId = tmdbId, title = title, mediaType = mediaType, airDateUtc = "2026-07-14T00:00:00Z")

    /** Flushes Compose snapshot writes so the snapshotFlow watcher fires. */
    private fun flushSnapshots() {
        Snapshot.sendApplyNotifications()
    }

    /**
     * Polls [cond] on real time — the enrichment path hops to
     * [Dispatchers.Default] (real threads, real delays), invisible to the
     * virtual test scheduler.
     */
    private suspend fun TestScope.awaitReal(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!cond()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                error("condition not met within ${timeoutMs}ms")
            }
            advanceUntilIdle()
            flushSnapshots()
            withContext(Dispatchers.Default) { delay(20) }
        }
    }

    // ── feature-disabled gate ──────────────────────────────────────────────

    @Test
    fun refreshIsAGatekeeperNoOpWhileFeatureDisabled() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertTrue(arr.refreshCalls.isEmpty(), "refreshCalendar must not be called while the flag is off")
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun refreshFetchesTheVisibleMonthWindowWhenEnabled() = runTest {
        enableDirectArr()
        val vm = newViewModel()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        val month = YearMonth.now()
        assertEquals(month.atDay(1) to month.atEndOfMonth(), arr.refreshCalls.single())
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun refreshFailureSurfacesErrorMessage() = runTest {
        enableDirectArr()
        arr.refreshResult = Result.failure(RuntimeException("boom"))
        val vm = newViewModel()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals("boom", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    // ── month collector ────────────────────────────────────────────────────

    @Test
    fun calendarFlowItemsMirrorIntoState() = runTest {
        enableDirectArr()
        val vm = newViewModel()
        advanceUntilIdle()

        val items = listOf(item(1), item(2))
        arr.calendarItems.value = items
        advanceUntilIdle()

        assertEquals(items, vm.state.value.items)
        assertNull(vm.state.value.error)
    }

    @Test
    fun changeMonthClearsItemsAndRestartsCollectorOnNewWindow() = runTest {
        enableDirectArr()
        arr.calendarItems.value = listOf(item(1))
        val vm = newViewModel()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)

        val currentMonth = YearMonth.now()
        vm.changeMonth(1)
        // Items clear synchronously so the list doesn't show the old month.
        assertTrue(vm.state.value.items.isEmpty())
        advanceUntilIdle()

        val nextMonth = currentMonth.plusMonths(1)
        assertEquals(
            nextMonth.atDay(1) to nextMonth.atEndOfMonth(),
            arr.calendarWindows.last(),
            "collector must restart scoped to the new month window",
        )
        assertEquals(
            nextMonth.atDay(1) to nextMonth.atEndOfMonth(),
            arr.refreshCalls.last(),
            "refresh must run for the new month window",
        )
    }

    @Test
    fun goToDateReportsWhetherTheMonthChanged() = runTest {
        enableDirectArr()
        val vm = newViewModel()
        advanceUntilIdle()
        val windowsBefore = arr.calendarWindows.size

        // Same month → no-op.
        assertFalse(vm.goToDate(LocalDate.now()))
        assertEquals(windowsBefore, arr.calendarWindows.size)

        // Different month → swap + collector restart.
        val otherMonth = YearMonth.now().plusMonths(2)
        assertTrue(vm.goToDate(otherMonth.atDay(15)))
        advanceUntilIdle()
        assertEquals(
            otherMonth.atDay(1) to otherMonth.atEndOfMonth(),
            arr.calendarWindows.last(),
        )
    }

    @Test
    fun setFilterIsPureClientSideState() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.setFilter(CalendarFilter.SERIES)
        assertEquals(CalendarFilter.SERIES, vm.state.value.filter)
        assertTrue(arr.refreshCalls.isEmpty())
    }

    // ── enrichment ─────────────────────────────────────────────────────────

    @Test
    fun enrichmentResolvesPostersThroughTheRightDetailsEndpoint() = runTest {
        enableDirectArr()
        arr.calendarItems.value = listOf(
            item(101, mediaType = com.raulshma.jellyplay.core.model.arr.ArrMediaType.MOVIE),
            item(202, mediaType = com.raulshma.jellyplay.core.model.arr.ArrMediaType.SERIES),
            item(null, title = "No tmdb id"),
        )
        val vm = newViewModel()
        advanceUntilIdle()
        flushSnapshots()

        awaitReal { vm.state.value.enrichedPosters.keys == setOf(101, 202) }

        assertEquals(
            "${TmdbImageUrls.POSTER_W500}/movie.jpg",
            vm.state.value.enrichedPosters[101],
        )
        assertEquals(
            "${TmdbImageUrls.POSTER_W500}/tv.jpg",
            vm.state.value.enrichedPosters[202],
        )
        // No tmdbId → nothing to enrich.
        assertFalse(vm.state.value.enrichedPosters.containsKey(0))
    }

    @Test
    fun enrichmentSemaphoreBoundsConcurrentLookups() = runTest {
        enableDirectArr()
        seerr.lookupDelayMs = 100
        arr.calendarItems.value = (1..8).map { item(it) }
        val vm = newViewModel()
        advanceUntilIdle()
        flushSnapshots()

        awaitReal { vm.state.value.enrichedPosters.size == 8 }

        assertEquals(8, vm.state.value.enrichedPosters.size)
        assertTrue(seerr.maxConcurrent.get() <= 4, "semaphore must cap concurrency at 4, saw ${seerr.maxConcurrent.get()}")
        assertTrue(seerr.maxConcurrent.get() >= 2, "cap must still allow parallel lookups, saw ${seerr.maxConcurrent.get()}")
    }

    @Test
    fun enrichmentIsAppendOnlyAndSkipsAlreadyEnrichedIds() = runTest {
        enableDirectArr()
        arr.calendarItems.value = listOf(item(1))
        val vm = newViewModel()
        advanceUntilIdle()
        flushSnapshots()
        awaitReal { vm.state.value.enrichedPosters.containsKey(1) }
        val served = seerr.servedMovieIds.size

        // Same items re-emit (e.g. collector refresh): nothing re-fetched.
        // awaitReal can't wait on a negative, so settle on real time for a
        // window instead — a regression (re-fetch of enriched ids) fires on
        // the Default fan-out within it.
        arr.calendarItems.value = listOf(item(1))
        val settleStart = System.currentTimeMillis()
        while (System.currentTimeMillis() - settleStart < 300) {
            advanceUntilIdle()
            flushSnapshots()
            withContext(Dispatchers.Default) { delay(20) }
        }
        assertEquals(served, seerr.servedMovieIds.size, "already-enriched ids must not be re-fetched")
    }

    // ── stableRowId ────────────────────────────────────────────────────────

    @Test
    fun stableRowIdIsStableAndDiscriminating() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        val a = item(7, title = "Title")
        val same = item(7, title = "Title")
        assertEquals(vm.stableRowId(a), vm.stableRowId(same))

        // Every field participates: differing mediaType / tvdbId / tmdbId /
        // title / airDateUtc each shift the id.
        val variants = listOf(
            a.copy(mediaType = com.raulshma.jellyplay.core.model.arr.ArrMediaType.SERIES),
            a.copy(tvdbId = 99),
            a.copy(tmdbId = 8),
            a.copy(title = "Other"),
            a.copy(airDateUtc = null),
        )
        val ids = variants.map { vm.stableRowId(it) } + vm.stableRowId(a)
        assertEquals(ids.size, ids.toSet().size, "row ids must be pairwise distinct across field variations")
    }
}

// ── Hand-rolled fakes (interfaces — no mocking library needed) ─────────────

/**
 * [ArrRepository] fake: only the calendar surface is live; every other member
 * fails loudly so an unexpected code path can't pass silently.
 */
private class FakeArrRepository : ArrRepository {
    val calendarWindows = mutableListOf<Pair<LocalDate, LocalDate>>()
    val refreshCalls = mutableListOf<Pair<LocalDate, LocalDate>>()
    val calendarItems = MutableStateFlow(emptyList<ArrCalendarItem>())
    var refreshResult: Result<Unit> = Result.success(Unit)

    override fun calendar(from: LocalDate, to: LocalDate): Flow<List<ArrCalendarItem>> {
        calendarWindows += from to to
        return calendarItems
    }

    override suspend fun refreshCalendar(from: LocalDate, to: LocalDate): Result<Unit> {
        refreshCalls += from to to
        return refreshResult
    }

    override suspend fun resolveServers(): Result<ArrServiceSummary> = unused()
    override fun invalidateServers() = unused()
    override fun queue(): Flow<List<ArrQueueItem>> = unused()
    override fun blocklist(): Flow<List<ArrBlocklistItem>> = unused()
    override suspend fun refreshQueue(): Result<Unit> = unused()
    override suspend fun refreshBlocklist(): Result<Unit> = unused()
    override suspend fun getQueueForTmdb(tmdbId: Int): ArrQueueItem? = unused()
    override suspend fun getDownloadSummaryForTmdb(tmdbId: Int): ArrDownloadSummary? = unused()
    override suspend fun testServer(server: ArrServerConfig): Result<Unit> = unused()
    override suspend fun deleteQueueItem(item: ArrQueueItem, options: ArrQueueDeleteOptions): Result<Unit> = unused()
    override suspend fun deleteQueueItems(items: List<ArrQueueItem>, options: ArrQueueDeleteOptions): Result<Unit> = unused()
    override suspend fun grabQueueItem(item: ArrQueueItem): Result<Unit> = unused()
    override suspend fun importQueueItem(item: ArrQueueItem): Result<Unit> = unused()
    override suspend fun deleteBlocklistItem(item: ArrBlocklistItem): Result<Unit> = unused()
    override suspend fun searchForTmdb(tmdbId: Int, kind: ArrServiceKind): Result<List<ArrCommand>> = unused()
    override suspend fun redownloadMedia(
        tmdbId: Int,
        kind: ArrServiceKind,
        tvdbId: Int?,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): Result<ArrRedownloadResult> = unused()
    override suspend fun resolveSonarrSeries(tvdbId: Int): Result<ArrSeriesResolution> = unused()
    override suspend fun getSonarrEpisodes(tvdbId: Int): Result<List<ArrSeriesEpisode>> = unused()
    override suspend fun monitorSonarrEpisodes(tvdbId: Int, episodeIds: List<Int>, monitored: Boolean): Result<Unit> = unused()
    override suspend fun deleteSonarrEpisodeFile(tvdbId: Int, episodeFileId: Int): Result<Unit> = unused()
    override suspend fun searchSonarrEpisodes(tvdbId: Int, episodeIds: List<Int>): Result<Unit> = unused()
    override suspend fun searchMonitoredSonarrSeason(tvdbId: Int, seasonNumber: Int): Result<Unit> = unused()
    override suspend fun refreshSonarrSeries(tvdbId: Int): Result<Unit> = unused()
    override suspend fun rescanSonarrSeries(tvdbId: Int): Result<Unit> = unused()
    override suspend fun searchSonarrSeries(tvdbId: Int): Result<Unit> = unused()

    private fun unused(): Nothing = error("unused in calendar tests")
}

/**
 * [SeerrRepository] fake: only getMovieDetails/getTvDetails (the poster
 * enrichment endpoints) are live, with concurrency instrumentation for the
 * semaphore test.
 */
private class FakeSeerrRepository : SeerrRepository {
    val servedMovieIds = mutableListOf<Int>()
    val servedTvIds = mutableListOf<Int>()
    var posterForMovie: (Int) -> String? = { "/movie.jpg" }
    var posterForTv: (Int) -> String? = { "/tv.jpg" }
    var lookupDelayMs: Long = 0

    private val active = AtomicInteger(0)
    val maxConcurrent = AtomicInteger(0)

    private suspend fun <T> instrument(block: suspend () -> T): T {
        val now = active.incrementAndGet()
        maxConcurrent.accumulateAndGet(now) { a, b -> maxOf(a, b) }
        try {
            if (lookupDelayMs > 0) delay(lookupDelayMs)
            return block()
        } finally {
            active.decrementAndGet()
        }
    }

    override suspend fun getMovieDetails(tmdbId: Int): Result<SeerrMovieDetails> = instrument {
        servedMovieIds += tmdbId
        Result.success(SeerrMovieDetails(id = tmdbId, posterPath = posterForMovie(tmdbId)))
    }

    override suspend fun getTvDetails(tmdbId: Int): Result<SeerrTvDetails> = instrument {
        servedTvIds += tmdbId
        Result.success(SeerrTvDetails(id = tmdbId, posterPath = posterForTv(tmdbId)))
    }

    override suspend fun testConnection(): Result<SeerrStatusResponse> = unused()
    override suspend fun loginJellyfin(username: String, password: String): Result<SeerrStatusResponse> = unused()
    override suspend fun loginLocal(email: String, password: String): Result<SeerrStatusResponse> = unused()
    override suspend fun testApiKeyConnection(): Result<SeerrStatusResponse> = unused()
    override suspend fun search(query: String, page: Int): Result<SeerrSearchResponse> = unused()
    override suspend fun getTvSeasonDetails(tvId: Int, seasonNumber: Int): Result<SeerrSeasonDetail> = unused()
    override suspend fun getRatings(tmdbId: Int, mediaType: String): Result<SeerrRatings> = unused()
    override suspend fun getRecommendations(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse> = unused()
    override suspend fun getSimilar(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse> = unused()
    override suspend fun getTmdbVideos(tmdbId: Int, mediaType: MediaType): Result<List<SeerrRelatedVideo>> = unused()
    override suspend fun getTmdbReviews(tmdbId: Int, mediaType: MediaType): Result<List<TmdbReview>> = unused()
    override suspend fun getRadarrSettings(): Result<List<SeerrRadarrSettings>> = unused()
    override suspend fun getSonarrSettings(): Result<List<SeerrSonarrSettings>> = unused()
    override suspend fun getRadarrServiceDetail(id: Int): Result<SeerrRadarrServiceDetail> = unused()
    override suspend fun getSonarrServiceDetail(id: Int): Result<SeerrSonarrServiceDetail> = unused()
    override suspend fun getServiceRadarrServers(): Result<List<SeerrServiceServer>> = unused()
    override suspend fun getServiceSonarrServers(): Result<List<SeerrServiceServer>> = unused()
    override suspend fun getServiceRadarrDetail(id: Int): Result<SeerrRadarrServiceDetail> = unused()
    override suspend fun getServiceSonarrDetail(id: Int): Result<SeerrSonarrServiceDetail> = unused()
    override suspend fun requestMedia(
        tmdbId: Int,
        mediaType: String,
        seasons: List<Int>?,
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
        tags: List<Int>?,
    ): Result<SeerrMediaRequest> = unused()
    override suspend fun getRequests(
        take: Int,
        skip: Int,
        filter: String,
        sort: String,
        sortDirection: String,
        requestedBy: Int?,
        mediaType: String?,
        search: String?,
    ): Result<SeerrRequestListResponse> = unused()
    override suspend fun getRequest(id: Int): Result<SeerrRequestItem> = unused()
    override suspend fun approveRequest(id: Int): Result<SeerrRequestItem> = unused()
    override suspend fun declineRequest(id: Int): Result<SeerrRequestItem> = unused()
    override suspend fun retryRequest(id: Int): Result<SeerrRequestItem> = unused()
    override suspend fun deleteRequest(id: Int): Result<Unit> = unused()
    override suspend fun deleteMedia(mediaId: Int, is4k: Boolean): Result<Unit> = unused()
    override suspend fun editRequest(
        id: Int,
        mediaType: String,
        mediaId: Int,
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
        tags: List<Int>?,
        seasons: List<Int>?,
    ): Result<SeerrRequestItem> = unused()
    override suspend fun getRequestCount(): Result<SeerrRequestCount> = unused()
    override suspend fun getCurrentUser(): Result<SeerrCurrentUser> = unused()
    override fun isConnected(): Flow<Boolean> = unused()
    override fun isEnabled(): Flow<Boolean> = unused()
    override fun isSearchEnabled(): Flow<Boolean> = unused()
    override fun isRecommendationsEnabled(): Flow<Boolean> = unused()
    override fun isDiscoverEnabled(): Flow<Boolean> = unused()
    override fun getPreferences(): Flow<SeerrPreferences> = unused()
    override suspend fun getTrending(page: Int): Result<SeerrSearchResponse> = unused()
    override suspend fun getDiscoverMovies(page: Int, primaryReleaseDateGte: String?): Result<SeerrSearchResponse> = unused()
    override suspend fun getDiscoverTv(page: Int, firstAirDateGte: String?): Result<SeerrSearchResponse> = unused()
    override fun isAdmin(): Flow<Boolean> = unused()
    override val currentUser: StateFlow<SeerrCurrentUser?> get() = unused()
    override val pendingRequestCount: StateFlow<Int> get() = unused()
    override fun startPolling() = unused()
    override fun stopPolling() = unused()

    private fun unused(): Nothing = error("unused in calendar tests")
}

/** In-memory [DataStore] — single-threaded sequential updates, re-emits on write. */
private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
