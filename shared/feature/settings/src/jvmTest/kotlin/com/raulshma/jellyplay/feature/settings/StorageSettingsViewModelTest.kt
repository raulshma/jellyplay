package com.raulshma.jellyplay.feature.settings

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
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.DownloadPreferences
import com.raulshma.jellyplay.core.model.StoragePreferences
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
 * Pins the storage screen's filesystem-derived state through the [StorageAreas]
 * platform seam (pairs with [FileSizeTest], which tests the walk the desktop
 * actual performs): the four-bucket breakdown math (cache + external cache +
 * downloads + image cache → mebibyte triple + total), the download-location
 * preference passthrough, cache-clear error handling, and the mount
 * enumeration on init (including the swallow-on-failure guard).
 *
 * Unlike the other ViewModel suites the editor here is a REAL
 * [PreferencesEditor] over a mocked [PreferencesEditScope] — that is the only
 * way the `edit { … }` lambda actually runs, so store-level setters and the
 * auto-download scheduler poke are observable. Main-dispatcher rule inlined
 * (StandardTestDispatcher + setMain/resetMain — module jvmTest pattern).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StorageSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var projections: PreferenceProjections
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var playbackStore: PlaybackStore
    private lateinit var videoPlayerStore: VideoPlayerStore
    private lateinit var downloadsStore: DownloadsStore
    private lateinit var engineStore: PlayerEngineStore
    private lateinit var homeDiscoveryStore: HomeDiscoveryStore
    private lateinit var audioStore: AudioStore
    private lateinit var audioEffectsStore: AudioEffectsStore
    private lateinit var audioCacheStore: AudioCacheStore
    private lateinit var libraryStore: LibraryStore
    private lateinit var navigationStore: NavigationStore
    private lateinit var networkOfflineStore: NetworkOfflineStore
    private lateinit var notificationStore: NotificationStore
    private lateinit var screensaverStore: ScreensaverStore
    private lateinit var securityStore: SecurityStore
    private lateinit var subtitleLanguageStore: SubtitleLanguageStore
    private lateinit var syncPlayCastStore: SyncPlayCastStore
    private lateinit var experimentalStore: ExperimentalStore
    private lateinit var appRuntimeStateStore: AppRuntimeStateStore
    private lateinit var userPreferencesStore: UserPreferencesStore
    private lateinit var storageAreas: StorageAreas
    private lateinit var storageMountsProvider: StorageMountsProvider

    private val autoDownloadSyncs = mutableListOf<Unit>()

    private val storagePrefs = MutableStateFlow(StoragePreferences())
    private val downloadPrefs = MutableStateFlow(DownloadPreferences())
    private val showAdvanced = MutableStateFlow(false)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        projections = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        playbackStore = mockk(relaxed = true)
        videoPlayerStore = mockk(relaxed = true)
        downloadsStore = mockk(relaxed = true)
        engineStore = mockk(relaxed = true)
        homeDiscoveryStore = mockk(relaxed = true)
        audioStore = mockk(relaxed = true)
        audioEffectsStore = mockk(relaxed = true)
        audioCacheStore = mockk(relaxed = true)
        libraryStore = mockk(relaxed = true)
        navigationStore = mockk(relaxed = true)
        networkOfflineStore = mockk(relaxed = true)
        notificationStore = mockk(relaxed = true)
        screensaverStore = mockk(relaxed = true)
        securityStore = mockk(relaxed = true)
        subtitleLanguageStore = mockk(relaxed = true)
        syncPlayCastStore = mockk(relaxed = true)
        experimentalStore = mockk(relaxed = true)
        appRuntimeStateStore = mockk(relaxed = true)
        userPreferencesStore = mockk(relaxed = true)
        storageAreas = mockk(relaxed = true)
        storageMountsProvider = mockk()

        every { projections.storagePreferences } returns storagePrefs
        every { projections.downloadPreferences } returns downloadPrefs
        every { appearanceStore.showAdvancedSettings } returns showAdvanced
        coEvery { storageMountsProvider.availableMounts() } returns emptyList()
        autoDownloadSyncs.clear()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Real editor over the mocked scope so `edit { … }` blocks actually run. */
    private fun realEditor(): PreferencesEditor = PreferencesEditor(
        scope = CoroutineScope(testDispatcher + Job()),
        editScope = PreferencesEditScope(
            playback = playbackStore,
            appearance = appearanceStore,
            videoPlayer = videoPlayerStore,
            downloads = downloadsStore,
            engine = engineStore,
            homeDiscovery = homeDiscoveryStore,
            audio = audioStore,
            audioEffects = audioEffectsStore,
            audioCache = audioCacheStore,
            library = libraryStore,
            navigation = navigationStore,
            networkOffline = networkOfflineStore,
            notification = notificationStore,
            screensaver = screensaverStore,
            security = securityStore,
            subtitle = subtitleLanguageStore,
            syncPlayCast = syncPlayCastStore,
            experimental = experimentalStore,
            appRuntimeState = appRuntimeStateStore,
        ),
        store = userPreferencesStore,
    )

    private fun viewModel(): StorageSettingsViewModel = StorageSettingsViewModel(
        projections = projections,
        appearanceStore = appearanceStore,
        editor = realEditor(),
        autoDownloadSync = AutoDownloadSync { autoDownloadSyncs.add(Unit) },
        storageAreas = storageAreas,
        storageMountsProvider = storageMountsProvider,
    )

    // ---------------------------------------------------------------- init / mounts

    @Test
    fun `init enumerates the download mounts from the provider`() = runTest(testDispatcher) {
        val mounts = listOf(
            StorageMount("INTERNAL", StorageMountKind.INTERNAL, 1_000L, "/data"),
            StorageMount("EXTERNAL", StorageMountKind.REMOVABLE, 2_000L, "/sdcard"),
        )
        coEvery { storageMountsProvider.availableMounts() } returns mounts

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(mounts, vm.storageMounts)
    }

    @Test
    fun `init swallows a failing mount provider and keeps the empty list`() = runTest(testDispatcher) {
        coEvery { storageMountsProvider.availableMounts() } throws RuntimeException("no storage service")

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.storageMounts.isEmpty(), "a dead mount provider must not crash construction")
    }

    // ---------------------------------------------------------------- breakdown

    @Test
    fun `refreshCacheSize builds the four-bucket breakdown in mebibytes`() = runTest(testDispatcher) {
        coEvery { storageAreas.sizeEstimateBytes(any()) } returns StorageSizeEstimate(
            cacheBytes = 2L * 1024 * 1024,
            externalCacheBytes = 1L * 1024 * 1024,
            downloadsBytes = 4L * 1024 * 1024,
            imageCacheBytes = 8L * 1024 * 1024,
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.refreshCacheSize()
        advanceUntilIdle()

        assertEquals(3L, vm.cacheSizeMb, "internal + external cache combine into the cache bucket")
        assertEquals(
            StorageBreakdown(cacheMb = 3, downloadsMb = 4, imagesMb = 8, totalMb = 15),
            vm.storageBreakdown,
        )
        assertNull(vm.cacheError)
    }

    @Test
    fun `refreshCacheSize honors the download-storage-location preference`() = runTest(testDispatcher) {
        downloadPrefs.value = DownloadPreferences(downloadStorageLocation = "EXTERNAL")
        coEvery { storageAreas.sizeEstimateBytes(any()) } returns StorageSizeEstimate(0, 0, 0, 0)
        val vm = viewModel()
        advanceUntilIdle()

        vm.refreshCacheSize()
        advanceUntilIdle()

        coVerify(exactly = 1) { storageAreas.sizeEstimateBytes("EXTERNAL") }
    }

    // ---------------------------------------------------------------- clears

    @Test
    fun `clearCache wipes via the seam and re-measures`() = runTest(testDispatcher) {
        coEvery { storageAreas.sizeEstimateBytes(any()) } returns StorageSizeEstimate(0, 0, 0, 0)
        val vm = viewModel()
        advanceUntilIdle()

        vm.clearCache()
        advanceUntilIdle()

        coVerify(exactly = 1) { storageAreas.clearCache() }
        // The clear's `finally` re-runs refreshCacheSize — the only size walk
        // (init deliberately does NOT walk the filesystem).
        coVerify(exactly = 1) { storageAreas.sizeEstimateBytes(any()) }
        assertNull(vm.cacheError)
    }

    @Test
    fun `clearCache failure surfaces the message but still re-measures`() = runTest(testDispatcher) {
        coEvery { storageAreas.sizeEstimateBytes(any()) } returns StorageSizeEstimate(0, 0, 0, 0)
        coEvery { storageAreas.clearCache() } throws RuntimeException("walk denied")
        val vm = viewModel()
        advanceUntilIdle()

        vm.clearCache()
        advanceUntilIdle()

        assertEquals("walk denied", vm.cacheError)
        coVerify(atLeast = 1) { storageAreas.sizeEstimateBytes(any()) }
    }

    @Test
    fun `clearImageCache failure surfaces the message`() = runTest(testDispatcher) {
        coEvery { storageAreas.sizeEstimateBytes(any()) } returns StorageSizeEstimate(0, 0, 0, 0)
        coEvery { storageAreas.clearImageCache() } throws IllegalStateException("image dir busy")
        val vm = viewModel()
        advanceUntilIdle()

        vm.clearImageCache()
        advanceUntilIdle()

        assertEquals("image dir busy", vm.cacheError)
        coVerify(exactly = 1) { storageAreas.clearImageCache() }
    }

    // ---------------------------------------------------------------- editor wiring

    @Test
    fun `setShowAdvancedSettings reaches the appearance store through the editor`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setShowAdvancedSettings(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { appearanceStore.setShowAdvancedSettings(true) }
    }

    @Test
    fun `auto-download toggle also pokes the scheduler`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setAutoDownloadNewEpisodes(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadsStore.setAutoDownloadNewEpisodes(true) }
        assertEquals(1, autoDownloadSyncs.size, "the WorkManager sync must fire with the preference write")
    }

    @Test
    fun `download-location setter routes to the downloads store`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.edit { it.downloads.setDownloadStorageLocation("EXTERNAL") }
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadsStore.setDownloadStorageLocation("EXTERNAL") }
    }

    @Test
    fun `cache-cap setter routes to the network-offline store`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.edit { it.networkOffline.setMaxCacheSize(512) }
        advanceUntilIdle()

        coVerify(exactly = 1) { networkOfflineStore.setMaxCacheSize(512) }
    }
}
