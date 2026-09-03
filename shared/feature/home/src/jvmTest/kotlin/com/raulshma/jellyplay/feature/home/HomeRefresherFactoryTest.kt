package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import kotlin.test.Test
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionPrefs
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Invariants pinned for the [HomeRefresherFactory] construction seam (it
 * "adds no behavioural seam" by design — this suite pins exactly that):
 *  - [HomeRefresherFactory.create] builds a [HomeRefresher] wired to the SAME
 *    collaborator instances the factory owns — a fetch driven through the
 *    built refresher lands on the factory's [MediaRepository] and its sections
 *    reach the refresher's state.
 *  - The per-call inputs (offline gate, outbox drain gate, preference
 *    mirrors) are passed through from [create]'s arguments, not silently
 *    dropped: an offline manager reporting offline short-circuits the fetch.
 *
 * Harness mirrors [HomeRefresherTest]: MockK collaborators, inlined
 * StandardTestDispatcher, refresher torn down in @After.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeRefresherFactoryTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var widgetDataStore: WidgetDataStore
    private lateinit var continueWatchingBroadcaster: ContinueWatchingBroadcaster
    private lateinit var tvWatchNextScheduler: TvWatchNextScheduler
    private lateinit var librarySyncHook: LibrarySyncHook
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var fakeTimeSource: FakeTimeSource

    private var refresher: HomeRefresher? = null
    private var refresherScope: CoroutineScope? = null

    private val userDataEvents = MutableSharedFlow<com.raulshma.jellyplay.core.model.UserDataChange>(extraBufferCapacity = 64)
    private val networkStatusFlow = MutableStateFlow(NetworkStatus.Online)
    private val offlineModeFlow = MutableStateFlow(OfflineMode.ONLINE)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        arrRepository = mockk(relaxed = true)
        widgetDataStore = mockk(relaxed = true)
        continueWatchingBroadcaster = mockk(relaxed = true)
        tvWatchNextScheduler = mockk(relaxed = true)
        librarySyncHook = mockk(relaxed = true)
        offlineModeManager = mockk(relaxed = true)
        fakeTimeSource = FakeTimeSource()

        every { mediaRepository.userDataChanges } returns userDataEvents
        every { offlineModeManager.networkStatus } returns networkStatusFlow
        every { offlineModeManager.offlineMode } returns offlineModeFlow
        every { offlineModeManager.isOffline } returns false
    }

    @AfterTest
    fun stopRefresher() {
        refresher?.stop()
        refresherScope?.cancel()

        Dispatchers.resetMain()
    }

    private fun TestScope.createRefresher(): HomeRefresher {
        val factory = HomeRefresherFactory(
            timeSource = fakeTimeSource,
            mediaRepository = mediaRepository,
            seerrRepository = seerrRepository,
            arrRepository = arrRepository,
            orderHomeSections = OrderHomeSectionsUseCase(),
            widgetDataStore = widgetDataStore,
            continueWatchingBroadcaster = continueWatchingBroadcaster,
            tvWatchNextScheduler = tvWatchNextScheduler,
            librarySyncHook = librarySyncHook,
        )
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        refresherScope = scope
        return factory.create(
            scope = scope,
            offlineModeManager = offlineModeManager,
            awaitOutboxDrained = {},
            sectionPrefsProvider = {
                HomeSectionPrefs(
                    query = HomeSectionQuery(),
                    homeSectionOrder = HomeSectionType.CONFIGURABLE,
                    mergeContinueWatchingAndNextUp = false,
                )
            },
            seerrPreferencesProvider = { SeerrPreferences() },
            discoverEnabledProvider = { false },
            directArrEnabledProvider = { false },
            androidTvWatchNextEnabledProvider = { true },
        ).also { refresher = it }
    }

    @Test
    fun create_wiresTheFactorysRepositories_intoTheBuiltRefresher() = runTest {
        val fetched = HomeSection(
            id = HomeSectionType.LATEST_MEDIA.name,
            title = HomeSectionType.LATEST_MEDIA.displayName,
            type = HomeSectionType.LATEST_MEDIA,
            items = listOf(MediaItem(id = "m1", name = "m1", mediaType = MediaType.MOVIE)),
        )
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.success(HomeSectionsResult(sections = listOf(fetched)))
        val refresher = createRefresher()

        refresher.fetchOnce()
        runCurrent()

        io.mockk.coVerify {
            mediaRepository.getHomeSections(any(), force = false)
        }
        assertEquals(listOf(fetched), refresher.state.value.sections)
    }

    @Test
    fun create_passesTheOfflineGateThrough_offlineManagerShortCircuitsTheFetch() = runTest {
        every { offlineModeManager.isOffline } returns true
        val refresher = createRefresher()

        refresher.fetchOnce()
        runCurrent()

        io.mockk.coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }
        assertTrue(refresher.state.value.sections.isEmpty())
        assertEquals(OfflineMode.ONLINE, refresher.state.value.offlineMode)
    }

    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: java.time.ZoneId): java.time.LocalDate = java.time.LocalDate.of(2026, 1, 1)
    }
}
