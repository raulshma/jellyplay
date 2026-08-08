package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector
import com.raulshma.jellyplay.core.model.StreamingQuality
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression guard for the audio-crossfade session rebuild in
 * [AudioPlaybackManager.onCrossfadeTransition].
 *
 * `AudioPlaybackManager` is not unit-testable in isolation (its Hilt-injected
 * constructor pulls in ~20 repositories/processors), so the rebuild is verified
 * here at the [PlaybackSessionManager] level using the **exact production
 * construction path**: [AudioLibraryBrowser.buildMediaSession]. Both the
 * initial audio session and the post-crossfade rebuild go through that shared
 * builder (the extraction that fixed the downgrade), so this test exercises the
 * real code the service depends on, not a test-local copy.
 *
 * The host service ([JellyPlayPlaybackService.onGetSession]) casts the active
 * session with `as? MediaLibrarySession`. Pre-fix, the crossfade rebuilt a plain
 * [MediaSession], the cast returned null, the service rejected controller
 * connections, and the now-playing notification + headset buttons stayed dead
 * until app restart — the same bug class already fixed for video in
 * MediaSessionController and pinned by
 * [PlaybackSessionManagerPriorityTest].
 *
 * Uses real [MediaSession] instances (Robolectric context) wrapping mockk
 * [Player]s via the shared [stubMediaSessionPlayer] testFixture.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioPlaybackManagerCrossfadeTest {

    private lateinit var manager: PlaybackSessionManager
    private lateinit var browser: AudioLibraryBrowser
    private val createdSessions = mutableListOf<MediaSession>()
    private var idCounter = 0

    @Before
    fun setUp() {
        manager = PlaybackSessionManager(ApplicationProvider.getApplicationContext())
        // A real browser with mocked collaborators: the crossfade fix lives in
        // its buildMediaSession(), so the constructor only needs to succeed.
        browser = AudioLibraryBrowser(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            mediaRepository = mockk<MediaRepository>(relaxed = true),
            downloadRepository = mockk<DownloadRepository>(relaxed = true),
            playbackRepository = mockk<PlaybackRepository>(relaxed = true),
            playbackSourceResolver = mockk<PlaybackSourceResolver>(relaxed = true),
            streamingQualityProvider = { StreamingQuality.AUTO },
            adaptiveBitrateSelector = mockk<AdaptiveBitrateSelector>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        createdSessions.forEach { runCatching { it.release() } }
        createdSessions.clear()
    }

    @Test
    fun crossfadeRebuild_producesSessionThatSurvives_theServiceCast() {
        // The rebuild must remain a MediaLibrarySession so
        // JellyPlayPlaybackService.onGetSession's `as? MediaLibrarySession`
        // returns it (notification + headset buttons stay alive past crossfade).
        val rebuilt = crossfadeRebuild(isPlaying = true)

        assertTrue(manager.currentSession === rebuilt)
        assertTrue(manager.currentSession is MediaLibrarySession)
    }

    @Test
    fun crossfadeRebuild_displacesPreviousSession_whileStillPlaying() {
        // The guard in setActiveSession rejects idle challengers, but a
        // crossfade swaps playing -> playing: the rebuild must displace the
        // previous audio session, not be blocked or release it wrongly.
        val first = crossfadeRebuild(isPlaying = true)
        val second = crossfadeRebuild(isPlaying = true)

        assertSame(second, manager.currentSession)
        assertTrue(second is MediaLibrarySession)
    }

    /**
     * Mirrors the fixed [AudioPlaybackManager.onCrossfadeTransition] rebuild:
     * release the old session, build a new [MediaLibrarySession] around the
     * swapped-in player via the shared production builder, and activate it
     * through the session manager.
     */
    private fun crossfadeRebuild(isPlaying: Boolean): MediaLibrarySession {
        val player = stubMediaSessionPlayer(isPlaying)
        // Media3's MediaLibrarySession construction requires its context to be a
        // MediaLibraryService; pass the Robolectric-built JellyPlayPlaybackService
        // so the exact production construction path runs (a plain Application
        // context throws ClassCastException under Robolectric SDK 35).
        val service = Robolectric.buildService(JellyPlayPlaybackService::class.java).get()
        val session = browser.buildMediaSession(
            context = service,
            player = player,
            sessionId = "crossfade_${idCounter++}",
        )
        createdSessions.add(session)
        manager.setActiveSession(session)
        return session
    }
}