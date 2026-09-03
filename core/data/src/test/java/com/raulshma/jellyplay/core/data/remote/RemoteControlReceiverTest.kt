package com.raulshma.jellyplay.core.data.remote


import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.remote.GeneralCommand
import com.raulshma.jellyplay.core.model.remote.PlayRequest
import com.raulshma.jellyplay.core.model.remote.PlaystateCommand
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.network.websocket.WebSocketEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Pins [RemoteControlReceiver]'s Jellyfin remote-control bridge invariants:
 *
 * - The user's remote-control preference gates the whole stream: when disabled,
 *   no WebSocket event is ever handled.
 * - Unauthenticated sessions ignore every event.
 * - "Play" with empty ItemIds is ignored; otherwise the first item's detail
 *   picks the domain (audio media types → audio dispatcher, anything else —
 *   including a failed detail fetch — → video dispatcher) and one fetched
 *   detail serves both routing and the banner payload.
 * - "Playstate" maps the wire command names onto [PlaystateCommand] (Seek
 *   carries ticks; unknown names are dropped) and dispatches to BOTH engines.
 * - "GeneralCommand" parses the arguments map: SetVolume requires a Volume
 *   argument, engine-scoped commands go to both dispatchers, bitrate goes to
 *   video+UI, fullscreen only to UI, DisplayMessage additionally surfaces a
 *   payload, unknown names land on the UI dispatcher as [GeneralCommand.Unknown].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteControlReceiverTest {

    private val webSocketClient: JellyfinWebSocketClient = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val videoDispatcher: VideoRemoteControlDispatcher = mockk(relaxed = true)
    private val audioDispatcher: AudioRemoteControlDispatcher = mockk(relaxed = true)
    private val uiDispatcher: UiRemoteControlDispatcher = mockk(relaxed = true)
    private val activePlayerController: ActivePlayerController = mockk(relaxed = true)
    private val securityStore: SecurityStore = mockk(relaxed = true)

    private val events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 16)

    @Before
    fun setUp() {
        every { webSocketClient.events } returns events
        every { authRepository.isAuthenticated } returns flowOf(true)
        every { securityStore.security } returns MutableStateFlow(SecuritySlice(remoteControlEnabled = true))
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.failure(IOException("down"))
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
    )

    private fun startAndAwaitSubscription(r: RemoteControlReceiver) {
        r.start()
        // The collector subscribes on Dispatchers.Default; give it a moment so
        // the SharedFlow emissions below always land on an active subscriber.
        Thread.sleep(250)
    }

    private fun playEvent(itemIds: List<String>, startPositionTicks: Long = 0L) = WebSocketEvent(
        type = "Play",
        data = JSONObject()
            .put("ItemIds", JSONArray(itemIds))
            .put("StartPositionTicks", startPositionTicks)
            .put("PlayCommand", "PlayNow"),
        rawText = "{}",
    )

    private fun audioDetail(id: String) = Result.success(
        MediaDetail(item = MediaItem(id = id, name = "Song", mediaType = MediaType.MUSIC)),
    )

    // ── gating ───────────────────────────────────────────────────────────

    @Test
    fun `remote control disabled in preferences ignores every event`() {
        every { securityStore.security } returns MutableStateFlow(SecuritySlice(remoteControlEnabled = false))
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(playEvent(listOf("i1")))

        Thread.sleep(300)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
        coVerify(exactly = 0) { videoDispatcher.play(any()) }
        coVerify(exactly = 0) { audioDispatcher.play(any()) }
        r.stop()
    }

    @Test
    fun `unauthenticated sessions ignore every event`() {
        every { authRepository.isAuthenticated } returns flowOf(false)
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(playEvent(listOf("i1")))

        Thread.sleep(300)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
        r.stop()
    }

    // ── Play ─────────────────────────────────────────────────────────────

    @Test
    fun `play with empty ItemIds is ignored`() {
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(playEvent(emptyList()))

        Thread.sleep(300)
        coVerify(exactly = 0) { videoDispatcher.play(any()) }
        coVerify(exactly = 0) { audioDispatcher.play(any()) }
        r.stop()
    }

    @Test
    fun `a failed detail fetch routes play to the video dispatcher with an empty banner`() {
        val r = receiver()
        startAndAwaitSubscription(r)

        val banner = receiveFirst(r.playEvents) {
            events.tryEmit(playEvent(listOf("i1", "i2"), startPositionTicks = 123L))
        }

        assertEquals("i1", banner.itemId)
        assertEquals("", banner.title)
        assertEquals(123L, banner.startPositionTicks)
        coVerify(timeout = 3_000, exactly = 2) { mediaRepository.getMediaDetail("i1", any()) } // one silent retry
        coVerify(timeout = 3_000) {
            videoDispatcher.play(
                match<PlayRequest> {
                    it.itemIds == listOf("i1", "i2") &&
                        it.startPositionTicks == 123L &&
                        it.playCommand == "PlayNow" &&
                        it.mediaSourceId == null &&
                        it.audioStreamIndex == null &&
                        it.subtitleStreamIndex == null
                },
            )
        }
        coVerify(exactly = 0) { audioDispatcher.play(any()) }
        r.stop()
    }

    @Test
    fun `an audio first item routes play to the audio dispatcher with the banner title`() {
        val r = receiver()
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns audioDetail("i1")
        startAndAwaitSubscription(r)

        val banner = receiveFirst(r.playEvents) {
            events.tryEmit(playEvent(listOf("i1")))
        }

        assertEquals("Song", banner.title)
        coVerify(timeout = 3_000) { audioDispatcher.play(match { it.itemIds == listOf("i1") }) }
        coVerify(exactly = 0) { videoDispatcher.play(any()) }
        r.stop()
    }

    // ── Playstate ────────────────────────────────────────────────────────

    @Test
    fun `playstate Seek carries the ticks to both engines`() {
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(
            WebSocketEvent(
                type = "Playstate",
                data = JSONObject().put("Command", "Seek").put("SeekPositionTicks", 42_000_000L),
                rawText = "{}",
            ),
        )

        coVerify(timeout = 3_000) { audioDispatcher.handlePlaystate(PlaystateCommand.Seek(42_000_000L)) }
        coVerify(timeout = 3_000) { videoDispatcher.handlePlaystate(PlaystateCommand.Seek(42_000_000L)) }
        r.stop()
    }

    @Test
    fun `playstate Pause reaches both engines and unknown commands are dropped`() {
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(WebSocketEvent("Playstate", JSONObject().put("Command", "Pause"), rawText = "{}"))
        events.tryEmit(WebSocketEvent("Playstate", JSONObject().put("Command", "Bogus"), rawText = "{}"))

        coVerify(timeout = 3_000) { audioDispatcher.handlePlaystate(PlaystateCommand.Pause) }
        coVerify(timeout = 3_000) { videoDispatcher.handlePlaystate(PlaystateCommand.Pause) }
        Thread.sleep(300)
        coVerify(exactly = 1) { audioDispatcher.handlePlaystate(any()) }
        r.stop()
    }

    // ── GeneralCommand ───────────────────────────────────────────────────

    @Test
    fun `SetVolume parses volume and mute and reaches both engines`() {
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(
            WebSocketEvent(
                type = "GeneralCommand",
                data = JSONObject()
                    .put("Name", "SetVolume")
                    .put("Arguments", JSONObject().put("Volume", "30").put("Mute", "true")),
                rawText = "{}",
            ),
        )

        coVerify(timeout = 3_000) {
            audioDispatcher.handleGeneral(GeneralCommand.SetVolume(volume0to100 = 30, mute = true))
        }
        coVerify(timeout = 3_000) {
            videoDispatcher.handleGeneral(GeneralCommand.SetVolume(volume0to100 = 30, mute = true))
        }
        r.stop()
    }

    @Test
    fun `SetVolume without a Volume argument is dropped`() {
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(
            WebSocketEvent(
                type = "GeneralCommand",
                data = JSONObject().put("Name", "SetVolume"),
                rawText = "{}",
            ),
        )

        Thread.sleep(300)
        coVerify(exactly = 0) { audioDispatcher.handleGeneral(any()) }
        coVerify(exactly = 0) { videoDispatcher.handleGeneral(any()) }
        r.stop()
    }

    @Test
    fun `ToggleFullscreen reaches only the UI dispatcher`() {
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(
            WebSocketEvent(
                type = "GeneralCommand",
                data = JSONObject().put("Name", "ToggleFullscreen"),
                rawText = "{}",
            ),
        )

        coVerify(timeout = 3_000) { uiDispatcher.handleGeneral(GeneralCommand.ToggleFullscreen) }
        coVerify(exactly = 0) { audioDispatcher.handleGeneral(any()) }
        coVerify(exactly = 0) { videoDispatcher.handleGeneral(any()) }
        r.stop()
    }

    @Test
    fun `DisplayMessage surfaces a payload and reaches the UI dispatcher`() {
        val r = receiver()
        startAndAwaitSubscription(r)

        val payload = receiveFirst(r.displayMessages) {
            events.tryEmit(
                WebSocketEvent(
                    type = "GeneralCommand",
                    data = JSONObject()
                        .put("Name", "DisplayMessage")
                        .put("Arguments", JSONObject().put("Header", "Hello").put("Text", "World").put("TimeoutMs", 4000)),
                    rawText = "{}",
                ),
            )
        }

        assertEquals("Hello", payload.header)
        assertEquals("World", payload.text)
        assertEquals(4000, payload.timeoutMs)
        coVerify(timeout = 3_000) {
            uiDispatcher.handleGeneral(match<GeneralCommand.DisplayMessage> { it.header == "Hello" })
        }
        r.stop()
    }

    @Test
    fun `unknown general command names land on the UI dispatcher as Unknown`() {
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(
            WebSocketEvent(
                type = "GeneralCommand",
                data = JSONObject().put("Name", "Bogus"),
                rawText = "{}",
            ),
        )

        coVerify(timeout = 3_000) { uiDispatcher.handleGeneral(GeneralCommand.Unknown("Bogus")) }
        coVerify(exactly = 0) { audioDispatcher.handleGeneral(any()) }
        r.stop()
    }

    // ── unrelated messages ───────────────────────────────────────────────

    @Test
    fun `other message types are ignored`() {
        val r = receiver()
        startAndAwaitSubscription(r)

        events.tryEmit(WebSocketEvent("UserDataChanged", JSONObject(), rawText = "{}"))

        Thread.sleep(300)
        coVerify(exactly = 0) { videoDispatcher.play(any()) }
        coVerify(exactly = 0) { audioDispatcher.handlePlaystate(any()) }
        coVerify(exactly = 0) { uiDispatcher.handleGeneral(any()) }
        r.stop()
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Subscribes to [flow], waits for the subscription to be active, runs
     * [emit], and returns the first value received within the timeout.
     */
    private fun <T> receiveFirst(
        flow: kotlinx.coroutines.flow.SharedFlow<T>,
        timeoutMs: Long = 3_000,
        emit: () -> Unit,
    ): T = runBlocking {
        val received = CompletableDeferred<T>()
        val job = launch { received.complete(flow.first()) }
        // The caller awaits the subscription before emitting (startAndAwaitSubscription),
        // so no subscriptionCount probe here — SharedFlow exposes none.
        emit()
        val value = withTimeout(timeoutMs) { received.await() }
        job.cancel()
        value
    }
}
