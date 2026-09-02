package com.raulshma.jellyplay.feature.settings

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
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.PinLockoutState
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNotEquals

/**
 * The factory-reset review screen: the one-shot current-vs-factory snapshot
 * (18 domain slices + runtime + PIN lockout → [UserPreferences]) and the two
 * destructive delegates (per-category reset, full clear) that must reach
 * [PreferencesEditor] — the single auditable write seam — unchanged.
 * Regression-critical: `resetAll` wipes every preference.
 *
 * Stores are mockk'd with real default-slice flows (init-block snapshot
 * readers); the editor is a relaxed mock so the delegation is verified
 * verbatim. Main-dispatcher rule inlined (StandardTestDispatcher +
 * setMain/resetMain — module jvmTest pattern).
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

    private fun viewModel(): FactoryResetViewModel = FactoryResetViewModel(
        playbackStore = playbackStore,
        appearanceStore = appearanceStore,
        videoPlayerStore = videoPlayerStore,
        downloadsStore = downloadsStore,
        engineStore = engineStore,
        homeDiscoveryStore = homeDiscoveryStore,
        audioStore = audioStore,
        audioEffectsStore = audioEffectsStore,
        audioCacheStore = audioCacheStore,
        libraryStore = libraryStore,
        navigationStore = navigationStore,
        networkOfflineStore = networkOfflineStore,
        notificationStore = notificationStore,
        screensaverStore = screensaverStore,
        securityStore = securityStore,
        subtitleLanguageStore = subtitleLanguageStore,
        syncPlayCastStore = syncPlayCastStore,
        experimentalStore = experimentalStore,
        appRuntimeStateStore = appRuntimeStateStore,
        pinRateLimiter = pinRateLimiter,
        editor = editor,
    )

    // ---------------------------------------------------------------- snapshot

    @Test
    fun `all-default slices reproduce the factory baseline`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(vm.factory, vm.preferences, "untouched stores must diff clean against the baseline")
    }

    @Test
    fun `non-default slice values land in the one-shot snapshot`() = runTest(testDispatcher) {
        withAppearance(AppearanceSlice(themeMode = ThemeMode.DARK, oledMode = true))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, vm.preferences.themeMode)
        assertEquals(true, vm.preferences.oledMode)
        assertNotEquals(vm.factory, vm.preferences, "a changed slice must diverge from the baseline")
        assertEquals(ThemeMode.SYSTEM, vm.factory.themeMode, "the baseline itself stays immutable")
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
}
