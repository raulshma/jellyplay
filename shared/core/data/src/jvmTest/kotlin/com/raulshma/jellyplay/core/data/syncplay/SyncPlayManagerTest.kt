package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.network.websocket.WebSocketEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class SyncPlayManagerTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val webSocketClient: JellyfinWebSocketClient = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val timeSyncManager: TimeSyncManager = mockk(relaxed = true)
    private val serverIdentityStore: ServerIdentityStore = mockk(relaxed = true)
    private val eventHandler: SyncPlayEventHandler = mockk(relaxed = true)
    private val syncPlayController: SyncPlayController = mockk(relaxed = true)
    private val playbackCore: SyncPlayPlaybackCore = mockk(relaxed = true)
    private val queueCore: SyncPlayQueueCore = mockk(relaxed = true)

    private lateinit var manager: SyncPlayManager

    @BeforeTest
    fun setup() {
        every { webSocketClient.isConnected } returns MutableStateFlow(true)
        every { timeSyncManager.pingUpdated } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { timeSyncManager.remoteNow() } returns System.currentTimeMillis()
        coEvery { apiClient.postCapabilities() } returns Result.success(Unit)
        coEvery { apiClient.joinSyncPlayGroup(any()) } returns Result.success(Unit)
        coEvery { apiClient.getSyncPlayInfo(any()) } returns Result.failure(Exception("Not in group"))

        manager = SyncPlayManager(
            apiClient = apiClient,
            webSocketClient = webSocketClient,
            authRepository = authRepository,
            timeSyncManager = timeSyncManager,
            serverIdentityStore = serverIdentityStore,
            eventHandler = eventHandler,
            syncPlayController = syncPlayController,
            playbackCore = playbackCore,
            queueCore = queueCore,
        )
    }

    @Test
    fun `initial state has no active group`() {
        assertNull(manager.activeGroupId)
        assertNull(manager.currentGroup)
        assertFalse(manager.isInSyncPlaySession)
    }

    @Test
    fun `reset clears all state`() {
        manager.reset()

        assertNull(manager.activeGroupId)
        assertNull(manager.currentGroup)
        assertFalse(manager.isInSyncPlaySession)
    }

    @Test
    fun `estimateCurrentTicks adds elapsed time`() {
        val positionTicks = 10000000L
        val whenMs = System.currentTimeMillis() - 1000L
        every { timeSyncManager.remoteNow() } returns System.currentTimeMillis()

        val estimated = manager.estimateCurrentTicks(positionTicks, whenMs)

        assertTrue(estimated > positionTicks)
    }

    @Test
    fun `remoteNow delegates to timeSyncManager`() {
        val expected = 123456789L
        every { timeSyncManager.remoteNow() } returns expected

        assertEquals(expected, manager.remoteNow())
    }

    @Test
    fun `createGroup calls API`() = runTest {
        coEvery { apiClient.createSyncPlayGroup("Test Group") } returns Result.success(Unit)

        val result = manager.createGroup("Test Group")

        assertTrue(result.isSuccess)
        coEvery { apiClient.createSyncPlayGroup("Test Group") }
    }

    @Test
    fun `createGroup returns success even when API returns failure`() = runTest {
        coEvery { apiClient.postCapabilities() } returns Result.success(Unit)
        coEvery { apiClient.createSyncPlayGroup("Test Group") } returns
            Result.failure(Exception("Network error"))

        val result = manager.createGroup("Test Group")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `reset performs the full teardown`() {
        manager.reset()

        assertNull(manager.currentGroup)
        assertNull(manager.activeGroupId)
        assertFalse(manager.isInSyncPlaySession)
        verify { queueCore.clear() }
        verify { playbackCore.onGroupLeft() }
        verify { timeSyncManager.stop() }
        verify { webSocketClient.disconnect() }
    }

    @Test
    fun `GroupLeft clears session state but keeps the listener and websocket alive`() = runBlocking {
        val wsEvents = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 8)
        every { webSocketClient.events } returns wsEvents
        every { eventHandler.parse("GroupLeft", any()) } returns SyncPlayEvent.GroupLeft
        val groupLeftEvent = WebSocketEvent(type = "GroupLeft", data = JSONObject(), rawText = "{}")
        val received = ConcurrentLinkedQueue<SyncPlayEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            manager.events.collect { received.add(it) }
        }

        manager.startListening()
        withTimeout(5_000L) { wsEvents.subscriptionCount.first { it > 0 } }
        wsEvents.emit(groupLeftEvent)
        withTimeout(5_000L) { while (received.isEmpty()) delay(10) }

        assertNull(manager.currentGroup)
        assertFalse(manager.isInSyncPlaySession)
        verify(exactly = 0) { timeSyncManager.stop() }
        verify(exactly = 0) { webSocketClient.disconnect() }

        // The event listener survives the GroupLeft teardown — a second event
        // is still delivered, pinning the "keep listening for rejoin" level.
        wsEvents.emit(groupLeftEvent)
        withTimeout(5_000L) { while (received.size < 2) delay(10) }

        collector.cancel()
    }
}
