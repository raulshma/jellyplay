package com.raulshma.jellyplay.navigation.playbackhost

import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.playback.ResolvedPlaybackSource
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.ExternalPlayerApp
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.navigation.ExternalPlaybackOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the external-player reporting contracts ([ExternalPlayerReports]):
 *
 *  - the launch builder maps the resolved source (local download or stream)
 *    onto an ACTION_VIEW intent with the `video` mime type advertising
 *    `return_result`, carrying the start position in ms only when positive,
 *    side-loads the external subtitle streams of the chosen source
 *    (external, non-image codecs only), and null resolution yields null;
 *  - every launch mints its own play session id, and the preferred-app
 *    targeting reads the playback preference slice (the default resolves to
 *    the system chooser, so no app is recorded on the launch);
 *  - the stop reports map the parsed outcome — stopped-at credits its
 *    ticks, cancellation credits the start position, completion marks the
 *    item played and reports the completion position (falling back to the
 *    start ticks when the contract reports completion without a position).
 *
 * Robolectric for the Intent/Uri assertions; the fire-and-forget reports run
 * on the class's own Main-immediate scope, driven here through
 * [Dispatchers.setMain] and advanced with [advanceUntilIdle] until each
 * report completes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ExternalPlayerReportsTest {

    private val dispatcher = StandardTestDispatcher()

    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val playbackSourceResolver: PlaybackSourceResolver = mockk(relaxed = true)
    private val playbackStore: PlaybackStore = mockk(relaxed = true)

    private val playbackSlice = MutableStateFlow(PlaybackSlice())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { playbackStore.playback } returns playbackSlice
        coEvery { playbackRepository.reportPlaybackStart(any()) } returns Result.success(Unit)
        coEvery { playbackRepository.reportPlaybackStopped(any(), any(), any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createReports() = ExternalPlayerReports(
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        playbackSourceResolver = playbackSourceResolver,
        playbackStore = playbackStore,
    )

    // ── external player launch builder ─────────────────────────────────────

    @Test
    fun `stream resolution builds a view intent with return_result and ms position`() = runTest(dispatcher) {
        val reports = createReports()
        coEvery { playbackSourceResolver.resolvePlaybackSource(any(), any(), any()) } returns
            ResolvedPlaybackSource.Stream(
                itemId = "item-1",
                url = "https://server/videos/1/stream",
                title = "Movie",
                mediaSourceId = null,
            )

        val launch = reports.buildExternalPlayerLaunch(ExternalPlayerRequest("item-1", startPositionTicks = 900_000_000L))

        assertNotNull(launch)
        assertEquals("item-1", launch!!.itemId)
        assertEquals("https://server/videos/1/stream", launch.intent.data.toString())
        assertEquals("video/*", launch.intent.type)
        assertEquals("Movie", launch.intent.getStringExtra("title"))
        assertTrue(launch.intent.getBooleanExtra("return_result", false))
        assertEquals(90_000L, launch.intent.getLongExtra("position", -1L))
        coVerify(exactly = 1) {
            playbackSourceResolver.resolvePlaybackSource(
                itemId = "item-1",
                mediaSourceId = null,
                startPositionTicks = 900_000_000L,
            )
        }
    }

    @Test
    fun `local download resolution plays the file uri with the download title`() = runTest(dispatcher) {
        val reports = createReports()
        coEvery { playbackSourceResolver.resolvePlaybackSource(any(), any(), any()) } returns
            ResolvedPlaybackSource.Local(
                itemId = "item-2",
                filePath = "/data/files/movie.mp4",
                uri = "file:///data/files/movie.mp4",
                title = "Downloaded Movie",
                download = downloadItem(),
            )

        val launch = reports.buildExternalPlayerLaunch(ExternalPlayerRequest("item-2", "ms-1"))

        assertNotNull(launch)
        assertEquals("file:///data/files/movie.mp4", launch!!.intent.data.toString())
        assertEquals("Downloaded Movie", launch.intent.getStringExtra("title"))
        // No position extra when the start position is zero.
        assertEquals(-1L, launch.intent.getLongExtra("position", -1L))
        // Local downloads carry no subtitle hand-off payload.
        assertTrue(launch.subtitles.isEmpty())
        assertFalse(launch.intent.hasExtra("subs"))
    }

    @Test
    fun `unresolvable playback source yields no external launch`() = runTest(dispatcher) {
        val reports = createReports()
        coEvery { playbackSourceResolver.resolvePlaybackSource(any(), any(), any()) } returns null

        assertNull(reports.buildExternalPlayerLaunch(ExternalPlayerRequest("item-3")))
    }

    @Test
    fun `each external launch carries a fresh play session id`() = runTest(dispatcher) {
        val reports = createReports()
        coEvery { playbackSourceResolver.resolvePlaybackSource(any(), any(), any()) } returns
            ResolvedPlaybackSource.Stream("item-1", "https://server/v", "T", null)

        val first = reports.buildExternalPlayerLaunch(ExternalPlayerRequest("item-1"))!!
        val second = reports.buildExternalPlayerLaunch(ExternalPlayerRequest("item-1"))!!

        assertTrue(first.playSessionId.isNotBlank())
        assertTrue(first.playSessionId != second.playSessionId)
    }

    @Test
    fun `stream resolution with a media source side-loads the external subtitle streams`() = runTest(dispatcher) {
        val reports = createReports()
        val source = MediaSource(
            id = "source-1",
            name = "1080p",
            mediaStreams = listOf(
                mediaStream(index = 2, isExternal = true, codec = "subrip", language = "eng", displayTitle = "English"),
                // Embedded text sub: the target player demuxes the container.
                mediaStream(index = 3, isExternal = false, codec = "subrip", language = "ger", displayTitle = "Deutsch"),
                // External image sub without a delivery URL: the subtitle
                // builder refuses image codecs ("" URL) → dropped.
                mediaStream(index = 4, isExternal = true, codec = "pgs", language = "jpn", displayTitle = "PGS"),
            ),
        )
        coEvery { playbackSourceResolver.resolvePlaybackSource(any(), any(), any()) } returns
            ResolvedPlaybackSource.Stream(
                itemId = "item-1",
                url = "https://server/videos/1/stream",
                title = "Movie",
                mediaSourceId = "source-1",
                mediaSource = source,
            )
        // The subtitle URL ladder lives behind resolveSubtitleStreamUrl — a
        // default interface method the relaxed mock would intercept and
        // answer "" — so the test stubs the ladder's outcomes directly: an
        // external text sub resolves, the embedded track resolves to null
        // (the target player demuxes the container), and the image codec
        // resolves to null (the ladder refuses it).
        every {
            playbackRepository.resolveSubtitleStreamUrl(any(), any(), any(), any())
        } answers {
            val stream = firstArg<MediaStream>()
            when {
                !stream.deliveryUrl.isNullOrBlank() -> "https://server/delivery"
                !stream.isExternal -> null
                stream.codec == "pgs" -> null
                else -> "https://server/sub/${stream.index}"
            }
        }

        val launch = reports.buildExternalPlayerLaunch(ExternalPlayerRequest("item-1", "source-1", subtitleStreamIndex = 2))

        assertNotNull(launch)
        // Only the side-loadable external sub survives (the embedded track is
        // the target player's demux job; the image codec is refused).
        assertEquals(listOf("https://server/sub/2"), launch!!.subtitles.map { it.url })
        assertEquals(listOf("English"), launch.subtitles.map { it.name })
        assertEquals(listOf("eng"), launch.subtitles.map { it.filename })
        assertEquals(listOf(true), launch.subtitles.map { it.isSelected })
        // The intent carries the array extras + the selected track's enable URL.
        val subs = launch.intent.getParcelableArrayListExtra<Uri>("subs")!!
        assertEquals(listOf("https://server/sub/2"), subs.map { it.toString() })
        assertEquals("https://server/sub/2", launch.intent.getParcelableExtra<Uri>("subs.enable")!!.toString())
        // Default preference (SYSTEM_CHOOSER): no targeting recorded yet.
        assertNull(launch.resolvedApp)
        assertEquals(ExternalPlayerApp.SYSTEM_CHOOSER, launch.preferredApp)
    }

    // ── external playback progress reporting ───────────────────────────────

    @Test
    fun `external playback start reports the launch position under the session id`() = runTest(dispatcher) {
        val reports = createReports()
        val launch = ExternalPlayerLaunch(
            intent = Intent(Intent.ACTION_VIEW),
            itemId = "item-1",
            startPositionTicks = 120_000_000L,
            playSessionId = "session-1",
        )

        reports.reportExternalPlaybackStart(launch)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = "item-1",
                    sessionId = "session-1",
                    startPositionTicks = 120_000_000L,
                ),
            )
        }
    }

    @Test
    fun `external playback stop reports the final position`() = runTest(dispatcher) {
        val reports = createReports()
        val launch = ExternalPlayerLaunch(
            intent = Intent(Intent.ACTION_VIEW),
            itemId = "item-1",
            startPositionTicks = 120_000_000L,
            playSessionId = "session-1",
        )

        reports.reportExternalPlaybackStopped(launch, ExternalPlaybackOutcome.StoppedAt(300_000_000L))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "session-1", 300_000_000L)
        }
        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
    }

    @Test
    fun `external playback completion marks the item played and reports the completion position`() = runTest(dispatcher) {
        val reports = createReports()
        val launch = reportLaunchOf()

        reports.reportExternalPlaybackStopped(launch, ExternalPlaybackOutcome.Completed(9_000_000_000L))
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.markPlayed("item-1") }
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "session-1", 9_000_000_000L)
        }
    }

    @Test
    fun `external playback completion without a position still marks played and falls back to the start ticks`() = runTest(dispatcher) {
        val reports = createReports()
        val launch = reportLaunchOf()

        // The MPV/mpvKt contract reports completion without a position.
        reports.reportExternalPlaybackStopped(launch, ExternalPlaybackOutcome.Completed(0L))
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.markPlayed("item-1") }
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "session-1", 120_000_000L)
        }
    }

    @Test
    fun `external playback cancellation credits the start position`() = runTest(dispatcher) {
        val reports = createReports()
        val launch = reportLaunchOf()

        reports.reportExternalPlaybackStopped(launch, ExternalPlaybackOutcome.Cancelled(120_000_000L))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "session-1", 120_000_000L)
        }
        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /** The report-pair tests' one launch shape: item-1, 120 s start, session-1. */
    private fun reportLaunchOf(startPositionTicks: Long = 120_000_000L): ExternalPlayerLaunch =
        ExternalPlayerLaunch(
            intent = Intent(Intent.ACTION_VIEW),
            itemId = "item-1",
            startPositionTicks = startPositionTicks,
            playSessionId = "session-1",
        )

    private fun downloadItem() = DownloadItem(
        id = "dl-1",
        mediaItemId = "item-2",
        name = "Downloaded Movie",
        mediaType = MediaType.MOVIE,
        downloadPath = "/data/files/movie.mp4",
        downloadUrl = "https://server/download",
        totalSizeBytes = 100L,
        downloadedBytes = 100L,
        status = DownloadStatus.COMPLETED,
    )

    /** Subtitle stream for the external hand-off payload tests. */
    private fun mediaStream(
        index: Int,
        isExternal: Boolean,
        codec: String?,
        language: String?,
        displayTitle: String?,
    ) = MediaStream(
        index = index,
        type = StreamType.SUBTITLE,
        codec = codec,
        language = language,
        displayTitle = displayTitle,
        isExternal = isExternal,
    )
}
