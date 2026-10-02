package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfileStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the "Hidden from Next Up" management ViewModel: the metadata hydration
 * of the excluded-series id set through the repository's cached detail read
 * (resolved rows carry name/year; a FAILED fetch degrades to a placeholder row
 * that is still listed and still restorable), and the two restore commands'
 * routing through the owning store (per-row `includeSeriesInNextUp`, bulk
 * `clearNextUpExclusions`).
 *
 * The editor is a REAL [PreferencesEditor] over a mocked
 * [PreferencesEditScope] (DiscoverRowsViewModelTest pattern — the only way
 * `edit { … }` blocks actually run, so the store commands are observable), and
 * the store mock mirrors restores back into its slice flow so the VM's
 * re-reconcile (rows shrink, bookkeeping prunes) is exercised end-to-end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NextUpExcludedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var homeDiscovery: HomeDiscoveryStore
    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var editor: PreferencesEditor

    private val homeDiscoverySlice = MutableStateFlow(HomeDiscoverySlice())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        homeDiscovery = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        every { homeDiscovery.homeDiscovery } returns homeDiscoverySlice
        every { imageUrlProvider.getImageUrl(any()) } answers { "https://server/Items/${firstArg<String>()}/Images/Primary" }
        editor = PreferencesEditor(
            scope = CoroutineScope(testDispatcher + Job()),
            editScope = PreferencesEditScope(
                playback = mockk<PlaybackStore>(relaxed = true),
                appearance = mockk<AppearanceStore>(relaxed = true),
                videoPlayer = mockk<VideoPlayerStore>(relaxed = true),
                downloads = mockk<DownloadsStore>(relaxed = true),
                engine = mockk<PlayerEngineStore>(relaxed = true),
                homeDiscovery = homeDiscovery,
                audio = mockk<AudioStore>(relaxed = true),
                audioEffects = mockk<AudioEffectsStore>(relaxed = true),
                audioCache = mockk<AudioCacheStore>(relaxed = true),
                library = mockk<LibraryStore>(relaxed = true),
                navigation = mockk<NavigationStore>(relaxed = true),
                networkOffline = mockk<NetworkOfflineStore>(relaxed = true),
                notification = mockk<NotificationStore>(relaxed = true),
                screensaver = mockk<ScreensaverStore>(relaxed = true),
                security = mockk<SecurityStore>(relaxed = true),
                subtitle = mockk<SubtitleLanguageStore>(relaxed = true),
                syncPlayCast = mockk<SyncPlayCastStore>(relaxed = true),
                experimental = mockk<ExperimentalStore>(relaxed = true),
                volumeProfile = mockk<VolumeProfileStore>(relaxed = true),
                appRuntimeState = mockk<AppRuntimeStateStore>(relaxed = true),
            ),
            store = mockk<UserPreferencesStore>(relaxed = true),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun series(id: String, name: String, year: Int?) =
        MediaItem(id = id, name = name, mediaType = MediaType.SERIES, year = year)

    private fun viewModel(): NextUpExcludedViewModel =
        NextUpExcludedViewModel(homeDiscovery, editor, mediaRepository, imageUrlProvider)

    // ---------------------------------------------------------------- hydration

    @Test
    fun `excluded ids hydrate to rows with series metadata`() = runTest(testDispatcher) {
        homeDiscoverySlice.value = HomeDiscoverySlice(
            nextUpExcludedSeriesIds = setOf("s1", "s2"),
        )
        coEvery { mediaRepository.getMediaDetail("s1") } returns
            Result.success(MediaDetail(item = series("s1", "Series One", 2020)))
        coEvery { mediaRepository.getMediaDetail("s2") } returns
            Result.success(MediaDetail(item = series("s2", "Series Two", 1999)))

        val vm = viewModel()
        advanceUntilIdle()

        val rows = vm.state.value.series
        assertEquals(listOf("s1", "s2"), rows.map { it.id }, "rows follow the persisted exclusion order")
        assertEquals("Series One", rows[0].item?.name)
        assertEquals(2020, rows[0].item?.year)
        assertEquals("Series Two", rows[1].item?.name)
        assertEquals(1999, rows[1].item?.year)
        assertTrue(!vm.state.value.loading, "hydration settles with loading off")
    }

    @Test
    fun `a failed detail fetch keeps the row as a still-restorable placeholder`() = runTest(testDispatcher) {
        homeDiscoverySlice.value = HomeDiscoverySlice(nextUpExcludedSeriesIds = setOf("s1", "s2"))
        coEvery { mediaRepository.getMediaDetail("s1") } returns
            Result.success(MediaDetail(item = series("s1", "Series One", 2020)))
        coEvery { mediaRepository.getMediaDetail("s2") } returns Result.failure(IllegalStateException("offline"))

        val vm = viewModel()
        advanceUntilIdle()

        val rows = vm.state.value.series
        assertEquals(listOf("s1", "s2"), rows.map { it.id }, "the unresolved series stays listed")
        assertNull(rows[1].item, "the failed row degrades to the null-item placeholder")
        assertEquals("Series One", rows[0].item?.name)
        assertTrue(!vm.state.value.loading)
    }

    @Test
    fun `an empty exclusion set shows the empty state without fetching`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.series.isEmpty())
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    // ---------------------------------------------------------------- restore commands

    @Test
    fun `restore routes through includeSeriesInNextUp and the row leaves the list`() = runTest(testDispatcher) {
        homeDiscoverySlice.value = HomeDiscoverySlice(nextUpExcludedSeriesIds = setOf("s1", "s2"))
        coEvery { mediaRepository.getMediaDetail(any()) } answers {
            Result.success(MediaDetail(item = series(firstArg(), "Series", 2020)))
        }
        // Mirror the write back into the slice, the way the real store's edit
        // re-emits its projection.
        coEvery { homeDiscovery.includeSeriesInNextUp("s1") } coAnswers {
            homeDiscoverySlice.value = homeDiscoverySlice.value
                .copy(nextUpExcludedSeriesIds = homeDiscoverySlice.value.nextUpExcludedSeriesIds - "s1")
        }

        val vm = viewModel()
        advanceUntilIdle()

        vm.restore("s1")
        advanceUntilIdle()

        coVerify(exactly = 1) { homeDiscovery.includeSeriesInNextUp("s1") }
        assertEquals(listOf("s2"), vm.state.value.series.map { it.id }, "the restored row drops from the list")
    }

    @Test
    fun `restoreAll routes through clearNextUpExclusions and empties the list`() = runTest(testDispatcher) {
        homeDiscoverySlice.value = HomeDiscoverySlice(nextUpExcludedSeriesIds = setOf("s1", "s2"))
        coEvery { mediaRepository.getMediaDetail(any()) } answers {
            Result.success(MediaDetail(item = series(firstArg(), "Series", 2020)))
        }
        coEvery { homeDiscovery.clearNextUpExclusions() } coAnswers {
            homeDiscoverySlice.value = homeDiscoverySlice.value.copy(nextUpExcludedSeriesIds = emptySet())
        }

        val vm = viewModel()
        advanceUntilIdle()

        vm.restoreAll()
        advanceUntilIdle()

        coVerify(exactly = 1) { homeDiscovery.clearNextUpExclusions() }
        assertTrue(vm.state.value.series.isEmpty(), "the bulk restore empties the list")
    }
}
