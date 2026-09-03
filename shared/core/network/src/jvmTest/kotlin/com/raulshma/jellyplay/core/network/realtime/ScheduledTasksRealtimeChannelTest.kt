package com.raulshma.jellyplay.core.network.realtime

import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.network.websocket.WebSocketEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the subscription lifecycle + payload handling of
 * [ScheduledTasksRealtimeChannel]:
 *  1. the first collector sends `ScheduledTasksInfoStart` with the 1 Hz data
 *     string ("0,1000") — Jellyfin pushes nothing without it;
 *  2. the Start message is re-sent after every socket false→true reconnect
 *     (the server drops per-socket subscriptions on disconnect);
 *  3. a `ScheduledTasksInfo` array push is parsed into task models; a push
 *     without a parseable array is skipped entirely — no emission AND no
 *     [ScheduledTasksRealtimeChannel.lastPushAtMs] stamp;
 *  4. identical consecutive pushes are deduplicated (distinctUntilChanged);
 *  5. `scanLibraryTask` projects the `RefreshLibrary` task only;
 *  6. the channel sends `ScheduledTasksInfoStop` when its scope is cancelled.
 *
 * Pure policy tests — the socket is a stubbed [JellyfinWebSocketClient]
 * (same shape as [UserDataRealtimeChannelTest]); no real socket involved.
 */
class ScheduledTasksRealtimeChannelTest {

    private lateinit var webSocketClient: JellyfinWebSocketClient
    private lateinit var channelScope: CoroutineScope

