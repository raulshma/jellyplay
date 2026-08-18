package com.raulshma.jellyplay.shell

import android.content.Context
import com.raulshma.jellyplay.core.data.network.ServerHealthMonitor
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.RealtimeConnection
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.startup.CacheMaintenanceInitializer
import com.raulshma.jellyplay.widget.WidgetWorkScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the two ordering invariants the shell seam exists for, tested against
 * [SessionCoordinator]'s own interface:
 *
 *  1. The session→update ordering callback — [SessionCoordinator.start]'s
 *     `onSessionRestored` fires exactly once, *after* session restore settles
 *     (success or failure), so the launch-time update check never races the
 *     session it depends on.
 *  2. The capabilities-after-WebSocket-reconnect rule — capabilities are
 *     re-posted on every false→true socket transition while authenticated and
 *     never posted while logged out, so the device stays castable after every
 *     reconnect (the server drops the session's controller on socket loss).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SessionCoordinatorTest {

    private val dispatcher = StandardTestDispatcher()

    /**
     * Stand-in for the ViewModel scope [SessionCoordinator.start] runs on in
     * production: shares the test scheduler so
     * [kotlinx.coroutines.test.advanceUntilIdle] drives the coordinator's
     * collectors, and is cancelled in teardown so they never outlive the test.
     */
    private val lifecycleScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(dispatcher.scheduler))

    private val context: Context = RuntimeEnvironment.getApplication()

    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val realtimeConnection = mockk<RealtimeConnection>(relaxed = true)
    private val experimentalStore = mockk<ExperimentalStore>(relaxed = true)
    private val serverIdentityStore = mockk<ServerIdentityStore>(relaxed = true)
    private val serverHealthMonitor = mockk<ServerHealthMonitor>(relaxed = true)
    private val remoteControlReceiver = mockk<RemoteControlReceiver>(relaxed = true)
    private val widgetWorkScheduler = mockk<WidgetWorkScheduler>(relaxed = true)
    private val cacheMaintenanceInitializer = mockk<CacheMaintenanceInitializer>(relaxed = true)
    private val mediaRepository = mockk<MediaRepository>(relaxed = true)

    private val isAuthenticated = MutableStateFlow(false)
    private val isConnected = MutableStateFlow(false)
    private val currentServer = MutableStateFlow<ServerInfo?>(null)
    private val currentUser = MutableStateFlow<UserInfo?>(null)

    private lateinit var coordinator: SessionCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepository.isAuthenticated } returns isAuthenticated
        every { authRepository.currentServer } returns currentServer
        every { authRepository.currentUser } returns currentUser
        every { realtimeConnection.isConnected } returns isConnected
        every { realtimeConnection.serverUrl() } returns "http://jellyfin.local"
        coEvery { authRepository.restoreSession() } returns Result.success(Unit)
        every { experimentalStore.experimental } returns MutableStateFlow(ExperimentalSlice())
        coEvery { serverIdentityStore.ensureDeviceId() } returns "test-device-id"
        every { serverHealthMonitor.serverHealth } returns MutableStateFlow(ServerHealth.Unknown)
        coEvery { mediaRepository.getLibraryFolders() } returns Result.success(emptyList<LibraryFolder>())

        coordinator = SessionCoordinator(
            context = context,
            authRepository = authRepository,
            realtimeConnection = realtimeConnection,
            experimentalStore = experimentalStore,
            serverIdentityStore = serverIdentityStore,
            serverHealthMonitor = serverHealthMonitor,
            remoteControlReceiver = remoteControlReceiver,
            widgetWorkScheduler = widgetWorkScheduler,
            cacheMaintenanceInitializer = cacheMaintenanceInitializer,
            mediaRepository = mediaRepository,
        )
    }

    @After
    fun tearDown() {
        lifecycleScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `onSessionRestored fires exactly once after restore completes`() = runTest(dispatcher) {
        val order = mutableListOf<String>()
        coEvery { authRepository.restoreSession() } answers {
            order += "restoreSession"
            Result.success(Unit)
        }

        coordinator.start(lifecycleScope) { order += "onSessionRestored" }
        advanceUntilIdle()

        assertEquals(listOf("restoreSession", "onSessionRestored"), order)
        assertFalse(coordinator.isRestoring.value)
        coVerify(exactly = 1) { authRepository.restoreSession() }
    }

    @Test
    fun `onSessionRestored fires even when restore fails`() = runTest(dispatcher) {
        coEvery { authRepository.restoreSession() } returns
            Result.failure(IllegalStateException("no persisted session"))

        var callbacks = 0
        coordinator.start(lifecycleScope) { callbacks++ }
        advanceUntilIdle()

        assertEquals(1, callbacks)
        assertFalse(coordinator.isRestoring.value)
    }

    @Test
    fun `capabilities re-posted on every websocket reconnect while authenticated`() = runTest(dispatcher) {
        coordinator.start(lifecycleScope) { }
        advanceUntilIdle()

        // Authenticate with a live server + user so the auth fan-out connects.
        currentServer.value = ServerInfo(id = "server-1", name = "Server", address = "http://jellyfin.local")
        currentUser.value = UserInfo(
            id = "user-1",
            name = "user",
            serverAddress = "http://jellyfin.local",
            accessToken = "token",
        )
        isAuthenticated.value = true
        advanceUntilIdle()
        verify { realtimeConnection.connect(any()) }
        // Authentication alone must not post capabilities — only a socket
        // false→true transition does.
        coVerify(exactly = 0) { authRepository.postCapabilities() }

        // First connect.
        isConnected.value = true
        advanceUntilIdle()
        coVerify(exactly = 1) { authRepository.postCapabilities() }

        // Socket drop + reconnect: the server dropped the session's
        // controller, so capabilities must be re-armed.
        isConnected.value = false
        advanceUntilIdle()
        isConnected.value = true
        advanceUntilIdle()
        coVerify(exactly = 2) { authRepository.postCapabilities() }

        // Teardown gate: a stray reconnect while logged out must not re-post.
        isAuthenticated.value = false
        advanceUntilIdle()
        isConnected.value = false
        advanceUntilIdle()
        isConnected.value = true
        advanceUntilIdle()
        coVerify(exactly = 2) { authRepository.postCapabilities() }
    }
}
