package com.raulshma.jellyplay.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AudioLyricsManager
import com.raulshma.jellyplay.core.data.playback.DesktopAudioQueueManager
import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.SleepCountdownClock
import com.raulshma.jellyplay.core.data.playback.focus.NoopPlaybackFocus
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.remote.DisplayMessagePayload
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.update.PendingAppUpdate
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.testfixtures.FakeMediaEngine
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import com.raulshma.jellyplay.core.ui.navigation.NavigationState
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.desktop.player.FakeImages
import com.raulshma.jellyplay.desktop.player.FakeLyricsRepository
import com.raulshma.jellyplay.desktop.player.FakePlaybackRepository
import com.raulshma.jellyplay.desktop.player.FakeResolver
import com.raulshma.jellyplay.desktop.player.InMemoryQueueDao
import com.raulshma.jellyplay.desktop.player.TestTimeSource
import com.raulshma.jellyplay.feature.music.feedback.DesktopMusicMessageBus
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient

/**
 * Pins [DesktopShellServices]' KDoc claim — "a plain class on
 * constructor-injected collaborators ... so the wiring is JVM-constructible"
 * — by CONSTRUCTING the holder over fake/plain collaborators (no Koin, no
 * Compose) and exercising the wiring's basic operations, the coverage the
 * composable-inline predecessor never had:
 *
 *  - the three service bundles assemble: the user-message source list has
 *    exactly the three sources ([desktopUserMessageSources]' order), the
 *    guarded navigator + empty section registry, the session controller
 *    over the fake auth flows;
 *  - the guard's safe direction: before the scaffold's graph build attaches
 *    the registry, EVERY route is a dead end — the navigate is swallowed and
 *    exactly one snackbar lands through the shared showMessage sink;
 *  - the update check maps a failing repository onto the failure wording
 *    through the same sink;
 *  - homeMode writes run the full chain (session controller → store write
 *    → the store's projected slice);
 *  - the session controller's logout fork dispatches to the fake auth
 *    repository's revoke/plain arms.
 *
 * The collaborators follow the established idioms: plain in-memory
 * [NavigationState]s (DesktopNavGuardTest), the app-level audio fixtures
 * (DesktopAudioQueueManagerFixtures), a real
 * [PreferenceDataStoreFactory.create]-backed file for the stores
 * (HomeDiscoveryStoreTest's Unconfined-scope shape), and the ADR-trap fake
 * shape for [AppUpdateRepository] (DesktopUpdateCheckControllerTest). The
 * two seams the holder consumes as plain lambdas/flows — MoveFocus and the
 * receiver's displayMessages — need no doubles at all.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DesktopShellServicesTest {

    /** Real Unconfined scope for the DataStore-backed stores (their eager slices). */
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** Per-test service scopes, cancelled with the stores in [tearDown]. */
    private val serviceScopes = mutableListOf<CoroutineScope>()

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("desktop-shell-services-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        serviceScopes.forEach { it.cancel() }
        serviceScopes.clear()
        storeScope.cancel()
        tempDir.deleteRecursively()
    }

    // ── the collaborators ───────────────────────────────────────────────

    /**
     * Fake [AuthRepository]: the flows the holder's session controller and
     * idle reads consume are real StateFlows; logout/revoke are counted.
     * Everything else is unreachable from the holder (it consumes only the
     * flows + the two sign-out arms + refreshCurrentUser) and fails loudly.
     */
    private class FakeAuthRepository : AuthRepository {
        val authenticated = MutableStateFlow(false)
        var revokes = 0
            private set
        var logouts = 0
            private set

        override val servers = MutableStateFlow<List<ServerInfo>>(emptyList())
        override val currentServer = MutableStateFlow<ServerInfo?>(null)
        override val currentUser = MutableStateFlow<UserInfo?>(null)
        override val isAuthenticated: StateFlow<Boolean> get() = authenticated
        override val currentServerUsers = MutableStateFlow<List<UserInfo>>(emptyList())

        override suspend fun addServer(address: String): Result<ServerInfo> = unexpected()
        override suspend fun probeServer(address: String): Result<ServerInfo> = unexpected()
        override suspend fun removeServer(serverId: String) = unexpected()
        override suspend fun switchServer(serverId: String): Result<Unit> = unexpected()
        override suspend fun addServerAddress(serverId: String, address: String): Result<Unit> = unexpected()
        override suspend fun removeServerAddress(serverId: String, address: String): Result<Unit> = unexpected()
        override suspend fun switchServerAddress(serverId: String, address: String): Result<Unit> = unexpected()
        override suspend fun login(serverAddress: String, username: String, password: String): Result<UserInfo> = unexpected()
        override suspend fun isQuickConnectEnabled(): Result<Boolean> = unexpected()
        override suspend fun initiateQuickConnect(): Result<QuickConnectInfo> = unexpected()
        override suspend fun pollQuickConnect(secret: String): Result<QuickConnectState> = unexpected()
        override suspend fun loginWithQuickConnect(serverAddress: String, secret: String): Result<UserInfo> = unexpected()
        override suspend fun authorizeQuickConnect(code: String): Result<Boolean> = unexpected()
        override suspend fun restoreSession(): Result<Unit> = unexpected()

        override suspend fun refreshCurrentUser(): Result<UserInfo> {
            val user = currentUser.value ?: return Result.failure(IllegalStateException("signed out"))
            return Result.success(user)
        }

        override suspend fun logout() {
            logouts++
        }

        override suspend fun revokeServerSession() {
            revokes++
        }

        override suspend fun switchUser(userId: String): Result<Unit> = unexpected()
        override suspend fun removeUser(userId: String) = unexpected()
        override suspend fun getUsersForServer(serverId: String): List<UserInfo> = unexpected()
        override suspend fun postCapabilities(): Result<Unit> = unexpected()

        private fun unexpected(): Nothing =
            error("unreachable from DesktopShellServices — the holder consumes only the flows and the sign-out arms")
    }

    /** The ADR-trap fake shape: only the check member is reachable. */
    private class FakeAppUpdateRepository : AppUpdateRepository {
        override suspend fun checkForUpdate(): Result<AppUpdateInfo> =
            Result.failure(IOException("offline"))

        override suspend fun downloadUpdate(
            info: AppUpdateInfo,
            onProgress: (Float, Long, Long) -> Unit,
        ): Result<File> = error("desktop must never download an update")

        override suspend fun getPendingUpdate(): PendingAppUpdate? =
            error("desktop must never stage a pending update")

        override fun cleanupDownloadedUpdate(): Unit =
            error("desktop must never touch the updates dir")
    }

    /** The rail's offline hide-set seam — vacuous (optimistically online). */
    private class FakeNetworkMonitor : NetworkMonitor {
        override val networkStatus = MutableStateFlow(NetworkStatus.Online)
        override val isMetered = MutableStateFlow(false)
    }

    /** One holder + everything its wiring touches, built per test inside [runTest]. */
    private class Harness(
        val services: DesktopShellServices,
        val state: NavigationState,
        val auth: FakeAuthRepository,
        val identityStore: ServerIdentityStore,
        val homeStore: HomeDiscoveryStore,
        val messages: MutableList<String>,
        val focusDirections: MutableList<RemoteFocusDirection>,
    )

    private fun TestScope.buildHarness(auth: FakeAuthRepository = FakeAuthRepository()): Harness {
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(tempDir, "test-${System.nanoTime()}.preferences_pb")
        }
        val identityStore = ServerIdentityStore(dataStore, storeScope)
        val homeStore = HomeDiscoveryStore(dataStore, storeScope, identityStore)
        val audioQueueManager = DesktopAudioQueueManager(
            trackResolver = FakeResolver(),
            playbackRepository = FakePlaybackRepository(),
            imageUrlProvider = FakeImages(),
            queuePersistenceHelper = QueuePersistenceHelper(InMemoryQueueDao()),
            // play()'s lyrics fetch launches on the manager's scope; without
            // initialize() the fetch would throw lateinit (the fixtures'
            // documented requirement).
            lyricsManager = AudioLyricsManager(FakeLyricsRepository()).also { it.initialize(storeScope) },
            sleepCountdown = SleepCountdown(SleepCountdownClock { TestTimeSource().nowElapsedRealtimeMillis() }),
            scope = storeScope,
            engineFactory = { FakeMediaEngine(FakeMediaEngine.LoadBehavior.AUTO_PLAY) },
            mainThreadGuard = false,
            playbackFocus = NoopPlaybackFocus,
        )
        // The scaffold's nav3 state, minus composition: the guard's back
        // stacks keyed by every desktop tab (the rememberNavigationState
        // shape — DesktopNavGuardTest's idiom).
        val state = NavigationState(
            startRoute = Route.Home,
            topLevelRoute = mutableStateOf(Route.Home),
            backStacks = DESKTOP_TOP_LEVEL_ROUTES.associateWith { NavBackStack(it) },
        )
        val messages = mutableListOf<String>()
        val focusDirections = mutableListOf<RemoteFocusDirection>()
        // A dedicated scope on the test scheduler's UnconfinedTestDispatcher:
        // the holder's one-shots (guard snackbar, logout, update check) then
        // run EAGERLY inside navigate()/logout()/checkForUpdate(), so the
        // assertions are deterministic — neither runTest's foreground scope
        // (the session controller's init launches a forever homeModeChanges
        // collector → UncompletedCoroutinesError) nor backgroundScope (its
        // work only runs while the test body is suspended, so advanceUntilIdle
        // never runs a one-shot) fits. Virtual time is still shared with the
        // test; the scope is cancelled in [tearDown].
        val servicesScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        serviceScopes += servicesScope
        val services = DesktopShellServices(
            scope = servicesScope,
            navigation = state,
            showMessage = { messages.add(it) },
            moveFocus = { focusDirections.add(it) },
            windowRef = null,
            authRepository = auth,
            homeDiscoveryStore = homeStore,
            appUpdateRepository = FakeAppUpdateRepository(),
            sharedUserMessageBus = UserMessageBus(),
            musicMessageBus = DesktopMusicMessageBus(),
            remoteDisplayMessages = MutableSharedFlow<DisplayMessagePayload>(),
            remoteNavigationBridge = RemoteNavigationBridge(),
            audioQueueManager = audioQueueManager,
            activePlayerRegistry = ActivePlayerController(),
            webSocketClient = JellyfinWebSocketClient(OkHttpClient()),
            screensaverStore = ScreensaverStore(dataStore, storeScope),
            idleReporter = NowPlayingReporter(),
            networkMonitor = FakeNetworkMonitor(),
            navigationStore = NavigationStore(dataStore, storeScope),
            appRuntimeStateStore = AppRuntimeStateStore(dataStore, storeScope),
            appearanceStore = AppearanceStore(dataStore, storeScope),
        )
        return Harness(services, state, auth, identityStore, homeStore, messages, focusDirections)
    }

    // ── the pins ────────────────────────────────────────────────────────

    @Test
    fun `the holder assembles its services over plain collaborators`() = runTest {
        val harness = buildHarness()

        assertEquals(3, harness.services.userMessageSources.size, "exactly the three hosted sources")
        assertFalse(harness.services.sessionController.isAdmin.value, "signed-out fake → no admin")
        assertFalse(harness.services.idleAmbientController.isIdle.value, "constructed idle, not idle")
        assertTrue(harness.messages.isEmpty(), "construction arms no snackbars")
        assertTrue(harness.focusDirections.isEmpty(), "construction arms no focus moves")
    }

    @Test
    fun `before the graph attach every route is a guarded dead end`() = runTest {
        val harness = buildHarness()

        harness.services.guardedNavigator.navigate(Route.MediaDetail("m1"))
        advanceUntilIdle()

        assertEquals(Route.Home, harness.services.guardedNavigator.currentRoute(), "the dead end never reached the stack")
        assertEquals(1, harness.state.backStacks.getValue(Route.Home).size)
        assertEquals(
            listOf("MediaDetail is not available on desktop yet."),
            harness.messages,
            "exactly one guard snackbar through the shared sink",
        )
    }

    @Test
    fun `the update check surfaces a failing check through the shared sink`() = runTest {
        val harness = buildHarness()

        harness.services.updateCheckController.checkForUpdate()
        advanceUntilIdle()

        assertEquals(
            listOf("Update check failed: offline"),
            harness.messages,
        )
    }

    @Test
    fun `homeMode writes persist through the session controller into the store`() = runTest {
        val harness = buildHarness()
        // The store skips pre-login writes (no namespace) — sign the fake in first.
        harness.identityStore.setActiveUser("u1")

        harness.services.sessionController.setHomeMode(HomeMode.MUSIC)
        advanceUntilIdle()

        assertEquals(HomeMode.MUSIC, harness.services.sessionController.homeMode.value, "the optimistic set lands")
        assertEquals(
            HomeMode.MUSIC,
            harness.homeStore.homeDiscovery.first { it.homeMode == HomeMode.MUSIC }.homeMode,
            "the write reached the projected slice",
        )
    }

    @Test
    fun `logout dispatches through the session controller's revoke fork`() = runTest {
        val harness = buildHarness()

        harness.services.sessionController.logout(revoke = true)
        advanceUntilIdle()
        assertEquals(1, harness.auth.revokes)
        assertEquals(0, harness.auth.logouts)

        harness.services.sessionController.logout(revoke = false)
        advanceUntilIdle()
        assertEquals(1, harness.auth.revokes, "the plain arm must not also revoke")
        assertEquals(1, harness.auth.logouts)
    }
}