    private val socketEvents = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)
    private val connected = MutableStateFlow(false)

    @BeforeTest
    fun setUp() {
        webSocketClient = mockk(relaxed = true)
        every { webSocketClient.events } returns socketEvents
        every { webSocketClient.isConnected } returns connected
    }

    private fun TestScope.createChannel(): ScheduledTasksRealtimeChannel {
        channelScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        return ScheduledTasksRealtimeChannel(webSocketClient, channelScope)
    }

    @Test
    fun `first collector sends ScheduledTasksInfoStart with the 1Hz data string`() = runTest {
        connected.value = true
        val channel = createChannel()
        val received = mutableListOf<List<com.raulshma.jellyplay.core.model.ScheduledTaskInfo>>()
        val job = launch { channel.tasks.take(1).toList(received) }
        runCurrent()

        verify(exactly = 1) {
            webSocketClient.sendMessageWithDataString("ScheduledTasksInfoStart", "0,1000")
        }
        job.cancel()
    }

    @Test
    fun `subscribes once the socket reports connected after starting disconnected`() = runTest {
        val channel = createChannel()
        val received = mutableListOf<List<com.raulshma.jellyplay.core.model.ScheduledTaskInfo>>()
        val job = launch { channel.tasks.take(1).toList(received) }
        runCurrent()

        connected.value = true
        runCurrent()

        verify(atLeast = 1) {
            webSocketClient.sendMessageWithDataString("ScheduledTasksInfoStart", any())
        }
        job.cancel()
    }

    @Test
    fun `re-sends the Start message after a socket reconnect`() = runTest {
        connected.value = true
        val channel = createChannel()
        val received = mutableListOf<List<com.raulshma.jellyplay.core.model.ScheduledTaskInfo>>()
        val job = launch { channel.tasks.take(1).toList(received) }
        runCurrent()

        connected.value = false
        runCurrent()
        connected.value = true
        runCurrent()

        // One subscribe on the first collector + one re-subscribe on the
        // false→true reconnect transition — the server dropped the old one.
        verify(exactly = 2) {
            webSocketClient.sendMessageWithDataString("ScheduledTasksInfoStart", "0,1000")
        }
        job.cancel()
    }

    @Test
    fun `parses a ScheduledTasksInfo push into task models and stamps lastPushAtMs`() = runTest {
        connected.value = true
        val channel = createChannel()
        val received = mutableListOf<List<com.raulshma.jellyplay.core.model.ScheduledTaskInfo>>()
        val job = launch { channel.tasks.take(1).toList(received) }
        runCurrent()

        socketEvents.tryEmit(
            scheduledTasksEvent(
                task(
                    id = "t1",
                    key = "RefreshLibrary",
                    name = "Scan media library",
                    state = "Running",
                    progress = 42.5,
                    category = "Library",
                ),
            ),
        )
        runCurrent()

        assertEquals(1, received.size)
        val task = received[0].single()
        assertEquals("t1", task.id)
        assertEquals("RefreshLibrary", task.key)
        assertEquals("Scan media library", task.name)
        assertEquals(TaskState.RUNNING, task.state)
        assertEquals(42.5, task.currentProgressPercentage)
        assertEquals("Library", task.category)
        assertTrue(channel.lastPushAtMs > 0, "a parsed push stamps lastPushAtMs")
        job.cancel()
    }

    @Test
    fun `skips a push without a parseable array and leaves lastPushAtMs unstamped`() = runTest {
        connected.value = true
        val channel = createChannel()
        val received = mutableListOf<List<com.raulshma.jellyplay.core.model.ScheduledTaskInfo>>()
        val job = launch { channel.tasks.take(1).toList(received) }
        runCurrent()

        // Object-payload shape (dataArray == null): the parser cannot read it,
        // so the last good list stays and freshness is NOT re-stamped.
        socketEvents.tryEmit(
            WebSocketEvent(
                type = "ScheduledTasksInfo",
                data = JSONObject().put("Data", "garbage"),
                dataArray = null,
                rawText = "garbage",
            ),
        )
        runCurrent()

        assertTrue(received.isEmpty())
        assertEquals(0L, channel.lastPushAtMs)
        job.cancel()
    }

    @Test
    fun `identical consecutive pushes are deduplicated`() = runTest {
        connected.value = true
        val channel = createChannel()
        val received = mutableListOf<List<com.raulshma.jellyplay.core.model.ScheduledTaskInfo>>()
        val job = launch { channel.tasks.take(2).toList(received) }
        runCurrent()

        // The server pushes the full list at a fixed cadence; only changed
        // content may reach consumers. First push + a CHANGED second push
        // complete the take(2); the third (identical to the second) must not.
        socketEvents.tryEmit(scheduledTasksEvent(task(id = "t1", state = "Idle")))
        runCurrent()
        socketEvents.tryEmit(scheduledTasksEvent(task(id = "t1", state = "Running")))
        runCurrent()
        socketEvents.tryEmit(scheduledTasksEvent(task(id = "t1", state = "Running")))
        runCurrent()

        assertEquals(2, received.size)
        assertEquals(TaskState.RUNNING, received[1].single().state)
        job.cancel()
    }

    @Test
    fun `scanLibraryTask projects only the RefreshLibrary task`() = runTest {
        connected.value = true
        val channel = createChannel()
        val received = mutableListOf<com.raulshma.jellyplay.core.model.ScheduledTaskInfo?>()
        val job = launch { channel.scanLibraryTask.take(2).toList(received) }
        runCurrent()

        socketEvents.tryEmit(
            scheduledTasksEvent(
                task(id = "t1", key = "OptimizeDatabase", name = "Optimize database"),
                task(id = "t2", key = "RefreshLibrary", name = "Scan media library", state = "Running"),
            ),
        )
        runCurrent()

        assertEquals(1, received.size)
        assertEquals("t2", received[0]?.id)
        job.cancel()
    }

    @Test
    fun `idle push with no progress parses a null percentage`() = runTest {
        connected.value = true
        val channel = createChannel()
        val received = mutableListOf<List<com.raulshma.jellyplay.core.model.ScheduledTaskInfo>>()
        val job = launch { channel.tasks.take(1).toList(received) }
        runCurrent()

        socketEvents.tryEmit(scheduledTasksEvent(task(id = "t1", state = "Idle")))
        runCurrent()

        val task = received[0].single()
        assertEquals(TaskState.IDLE, task.state)
        assertNull(task.currentProgressPercentage)
        job.cancel()
    }

    @Test
    fun `cancelling the channel scope sends ScheduledTasksInfoStop`() = runTest {
        connected.value = true
        val channel = createChannel()
        val received = mutableListOf<List<com.raulshma.jellyplay.core.model.ScheduledTaskInfo>>()
        val job = launch { channel.tasks.take(1).toList(received) }
        runCurrent()

        // Cancelling the channel's own scope tears down the shared upstream;
        // awaitClose must send the Stop message (the server keeps pushing
        // TaskInfo[] at 1 Hz forever otherwise).
        channelScope.cancel()
        runCurrent()

        // Match the default `data` arg with any(): org.json.JSONObject has no
        // structural equals, so an eq({}) matcher against the recorded call's
        // own default JSONObject instance can never match.
        verify(atLeast = 1) { webSocketClient.sendMessage("ScheduledTasksInfoStop", any()) }
        job.cancel()
    }

    @Test
    fun `second collector replays the last push once`() = runTest {
        connected.value = true
        val channel = createChannel()
        val first = mutableListOf<List<com.raulshma.jellyplay.core.model.ScheduledTaskInfo>>()
        val firstJob = launch { channel.tasks.take(1).toList(first) }
        runCurrent()
        socketEvents.tryEmit(scheduledTasksEvent(task(id = "t1")))
        runCurrent()
        assertEquals(1, first.size)
        firstJob.cancel()

        val second = mutableListOf<List<com.raulshma.jellyplay.core.model.ScheduledTaskInfo>>()
        val secondJob = launch { second.add(channel.tasks.first()) }
        runCurrent()

        assertEquals(1, second.size, "replay=1 gives a new collector the last list immediately")
        assertEquals("t1", second[0].single().id)
        secondJob.cancel()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Builds a `ScheduledTasksInfo` event whose Data is the task array. */
    private fun scheduledTasksEvent(vararg tasks: JSONObject): WebSocketEvent {
        val array = JSONArray(tasks.toList())
        return WebSocketEvent(
            type = "ScheduledTasksInfo",
            data = JSONObject(),
            dataArray = array,
            rawText = array.toString(),
        )
    }

    /** Minimal PascalCase `TaskInfo` payload matching the server's WS contract. */
    private fun task(
        id: String,
        key: String = "Key-$id",
        name: String = "Task $id",
        state: String = "Idle",
        progress: Double? = null,
        category: String? = null,
    ): JSONObject {
        val obj = JSONObject()
            .put("Id", id)
            .put("Key", key)
            .put("Name", name)
            .put("State", state)
            .put("Hidden", false)
        if (progress != null) obj.put("CurrentProgressPercentage", progress)
        if (category != null) obj.put("Category", category)
        return obj
    }
}
