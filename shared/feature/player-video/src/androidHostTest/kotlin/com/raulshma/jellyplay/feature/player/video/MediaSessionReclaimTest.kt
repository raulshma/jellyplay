package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.playback.JellyPlayPlaybackService
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.feature.player.video.engine.Media3PlayerHost
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the video session's slot choreography: the
 * [MediaSessionController.ensureActiveForItem] play-edge reclaim AND the
 * [AndroidMediaSessionController.createForItem] reuse fast path. The
 * manager's priority guard releases a session born idle against a
 * then-playing holder; when the granted focus claim later pauses that
 * holder, the reclaim must give video its session back — and must be a
 * no-op while the held session is already the active one. The fast path is
 * the reclaim's counterpart in [createForItem]: a rebind whose item id /
 * title / subtitle / artwork / transport player all match the held session
 * keeps the exact [MediaSession] instance (no teardown, no re-registration,
 * no listener churn, no notification flicker), and rebuilds the moment any
 * of those genuinely change — including the engine-swap case where only the
 * player identity moved (a URL-swap reload always replaces the transport).
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

    /** An engine whose hosted player can be re-stubbed mid-test (the reload family's engine swap). */
    private fun swappableEngine(): MediaEngine =
        mockk(relaxed = true, moreInterfaces = arrayOf(Media3PlayerHost::class))

    /** The pinned-metadata title our ForwardingPlayer override serves (unwrapped from Media3's PlayerWrapper). */
    // Read from the SESSION player, not the unwrapped transport: the pinned
    // metadata IS the ForwardingPlayer's getMediaMetadata() override, so the
    // wrapped player would report its own (null) metadata and always fail.
    private fun activePinnedTitle(): String? =
        manager.currentSession?.player?.mediaMetadata?.title?.toString()

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

    // ── the createForItem reuse fast path ───────────────────────────────────

    @Test
    fun `same item same labels same player rebind keeps the session instance`() {
        val itemId = nextItemId()
        val video = controller(engineHosting(videoPlayer))
        video.createForItem(itemId, "Title", "Subtitle")
        val first = manager.currentSession

        // The reload funnel re-fires for an unchanged rebind (the shape of the
        // mini-player reclaim and the play-edge check): the fast path must
        // keep the exact MediaSession — no teardown, no re-registration of the
        // jellyplay_video_<itemId> id, no notification rebuild.
        video.createForItem(itemId, "Title", "Subtitle")

        assertSame("an unchanged rebind must not rebuild", first, manager.currentSession)
        assertSame(videoPlayer, activeUnderlyingPlayer())
    }

    @Test
    fun `changed title rebuilds and the replacement pins the new title`() {
        val itemId = nextItemId()
        val video = controller(engineHosting(videoPlayer))
        video.createForItem(itemId, "Title", "Subtitle")
        val first = manager.currentSession

        video.createForItem(itemId, "New Title", "Subtitle")

        assertNotSame("a label change must rebuild", first, manager.currentSession)
        // The rebuild's pinned metadata is the ONLY title the session serves —
        // media controls must reflect the new item metadata, never the stale one.
        assertEquals("New Title", activePinnedTitle())
    }

    @Test
    fun `changed item rebuilds under the new item's session id`() {
        val itemId = nextItemId()
        val video = controller(engineHosting(videoPlayer))
        video.createForItem(itemId, "Title", "Subtitle")
        val first = manager.currentSession

        val nextItem = nextItemId()
        video.createForItem(nextItem, "Other Title", "Subtitle")

        assertNotSame("a different item must rebuild", first, manager.currentSession)
        assertEquals("jellyplay_video_$nextItem", manager.currentSession?.id)
    }

    @Test
    fun `same item with a replaced transport player rebuilds onto the new player`() {
        val itemId = nextItemId()
        val engine = swappableEngine()
        every { (engine as Media3PlayerHost).media3Player } returns videoPlayer
        val video = controller(engine)
        video.createForItem(itemId, "Title", "Subtitle")
        val first = manager.currentSession

        // The reload family's engine swap for the SAME item: a replacement
        // engine whose transport is a NEW player instance. The pin's player
        // identity term must fail the reuse and put the session on the live
        // player — keeping it would drive notification commands into the
        // released instance.
        val replacementPlayer = stubPlayer()
        every { (engine as Media3PlayerHost).media3Player } returns replacementPlayer
        video.createForItem(itemId, "Title", "Subtitle")

        assertNotSame("a transport swap must rebuild even for the same item", first, manager.currentSession)
        assertSame(replacementPlayer, activeUnderlyingPlayer())
    }

    @Test
    fun `changed artwork url rebuilds even when labels and item are unchanged`() {
        // A url lambda over a var — the controller's real getImageUrl folds in
        // the item's primary image tag from the detail snapshot held at call
        // time, so the SAME id can resolve to a DIFFERENT url after a detail
        // refresh. Also proves the pin keys on the RESOLVED transport (both
        // engine mocks here host the same player instance), not the engine wrapper.
        var artworkUrl = "https://server/Items/x/Primary?tag=a"
        val video = AndroidMediaSessionController(
            context = service as Context,
            sessionManager = manager,
            getEngine = { engineHosting(videoPlayer) },
            getCastPlayer = { null },
            getImageUrl = { _, _ -> artworkUrl },
        )
        val itemId = nextItemId()
        video.createForItem(itemId, "Title", "Subtitle")
        val first = manager.currentSession

        artworkUrl = "https://server/Items/x/Primary?tag=b"
        video.createForItem(itemId, "Title", "Subtitle")

        assertNotSame("an artwork change must rebuild", first, manager.currentSession)
    }

    @Test
    fun `fast path fires no sessionChanged while a genuine rebuild fires exactly one`() {
        var changes = 0
        manager.addListener(object : PlaybackSessionManager.Listener {
            override fun onSessionChanged(newSession: MediaSession?, oldSession: MediaSession?) {
                changes++
            }
        })
        val itemId = nextItemId()
        val video = controller(engineHosting(videoPlayer))

        video.createForItem(itemId, "Title", "Subtitle")
        assertEquals(1, changes)

        // The fast path's whole point: the manager slot already points at the
        // kept session, so setActiveSession (and with it every listener + the
        // service restart) must not run again.
        video.createForItem(itemId, "Title", "Subtitle")
        assertEquals("an unchanged rebind must not churn the listeners", 1, changes)

        video.createForItem(itemId, "New Title", "Subtitle")
        assertEquals("a genuine rebuild fires the swap exactly once", 2, changes)
    }
}
