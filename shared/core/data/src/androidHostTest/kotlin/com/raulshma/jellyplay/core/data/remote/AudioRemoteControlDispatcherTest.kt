package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NameGuidPair
import com.raulshma.jellyplay.core.model.remote.GeneralCommand
import com.raulshma.jellyplay.core.model.remote.PlayRequest
import com.raulshma.jellyplay.core.model.remote.PlaystateCommand
import com.raulshma.jellyplay.core.model.remote.PlaybackDomain
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Pins the [AudioRemoteControlDispatcher] routing invariant: playstate and
 * general commands drive the singleton [AudioPlaybackManager] on the main
 * thread (`withContext(Main.immediate)`), Stop is terminal
 * (`stopAndRelease` + [NavigationTarget.ClosePlayer]), and a fresh "Play"
 * opens the audio player: a single-item request plays that item directly,
 * while a multi-item request builds the queue via `MediaRepository` (failed
 * detail fetches dropped, order preserved) and coerces `startIndex` into the
 * **resolved** queue's bounds. Navigation to [NavigationTarget.OpenAudioPlayer]
 * always targets the first requested id. An empty PlayRequest is a full no-op.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioRemoteControlDispatcherTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val audioPlaybackManager: AudioPlaybackManager = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val bridge = RemoteNavigationBridge()

    private fun dispatcher() = AudioRemoteControlDispatcher(
        audioPlaybackManager = audioPlaybackManager,
        mediaRepository = mediaRepository,
        remoteNavigationBridge = bridge,
    )

    private fun detail(
        id: String,
        name: String,
        albumArtist: String? = "Album Artist",
        artistName: String? = null,
        album: String? = "Album",
        runTimeTicks: Long? = 200_000_000L,
        normalizationGain: Float? = null,
        mediaSourceId: String? = "ms-$id",
    ) = MediaDetail(
        item = MediaItem(
            id = id,
            name = name,
            mediaType = MediaType.MUSIC,
            albumArtist = albumArtist,
            album = album,
            artistItems = artistName?.let { listOf(NameGuidPair(name = it, id = "artist-1")) } ?: emptyList(),
            runTimeTicks = runTimeTicks,
            normalizationGain = normalizationGain,
        ),
        mediaSources = mediaSourceId?.let { listOf(MediaSource(id = it, name = "src")) } ?: emptyList(),
    )

    private fun stubDetail(item: MediaDetail) {
        coEvery { mediaRepository.getMediaDetail(item.item.id, any()) } returns Result.success(item)
    }

    private fun stubDetailFailure(id: String) {
        coEvery { mediaRepository.getMediaDetail(id, any()) } returns Result.failure(IOException("404"))
    }

    private suspend fun CoroutineScope.collectTargets(block: suspend () -> Unit): List<NavigationTarget> {
        val targets = mutableListOf<NavigationTarget>()
        val collector = launch(UnconfinedTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)) {
            bridge.targets.collect { targets += it }
        }
        try {
            block()
        } finally {
            collector.cancel()
        }
        return targets
    }

    // ---- domain ------------------------------------------------------------

    @Test
    fun `domain is AUDIO`() {
        assertEquals(PlaybackDomain.AUDIO, dispatcher().domain)
    }

    // ---- play ----------------------------------------------------------------

    @Test
    fun `single-item play plays the item directly and opens the audio player`() = runTest(mainDispatcherRule.testDispatcher) {
        val targets = collectTargets {
            dispatcher().play(
                PlayRequest(itemIds = listOf("track-1"), startPositionTicks = 5_000_000L),
            )
        }

        verify(exactly = 1) { audioPlaybackManager.play("track-1") }
        verify(exactly = 0) { audioPlaybackManager.playQueue(any(), any()) }
        assertEquals(listOf<NavigationTarget>(NavigationTarget.OpenAudioPlayer("track-1")), targets)
    }

    @Test
    fun `empty play request is a full no-op`() = runTest(mainDispatcherRule.testDispatcher) {
        val targets = collectTargets {
            dispatcher().play(PlayRequest(itemIds = emptyList()))
        }

        verify { audioPlaybackManager wasNot Called }
        assertTrue(targets.isEmpty())
    }

    @Test
    fun `multi-item play builds the queue and coerces startIndex into bounds`() = runTest(mainDispatcherRule.testDispatcher) {
        stubDetail(detail("t1", "Track One"))
        stubDetail(detail("t2", "Track Two"))
        stubDetail(detail("t3", "Track Three"))

        val targets = collectTargets {
            dispatcher().play(
                PlayRequest(itemIds = listOf("t1", "t2", "t3"), startIndex = 9),
            )
        }

        verify(exactly = 0) { audioPlaybackManager.play(any()) }
        verify(exactly = 1) {
            audioPlaybackManager.playQueue(
                listOf(
                    AudioQueueItem(
                        id = "t1",
                        name = "Track One",
                        artist = "Album Artist",
                        album = "Album",
                        imageUrl = null,
                        mediaSourceId = "ms-t1",
                        // 200_000_000 ticks / 10_000 = 20_000 ms
                        durationMs = 20_000L,
                    ),
                    AudioQueueItem(
                        id = "t2",
                        name = "Track Two",
                        artist = "Album Artist",
                        album = "Album",
                        imageUrl = null,
                        mediaSourceId = "ms-t2",
                        durationMs = 20_000L,
                    ),
                    AudioQueueItem(
                        id = "t3",
                        name = "Track Three",
                        artist = "Album Artist",
                        album = "Album",
                        imageUrl = null,
                        mediaSourceId = "ms-t3",
                        durationMs = 20_000L,
                    ),
                ),
                2, // startIndex 9 coerced to items.lastIndex
            )
        }
        // Navigation still targets the first requested id.
        assertEquals(listOf<NavigationTarget>(NavigationTarget.OpenAudioPlayer("t1")), targets)
    }

    @Test
    fun `multi-item play drops failed detail fetches and coerces against the resolved queue`() = runTest(mainDispatcherRule.testDispatcher) {
        stubDetail(detail("t1", "Track One"))
        stubDetailFailure("t2")
        stubDetail(detail("t3", "Track Three"))

        val targets = collectTargets {
            dispatcher().play(
                PlayRequest(itemIds = listOf("t1", "t2", "t3"), startIndex = 5),
            )
        }

        verify(exactly = 1) {
            audioPlaybackManager.playQueue(
                match { items ->
                    items.map { it.id } == listOf("t1", "t3")
                },
                1, // coerced to the resolved (2-item) queue's lastIndex
            )
        }
        assertEquals(listOf<NavigationTarget>(NavigationTarget.OpenAudioPlayer("t1")), targets)
    }

    @Test
    fun `negative startIndex coerces to zero`() = runTest(mainDispatcherRule.testDispatcher) {
        stubDetail(detail("t1", "Track One"))
        stubDetail(detail("t2", "Track Two"))

        collectTargets {
            dispatcher().play(
                PlayRequest(itemIds = listOf("t1", "t2"), startIndex = -3),
            )
        }

        verify(exactly = 1) {
            audioPlaybackManager.playQueue(match { it.map { q -> q.id } == listOf("t1", "t2") }, 0)
        }
    }

    @Test
    fun `queue items fall back to artistItems name when albumArtist missing`() = runTest(mainDispatcherRule.testDispatcher) {
        stubDetail(detail("t1", "Track One", albumArtist = null, artistName = "Fallback Artist"))
        stubDetail(detail("t2", "Track Two"))

        collectTargets {
            dispatcher().play(PlayRequest(itemIds = listOf("t1", "t2")))
        }

        verify(exactly = 1) {
            audioPlaybackManager.playQueue(match { items -> items.first().artist == "Fallback Artist" }, any())
        }
    }

    // ---- playstate -------------------------------------------------------------

    @Test
    fun `Stop releases the audio engine and requests ClosePlayer`() = runTest(mainDispatcherRule.testDispatcher) {
        val targets = collectTargets {
            dispatcher().handlePlaystate(PlaystateCommand.Stop)
        }

        verify(exactly = 1) { audioPlaybackManager.stopAndRelease() }
        assertEquals(listOf<NavigationTarget>(NavigationTarget.ClosePlayer), targets)
    }

    @Test
    fun `Pause Unpause and PlayPause route to the manager`() = runTest(mainDispatcherRule.testDispatcher) {
        dispatcher().handlePlaystate(PlaystateCommand.Pause)
        dispatcher().handlePlaystate(PlaystateCommand.Unpause)
        dispatcher().handlePlaystate(PlaystateCommand.PlayPause)

        verifySequence {
            audioPlaybackManager.pause()
            audioPlaybackManager.resume()
            audioPlaybackManager.togglePlayPause()
        }
    }

    @Test
    fun `Seek converts ticks to milliseconds`() = runTest(mainDispatcherRule.testDispatcher) {
        dispatcher().handlePlaystate(PlaystateCommand.Seek(positionTicks = 123_456L))

        verify(exactly = 1) { audioPlaybackManager.seekTo(12L) }
    }

    @Test
    fun `NextTrack and PreviousTrack skip the queue`() = runTest(mainDispatcherRule.testDispatcher) {
        dispatcher().handlePlaystate(PlaystateCommand.NextTrack)
        dispatcher().handlePlaystate(PlaystateCommand.PreviousTrack)

        verifySequence {
            audioPlaybackManager.skipToNext()
            audioPlaybackManager.skipToPrevious()
        }
    }

    @Test
    fun `Rewind seeks back 10 seconds floored at zero`() = runTest(mainDispatcherRule.testDispatcher) {
        every { audioPlaybackManager.currentPosition } returns MutableStateFlow(25_000L).asStateFlow()

        dispatcher().handlePlaystate(PlaystateCommand.Rewind)

        verify(exactly = 1) { audioPlaybackManager.seekTo(15_000L) }

        every { audioPlaybackManager.currentPosition } returns MutableStateFlow(5_000L).asStateFlow()
        dispatcher().handlePlaystate(PlaystateCommand.Rewind)
        verify(exactly = 1) { audioPlaybackManager.seekTo(0L) }
    }

    @Test
    fun `FastForward seeks forward 10 seconds`() = runTest(mainDispatcherRule.testDispatcher) {
        every { audioPlaybackManager.currentPosition } returns MutableStateFlow(25_000L).asStateFlow()

        dispatcher().handlePlaystate(PlaystateCommand.FastForward)

        verify(exactly = 1) { audioPlaybackManager.seekTo(35_000L) }
    }

    // ---- general commands --------------------------------------------------------

    @Test
    fun `SetVolume coerces to 0to1 and applies explicit mute`() = runTest(mainDispatcherRule.testDispatcher) {
        dispatcher().handleGeneral(GeneralCommand.SetVolume(volume0to100 = 150, mute = true))

        verifySequence {
            audioPlaybackManager.setVolume(1.0f)
            audioPlaybackManager.setMuted(true)
        }

        dispatcher().handleGeneral(GeneralCommand.SetVolume(volume0to100 = 40, mute = null))
        verify(exactly = 1) { audioPlaybackManager.setVolume(0.4f) }
        verify(exactly = 1) { audioPlaybackManager.setMuted(any()) } // only the first call
    }

    @Test
    fun `volume and mute commands route to the manager`() = runTest(mainDispatcherRule.testDispatcher) {
        dispatcher().handleGeneral(GeneralCommand.VolumeUp)
        dispatcher().handleGeneral(GeneralCommand.VolumeDown)
        dispatcher().handleGeneral(GeneralCommand.Mute)
        dispatcher().handleGeneral(GeneralCommand.Unmute)
        dispatcher().handleGeneral(GeneralCommand.ToggleMute)

        verifySequence {
            audioPlaybackManager.increaseVolume()
            audioPlaybackManager.decreaseVolume()
            audioPlaybackManager.setMuted(true)
            audioPlaybackManager.setMuted(false)
            audioPlaybackManager.toggleMute()
        }
    }

    @Test
    fun `SetRepeatMode maps server modes to player modes`() = runTest(mainDispatcherRule.testDispatcher) {
        dispatcher().handleGeneral(GeneralCommand.SetRepeatMode(mode = "RepeatOne"))
        dispatcher().handleGeneral(GeneralCommand.SetRepeatMode(mode = "RepeatAll"))
        dispatcher().handleGeneral(GeneralCommand.SetRepeatMode(mode = "RepeatNone"))

        verifySequence {
            audioPlaybackManager.setRepeatMode(2)
            audioPlaybackManager.setRepeatMode(1)
            audioPlaybackManager.setRepeatMode(0)
        }
    }

    @Test
    fun `shuffle commands route to setShuffleMode`() = runTest(mainDispatcherRule.testDispatcher) {
        dispatcher().handleGeneral(GeneralCommand.SetShuffleQueue(shuffle = true))
        dispatcher().handleGeneral(GeneralCommand.SetPlaybackOrder(order = "Sorted"))

        verifySequence {
            audioPlaybackManager.setShuffleMode(true)
            audioPlaybackManager.setShuffleMode(false)
        }
    }

    @Test
    fun `SetPlaybackOrder treats Shuffle and Random as shuffle`() = runTest(mainDispatcherRule.testDispatcher) {
        dispatcher().handleGeneral(GeneralCommand.SetPlaybackOrder(order = "Random"))

        verify(exactly = 1) { audioPlaybackManager.setShuffleMode(true) }
    }

    @Test
    fun `unsupported general commands are silently ignored`() = runTest(mainDispatcherRule.testDispatcher) {
        dispatcher().handleGeneral(GeneralCommand.SetAudioStreamIndex(index = 1))
        dispatcher().handleGeneral(GeneralCommand.SetSubtitleStreamIndex(index = 2))
        dispatcher().handleGeneral(GeneralCommand.SetMaxStreamingBitrate(bitrate = 1_000))
        dispatcher().handleGeneral(GeneralCommand.ToggleFullscreen)
        dispatcher().handleGeneral(GeneralCommand.DisplayMessage(header = "h", text = "t", timeoutMs = null))
        dispatcher().handleGeneral(GeneralCommand.Unknown(name = "Whatever"))

        verify { audioPlaybackManager wasNot Called }
    }
}
