package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the priority guard in [PlaybackSessionManager.setActiveSession]: an
 * actively-playing session must not be displaced by an idle challenger, which
 * was the root cause of the recurring "notification media player invisible +
 * headset pause dead until app restart" bug.
 *
 * Uses real [MediaSession] instances (Robolectric context) wrapping mockk
 * [Player]s so the guard's `player.isPlaying` predicate is exercised exactly as
 * in production. mockk auto-stubs the Player interface; we only shape
 * `isPlaying`, `playbackState`, and `applicationLooper` (the latter is probed by
 * `MediaSession`'s constructor).
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackSessionManagerPriorityTest {

    private lateinit var manager: PlaybackSessionManager
    private val createdSessions = mutableListOf<MediaSession>()
    private var idCounter = 0

    @Before
    fun setUp() {
        manager = PlaybackSessionManager(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        createdSessions.forEach { runCatching { it.release() } }
        createdSessions.clear()
    }

    @Test
    fun playingSession_isNotDisplacedBy_idleChallenger() {
        val playing = newSession(isPlaying = true)
        val idle = newSession(isPlaying = false)

        manager.setActiveSession(playing)
        assertSame(playing, manager.currentSession)

        // Idle challenger must be rejected; the playing holder keeps the slot.
        manager.setActiveSession(idle)

        assertSame(playing, manager.currentSession)
    }

    @Test
    fun playingSession_isDisplacedBy_anotherPlayingChallenger() {
        // Crossfade-style swap: playing -> playing must still displace, so audio
        // crossfade and same-engine reloads are not blocked by the guard.
        val first = newSession(isPlaying = true)
        val second = newSession(isPlaying = true)

        manager.setActiveSession(first)
        manager.setActiveSession(second)

        assertSame(second, manager.currentSession)
    }

    @Test
    fun idleSession_isDisplacedBy_anyChallenger() {
        // No playing holder -> normal last-writer-wins semantics.
        val idleHolder = newSession(isPlaying = false)
        val idleChallenger = newSession(isPlaying = false)

        manager.setActiveSession(idleHolder)
        manager.setActiveSession(idleChallenger)

        assertSame(idleChallenger, manager.currentSession)
    }

    @Test
    fun clearSession_removesHolder_whenItIsCurrent() {
        val session = newSession(isPlaying = true)
        manager.setActiveSession(session)

        manager.clearSession(session)

        assertNull(manager.currentSession)
    }

    @Test
    fun clearSession_isNoOp_forForeignSession() {
        val holder = newSession(isPlaying = true)
        val foreign = newSession(isPlaying = true)
        manager.setActiveSession(holder)

        manager.clearSession(foreign)

        assertSame(holder, manager.currentSession)
        assertNotNull(manager.currentSession)
    }

    @Test
    fun playingLibraryHolder_isNotDisplacedBy_idleLibraryChallenger() {
        // Same guard with the real production types: the audio/initial + rebuilt
        // crossfade sessions are MediaLibrarySessions, not plain MediaSessions.
        val playing = newLibrarySession(isPlaying = true)
        val idle = newLibrarySession(isPlaying = false)

        manager.setActiveSession(playing)
        manager.setActiveSession(idle)

        assertSame(playing, manager.currentSession)
    }

    @Test
    fun playingLibraryHolder_isDisplacedBy_playingLibraryChallenger() {
        // Crossfade-style swap with production types: playing -> playing must
        // still displace, so audio crossfade rebuilds are not blocked.
        val first = newLibrarySession(isPlaying = true)
        val second = newLibrarySession(isPlaying = true)

        manager.setActiveSession(first)
        manager.setActiveSession(second)

        assertSame(second, manager.currentSession)
    }

    @Test
    fun libraryChallenger_survives_theServiceCast() {
        // JellyPlayPlaybackService.onGetSession casts the current session to
        // MediaLibrarySession. A MediaLibrarySession must never be rejected.
        val library = newLibrarySession(isPlaying = true)
        manager.setActiveSession(library)

        assertTrue(manager.currentSession is MediaLibrarySession)
    }

    @Test
    fun plainMediaSessionChallenger_isTheKnownRegression_forTheServiceCast() {
        // Regression guard: the pre-fix audio crossfade path rebuilt a plain
        // MediaSession; it fails the `as? MediaLibrarySession` cast in
        // JellyPlayPlaybackService.onGetSession, which breaks the now-playing
        // notification + headset buttons until app restart. A re-introduction of
        // a plain-session challenger must fail this test.
        val plain = newSession(isPlaying = true)
        manager.setActiveSession(plain)

        assertTrue(manager.currentSession as? MediaLibrarySession == null)
    }

    private fun newLibrarySession(isPlaying: Boolean): MediaLibrarySession {
        val player = stubMediaSessionPlayer(isPlaying)
        // Media3's MediaLibrarySession construction requires its context to be a
        // MediaLibraryService (it hooks the session to the host service). Passing
        // the Robolectric-built JellyPlayPlaybackService as the context boots the
        // exact production construction path; a plain Application context throws
        // ClassCastException on Robolectric SDK 35.
        val service = Robolectric.buildService(JellyPlayPlaybackService::class.java).get()
        val session = MediaLibrarySession.Builder(
            service,
            player,
            NO_OP_LIBRARY_CALLBACK,
        ).setId("${this.javaClass.simpleName}_lib_${idCounter++}").build()
        createdSessions.add(session)
        return session
    }

    private fun newSession(isPlaying: Boolean): MediaSession {
        val player = stubMediaSessionPlayer(isPlaying)
        val session = MediaSession.Builder(
            ApplicationProvider.getApplicationContext(),
            player,
        ).setId("${this.javaClass.simpleName}_${idCounter++}").build()
        createdSessions.add(session)
        return session
    }

    private companion object {
        /**
         * Satisfies [MediaLibrarySession]'s constructor so the manager tests can
         * exercise the real production session type. The base callback's
         * defaults reject every library method, which is correct here — the
         * guard only reads the player.
         */
        val NO_OP_LIBRARY_CALLBACK = object : MediaLibrarySession.Callback {}
    }
}
