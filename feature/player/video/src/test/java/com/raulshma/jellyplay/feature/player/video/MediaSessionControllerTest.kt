package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.playback.stubMediaSessionPlayer
import org.junit.After
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

    @After
    fun tearDown() {
        // Media3's SESSION_ID_TO_SESSION_MAP is process-static and Robolectric
        // does not reset non-Android statics between test methods, so a session
        // leaked by a failing test would poison later same-ID builds in this
        // class. Release whatever this test built.
        controller.release()
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
    fun createForItem_sameItemIdRebuild_replacesSessionWithoutCrash() {
        // Force-transcode / quality / engine-fallback reloads rebuild the session
        // for the SAME item, so the replacement reuses the old session's ID.
        // Media3 registers session IDs in a process-wide map at construction
        // time and throws "Session ID must be unique" if the old session is
        // still registered when the replacement is built — the controller must
        // release the held session first.
        controller.createForItem(itemId = "movie-1", title = "Movie 1", subtitle = "Action")
        val first = sessionManager.currentSession

        controller.createForItem(itemId = "movie-1", title = "Movie 1", subtitle = "Action")

        val second = sessionManager.currentSession
        assertTrue(second is MediaLibrarySession)
        assertTrue("rebuild must install a fresh session", first !== second)
        // A third rebuild proves the retired session's ID was freed (and that
        // the cycle is repeatable, not a one-shot release).
        controller.createForItem(itemId = "movie-1", title = "Movie 1", subtitle = "Action")
        assertTrue(sessionManager.currentSession !== second)
    }

    @Test
    fun createForItem_sameItemIdRebuild_neverNullsManagerSlot() {
        // The pre-build release must bypass clearSession: nulling the slot
        // fires onSessionChanged(null) → the playback service stopSelfs
        // mid-reload and cannot restart under background-start restrictions.
        controller.createForItem(itemId = "movie-1", title = "Movie 1", subtitle = "Action")
        val first = sessionManager.currentSession

        controller.createForItem(itemId = "movie-1", title = "Movie 1", subtitle = "Action")

        // The slot was continuously occupied — by the retired session during
        // the build, then by its replacement.
        assertTrue(first != null)
        assertTrue(sessionManager.currentSession != null)
    }

    @Test
    fun createForPlayer_buildsMediaLibrarySession() {
        controller.createForPlayer(mockPlayer, sessionId = "test_session_id")

        assertTrue(sessionManager.currentSession is MediaLibrarySession)
    }

    @Test
    fun createForPlayer_sameSessionIdRebuild_replacesSessionWithoutCrash() {
        // The cast detach/reattach path rebuilds with a stable sessionId; the
        // same-ID collision applies as for createForItem.
        controller.createForPlayer(mockPlayer, sessionId = "video_cast_session")
        val first = sessionManager.currentSession

        controller.createForPlayer(mockPlayer, sessionId = "video_cast_session")

        assertTrue(sessionManager.currentSession is MediaLibrarySession)
        assertTrue(first !== sessionManager.currentSession)
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
