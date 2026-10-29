package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins the crossfade execution half ([AudioCrossfadeHandoff]) — the transition
 * tail extracted from [AudioPlaybackManager] (the decision/execution split:
 * [AudioCrossfader] stays the trigger/ramp half, the handoff owns the swap):
 *
 *  - the stop report runs SYNCHRONOUSLY BEFORE the commit/swap writes (the
 *    crossfade site's load-bearing ordering — the manager's
 *    `commitTrackTransition` KDoc), and the session rebuild + start report
 *    read the play-session id AFTER the stop report's synchronous rotation
 *    (the fresh-id invariant [AudioProgressReporter] documents);
 *  - the promoted secondary BECOMES the live engine, the old session is
 *    released, the rebuilt session rides the shared
 *    [AudioLibraryBrowser.buildMediaSession] path and is activated through
 *    the [PlaybackSessionManager] (the service-cast fix);
 *  - the effects re-attach lands on the secondary (wholesale, standing in
 *    for the single-row ReplayGain the natural-transition site re-applies —
 *    pinned as `reapplyReplayGain = false` on the commit port);
 *  - the crossfading flag lowers and the windowed pre-warm fans out;
 *  - [AudioCrossfadeHandoff.onCrossfadeFailed] advances the primary to the
 *    requested index (seek/prepare/play) and writes the cursor — with the
 *    bounds and no-live-player guards of the manual skipToNext mirror.
 *
 * The suite constructs the handoff directly: a real chassis
 * ([AudioQueueStateCore]) for the cells, mockk collaborators everywhere, the
 * stop report mocked to reproduce the reporter's one tail-relevant contract
 * (the synchronous session-id rotation).
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioCrossfadeHandoffTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val effectsProcessor: AudioEffectsProcessor = mockk(relaxed = true)
    private val libraryBrowser: AudioLibraryBrowser = mockk(relaxed = true)
    private val sessionManager: PlaybackSessionManager = mockk(relaxed = true)
    private val queueMirror: QueuePlaylistMirror = mockk(relaxed = true)
    private val progressReporter: AudioProgressReporter = mockk(relaxed = true)
    private val playerListener: Player.Listener = mockk(relaxed = true)

    private val primaryPlayer: ExoPlayer = mockk(relaxed = true)
    private val secondaryPlayer: ExoPlayer = mockk(relaxed = true) {
        every { audioSessionId } returns 4242
    }
    private val oldSession: MediaSession = mockk(relaxed = true)
    private val newSession: MediaLibrarySession = mockk(relaxed = true)

    /** The tail's call order — the stop-before-commit ordering pin reads it. */
    private val callOrder = mutableListOf<String>()
    private val commits = mutableListOf<Triple<Int, AudioQueueItem, Boolean>>()
    private val startReports = mutableListOf<PlaybackStartInfo>()
    private val stopReportSessions = mutableListOf<String?>()
    private val buildSessionIds = mutableListOf<String>()
    private var swappedEngine: ExoPlayer? = null
    private var replacedSession: MediaSession? = null

    private lateinit var state: AudioQueueStateCore

    @Before
    fun setUp() {
        state = AudioQueueStateCore(
            scope = scope,
            playbackRepository = playbackRepository,
            lyricsManager = mockk(relaxed = true),
            progressReporter = progressReporter,
            dispatch = mockk(relaxed = true),
        )
        state._queue.value = listOf(item("current"), item("next"))
        state.currentItemId = "current"
        state.playSessionId = "session-1"
        state._currentPosition.value = 30_000L
        state._duration.value = 60_000L

        // The reporter's one tail-relevant contract: the stop report rotates
        // the play session id SYNCHRONOUSLY before returning.
        every { progressReporter.reportStopped(any(), any(), any()) } answers {
            stopReportSessions += args[1] as String?
            state.playSessionId = "session-2"
            callOrder += "stop"
        }
        every { libraryBrowser.buildMediaSession(any(), any(), any()) } answers {
            buildSessionIds += thirdArg<String>()
            newSession
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun item(id: String) = AudioQueueItem(
        id = id,
        name = "Song $id",
        artist = "Artist",
        album = "Album",
        imageUrl = null,
        mediaSourceId = "ms-$id",
    )

    private val nextItem: AudioQueueItem get() = state.queue.value[1]

    // ── the transition tail ──────────────────────────────────────────────

    @Test
    fun `the stop report precedes the commit and the session reads the rotated id`() = runBlocking {
        val handoff = handoff()

        handoff.onCrossfadeTransition(secondaryPlayer, nextIndex = 1, nextItem = nextItem)

        assertEquals(listOf("stop", "commit"), callOrder)
        // The stop report carried the PRE-rotation session id...
        assertEquals(listOf("session-1"), stopReportSessions)
        // ...while the rebuilt session and the start report carry the fresh one.
        assertEquals(listOf("session-2"), buildSessionIds)
        assertEquals(listOf("session-2"), startReports.map { it.sessionId })
    }

    @Test
    fun `the transition swaps the engine and rebuilds through the shared session builder`() = runBlocking {
        val handoff = handoff()

        handoff.onCrossfadeTransition(secondaryPlayer, nextIndex = 1, nextItem = nextItem)

        assertSame(secondaryPlayer, swappedEngine)
        verify(exactly = 1) { oldSession.release() }
        assertSame(newSession, replacedSession)
        verify(exactly = 1) { sessionManager.setActiveSession(newSession) }
        verify(exactly = 1) { secondaryPlayer.addListener(playerListener) }
    }

    @Test
    fun `the commit rides the shared handoff block without the ReplayGain re-apply`() = runBlocking {
        val handoff = handoff()

        handoff.onCrossfadeTransition(secondaryPlayer, nextIndex = 1, nextItem = nextItem)

        assertEquals(1, commits.size)
        assertEquals(1, commits[0].first)
        assertEquals("next", commits[0].second.id)
        assertFalse("the wholesale re-attach stands in for the single-row ReplayGain", commits[0].third)
    }

    @Test
    fun `the effects re-attach lands on the promoted secondary`() = runBlocking {
        val handoff = handoff()

        handoff.onCrossfadeTransition(secondaryPlayer, nextIndex = 1, nextItem = nextItem)

        verify(exactly = 1) { effectsProcessor.applyNightMode() }
        verify(exactly = 1) { effectsProcessor.applyDialogueBoost() }
        verify(exactly = 1) { effectsProcessor.applyEqualizer() }
        verify(exactly = 1) { effectsProcessor.applyBassBoost() }
        verify(exactly = 1) { effectsProcessor.applyVirtualizer() }
        verify(exactly = 1) { effectsProcessor.reattachForCrossfade(4242) }
    }

    @Test
    fun `the tail lowers the flag, pre-warms the mirror and posts the start report`() = runBlocking {
        val handoff = handoff()

        handoff.onCrossfadeTransition(secondaryPlayer, nextIndex = 1, nextItem = nextItem)

        assertFalse(handoff.isCrossfading.value)
        verify(exactly = 1) { queueMirror.prewarmAround(secondaryPlayer, 1) }
        assertEquals(1, startReports.size)
        assertEquals("next", startReports[0].itemId)
        assertEquals("ms-next", startReports[0].mediaSourceId)
        coVerify(exactly = 1) { playbackRepository.reportPlaybackStart(startReports[0]) }
    }

    // ── the failed-setup reconciliation ──────────────────────────────────

    @Test
    fun `a failed setup advances the primary and the cursor to the requested index`() {
        val handoff = handoff()

        handoff.onCrossfadeFailed(nextIndex = 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, state.currentIndex.value)
        verify(exactly = 1) { primaryPlayer.seekTo(1, 0L) }
        verify(exactly = 1) { primaryPlayer.prepare() }
        verify(exactly = 1) { primaryPlayer.playWhenReady = true }
    }

    @Test
    fun `a failed setup out of the queue's bounds is a no-op`() {
        val handoff = handoff()

        handoff.onCrossfadeFailed(nextIndex = 7)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(-1, state.currentIndex.value)
        verify(exactly = 0) { primaryPlayer.seekTo(any(), any()) }
        verify(exactly = 0) { primaryPlayer.prepare() }
    }

    @Test
    fun `a failed setup with no live player is a no-op`() {
        val handoff = handoff(exoPlayerProvider = { null })

        handoff.onCrossfadeFailed(nextIndex = 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(-1, state.currentIndex.value)
        assertNull(swappedEngine)
        verify(exactly = 0) { primaryPlayer.prepare() }
    }

    private fun handoff(
        exoPlayerProvider: () -> ExoPlayer? = { primaryPlayer },
    ): AudioCrossfadeHandoff = AudioCrossfadeHandoff(
        scope = scope,
        context = context,
        effectsProcessor = effectsProcessor,
        detailFeed = mockk<DetailFeed>(relaxed = true),
        imageUrlProvider = mockk<ImageUrlProvider>(relaxed = true),
        playbackSourceResolver = mockk(relaxed = true),
        playbackRepository = playbackRepository,
        progressReporter = progressReporter,
        libraryBrowser = libraryBrowser,
        sessionManager = sessionManager,
        queueMirror = queueMirror,
        state = state,
        playerListener = playerListener,
        exoPlayerProvider = exoPlayerProvider,
        exoPlayerSetter = { swappedEngine = it },
        mediaSessionProvider = { oldSession },
        mediaSessionSetter = { replacedSession = it },
        audioBufferProvider = { 50_000 to 500_000 },
        dataSourceFactoryProvider = { mockk(relaxed = true) },
        commitTransition = { nextIndex, nextItem, reapply ->
            callOrder += "commit"
            commits += Triple(nextIndex, nextItem, reapply)
        },
        startReport = { item ->
            PlaybackStartInfo(
                itemId = item.id,
                sessionId = state.playSessionId,
                mediaSourceId = item.mediaSourceId,
            ).also { startReports += it }
        },
    )
}
