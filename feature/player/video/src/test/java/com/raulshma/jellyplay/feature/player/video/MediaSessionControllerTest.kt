package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.playback.stubMediaSessionPlayer
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaSessionControllerTest {

    private lateinit var context: Context
    private lateinit var sessionManager: PlaybackSessionManager
    private lateinit var mockPlayer: Player
    private lateinit var controller: MediaSessionController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Real manager so setActiveSession's guard + listener wiring are exercised.
        sessionManager = PlaybackSessionManager(context)
        mockPlayer = stubPlayer()

        controller = MediaSessionController(
            context = context,
            sessionManager = sessionManager,
            getPlayer = { mockPlayer },
            getImageUrl = { itemId, _ -> "https://example.com/art/$itemId.jpg" },
        )
    }

    @Test
    fun createForItem_buildsMediaLibrarySession_andActivatesIt() {
        controller.createForItem(itemId = "movie-1", title = "Movie 1", subtitle = "Action Movie")

        // onGetSession casts currentSession to MediaLibrarySession; a plain
        // MediaSession would be rejected and break background media buttons.
        val current = sessionManager.currentSession
        assertTrue("video session must be a MediaLibrarySession", current is MediaLibrarySession)
    }

    @Test
    fun createForItem_doesNotClearSlotBeforeActivating() {
        // Change 2 (atomic replace): createForItem must NOT call its own
        // release() first, which would null the singleton slot and stopSelf the
        // service mid-reload. Assert the slot is never null between create calls.
        controller.createForItem(itemId = "movie-1", title = "Movie 1", subtitle = "Action Movie")
        val first = sessionManager.currentSession
        assertTrue(first is MediaLibrarySession)

        controller.createForItem(itemId = "movie-2", title = "Movie 2", subtitle = "Comedy")

        // After the second create, the new session is current and the old one
        // was released by setActiveSession's atomic replace — never via a
        // controller-side clearSession that would briefly null the slot.
        val second = sessionManager.currentSession
        assertTrue(second is MediaLibrarySession)
        assertTrue(first !== second)
    }

    @Test
    fun createForPlayer_buildsMediaLibrarySession() {
        controller.createForPlayer(mockPlayer, sessionId = "test_session_id")

        assertTrue(sessionManager.currentSession is MediaLibrarySession)
    }

    @Test
    fun release_clearsSessionFromManager() {
        controller.createForItem(itemId = "movie-1", title = "Movie 1", subtitle = "Action Movie")
        controller.release()
        // Idempotent: second release must not throw.
        controller.release()

        assertTrue(sessionManager.currentSession == null)
    }

    private fun stubPlayer(): Player = stubMediaSessionPlayer(isPlaying = false)
}
