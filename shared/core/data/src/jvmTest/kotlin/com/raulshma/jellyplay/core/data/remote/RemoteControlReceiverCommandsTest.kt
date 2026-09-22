package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.network.websocket.WebSocketEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the additions to the (now jvmShared) [RemoteControlReceiver]
 * on the PLAIN JVM lane — the same class the desktop shell binds, so this
 * suite is also the compile-and-behave proof of the androidMain → jvmShared
 * promotion:
 *
 *  - the navigation-ladder general commands route to the
 *    [RemoteNavigationBridge] (NEVER to a playback dispatcher);
 *  - `DisplayContent` requires BOTH the opt-in preference and an idle
 *    device (no bound video engine, nothing playing in the audio queue),
 *    and carries the ItemId payload through the legacy message-type shape
 *    as well;
 *  - `TakeScreenshot` drives the engine registry's screenshot flow, or the
 *    standard notice when nothing is bound.
 */
class RemoteControlReceiverCommandsTest {

    private val webSocketClient: JellyfinWebSocketClient = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val videoDispatcher: VideoRemoteControlDispatcher = mockk(relaxed = true)
    private val audioDispatcher: AudioRemoteControlDispatcher = mockk(relaxed = true)
    private val uiDispatcher: UiRemoteControlDispatcher = mockk(relaxed = true)
    private val activePlayerController: ActivePlayerController = mockk(relaxed = true)
    private val securityStore: SecurityStore = mockk(relaxed = true)
    private val audioQueueManager: AudioQueueManager = mockk(relaxed = true)
    private val bridge = RemoteNavigationBridge()

