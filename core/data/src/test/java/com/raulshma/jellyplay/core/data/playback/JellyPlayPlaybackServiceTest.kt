package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector
import com.raulshma.jellyplay.core.model.StreamingQuality
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Pins [JellyPlayPlaybackService]'s media-session lifecycle invariants:
 *
 * - `onGetSession` hands out the active session only as a
 *   [MediaLibraryService.MediaLibrarySession] (the cast that must survive the
 *   crossfade rebuild — the same invariant pinned at the manager level by
 *   AudioPlaybackManagerCrossfadeTest).
 * - `onSessionChanged(null, _)` (session cleared) stops the service.
 * - `onTaskRemoved` with no active session stops the service; with a session
 *   whose player is paused it also stops-and-releases the audio manager (a
 *   swiped-away paused app must not leak the player), while a playing session
 *   keeps the service alive.
 *
 * Runs against the exact production construction path: a real
 * [PlaybackSessionManager] + [AudioLibraryBrowser.buildMediaSession] around a
 * mockk player (testFixture [stubMediaSessionPlayer]), with the
 * [AudioPlaybackManager] and [CastManager] mocked in a test-local Koin
 * container.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JellyPlayPlaybackServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audioPlaybackManager: AudioPlaybackManager = mockk(relaxed = true)
    private lateinit var manager: PlaybackSessionManager
    private lateinit var browser: AudioLibraryBrowser

    @Before
    fun setUp() {
        manager = PlaybackSessionManager(context)
        startKoin {
            modules(module {
                single<PlaybackSessionManager> { manager }
                single<AudioPlaybackManager> { audioPlaybackManager }
            })
        }
        browser = AudioLibraryBrowser(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            mediaRepository = mockk<MediaRepository>(relaxed = true),
            playlistRepository = mockk<PlaylistRepository>(relaxed = true),
            downloadRepository = mockk<DownloadRepository>(relaxed = true),
            playbackRepository = mockk<PlaybackRepository>(relaxed = true),
            playbackSourceResolver = mockk<PlaybackSourceResolver>(relaxed = true),
            streamingQualityProvider = { StreamingQuality.AUTO },
            adaptiveBitrateSelector = mockk<AdaptiveBitrateSelector>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun buildService(): JellyPlayPlaybackService =
        Robolectric.buildService(JellyPlayPlaybackService::class.java).get()

    private fun activeSession(isPlaying: Boolean): MediaLibraryServiceMediaLibrarySession {
        val service = buildService()
        val session = browser.buildMediaSession(
            context = service,
            player = stubMediaSessionPlayer(isPlaying),
            sessionId = "svc_${System.nanoTime()}",
        )
        manager.setActiveSession(session)
        return MediaLibraryServiceMediaLibrarySession(service, session)
    }

    /** Pairs the host service with the session built against it. */
    private class MediaLibraryServiceMediaLibrarySession(
        val service: JellyPlayPlaybackService,
        val session: MediaSession,
    )

    @Test
    fun `onGetSession hands out the active session as a MediaLibrarySession`() {
        val active = activeSession(isPlaying = true)
        val service = active.service

        val handed = service.onGetSession(mockk<ControllerInfo>(relaxed = true))

        assertSame(active.session, handed)
        assertTrue(handed is androidx.media3.session.MediaLibraryService.MediaLibrarySession)
    }

    @Test
    fun `clearing the session stops the service`() {
        val active = activeSession(isPlaying = true)

        active.service.onSessionChanged(null, active.session)

        assertTrue(shadowOf(active.service).isStoppedBySelf())
    }

    @Test
    fun `task removed without a session stops the service without touching the audio manager`() {
        val service = buildService()

        service.onTaskRemoved(null)

        assertTrue(shadowOf(service).isStoppedBySelf())
        verify(exactly = 0) { audioPlaybackManager.stopAndRelease() }
    }

    @Test
    fun `task removed with a paused session releases the audio manager and stops`() {
        val active = activeSession(isPlaying = false)

        active.service.onTaskRemoved(null)

        verify(exactly = 1) { audioPlaybackManager.stopAndRelease() }
        assertTrue(shadowOf(active.service).isStoppedBySelf())
    }

    @Test
    fun `task removed while playing keeps the service and audio alive`() {
        val active = activeSession(isPlaying = true)

        active.service.onTaskRemoved(null)

        verify(exactly = 0) { audioPlaybackManager.stopAndRelease() }
        assertFalse(shadowOf(active.service).isStoppedBySelf())
    }
}
