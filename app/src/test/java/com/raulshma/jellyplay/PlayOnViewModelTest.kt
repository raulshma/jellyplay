package com.raulshma.jellyplay

import android.content.Context
import com.raulshma.jellyplay.core.data.cast.CastDevice
import com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the "Play On" shell entry point: the uiState must prefer the remote
 * session's now-playing metadata and fall back to the locally playing item's
 * (the fling handoff window before the server reflects the new item), the
 * fling paths must load the locally playing item / probed item and pause or
 * skip local playback, the 5 s status poll must seed + repeat + stop with
 * the connection, and the transport commands must reach the strategy exactly
 * once each (the strategy is the transport home; the controller's members
 * are the narrow surface).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayOnViewModelTest {

    private val strategy: JellyfinRemotePlayCastStrategy = mockk(relaxed = true)
    private val audio: AudioPlaybackManager = mockk(relaxed = true)
    private val mainDispatcher = StandardTestDispatcher()

    private val discovered = MutableStateFlow<List<CastDevice>>(emptyList())
    private val connected = MutableStateFlow(false)
    private val remotePlaying = MutableStateFlow(false)
    private val remotePosition = MutableStateFlow(0L)
    private val remoteDuration = MutableStateFlow(0L)
    private val remoteVolume = MutableStateFlow(1f)
    private val remoteTitle = MutableStateFlow("")
    private val remoteSubtitle = MutableStateFlow("")
    private val remoteArtwork = MutableStateFlow("")
    private val localItemId = MutableStateFlow<String?>(null)
    private val localTitle = MutableStateFlow("Local Song")
    private val localArtist = MutableStateFlow("Local Artist")
    private val localArt = MutableStateFlow("file:///art.jpg")
    private val localPosition = MutableStateFlow(30_000L)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { strategy.discoveredDevices } returns discovered
        every { strategy.isConnected } returns connected
        every { strategy.isPlaying } returns remotePlaying
        every { strategy.positionMs } returns remotePosition
        every { strategy.durationMs } returns remoteDuration
        every { strategy.volume } returns remoteVolume
        every { strategy.nowPlayingTitle } returns remoteTitle
        every { strategy.nowPlayingSubtitle } returns remoteSubtitle
        every { strategy.nowPlayingArtworkUrl } returns remoteArtwork
        every { audio.currentPlayingItemId } returns localItemId
        every { audio.title } returns localTitle
        every { audio.artist } returns localArtist
        every { audio.albumArtUrl } returns localArt
        every { audio.currentPosition } returns localPosition
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = PlayOnViewModel(strategy, audio)

    /**
     * The exposed StateFlows use WhileSubscribed, so reading `.value` before
     * any collector returns only the initial state. Attaching a background
     * collector starts the upstream combine and lets `advanceUntilIdle`
     * propagate the configured values into `.value`.
     */
    private fun kotlinx.coroutines.test.TestScope.attach(vm: PlayOnViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect { }
        }
    }

    private fun device(name: String) = CastDevice(
        id = "id-$name",
        name = name,
        type = "jellyfin",
    )

    @Test
    fun `empty state has no devices and is not connected`() = runTest(mainDispatcher) {
        val vm = createVm()
        attach(vm)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isDiscovering)
        assertFalse(state.isConnected)
        assertEquals(emptyList<CastDevice>(), state.devices)
    }

    @Test
    fun `remote now-playing takes precedence over local metadata`() = runTest(mainDispatcher) {
        remoteTitle.value = "Remote Song"
        remoteSubtitle.value = "Remote Artist"
        remoteArtwork.value = "https://server/art.jpg"
        val vm = createVm()
        attach(vm)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Remote Song", state.title)
        assertEquals("Remote Artist", state.artist)
        assertEquals("https://server/art.jpg", state.artworkUri)
    }

    @Test
    fun `blank remote metadata falls back to the locally playing item`() = runTest(mainDispatcher) {
        val vm = createVm()
        attach(vm)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Local Song", state.title)
        assertEquals("Local Artist", state.artist)
        assertEquals("file:///art.jpg", state.artworkUri)
    }

    @Test
    fun `remote title without subtitle suppresses the local artist fallback`() = runTest(mainDispatcher) {
        // The subtitle only falls back to the local artist while the session
        // hasn't reported a title at all — once a title exists, a missing
        // subtitle must stay blank rather than mix sessions.
        remoteTitle.value = "Remote Song"
        val vm = createVm()
        attach(vm)
        advanceUntilIdle()

        assertEquals("Remote Song", vm.uiState.value.title)
        assertEquals("", vm.uiState.value.artist)
    }

    // Declared delta (2026-09-08): this test replaces
    // `canFling stays at its initial false because nothing subscribes it
    // (likely bug)` — the dead WhileSubscribed seam it pinned is deleted
    // (see PlayOnUiState's KDoc); the fling gate it gestured at is pinned
    // LIVE here instead, through the probe the Home redirect actually uses.
    @Test
    fun `home redirect probe flings only while a remote session is connected`() = runTest(mainDispatcher) {
        val vm = createVm()

        // Not connected: the probe returns false and issues nothing — the
        // Home caller falls through to local playback routing.
        assertFalse(vm.flingIfConnected("item-7", 1_234L))
        verify(exactly = 0) { strategy.loadMedia(any(), any()) }

        connected.value = true
        assertTrue(vm.flingIfConnected("item-7", 1_234L))
        verify(exactly = 1) { strategy.loadMedia(itemId = "item-7", startPositionMs = 1_234L) }
    }

    @Test
    fun `isDiscovering reflects discovered device presence`() = runTest(mainDispatcher) {
        val vm = createVm()
        attach(vm)
        advanceUntilIdle()

        discovered.value = listOf(device("Living Room"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isDiscovering)
        assertEquals("Living Room", state.devices.single().name)
    }

    @Test
    fun `connectAndFling with a playing local item loads it remotely and pauses local`() = runTest(mainDispatcher) {
        localItemId.value = "item-9"
        localPosition.value = 45_000L
        val vm = createVm()
        attach(vm)
        advanceUntilIdle()
        val context = mockk<Context>(relaxed = true)

        vm.connectAndFling(context, device("TV"))

        advanceUntilIdle()
        verify { strategy.connect(context, any()) }
        verify {
            strategy.loadMedia(itemId = "item-9", startPositionMs = 45_000L)
        }
        verify(exactly = 1) { audio.pause() }
        assertEquals("TV", vm.targetDeviceName.value)
    }

    @Test
    fun `connectAndFling with nothing playing connects without flinging`() = runTest(mainDispatcher) {
        val vm = createVm()
        attach(vm)
        advanceUntilIdle()
        val context = mockk<Context>(relaxed = true)

        vm.connectAndFling(context, device("TV"))
        advanceUntilIdle()

        verify { strategy.connect(context, any()) }
        verify(exactly = 0) { strategy.loadMedia(any(), any()) }
        coVerify(exactly = 0) { audio.pause() }
    }

    @Test
    fun `cast transport delegates straight to the strategy`() = runTest(mainDispatcher) {
        val vm = createVm()
        attach(vm)
        advanceUntilIdle()

        vm.castPlay()
        vm.castPause()
        vm.castSeekTo(1_500L)
        vm.setCastVolume(0.5f)
        vm.castNextTrack()
        vm.castPreviousTrack()
        advanceUntilIdle()

        verify(exactly = 1) { strategy.play() }
        verify(exactly = 1) { strategy.pause() }
        verify(exactly = 1) { strategy.seekTo(1_500L) }
        verify(exactly = 1) { strategy.setRendererVolume(0.5f) }
        verify(exactly = 1) { strategy.nextTrack() }
        verify(exactly = 1) { strategy.previousTrack() }
    }

    @Test
    fun `castStop clears the target device name`() = runTest(mainDispatcher) {
        val vm = createVm()
        attach(vm)
        advanceUntilIdle()
        val context = mockk<Context>(relaxed = true)

        vm.connectAndFling(context, device("TV"))
        advanceUntilIdle()
        assertEquals("TV", vm.targetDeviceName.value)

        vm.castStop(context)
        advanceUntilIdle()
        verify(exactly = 1) { strategy.stop(context) }
        assertEquals(null, vm.targetDeviceName.value)
    }

    @Test
    fun `status poll seeds immediately and repeats every 5 s until disconnect`() = runTest(mainDispatcher) {
        connected.value = true
        val vm = createVm()
        val context = mockk<Context>(relaxed = true)

        vm.connectAndFling(context, device("TV"))
        runCurrent()
        // Seed fires in the launch frame, before the first 5 s delay.
        coVerify(exactly = 1) { strategy.refreshPlaybackState() }

        advanceTimeBy(5_000L)
        runCurrent()
        coVerify(exactly = 2) { strategy.refreshPlaybackState() }

        // Disconnect tears the poll down: further ticks must not fire.
        vm.disconnect(context)
        advanceTimeBy(20_000L)
        runCurrent()
        coVerify(exactly = 2) { strategy.refreshPlaybackState() }
    }
}
