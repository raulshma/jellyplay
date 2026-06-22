package com.raulshma.jellyplay.floating

import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine
import com.raulshma.jellyplay.feature.player.video.VideoMiniPlayerState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FloatingPlayerStateTest {

    private lateinit var activePlayerController: ActivePlayerController
    private lateinit var miniPlayerState: VideoMiniPlayerState
    private lateinit var engine: RemotePlayableEngine
    private lateinit var state: FloatingPlayerState

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        activePlayerController = mockk(relaxed = true)
        miniPlayerState = mockk(relaxed = true)
        engine = mockk(relaxed = true)

        every { miniPlayerState.title } returns MutableStateFlow("Test Movie")
        every { miniPlayerState.subtitle } returns MutableStateFlow("Continue watching")
        every { miniPlayerState.itemId } returns MutableStateFlow("item-1")
        every { activePlayerController.activeEngine } returns MutableStateFlow(engine)
        every { activePlayerController.engine } returns engine
        every { engine.isPlaying } returns MutableStateFlow(false)
        every { engine.currentPositionMs } returns 30_000L

        state = FloatingPlayerState(activePlayerController, miniPlayerState)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onOverlayShown_setsIsActiveToTrue() {
        assertFalse(state.isActive.value)
        state.onOverlayShown()
        assertTrue(state.isActive.value)
    }

    @Test
    fun onOverlayHidden_setsIsActiveToFalse() {
        state.onOverlayShown()
        assertTrue(state.isActive.value)
        state.onOverlayHidden()
        assertFalse(state.isActive.value)
    }

    @Test
    fun togglePlayPause_whenPlaying_callsPause() {
        every { engine.isPlaying } returns MutableStateFlow(true)
        state = FloatingPlayerState(activePlayerController, miniPlayerState)

        state.togglePlayPause()

        verify { engine.pause() }
    }

    @Test
    fun togglePlayPause_whenPaused_callsPlay() {
        every { engine.isPlaying } returns MutableStateFlow(false)
        state = FloatingPlayerState(activePlayerController, miniPlayerState)

        state.togglePlayPause()

        verify { engine.play() }
    }

    @Test
    fun togglePlayPause_whenNoEngineBound_doesNothing() {
        every { activePlayerController.engine } returns null
        every { activePlayerController.activeEngine } returns MutableStateFlow(null)
        state = FloatingPlayerState(activePlayerController, miniPlayerState)

        state.togglePlayPause()

        verify(exactly = 0) { engine.play() }
        verify(exactly = 0) { engine.pause() }
    }

    @Test
    fun seekBy_positiveDelta_seeksForward() {
        every { engine.currentPositionMs } returns 30_000L

        state.seekBy(10_000L)

        verify { engine.seekTo(40_000L) }
    }

    @Test
    fun seekBy_negativeDelta_clampsToZero() {
        every { engine.currentPositionMs } returns 5_000L

        state.seekBy(-10_000L)

        verify { engine.seekTo(0L) }
    }

    @Test
    fun seekBy_whenNoEngineBound_doesNothing() {
        every { activePlayerController.engine } returns null
        every { activePlayerController.activeEngine } returns MutableStateFlow(null)
        state = FloatingPlayerState(activePlayerController, miniPlayerState)

        state.seekBy(10_000L)

        verify(exactly = 0) { engine.seekTo(any()) }
    }

    @Test
    fun title_reflectsMiniPlayerState() {
        assertEquals("Test Movie", state.title.value)
    }

    @Test
    fun subtitle_reflectsMiniPlayerState() {
        assertEquals("Continue watching", state.subtitle.value)
    }

    @Test
    fun updateMetadata_setsArtworkUrl() {
        state.updateMetadata("http://example.com/art.jpg")
        assertEquals("http://example.com/art.jpg", state.artworkUrl.value)
    }

    @Test
    fun updateMetadata_setsNullArtworkUrl() {
        state.updateMetadata(null)
        assertEquals(null, state.artworkUrl.value)
    }
}
