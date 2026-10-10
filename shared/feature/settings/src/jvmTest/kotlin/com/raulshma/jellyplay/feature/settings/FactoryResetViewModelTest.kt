package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationSlice
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverSlice
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceSnapshotReader
import com.raulshma.jellyplay.core.datastore.settings.PreferenceStores
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.PinLockoutState
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSyncRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlaySnapshotCreated
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The factory-reset review screen: the one-shot current-vs-factory snapshot
 * (18 domain slices + runtime + PIN lockout → `PreferenceSliceSnapshot`) and
 * the two
 * destructive delegates (per-category reset, full clear) that must reach
 * [PreferencesEditor] — the single auditable write seam — unchanged.
 * Regression-critical: `resetAll` wipes every preference.
 *
 * Stores are mockk'd with real default-slice flows (init-block snapshot
 * readers); the editor is a relaxed mock so the delegation is verified
 * verbatim. Main-dispatcher rule inlined (StandardTestDispatcher +
 * setMain/resetMain — module jvmTest pattern).
 *
 * The Wave-6 seam-present resets (the nullable `pluginApiClient`/`statusStore`
 * ctor seams) get their own block: a gated capture parks `resetRunning` and
 * refuses a second confirmation, a FAILED capture warns without blocking, and
 * the capture strictly precedes the destructive clear.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FactoryResetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var playbackStore: PlaybackStore
    private lateinit var appearanceStore: AppearanceStore
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
    private lateinit var pinRateLimiter: PinRateLimiter
    private lateinit var editor: PreferencesEditor
    private lateinit var userPreferencesStore: UserPreferencesStore

    private val runtimeState = MutableStateFlow(AppRuntimeState())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playbackStore = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
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
        pinRateLimiter = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        userPreferencesStore = mockk(relaxed = true)

        every { playbackStore.playback } returns MutableStateFlow(PlaybackSlice())
        every { videoPlayerStore.videoPlayer } returns MutableStateFlow(VideoPlayerSlice())
        every { engineStore.playerEngine } returns MutableStateFlow(PlayerEngineSlice())
        every { subtitleLanguageStore.subtitle } returns MutableStateFlow(SubtitleSlice())
        every { audioStore.audio } returns MutableStateFlow(AudioSlice())
        every { audioEffectsStore.audioEffects } returns MutableStateFlow(AudioEffectsSlice())
        every { audioCacheStore.audioCache } returns MutableStateFlow(AudioCacheSlice())
        every { appearanceStore.appearance } returns MutableStateFlow(AppearanceSlice())
        every { homeDiscoveryStore.homeDiscovery } returns MutableStateFlow(HomeDiscoverySlice())
        every { libraryStore.library } returns MutableStateFlow(LibrarySlice())
        every { navigationStore.navigation } returns MutableStateFlow(NavigationSlice())
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice())
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(NetworkOfflineSlice())
        every { notificationStore.notification } returns MutableStateFlow(NotificationSlice())
        every { screensaverStore.screensaver } returns MutableStateFlow(ScreensaverSlice())
        every { securityStore.security } returns MutableStateFlow(SecuritySlice())
        every { syncPlayCastStore.syncPlayCast } returns MutableStateFlow(SyncPlayCastSlice())
        every { experimentalStore.experimental } returns MutableStateFlow(ExperimentalSlice())
        every { appRuntimeStateStore.state } returns runtimeState
        every { pinRateLimiter.getPinLockoutState() } returns PinLockoutState.NOT_LOCKED
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun withAppearance(slice: AppearanceSlice) {
        every { appearanceStore.appearance } returns MutableStateFlow(slice)
    }

    private fun viewModel(
        pluginApiClient: JellyPlaySettingsSyncRoutes? = null,
        statusStore: JellyPlayPluginStatusStore? = null,
    ): FactoryResetViewModel = FactoryResetViewModel(
        snapshotReader = PreferenceSnapshotReader(
            stores = PreferenceStores(
                playback = playbackStore,
                videoPlayer = videoPlayerStore,
                engine = engineStore,
                subtitle = subtitleLanguageStore,
                audio = audioStore,
                audioEffects = audioEffectsStore,
                audioCache = audioCacheStore,
                appearance = appearanceStore,
                homeDiscovery = homeDiscoveryStore,
                library = libraryStore,
                navigation = navigationStore,
                downloads = downloadsStore,
                networkOffline = networkOfflineStore,
                notification = notificationStore,
                syncPlayCast = syncPlayCastStore,
                security = securityStore,
                experimental = experimentalStore,
                screensaver = screensaverStore,
                volumeProfile = mockk(relaxed = true),
            ),
            appRuntimeStateStore = appRuntimeStateStore,
            pinRateLimiter = pinRateLimiter,
        ),
        editor = editor,
        diffLabelResolver = { _ -> { res -> res.toString() } },
        pluginApiClient = pluginApiClient,
        statusStore = statusStore,
    )

    // ---------------------------------------------------------------- snapshot

    @Test
    fun `all-default slices reproduce the factory baseline`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(vm.factory.slices, vm.preferences.slices, "untouched stores must diff clean against the baseline")
    }

    @Test
    fun `non-default slice values land in the one-shot snapshot`() = runTest(testDispatcher) {
        withAppearance(AppearanceSlice(themeMode = ThemeMode.DARK, oledMode = true))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, vm.preferences.slices.appearance.themeMode)
        assertEquals(true, vm.preferences.slices.appearance.oledMode)
        assertNotEquals(vm.factory.slices, vm.preferences.slices, "a changed slice must diverge from the baseline")
        assertEquals(ThemeMode.SYSTEM, vm.factory.slices.appearance.themeMode, "the baseline itself stays immutable")
    }

    // ---------------------------------------------------------------- destructive delegates

    @Test
    fun `resetCategory delegates to the editor`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.resetCategory(PreferenceResetCategory.PLAYBACK)

        verify(exactly = 1) { editor.resetCategory(PreferenceResetCategory.PLAYBACK) }
    }

    @Test
    fun `resetAll clears all preferences through the editor`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.resetAll()

        verify(exactly = 1) { editor.clearAllPreferences() }
    }

    // ---------------------------------------------------------------- Wave-6 seam-present resets

    private val pluginStatus = MutableStateFlow(JellyPlayPluginStatus.UNAVAILABLE)
    private lateinit var pluginApi: JellyPlaySettingsSyncRoutes

    /**
     * The seam-present VM: the Wave-6 capture's gate is OPEN (probe AVAILABLE +
     * the `settings-sync` registry), so the safety capture actually runs — the
     * [JellyPlaySyncViewModelTest] api-mockk idiom.
     */
    private fun seamPresentViewModel(): FactoryResetViewModel {
        pluginApi = mockk(relaxed = true)
        val statusStore = mockk<JellyPlayPluginStatusStore>(relaxed = true)
        every { statusStore.status } returns pluginStatus
        every { statusStore.hasFeature(JellyPlayPluginFeatures.SettingsSync) } returns true
        pluginStatus.value = JellyPlayPluginStatus.AVAILABLE
        return viewModel(pluginApiClient = pluginApi, statusStore = statusStore)
    }

    /** Polls until [condition] holds, pumping the test scheduler between waits. */
    private suspend fun kotlinx.coroutines.test.TestScope.awaitUntil(description: String, condition: () -> Boolean) {
        advanceUntilIdle()
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            assertTrue(
                System.currentTimeMillis() < deadline,
                "$description (timed out waiting for the VM's continuation)",
            )
            withContext(Dispatchers.Default) { delay(10) }
            advanceUntilIdle()
        }
    }

    @Test
    fun `seam-present resetAll parks on the capture - a second confirmation is refused until it settles`() = runTest(testDispatcher) {
        val captureEntries = mutableListOf<Int>()
        val captureGate = CompletableDeferred<Unit>()
        val vm = seamPresentViewModel()
        coEvery { pluginApi.createSnapshot() } coAnswers {
            captureEntries += 1
            captureGate.await()
            Result.success(JellyPlaySnapshotCreated(0L))
        }

        vm.resetAll()
        awaitUntil("the capture parks mid-flight") { captureEntries.size == 1 }

        assertTrue(vm.resetRunning, "parked mid-capture must raise the running face")
        vm.resetAll()
        verify(exactly = 0) { editor.clearAllPreferences() }

        captureGate.complete(Unit)
        awaitUntil("the reset settles") { !vm.resetRunning }

        verify(exactly = 1) { editor.clearAllPreferences() }
        assertFalse(vm.safetySnapshotMissed, "a succeeding capture must not raise the warning face")
    }

    @Test
    fun `seam-present resetAll with a failed capture still completes and raises the warning face`() = runTest(testDispatcher) {
        val vm = seamPresentViewModel()
        // A failed capture must warn through the one-shot face — and never block the reset.
        coEvery { pluginApi.createSnapshot() } returns Result.failure(IllegalStateException("500"))

        vm.resetAll()
        advanceUntilIdle()

        verify(exactly = 1) { editor.clearAllPreferences() }
        assertTrue(vm.safetySnapshotMissed, "a missed capture must surface the warning face")
        assertFalse(vm.resetRunning, "the reset settled — the running face must drop")

        vm.clearSafetySnapshotMissed()
        assertFalse(vm.safetySnapshotMissed)
    }

    @Test
    fun `seam-present resetAll runs only after the capture settles - capture-before-reset ordering`() = runTest(testDispatcher) {
        val order = mutableListOf<String>()
        val vm = seamPresentViewModel()
        coEvery { pluginApi.createSnapshot() } coAnswers {
            order += "capture"
            Result.success(JellyPlaySnapshotCreated(0L))
        }
        every { editor.clearAllPreferences() } answers {
            order += "reset"
            Job()
        }

        vm.resetAll()
        advanceUntilIdle()

        assertEquals(listOf("capture", "reset"), order, "the destructive clear must wait for the capture to settle")
        assertFalse(vm.safetySnapshotMissed)
        assertFalse(vm.resetRunning)
    }
}
