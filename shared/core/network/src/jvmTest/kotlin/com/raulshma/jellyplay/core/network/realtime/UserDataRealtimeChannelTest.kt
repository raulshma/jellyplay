package com.raulshma.jellyplay.core.network.realtime

import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.network.websocket.WebSocketEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure policy tests for [UserDataRealtimeChannel]: parse + current-user
 * filtering + no-replay, driven through a stubbed [JellyfinWebSocketClient]
 * events flow. No socket / MockWebServer needed — the channel is a pure
 * filter/map over the inbound stream.
 */
class UserDataRealtimeChannelTest {

    private lateinit var webSocketClient: JellyfinWebSocketClient
    private lateinit var engine: JellyfinApiEngine

    private val socketEvents = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)

    @BeforeTest
    fun setUp() {
        webSocketClient = mockk()
        engine = mockk()
        every { webSocketClient.events } returns socketEvents
        every { engine.currentUser } returns MutableStateFlow(userInfo("u1"))
    }

    private fun TestScope.createChannel(): UserDataRealtimeChannel =
        UserDataRealtimeChannel(
            webSocketClient,
            engine,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
        )

    @Test
    fun `emits parsed change for current user`() = runTest {
        val channel = createChannel()
        val received = mutableListOf<UserDataChange>()
        val job = launch { channel.changes.toList(received) }
        runCurrent()

        socketEvents.tryEmit(userDataChangedEvent(userId = "u1", itemIds = listOf("i1", "i2")))
        runCurrent()

        assertEquals(listOf(UserDataChange(userId = "u1", itemIds = listOf("i1", "i2"))), received)
        job.cancel()
    }

    @Test
    fun `drops events for another user id`() = runTest {
        val channel = createChannel()
        val received = mutableListOf<UserDataChange>()
        val job = launch { channel.changes.toList(received) }
        runCurrent()

        socketEvents.tryEmit(userDataChangedEvent(userId = "someone-else", itemIds = listOf("i1")))
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(received.isEmpty())
        job.cancel()
    }

    @Test
    fun `drops malformed payload without UserId`() = runTest {
        val channel = createChannel()
        val received = mutableListOf<UserDataChange>()
        val job = launch { channel.changes.toList(received) }
        runCurrent()

        socketEvents.tryEmit(userDataChangedEvent(userId = null, itemIds = listOf("i1")))
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(received.isEmpty())
        job.cancel()
    }

    @Test
    fun `drops blank item ids and keeps valid ones`() = runTest {
        val channel = createChannel()
        val received = mutableListOf<UserDataChange>()
        val job = launch { channel.changes.toList(received) }
        runCurrent()

        socketEvents.tryEmit(userDataChangedEvent(userId = "u1", itemIds = listOf("i1", "", "i2")))
        runCurrent()

        assertEquals(listOf(UserDataChange(userId = "u1", itemIds = listOf("i1", "i2"))), received)
        job.cancel()
    }

    @Test
    fun `second collector does not receive previous emission`() = runTest {
        val channel = createChannel()
        val first = mutableListOf<UserDataChange>()
        val firstJob = launch { channel.changes.toList(first) }
        runCurrent()
        socketEvents.tryEmit(userDataChangedEvent(userId = "u1", itemIds = listOf("i1")))
        runCurrent()
        assertEquals(1, first.size)
        firstJob.cancel()

        // replay = 0: even though the WhileSubscribed grace window keeps the
        // upstream alive through this gap, the late collector must see nothing.
        val second = mutableListOf<UserDataChange>()
        val secondJob = launch { channel.changes.toList(second) }
        runCurrent()

        assertTrue(second.isEmpty())
        secondJob.cancel()
    }

    private fun userInfo(id: String) = UserInfo(
        id = id,
        name = "Tester",
        serverAddress = "http://server",
        accessToken = "token",
    )

    /** Builds a `UserDataChanged` event; a null [userId] omits the field (malformed). */
    private fun userDataChangedEvent(userId: String?, itemIds: List<String>): WebSocketEvent {
        val data = JSONObject()
        if (userId != null) data.put("UserId", userId)
        data.put(
            "UserDataList",
            JSONArray(itemIds.map { itemId -> JSONObject().put("ItemId", itemId) }),
        )
        return WebSocketEvent(type = "UserDataChanged", data = data, rawText = data.toString())
    }
}
