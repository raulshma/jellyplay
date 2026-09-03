package com.raulshma.jellyplay

import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.remote.DisplayMessagePayload
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.shortcuts.AppShortcutManager
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.data.playback.ResolvedPlaybackSource
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.deeplink.DeepLinkHandler
import com.raulshma.jellyplay.shell.SessionCoordinator
import com.raulshma.jellyplay.shell.SyncPlayOpenCoordinator
import com.raulshma.jellyplay.shell.UpdateCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the app-shell [MainViewModel] contracts:
 *
 *  - launcher-shortcut intents route 1:1 to their pending [Route]; unknown
 *    actions and a PLAY_AUDIO without an item id leave the pending route
 *    untouched; the SURPRISE_ME shortcut additionally arms the one-shot
 *    surprise-on-launch flag that [consumeSurpriseOnLaunch] resets.
 *  - deep links and shared text route through the same pending-route slot;
 *    shared text prefers a `jellyfin://media/<id>` item link, then any
 *    `https?://` URL as the search query, then the raw text, and surfaces a
 *    "nothing searchable" message on blank input.
 *  - the state-loss restore flag is consumed exactly once per ViewModel
 *    instance (fresh instance = restore after process death).
 *  - admin-status refresh de-duplicates: one in-flight refresh serializes
 *    concurrent entries and a successful refresh bounds re-fetches to once
 *    per the 30 s window.
 *  - the offline toggle raises the going-online busy flag only when leaving
 *    an offline mode and clears it when the mode settles back to ONLINE.
 *  - the external-player launch builder maps the resolved source (local
 *    download or stream) onto an ACTION_VIEW intent with the `video` mime
 *    type advertising `return_result`, carrying the start position in ms
 *    only when positive, and null resolution yields null; start/stop
 *    reports round-trip the session id, with a non-positive final position
 *    falling back to the start position.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val projections: PreferenceProjections = mockk(relaxed = true)
    private val homeDiscoveryStore: HomeDiscoveryStore = mockk(relaxed = true)
    private val appRuntimeStateStore: AppRuntimeStateStore = mockk(relaxed = true)
    private val pinRateLimiter: PinRateLimiter = mockk(relaxed = true)
    private val remoteControlReceiver: RemoteControlReceiver = mockk(relaxed = true)
    private val appShortcutManager: AppShortcutManager = mockk(relaxed = true)
    private val deepLinkHandler = DeepLinkHandler()
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val downloadRepository: DownloadRepository = mockk(relaxed = true)
    private val playbackSourceResolver: PlaybackSourceResolver = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val userMessageBus: UserMessageBus = mockk(relaxed = true)
    private val sessionCoordinator: SessionCoordinator = mockk(relaxed = true)
    private val updateCoordinator: UpdateCoordinator = mockk(relaxed = true)
    private val syncPlayOpenCoordinator: SyncPlayOpenCoordinator = mockk(relaxed = true)

    private val currentUser = MutableStateFlow<UserInfo?>(null)
    private val mainPreferences = MutableStateFlow(MainPreferences())
    private val runtimeState = MutableStateFlow(AppRuntimeState())
    private val pinLockout = MutableStateFlow(0L)
    private val displayMessages = MutableSharedFlow<DisplayMessagePayload>(extraBufferCapacity = 4)
    private val downloadCount = MutableStateFlow(0)
    private val offlineMode = MutableStateFlow(OfflineMode.ONLINE)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepository.currentUser } returns currentUser
        every { projections.mainPreferences } returns mainPreferences
        every { appRuntimeStateStore.state } returns runtimeState
        every { pinRateLimiter.pinLockoutUntilEpochMs } returns pinLockout
        every { remoteControlReceiver.displayMessages } returns displayMessages
        every { downloadRepository.getActiveDownloadCount() } returns downloadCount
        every { offlineModeManager.offlineMode } returns offlineMode
        coEvery { playbackRepository.reportPlaybackStart(any()) } returns Result.success(Unit)
        coEvery { playbackRepository.reportPlaybackStopped(any(), any(), any()) } returns Result.success(Unit)
        coEvery { authRepository.refreshCurrentUser() } returns Result.success(userInfo(isAdmin = false))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = MainViewModel(
        authRepository = authRepository,
        projections = projections,
        homeDiscoveryStore = homeDiscoveryStore,
        appRuntimeStateStore = appRuntimeStateStore,
        pinRateLimiter = pinRateLimiter,
        remoteControlReceiver = remoteControlReceiver,
        appShortcutManager = appShortcutManager,
        deepLinkHandler = deepLinkHandler,
        playbackRepository = playbackRepository,
        downloadRepository = downloadRepository,
        playbackSourceResolver = playbackSourceResolver,
        offlineModeManager = offlineModeManager,
        userMessageBus = userMessageBus,
        sessionCoordinator = sessionCoordinator,
        updateCoordinator = updateCoordinator,
        syncPlayOpenCoordinator = syncPlayOpenCoordinator,
    )

    // ── launcher shortcut routing ──────────────────────────────────────────

    @Test
    fun `shortcut actions route to their pending routes`() = runTest(dispatcher) {
        val vm = createVm()

        vm.handleShortcutIntent(Intent(AppShortcutManager.ACTION_CONTINUE_WATCHING))
        assertEquals(Route.NewsletterSectionList("CONTINUE_WATCHING"), vm.pendingRoute.value)
        vm.consumePendingRoute()

        vm.handleShortcutIntent(Intent(AppShortcutManager.ACTION_SEARCH))
        assertEquals(Route.Search, vm.pendingRoute.value)
        vm.consumePendingRoute()

        vm.handleShortcutIntent(Intent(AppShortcutManager.ACTION_PLAY_MUSIC))
        assertEquals(Route.MusicBrowse, vm.pendingRoute.value)
        vm.consumePendingRoute()

        vm.handleShortcutIntent(Intent(AppShortcutManager.ACTION_DOWNLOADS))
        assertEquals(Route.Downloads, vm.pendingRoute.value)
        vm.consumePendingRoute()

        vm.handleShortcutIntent(Intent(AppShortcutManager.ACTION_SETTINGS))
        assertEquals(Route.Settings, vm.pendingRoute.value)
        vm.consumePendingRoute()
    }

    @Test
    fun `play-audio shortcut with an item id routes to the audio player`() = runTest(dispatcher) {
        val vm = createVm()
        val intent = Intent(AppShortcutManager.ACTION_PLAY_AUDIO)
            .putExtra(AppShortcutManager.EXTRA_ITEM_ID, "track-7")

        vm.handleShortcutIntent(intent)

        assertEquals(Route.AudioPlayer("track-7"), vm.pendingRoute.value)
    }

    @Test
    fun `play-audio shortcut without an item id is ignored`() = runTest(dispatcher) {
        val vm = createVm()

        vm.handleShortcutIntent(Intent(AppShortcutManager.ACTION_PLAY_AUDIO))

        assertNull(vm.pendingRoute.value)
    }

    @Test
    fun `unknown shortcut action is ignored`() = runTest(dispatcher) {
        val vm = createVm()

        vm.handleShortcutIntent(Intent("com.raulshma.jellyplay.action.UNKNOWN"))

        assertNull(vm.pendingRoute.value)
    }

    @Test
    fun `surprise-me shortcut routes home and arms the one-shot launch flag`() = runTest(dispatcher) {
        val vm = createVm()

        vm.handleShortcutIntent(Intent(AppShortcutManager.ACTION_SURPRISE_ME))

        assertEquals(Route.Home, vm.pendingRoute.value)
        assertTrue(vm.surpriseOnLaunch.value)
        vm.consumeSurpriseOnLaunch()
        assertFalse(vm.surpriseOnLaunch.value)
    }

    @Test
    fun `consumePendingRoute clears the pending route`() = runTest(dispatcher) {
        val vm = createVm()
        vm.handleShortcutIntent(Intent(AppShortcutManager.ACTION_SEARCH))
        assertEquals(Route.Search, vm.pendingRoute.value)

        vm.consumePendingRoute()

        assertNull(vm.pendingRoute.value)
    }

    // ── deep links ─────────────────────────────────────────────────────────

    @Test
    fun `deep link intents parse into the pending route`() = runTest(dispatcher) {
        val vm = createVm()

        vm.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse("jellyplay://media/abc123")))

        assertEquals(Route.MediaDetail("abc123"), vm.pendingRoute.value)
    }

    @Test
    fun `unparseable deep links leave the pending route untouched`() = runTest(dispatcher) {
        val vm = createVm()

        vm.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse("jellyplay://unknown")))

        assertNull(vm.pendingRoute.value)
    }

    // ── shared text ────────────────────────────────────────────────────────

    @Test
    fun `shared text containing a jellyfin media link opens that item`() = runTest(dispatcher) {
        val vm = createVm()

        vm.handleSharedText("Watch: jellyfin://media/0a1b2c3d-0000-0000-0000-00000000def later")
        advanceUntilIdle()

        assertEquals(
            Route.MediaDetail("0a1b2c3d-0000-0000-0000-00000000def"),
            vm.pendingRoute.value,
        )
        assertNull(vm.pendingSearchQuery.value)
    }

    @Test
    fun `shared text containing a web URL searches for the URL`() = runTest(dispatcher) {
        val vm = createVm()

        vm.handleSharedText("Check https://example.com/page?q=1 please")
        advanceUntilIdle()

        assertEquals(Route.Search, vm.pendingRoute.value)
        assertEquals("https://example.com/page?q=1", vm.pendingSearchQuery.value)
    }

    @Test
    fun `shared plain text searches for the raw text`() = runTest(dispatcher) {
        val vm = createVm()

        vm.handleSharedText("interstellar")
        advanceUntilIdle()

        assertEquals(Route.Search, vm.pendingRoute.value)
        assertEquals("interstellar", vm.pendingSearchQuery.value)
    }

    @Test
    fun `shared blank text surfaces a nothing-searchable message`() = runTest(dispatcher) {
        val vm = createVm()

        vm.handleSharedText("   ")
        advanceUntilIdle()

        assertNull(vm.pendingRoute.value)
        verify(exactly = 1) { userMessageBus.info("No searchable content found in shared text") }
    }

    // ── search query plumbing ──────────────────────────────────────────────

    @Test
    fun `search intents set the pending query which consumption clears`() = runTest(dispatcher) {
        val vm = createVm()

        vm.handleSearchQuery("dune")

        assertEquals("dune", vm.pendingSearchQuery.value)
        vm.consumePendingSearchQuery()
        assertNull(vm.pendingSearchQuery.value)
    }

    // ── state-loss restore flag ────────────────────────────────────────────

    @Test
    fun `state-loss restore is consumed exactly once per instance`() = runTest(dispatcher) {
        val vm = createVm()
        assertTrue(vm.consumeStateLossRestore())
        assertFalse(vm.consumeStateLossRestore())

        // A fresh ViewModel = a restore after state loss, so it re-arms.
        assertTrue(createVm().consumeStateLossRestore())
    }

    // ── surprise-me signal ─────────────────────────────────────────────────

    @Test
    fun `requestSurprise emits a one-shot surprise request`() = runTest(dispatcher) {
        val vm = createVm()
        val received = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.surpriseRequests.collect { received.add(it) }
        }

        vm.requestSurprise()
        vm.requestSurprise()

        assertEquals(2, received.size)
    }

    // ── shell coordinators ─────────────────────────────────────────────────

    @Test
    fun `construction starts the session update and syncplay coordinators`() = runTest(dispatcher) {
        createVm()
        advanceUntilIdle()

        verify(exactly = 1) { sessionCoordinator.start(any(), any()) }
        verify(exactly = 1) { updateCoordinator.start(any()) }
        verify(exactly = 1) { syncPlayOpenCoordinator.start(any()) }
        verify(exactly = 1) { appShortcutManager.observePlaybackForDynamicShortcuts() }
    }

    // ── admin status refresh ───────────────────────────────────────────────

    @Test
    fun `refreshAdminStatus refreshes the current user once and de-duplicates inside the window`() = runTest(dispatcher) {
        val vm = createVm()

        vm.refreshAdminStatus()
        advanceUntilIdle()
        vm.refreshAdminStatus()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.refreshCurrentUser() }
        assertFalse(vm.isRefreshingAdmin.value)
    }

    @Test
    fun `refreshAdminStatus serializes concurrent entries behind the in-flight flag`() = runTest(dispatcher) {
        val vm = createVm()
        val gate = CompletableDeferred<Unit>()
        coEvery { authRepository.refreshCurrentUser() } coAnswers {
            gate.await()
            Result.success(userInfo(isAdmin = false))
        }

        vm.refreshAdminStatus()
        advanceUntilIdle() // suspends inside refreshCurrentUser
        vm.refreshAdminStatus() // early-out: refresh still in flight
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.refreshCurrentUser() }
    }

    @Test
    fun `isAdmin projects the current user's admin flag`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.isAdmin.collect { }
        }
        advanceUntilIdle()
        assertFalse(vm.isAdmin.value)

        currentUser.value = userInfo(isAdmin = true)
        advanceUntilIdle()
        assertTrue(vm.isAdmin.value)
    }

    // ── offline toggle ─────────────────────────────────────────────────────

    @Test
    fun `toggleOfflineMode while online does not raise the going-online flag`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        offlineMode.value = OfflineMode.ONLINE

        vm.toggleOfflineMode()
        advanceUntilIdle()

        assertFalse(vm.isGoingOnline.value)
        coVerify(exactly = 1) { offlineModeManager.toggleManualOffline() }
    }

    @Test
    fun `toggleOfflineMode while offline raises the flag and settling online clears it`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        offlineMode.value = OfflineMode.OFFLINE_MANUAL

        vm.toggleOfflineMode()
        advanceUntilIdle()
        assertTrue(vm.isGoingOnline.value)

        offlineMode.value = OfflineMode.ONLINE
        advanceUntilIdle()
        assertFalse(vm.isGoingOnline.value)
    }

    // ── preference plumbing ────────────────────────────────────────────────

    @Test
    fun `setHomeMode delegates to the home discovery store`() = runTest(dispatcher) {
        val vm = createVm()

        vm.setHomeMode(HomeMode.MUSIC)
        advanceUntilIdle()

        coVerify(exactly = 1) { homeDiscoveryStore.setHomeMode(HomeMode.MUSIC) }
    }

    @Test
    fun `markOnboardingCompleted persists the runtime flag`() = runTest(dispatcher) {
        val vm = createVm()

        vm.markOnboardingCompleted()
        advanceUntilIdle()

        coVerify(exactly = 1) { appRuntimeStateStore.setOnboardingCompleted(true) }
    }

    @Test
    fun `preferences merge the runtime-only lockout and onboarding fields`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.preferences.collect { }
        }
        advanceUntilIdle()

        pinLockout.value = 123_456L
        runtimeState.value = AppRuntimeState(onboardingCompleted = true)
        advanceUntilIdle()

        assertEquals(123_456L, vm.preferences.value.pinLockoutUntilEpochMs)
        assertTrue(vm.preferences.value.onboardingCompleted)
    }

    @Test
    fun `activeDownloadCount mirrors the download repository`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.activeDownloadCount.collect { }
        }
        advanceUntilIdle()
        assertEquals(0, vm.activeDownloadCount.value)

        downloadCount.value = 4
        advanceUntilIdle()
        assertEquals(4, vm.activeDownloadCount.value)
    }

    // ── server-pushed display messages ─────────────────────────────────────

    @Test
    fun `display messages with a header surface as header-newline-text`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()

        displayMessages.tryEmit(DisplayMessagePayload("Head", "Body", null))
        advanceUntilIdle()

        verify(exactly = 1) { userMessageBus.info("Head\nBody") }
    }

    @Test
    fun `display messages with a blank header surface the bare text`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()

        displayMessages.tryEmit(DisplayMessagePayload("  ", "Body", null))
        advanceUntilIdle()

        verify(exactly = 1) { userMessageBus.info("Body") }
    }

    // ── external player launch builder ─────────────────────────────────────

    @Test
    fun `stream resolution builds a view intent with return_result and ms position`() = runTest(dispatcher) {
        val vm = createVm()
        coEvery { playbackSourceResolver.resolvePlaybackSource(any(), any(), any()) } returns
            ResolvedPlaybackSource.Stream(
                itemId = "item-1",
                url = "https://server/videos/1/stream",
                title = "Movie",
                mediaSourceId = null,
            )

        val launch = vm.buildExternalPlayerLaunch("item-1", null, startPositionTicks = 900_000_000L)

        assertNotNull(launch)
        assertEquals("item-1", launch!!.itemId)
        assertEquals("https://server/videos/1/stream", launch.intent.data.toString())
        assertEquals("video/*", launch.intent.type)
        assertEquals("Movie", launch.intent.getStringExtra("title"))
        assertTrue(launch.intent.getBooleanExtra("return_result", false))
        assertEquals(90_000L, launch.intent.getLongExtra("position", -1L))
        coVerify(exactly = 1) {
            playbackSourceResolver.resolvePlaybackSource(
                itemId = "item-1",
                mediaSourceId = null,
                startPositionTicks = 900_000_000L,
            )
        }
    }

    @Test
    fun `local download resolution plays the file uri with the download title`() = runTest(dispatcher) {
        val vm = createVm()
        coEvery { playbackSourceResolver.resolvePlaybackSource(any(), any(), any()) } returns
            ResolvedPlaybackSource.Local(
                itemId = "item-2",
                filePath = "/data/files/movie.mp4",
                uri = "file:///data/files/movie.mp4",
                title = "Downloaded Movie",
                download = downloadItem(),
            )

        val launch = vm.buildExternalPlayerLaunch("item-2", "ms-1", startPositionTicks = 0L)

        assertNotNull(launch)
        assertEquals("file:///data/files/movie.mp4", launch!!.intent.data.toString())
        assertEquals("Downloaded Movie", launch.intent.getStringExtra("title"))
        // No position extra when the start position is zero.
        assertEquals(-1L, launch.intent.getLongExtra("position", -1L))
    }

    @Test
    fun `unresolvable playback source yields no external launch`() = runTest(dispatcher) {
        val vm = createVm()
        coEvery { playbackSourceResolver.resolvePlaybackSource(any(), any(), any()) } returns null

        assertNull(vm.buildExternalPlayerLaunch("item-3", null, 0L))
    }

    @Test
    fun `each external launch carries a fresh play session id`() = runTest(dispatcher) {
        val vm = createVm()
        coEvery { playbackSourceResolver.resolvePlaybackSource(any(), any(), any()) } returns
            ResolvedPlaybackSource.Stream("item-1", "https://server/v", "T", null)

        val first = vm.buildExternalPlayerLaunch("item-1", null, 0L)!!
        val second = vm.buildExternalPlayerLaunch("item-1", null, 0L)!!

        assertTrue(first.playSessionId.isNotBlank())
        assertTrue(first.playSessionId != second.playSessionId)
    }

    // ── external playback progress reporting ───────────────────────────────

    @Test
    fun `external playback start reports the launch position under the session id`() = runTest(dispatcher) {
        val vm = createVm()
        val launch = ExternalPlayerLaunch(
            intent = Intent(Intent.ACTION_VIEW),
            itemId = "item-1",
            startPositionTicks = 120_000_000L,
            playSessionId = "session-1",
        )

        vm.reportExternalPlaybackStart(launch)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = "item-1",
                    sessionId = "session-1",
                    startPositionTicks = 120_000_000L,
                ),
            )
        }
    }

    @Test
    fun `external playback stop reports the final position`() = runTest(dispatcher) {
        val vm = createVm()
        val launch = ExternalPlayerLaunch(
            intent = Intent(Intent.ACTION_VIEW),
            itemId = "item-1",
            startPositionTicks = 120_000_000L,
            playSessionId = "session-1",
        )

        vm.reportExternalPlaybackStopped(launch, finalPositionTicks = 300_000_000L)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "session-1", 300_000_000L)
        }
    }

    @Test
    fun `external playback stop with a non-positive final position falls back to the start position`() = runTest(dispatcher) {
        val vm = createVm()
        val launch = ExternalPlayerLaunch(
            intent = Intent(Intent.ACTION_VIEW),
            itemId = "item-1",
            startPositionTicks = 120_000_000L,
            playSessionId = "session-1",
        )

        vm.reportExternalPlaybackStopped(launch, finalPositionTicks = 0L)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "session-1", 120_000_000L)
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun userInfo(isAdmin: Boolean) = UserInfo(
        id = "user-1",
        name = "User",
        serverAddress = "https://server",
        accessToken = "token",
        isAdmin = isAdmin,
    )

    private fun downloadItem() = DownloadItem(
        id = "dl-1",
        mediaItemId = "item-2",
        name = "Downloaded Movie",
        mediaType = MediaType.MOVIE,
        downloadPath = "/data/files/movie.mp4",
        downloadUrl = "https://server/download",
        totalSizeBytes = 100L,
        downloadedBytes = 100L,
        status = DownloadStatus.COMPLETED,
    )
}
