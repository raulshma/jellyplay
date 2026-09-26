package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.websocket.WebSocketEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject

/**
 * Pins the pure halves of the [DesktopIdleAmbientController] seam (the idle
 * ladder itself stays pinned by [DesktopIdleMonitorTest]):
 *
 *  - the session-count collector: only `Sessions` pushes count, only while
 *    idle (the gate the inlined scaffold effect had), the count is the
 *    sessions carrying a `NowPlayingItem`, a malformed push degrades to 0
 *    without killing the collector, and a non-idle period's pushes are
 *    dropped;
 *  - [resolveIdleOverlayIdentity]: the legacy two-key server match
 *    (stored id OR address) and the signed-out null pair.
 *
 * Steps via `runCurrent` — never `advanceUntilIdle`, which would spin the
 * monitor's endless 1 s delay loop forever under virtual time (the settle
 * trap CONTEXT.md warns about); `isIdle` is driven by hand through
 * [DesktopIdleAmbientController.tick].
 */
class DesktopIdleAmbientControllerTest {

    // Mutable virtual clock (the DesktopIdleMonitorTest idiom): the monitor
    // stamps lastInputMs from nowMs() AT CONSTRUCTION, so a constant nowMs
    // would freeze the elapsed debounce at zero — the controller could never
    // go idle. Start at 0 and advance past the 1 min debounce in enterIdle.
    private var now = 0L

    private val events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 16)

    private fun TestScope.startController() = DesktopIdleAmbientController(
        settings = { IdleAmbientSettings(enabled = true, timeoutMin = 1L) },
        isAudioPlaying = { false },
        isVideoActive = { false },
        isWindowActive = { true },
        sessionsEvents = events,
        nowMs = { now },
    ).also { controller ->
        controller.start(this)
        runCurrent() // the collectors reach their first suspension point
    }

    /** Advances the clock past the debounce, steps one tick, settles collectors. */
    private fun TestScope.enterIdle(controller: DesktopIdleAmbientController) {
        now += 60_000L
        controller.tick()
        runCurrent()
    }

    private fun TestScope.emitAndRun(event: WebSocketEvent) {
        events.tryEmit(event)
        runCurrent()
    }

    // The `data = JSONObject()` fixture is the repo-wide WebSocketEvent idiom
    // (ScheduledTasksRealtimeChannelTest, RemoteControlReceiverCommandsTest):
    // event.data is a non-null org.json.JSONObject by type, and the controller
    // deliberately decodes rawText with kotlinx instead — so the field stays
    // empty here and the raw envelope carries the payload.
    private fun sessionsEvent(rawText: String) = WebSocketEvent(
        type = "Sessions",
        data = JSONObject(),
        rawText = rawText,
    )

    // Two of the three sessions are playing something; the wire DTO keeps
    // the PascalCase mapping (NowPlayingItem) the receiver's socket
    // actually delivers.
    private val twoPlayingEnvelope = """
        {"MessageType":"Sessions","Data":[
            {"Id":"s1","UserName":"alice","NowPlayingItem":{"Id":"i1","Name":"Movie"}},
            {"Id":"s2","UserName":"bob"},
            {"Id":"s3","UserName":"carol","NowPlayingItem":{"Name":"Song"}}
        ]}
    """.trimIndent()

    @Test
    fun `sessions pushes while idle count the sessions that are playing something`() = runTest {
        val controller = startController()
        enterIdle(controller)

        emitAndRun(sessionsEvent(twoPlayingEnvelope))

        assertEquals(2, controller.activeRemoteSessionCount.value)
        controller.stop()
    }

    @Test
    fun `events while not idle never touch the count`() = runTest {
        val controller = startController()

        emitAndRun(sessionsEvent(twoPlayingEnvelope))
        assertEquals(0, controller.activeRemoteSessionCount.value, "not idle: the push is dropped")

        enterIdle(controller)
        emitAndRun(sessionsEvent(twoPlayingEnvelope))
        assertEquals(2, controller.activeRemoteSessionCount.value)
        controller.stop()
    }

    @Test
    fun `non-sessions pushes are ignored`() = runTest {
        val controller = startController()
        enterIdle(controller)

        emitAndRun(WebSocketEvent(type = "GeneralCommand", data = JSONObject(), rawText = "{}"))
        emitAndRun(sessionsEvent(twoPlayingEnvelope))
        emitAndRun(WebSocketEvent(type = "UserDataChanged", data = JSONObject(), rawText = "{}"))

        assertEquals(2, controller.activeRemoteSessionCount.value)
        controller.stop()
    }

    @Test
    fun `a malformed sessions push degrades to zero without killing the collector`() = runTest {
        val controller = startController()
        enterIdle(controller)

        emitAndRun(sessionsEvent("not json at all"))
        assertEquals(0, controller.activeRemoteSessionCount.value, "a malformed push reads as zero playing")

        emitAndRun(sessionsEvent(twoPlayingEnvelope))
        assertEquals(2, controller.activeRemoteSessionCount.value, "the collector survived the malformed push")
        controller.stop()
    }

    // ── resolveIdleOverlayIdentity ───────────────────────────────────────

    private fun user(serverId: String? = null, serverAddress: String = "https://x") = UserInfo(
        id = "u1",
        name = "alice",
        serverAddress = serverAddress,
        accessToken = "t",
        serverId = serverId,
    )

    private fun server(id: String, address: String, name: String) = ServerInfo(
        id = id,
        name = name,
        address = address,
    )

    @Test
    fun `server resolves by stored server id first then by address`() {
        val byId = resolveIdleOverlayIdentity(
            currentUser = user(serverId = "srv-1"),
            servers = listOf(server("other", "https://y", "Wrong"), server("srv-1", "https://y", "By Id")),
        )
        assertEquals("By Id", byId.serverName)

        val byAddress = resolveIdleOverlayIdentity(
            currentUser = user(serverId = null, serverAddress = "https://y"),
            servers = listOf(server("srv-1", "https://y", "By Address")),
        )
        assertEquals("By Address", byAddress.serverName)
    }

    @Test
    fun `no server match or signed out yields the null identity pair`() {
        assertEquals("alice", resolveIdleOverlayIdentity(user(), servers = emptyList()).userName)
        assertEquals(
            null,
            resolveIdleOverlayIdentity(user(serverId = "srv-1"), servers = emptyList()).serverName,
        )
        assertEquals(
            IdleOverlayIdentity(serverName = null, userName = null),
            resolveIdleOverlayIdentity(currentUser = null, servers = listOf(server("s", "https://x", "Zeta"))),
        )
    }
}