    private val events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 16)
    private val security = MutableStateFlow(SecuritySlice(remoteControlEnabled = true))

    @BeforeTest
    fun setUp() {
        every { webSocketClient.events } returns events
        every { authRepository.isAuthenticated } returns MutableStateFlow(true)
        every { securityStore.security } returns security
        // Both persisted-read gates (start()'s remote-control check and
        // DisplayContent's opt-in) go through firstPersistedSecurity — answer
        // from the same flow the tests toggle via `security.value = …`.
        coEvery { securityStore.firstPersistedSecurity() } answers { security.value }
        every { audioQueueManager.currentPlayingItemId } returns MutableStateFlow(null)
        every { activePlayerController.engine } returns null
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.failure(IllegalStateException("down"))
    }

    private fun receiver() = RemoteControlReceiver(
        webSocketClient = webSocketClient,
        authRepository = authRepository,
        mediaRepository = mediaRepository,
        videoDispatcher = videoDispatcher,
        audioDispatcher = audioDispatcher,
        uiDispatcher = uiDispatcher,
        activePlayerController = activePlayerController,
        securityStore = securityStore,
        remoteNavigationBridge = bridge,
        audioQueueManager = audioQueueManager,
    )

    private fun startAndAwaitSubscription(r: RemoteControlReceiver) {
        r.start()
        // The collector subscribes on Dispatchers.Default; give it a moment
        // so the SharedFlow emissions below always land on an active
        // subscriber (the androidHostTest twin uses 1s under Robolectric
        // load; plain JVM needs less).
        Thread.sleep(500)
    }

    private fun generalCommand(name: String, arguments: JSONObject? = null) = WebSocketEvent(
        type = "GeneralCommand",
        data = JSONObject()
            .put("Name", name)
            .apply { arguments?.let { put("Arguments", it) } },
        rawText = "{}",
    )

    /**
     * Collects bridge targets into [sink] while [emit] re-fires until one
     * arrives (SharedFlow has no replay: emission can race the collector's
     * subscription under load — re-emit, the receiver is idempotent).
     */
    private suspend fun awaitFirstTarget(sink: CompletableDeferred<NavigationTarget>, emit: () -> Unit) {
        val deadline = System.currentTimeMillis() + 3_000
        while (!sink.isCompleted && System.currentTimeMillis() < deadline) {
            emit()
            delay(150) // suspend, so the launched collector coroutine runs
        }
    }

    // ── navigation ladder ─────────────────────────────────────────────────

    @Test
    fun `every navigation command maps onto its bridge target and never touches a dispatcher`() = runBlocking {
        val cases: List<Pair<String, NavigationTarget>> = listOf(
            "Back" to NavigationTarget.GoBack,
            "Select" to NavigationTarget.InvokeSelect,
            "MoveUp" to NavigationTarget.MoveFocus(RemoteFocusDirection.UP),
            "MoveDown" to NavigationTarget.MoveFocus(RemoteFocusDirection.DOWN),
            "MoveLeft" to NavigationTarget.MoveFocus(RemoteFocusDirection.LEFT),
            "MoveRight" to NavigationTarget.MoveFocus(RemoteFocusDirection.RIGHT),
            "GoHome" to NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.HOME),
            "GoToSettings" to NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SETTINGS),
            "GoToSearch" to NavigationTarget.GoToTopLevel(RemoteTopLevelDestination.SEARCH),
            "ToggleContextMenu" to NavigationTarget.OpenContextMenu,
        )
        val r = receiver()
        startAndAwaitSubscription(r)
        for ((wireName, expected) in cases) {
            val received = CompletableDeferred<NavigationTarget>()
            val job = launch { received.complete(bridge.targets.first()) }
            awaitFirstTarget(received) { events.tryEmit(generalCommand(wireName)) }
            assertEquals(expected, withTimeout(3_000) { received.await() }, wireName)
            job.cancel()
        }
        coVerify(exactly = 0) { videoDispatcher.handleGeneral(any()) }
        coVerify(exactly = 0) { audioDispatcher.handleGeneral(any()) }
        r.stop()
    }

    // ── DisplayContent ────────────────────────────────────────────────────

    @Test
    fun `DisplayContent is dropped while the opt-in is off`() = runBlocking {
        security.value = SecuritySlice(remoteControlEnabled = true, remoteDisplayContentEnabled = false)
        val r = receiver()
        startAndAwaitSubscription(r)
        val received = mutableListOf<NavigationTarget>()
        val job = launch { bridge.targets.collect { received.add(it) } }
        delay(200) // suspend so the collector coroutine subscribes before the emission

        events.tryEmit(
            generalCommand(
                "DisplayContent",
                JSONObject().put("ItemId", "i1").put("ItemName", "Movie").put("ItemType", "Movie"),
            ),
        )
        delay(300) // suspend so the collector drains before the assert

        assertTrue(received.isEmpty(), "no navigation may happen while the opt-in is off (got $received)")
        job.cancel()
        r.stop()
    }

    @Test
    fun `DisplayContent navigates when opted in and idle`() = runBlocking {
        security.value = SecuritySlice(remoteControlEnabled = true, remoteDisplayContentEnabled = true)
        val r = receiver()
        startAndAwaitSubscription(r)

        val received = CompletableDeferred<NavigationTarget>()
        val job = launch { received.complete(bridge.targets.first()) }
        awaitFirstTarget(received) {
            events.tryEmit(
                generalCommand(
                    "DisplayContent",
                    JSONObject().put("ItemId", "i1").put("ItemName", "Movie").put("ItemType", "Movie"),
                ),
            )
        }

        assertEquals(NavigationTarget.OpenMediaDetail("i1"), withTimeout(3_000) { received.await() })
        job.cancel()
        r.stop()
    }

    @Test
    fun `DisplayContent is dropped during active audio playback`() = runBlocking {
        security.value = SecuritySlice(remoteControlEnabled = true, remoteDisplayContentEnabled = true)
        every { audioQueueManager.currentPlayingItemId } returns MutableStateFlow("song-1")
        val r = receiver()
        startAndAwaitSubscription(r)
        val received = mutableListOf<NavigationTarget>()
        val job = launch { bridge.targets.collect { received.add(it) } }
        delay(200) // suspend so the collector coroutine subscribes before the emission

        events.tryEmit(generalCommand("DisplayContent", JSONObject().put("ItemId", "i1")))
        delay(300) // suspend so the collector drains before the assert

        assertTrue(received.isEmpty(), "playback-active DisplayContent must be dropped silently (got $received)")
        job.cancel()
        r.stop()
    }

    @Test
    fun `DisplayContent is dropped while a video engine is bound`() = runBlocking {
        security.value = SecuritySlice(remoteControlEnabled = true, remoteDisplayContentEnabled = true)
        every { activePlayerController.engine } returns mockk(relaxed = true)
        val r = receiver()
        startAndAwaitSubscription(r)
        val received = mutableListOf<NavigationTarget>()
        val job = launch { bridge.targets.collect { received.add(it) } }
        delay(200) // suspend so the collector coroutine subscribes before the emission

        events.tryEmit(generalCommand("DisplayContent", JSONObject().put("ItemId", "i1")))
        delay(300) // suspend so the collector drains before the assert

        assertTrue(received.isEmpty(), "engine-bound DisplayContent must be dropped silently (got $received)")
        job.cancel()
        r.stop()
    }

    @Test
    fun `DisplayContent without an ItemId argument is dropped`() = runBlocking {
        security.value = SecuritySlice(remoteControlEnabled = true, remoteDisplayContentEnabled = true)
        val r = receiver()
        startAndAwaitSubscription(r)
        val received = mutableListOf<NavigationTarget>()
        val job = launch { bridge.targets.collect { received.add(it) } }
        delay(200) // suspend so the collector coroutine subscribes before the emission

        events.tryEmit(generalCommand("DisplayContent", JSONObject().put("ItemName", "no id")))
        delay(300) // suspend so the collector drains before the assert

        assertTrue(received.isEmpty(), "blank ItemId is unparseable — dropped (got $received)")
        job.cancel()
        r.stop()
    }

    @Test
    fun `the legacy DisplayContent message type carries the payload through the same gate`() = runBlocking {
        security.value = SecuritySlice(remoteControlEnabled = true, remoteDisplayContentEnabled = true)
        val r = receiver()
        startAndAwaitSubscription(r)

        val received = CompletableDeferred<NavigationTarget>()
        val job = launch { received.complete(bridge.targets.first()) }
        awaitFirstTarget(received) {
            events.tryEmit(
                WebSocketEvent(
                    type = "DisplayContent",
                    data = JSONObject().put("ItemId", "legacy-1"),
                    rawText = "{}",
                ),
            )
        }

        assertEquals(NavigationTarget.OpenMediaDetail("legacy-1"), withTimeout(3_000) { received.await() })
        job.cancel()
        r.stop()
    }

    // ── TakeScreenshot ────────────────────────────────────────────────────

    @Test
    fun `TakeScreenshot requests a screenshot while an engine is bound`() {
        every { activePlayerController.engine } returns mockk(relaxed = true)
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(generalCommand("TakeScreenshot"))

        verify(timeout = 3_000) { activePlayerController.requestScreenshot() }
        r.stop()
    }

    @Test
    fun `TakeScreenshot with no engine surfaces the standard notice`() = runBlocking {
        every { activePlayerController.engine } returns null
        val r = receiver()
        startAndAwaitSubscription(r)

        val payload = CompletableDeferred<DisplayMessagePayload>()
        val job = launch { payload.complete(r.displayMessages.first()) }
        val deadline = System.currentTimeMillis() + 3_000
        while (!payload.isCompleted && System.currentTimeMillis() < deadline) {
            events.tryEmit(generalCommand("TakeScreenshot"))
            delay(150)
        }
        val notice = withTimeout(3_000) { payload.await() }
        job.cancel()

        assertEquals("Nothing playing to capture", notice.text)
        assertNull(notice.timeoutMs)
        verify(exactly = 0) { activePlayerController.requestScreenshot() }
        r.stop()
    }

    // ── malformed inputs ──────────────────────────────────────────────────

    @Test
    fun `malformed general command arguments never throw`() = runBlocking {
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(WebSocketEvent("GeneralCommand", JSONObject().put("Name", ""), rawText = "{}"))
        events.tryEmit(WebSocketEvent("GeneralCommand", JSONObject(), rawText = "{}"))
        events.tryEmit(
            WebSocketEvent(
                "GeneralCommand",
                JSONObject().put("Name", "DisplayContent").put("Arguments", "not-an-object"),
                rawText = "{}",
            ),
        )

        delay(300) // suspend so the receiver's collector drains before the assert
        coVerify(exactly = 0) { videoDispatcher.handleGeneral(any()) }
        verify(exactly = 0) { activePlayerController.requestScreenshot() }
        r.stop()
    }
}
