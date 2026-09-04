package com.raulshma.jellyplay.core.data.cast.remote

import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SessionNowPlayingItem
import com.raulshma.jellyplay.core.model.SessionPlayState
import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins [JellyfinRemotePlayCastStrategy]'s remote-session transport invariants:
 *
 * - `connect` only accepts a device whose tag is a [SessionInfo]; anything
 *   else is a silent no-op. A successful connect sets isConnected, clears
 *   isConnecting and records the target name.
 * - `disconnect` (and `stop`) resets every transport/now-playing flow to its
 *   idle value; `stop` also sends the server a `Stop` playstate command first.
 * - `refreshPlaybackState` maps the server session onto ms-based transport
 *   flows: ticks/10000 for position and duration, `!isPaused` while an item
 *   is loaded, volumeLevel/100, plus now-playing title/subtitle/itemId — and
 *   auto-disconnects when the connected session vanished server-side.
 * - Transport commands (play/pause/seek/next/previous/volume/loadMedia) are
 *   forwarded to the [AdminApiClient] with the exact wire arguments and are
 *   no-ops without a connected session.
 * - Discovery filters non-remote / blank-deviceId / own-device sessions and
 *   builds a human-readable display name.
 */
class JellyfinRemotePlayCastStrategyTest {

    private val adminApiClient: AdminApiClient = mockk(relaxed = true)
    private val serverIdentityStore: ServerIdentityStore = mockk(relaxed = true)
    private val webSocketClient: JellyfinWebSocketClient = mockk(relaxed = true)
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { webSocketClient.events } returns kotlinx.coroutines.flow.MutableSharedFlow()
        coEvery { serverIdentityStore.ensureDeviceId() } returns "self-device"
        coEvery { adminApiClient.getSessions() } returns Result.success(emptyList())
    }

    private fun strategy() = JellyfinRemotePlayCastStrategy(
        appContext = mockk(relaxed = true),
        adminApiClient = adminApiClient,
        serverIdentityStore = serverIdentityStore,
        webSocketClient = webSocketClient,
        imageUrlProvider = imageUrlProvider,
    )

    private fun remoteSession(
        id: String = "s1",
        deviceId: String = "other-device-12345678",
        deviceName: String = "TV",
        userName: String = "alice",
    ) = SessionInfo(
        id = id,
        deviceId = deviceId,
        deviceName = deviceName,
        userName = userName,
        client = "Jellyfin Web",
        supportsRemoteControl = true,
    )

    private fun playingSession() = remoteSession().copy(
        playState = SessionPlayState(
            positionTicks = 1_800_000L,
            isPaused = false,
            volumeLevel = 80,
        ),
        nowPlayingItem = SessionNowPlayingItem(
            id = "n1",
            name = "Song",
            seriesName = "Album",
            runTimeTicks = 2_400_000_000L,
        ),
    )

    private fun awaitTrue(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    // ── connect / disconnect ─────────────────────────────────────────────

    @Test
    fun `connect with a non-session tag is a no-op`() {
        val strategy = strategy()
        val device = com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "TV", type = "jellyfin", tag = "not-a-session", strategyName = "jellyfin",
        )

        strategy.connect(mockk(relaxed = true), device)

        assertFalse(strategy.isConnected.value)
    }

    @Test
    fun `connect records the target and flips the connection state`() {
        val strategy = strategy()
        val device = com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "Living Room TV", type = "jellyfin", tag = remoteSession(), strategyName = "jellyfin",
        )

        strategy.connect(mockk(relaxed = true), device)

        assertTrue(strategy.isConnected.value)
        assertFalse(strategy.isConnecting.value)
        assertEquals("Living Room TV", strategy.targetName.value)
    }

    @Test
    fun `disconnect resets every transport and now-playing flow`() {
        val strategy = strategy()
        coEvery { adminApiClient.getSessions() } returns Result.success(listOf(playingSession()))
        strategy.connect(mockk(relaxed = true), com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "TV", type = "jellyfin", tag = remoteSession(), strategyName = "jellyfin",
        ))
        runBlocking { strategy.refreshPlaybackState() }
        assertEquals(180L, strategy.positionMs.value)

        strategy.disconnect(mockk(relaxed = true))

        assertFalse(strategy.isConnected.value)
        assertNull(strategy.targetName.value)
        assertEquals(0L, strategy.positionMs.value)
        assertEquals(0L, strategy.durationMs.value)
        assertFalse(strategy.isPlaying.value)
        assertEquals(1f, strategy.volume.value)
        assertEquals("", strategy.nowPlayingTitle.value)
        assertEquals("", strategy.nowPlayingSubtitle.value)
        assertEquals("", strategy.nowPlayingItemId.value)
    }

    // ── refreshPlaybackState / applySessionState ─────────────────────────

    @Test
    fun `refreshPlaybackState maps the server session onto the transport flows`() {
        val strategy = strategy()
        coEvery { adminApiClient.getSessions() } returns Result.success(listOf(playingSession()))
        strategy.connect(mockk(relaxed = true), com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "TV", type = "jellyfin", tag = remoteSession(), strategyName = "jellyfin",
        ))

        runBlocking { strategy.refreshPlaybackState() }

        assertEquals(180L, strategy.positionMs.value)
        // Jellyfin ticks -> ms is /10_000: 2_400_000_000 ticks = 240 s.
        assertEquals(240_000L, strategy.durationMs.value)
        assertTrue(strategy.isPlaying.value)
        assertEquals(0.8f, strategy.volume.value)
        assertEquals("Song", strategy.nowPlayingTitle.value)
        assertEquals("Album", strategy.nowPlayingSubtitle.value)
        assertEquals("n1", strategy.nowPlayingItemId.value)
    }

    @Test
    fun `refreshPlaybackState disconnects when the connected session is gone`() {
        val strategy = strategy()
        coEvery { adminApiClient.getSessions() } returns Result.success(emptyList())
        strategy.connect(mockk(relaxed = true), com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "TV", type = "jellyfin", tag = remoteSession(), strategyName = "jellyfin",
        ))
        assertTrue(strategy.isConnected.value)

        runBlocking { strategy.refreshPlaybackState() }

        assertFalse(strategy.isConnected.value)
        assertNull(strategy.targetName.value)
    }

    @Test
    fun `refreshPlaybackState without a connection never touches the API`() {
        val strategy = strategy()

        runBlocking { strategy.refreshPlaybackState() }

        coVerify(exactly = 0) { adminApiClient.getSessions() }
    }

    // ── transport commands ───────────────────────────────────────────────

    @Test
    fun `play sends Unpause and flips isPlaying`() {
        val strategy = strategy()
        strategy.connect(mockk(relaxed = true), com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "TV", type = "jellyfin", tag = remoteSession(), strategyName = "jellyfin",
        ))

        strategy.play()

        coVerify(timeout = 3_000) { adminApiClient.sendPlaystateCommand("s1", "Unpause") }
        awaitTrue { strategy.isPlaying.value }
    }

    @Test
    fun `pause sends Pause and clears isPlaying`() {
        val strategy = strategy()
        strategy.connect(mockk(relaxed = true), com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "TV", type = "jellyfin", tag = remoteSession(), strategyName = "jellyfin",
        ))

        strategy.pause()

        coVerify(timeout = 3_000) { adminApiClient.sendPlaystateCommand("s1", "Pause") }
        awaitTrue { !strategy.isPlaying.value }
    }

    @Test
    fun `seekTo converts milliseconds to ticks`() {
        val strategy = strategy()
        strategy.connect(mockk(relaxed = true), com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "TV", type = "jellyfin", tag = remoteSession(), strategyName = "jellyfin",
        ))

        strategy.seekTo(5_000L)

        coVerify(timeout = 3_000) {
            adminApiClient.sendPlaystateCommand("s1", "Seek", seekPositionTicks = 50_000_000L)
        }
    }

    @Test
    fun `nextTrack and previousTrack forward the queue commands`() {
        val strategy = strategy()
        strategy.connect(mockk(relaxed = true), com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "TV", type = "jellyfin", tag = remoteSession(), strategyName = "jellyfin",
        ))

        strategy.nextTrack()
        strategy.previousTrack()

        coVerify(timeout = 3_000) { adminApiClient.sendPlaystateCommand("s1", "NextTrack") }
        coVerify(timeout = 3_000) { adminApiClient.sendPlaystateCommand("s1", "PreviousTrack") }
    }

    @Test
    fun `setRendererVolume converts to a 0-100 percent argument`() {
        val strategy = strategy()
        strategy.connect(mockk(relaxed = true), com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "TV", type = "jellyfin", tag = remoteSession(), strategyName = "jellyfin",
        ))

        strategy.setRendererVolume(0.5f)

        coVerify(timeout = 3_000) {
            adminApiClient.sendGeneralCommand(
                sessionId = "s1",
                commandName = "SetVolume",
                arguments = match { it["Volume"] == "50" },
            )
        }
        awaitTrue { strategy.volume.value == 0.5f }
    }

    @Test
    fun `stop sends Stop and disconnects`() {
        val strategy = strategy()
        strategy.connect(mockk(relaxed = true), com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "TV", type = "jellyfin", tag = remoteSession(), strategyName = "jellyfin",
        ))

        strategy.stop(mockk(relaxed = true))

        coVerify(timeout = 3_000) { adminApiClient.sendPlaystateCommand("s1", "Stop") }
        awaitTrue { !strategy.isConnected.value }
    }

    @Test
    fun `loadMedia sends PlayNow with the item and start position in ticks`() {
        val strategy = strategy()
        strategy.connect(mockk(relaxed = true), com.raulshma.jellyplay.core.data.cast.CastDevice(
            id = "s1", name = "TV", type = "jellyfin", tag = remoteSession(), strategyName = "jellyfin",
        ))

        strategy.loadMedia(
            itemId = "item1",
            startPositionMs = 10_000L,
            mediaSourceId = "ms1",
            audioStreamIndex = 2,
            subtitleStreamIndex = 3,
        )

        coVerify(timeout = 3_000) {
            adminApiClient.play(
                sessionId = "s1",
                playCommand = "PlayNow",
                itemIds = listOf("item1"),
                startPositionTicks = 100_000_000L,
                mediaSourceId = "ms1",
                audioStreamIndex = 2,
                subtitleStreamIndex = 3,
            )
        }
        awaitTrue { strategy.isPlaying.value }
    }

    @Test
    fun `transport commands without a connected session never touch the API`() {
        val strategy = strategy()

        strategy.play()
        strategy.pause()
        strategy.seekTo(1L)
        strategy.nextTrack()
        strategy.previousTrack()
        strategy.setRendererVolume(0.5f)
        strategy.loadMedia("item1")

        coVerify(exactly = 0) { adminApiClient.sendPlaystateCommand(any(), any()) }
        coVerify(exactly = 0) { adminApiClient.sendGeneralCommand(any(), any()) }
        coVerify(exactly = 0) { adminApiClient.play(any(), any(), any()) }
    }

    // ── discovery ────────────────────────────────────────────────────────

    @Test
    fun `discovery filters non-remote blank-device and own sessions out`() {
        val strategy = strategy()
        var pollCount = 0
        coEvery { adminApiClient.getSessions() } answers {
            pollCount++
            Result.success(
                listOf(
                    remoteSession(id = "self-session", deviceId = "self-device"),
                    remoteSession(id = "blank-device", deviceId = ""),
                    remoteSession(id = "no-remote", deviceId = "x1").copy(supportsRemoteControl = false),
                ),
            )
        }

        strategy.startDiscovery(mockk(relaxed = true))
        awaitTrue { pollCount > 0 }
        Thread.sleep(200) // allow any (wrongly) unfiltered emission to land

        assertTrue(strategy.discoveredDevices.value.isEmpty())
        assertFalse(strategy.isAvailable.value)
        strategy.stopDiscovery()
    }

    @Test
    fun `discovery surfaces the controllable session with its display name`() {
        val strategy = strategy()
        coEvery { adminApiClient.getSessions() } returns Result.success(listOf(remoteSession()))

        strategy.startDiscovery(mockk(relaxed = true))

        awaitTrue {
            strategy.discoveredDevices.value.any {
                it.id == "s1" && it.name == "TV (other-de) - alice" && it.strategyName == "jellyfin"
            }
        }
        assertTrue(strategy.isAvailable.value)
        strategy.stopDiscovery()
        assertFalse(strategy.isAvailable.value)
        assertTrue(strategy.discoveredDevices.value.isEmpty())
    }

    @Test
    fun `a discovery cycle failure is swallowed without crashing`() {
        val strategy = strategy()
        coEvery { adminApiClient.getSessions() } returns Result.failure(IllegalStateException("down"))

        strategy.startDiscovery(mockk(relaxed = true))

        // The loop must survive the failure and keep polling — give it time to
        // hit the catch at least once, then assert the process is still sane.
        Thread.sleep(300)
        strategy.stopDiscovery()
    }
}
