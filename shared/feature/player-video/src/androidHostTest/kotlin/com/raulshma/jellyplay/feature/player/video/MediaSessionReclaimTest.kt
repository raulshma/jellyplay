package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import com.raulshma.jellyplay.core.data.playback.JellyPlayPlaybackService
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.feature.player.video.engine.Media3PlayerHost
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins [MediaSessionController.ensureActiveForItem] — the video session's
 * play-edge reclaim. The manager's priority guard releases a session born
 * idle against a then-playing holder; when the granted focus claim later
 * pauses that holder, the reclaim must give video its session back — and
 * must be a no-op while the held session is already the active one.
 *
 * Sessions build against a Robolectric [JellyPlayPlaybackService] as their
 * context: MediaLibrarySession's constructor requires a MediaLibraryService
 * host (a plain Application context throws) — the
 * PlaybackSessionManagerPriorityTest precedent.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaSessionReclaimTest {

    private lateinit var service: JellyPlayPlaybackService
    private lateinit var manager: PlaybackSessionManager
    private val videoPlayer = stubPlayer()
    private var idCounter = 0

    @Before
    fun setUp() {
        service = Robolectric.buildService(JellyPlayPlaybackService::class.java).get()
        manager = PlaybackSessionManager(service)
    }

    @After
    fun tearDown() {
        manager.currentSession?.let { runCatching { it.release() } }
    }

    private fun stubPlayer(): Player = mockk(relaxed = true) {
        every { canAdvertiseSession() } returns true
        every { applicationLooper } returns android.os.Looper.getMainLooper()
        every { isPlaying } returns false
        every { playbackState } returns Player.STATE_IDLE
        every { currentPosition } returns 0L
        every { bufferedPosition } returns 0L
        every { duration } returns C.TIME_UNSET
        every { contentPosition } returns 0L
        every { contentBufferedPosition } returns 0L
        every { contentDuration } returns C.TIME_UNSET
        every { playbackParameters } returns PlaybackParameters.DEFAULT
        every { currentMediaItem } returns null
        every { currentMediaItemIndex } returns 0
        every { playWhenReady } returns false
        every { playbackSuppressionReason } returns 0
        every { playerError } returns null
        every { repeatMode } returns Player.REPEAT_MODE_OFF
        every { shuffleModeEnabled } returns false
    }

    private fun engineHosting(player: Player): MediaEngine =
        mockk<MediaEngine>(relaxed = true, moreInterfaces = arrayOf(Media3PlayerHost::class)) {
            every { (this@mockk as Media3PlayerHost).media3Player } returns player
        }

    private fun controller(engine: MediaEngine): AndroidMediaSessionController =
        AndroidMediaSessionController(
            context = service as Context,
            sessionManager = manager,
            getEngine = { engine },
            getCastPlayer = { null },
            getImageUrl = { _, _ -> "" },
        )

    private fun nextItemId(): String = "item${idCounter++}"

    /** The session player under [MediaSession.getPlayer]'s metadata pin wrapper. */
    private fun activeUnderlyingPlayer(): Player? =
        (manager.currentSession?.player as? ForwardingPlayer)?.wrappedPlayer
            ?: manager.currentSession?.player

    @Test
    fun `reclaim after a foreign session took the slot restores the video session`() {
        val itemId = nextItemId()
        val video = controller(engineHosting(videoPlayer))
        video.createForItem(itemId, "Title", "Subtitle")
        assertSame(videoPlayer, activeUnderlyingPlayer())

        // A foreign session (the audio side's session around another player)
        // displaces the video session — the manager releases the outgoing one.
        val foreignPlayer = stubPlayer()
        val foreign = controller(engineHosting(foreignPlayer))
        foreign.createForPlayer(engineHosting(foreignPlayer), "foreign_$itemId")
        assertSame(foreignPlayer, activeUnderlyingPlayer())

        video.ensureActiveForItem(itemId, "Title", "Subtitle")

        assertSame(videoPlayer, activeUnderlyingPlayer())
    }

    @Test
    fun `reclaim is a no-op while the held session is the active one`() {
        val itemId = nextItemId()
        val video = controller(engineHosting(videoPlayer))
        video.createForItem(itemId, "Title", "Subtitle")
        val active = manager.currentSession

        video.ensureActiveForItem(itemId, "Title", "Subtitle")

        assertSame("an ours-and-active session must never rebuild", active, manager.currentSession)
    }
}
