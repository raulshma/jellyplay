package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric coverage for the external-provider subtitle download path in
 * [SubtitleManager.downloadProviderSubtitle] — the upload-persistence contract
 * introduced for provider subtitles. The pure-JVM [SubtitleManagerTest] can't
 * exercise this path because it touches `android.util.Base64` and writes a cache
 * file; Robolectric supplies a working Base64 and a real cache dir.
 *
 * Verifies, for a successful external download:
 *  1. the bytes are side-loaded into the live engine (`addExternalSubtitle`);
 *  2. the bytes are uploaded to the Jellyfin server (`uploadSubtitle`);
 *  3. the media-detail cache is invalidated and re-fetched so the new stream
 *     surfaces (`invalidateDetailCache` + `onMediaDetailRefreshed`);
 *  4. the per-id status lands on [SubtitleDownloadState.DOWNLOADED].
 *
 * And for the failure branches: an upload failure marks the id FAILED and
 * surfaces an error, without performing the refresh.
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleManagerProviderDownloadTest {

    private lateinit var context: Context
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var subtitleProviderRepository: SubtitleProviderRepository
    private lateinit var userMessageBus: UserMessageBus
    private lateinit var addedSubtitles: MutableList<SubtitleSource>
    private lateinit var refreshedDetails: MutableList<MediaDetail>
    private lateinit var state: MutableStateFlow<VideoPlayerUiState>

    // Unconfined so the manager's `scope.launch` in downloadProviderSubtitle
    // runs to completion synchronously inside the runBlocking-driven call.
    // The body still switches to Dispatchers.IO for the cache-file write, so
    // tests drain the scope's children (see [drain]) before asserting.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** Waits for every job the manager launched on [scope] to complete. */
    private suspend fun drain() {
        scope.coroutineContext[Job]!!.children.forEach { it.join() }
    }

    private val result = SubtitleSearchResult(
        provider = SubtitleProviderKind.OPENSUBTITLES,
        id = "os-42",
        language = "eng",
        displayName = "Movie.en",
        format = "srt",
        isForced = false,
        isHearingImpaired = true,
        fileName = "Movie.en.srt",
    )
    private val bytes = "1\n00:00:01,000 --> 00:00:02,000\nHi\n".toByteArray()
    private val file = SubtitleFile(bytes = bytes, fileName = "Movie.en.srt", format = "srt", language = "eng")

    @Before
    fun setUp() {
        context = org.robolectric.RuntimeEnvironment.getApplication()
        playbackRepository = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        subtitleProviderRepository = mockk(relaxed = true)
        userMessageBus = mockk(relaxed = true)
        addedSubtitles = mutableListOf()
        refreshedDetails = mutableListOf()
        state = MutableStateFlow(VideoPlayerUiState())
    }

    private fun manager(): SubtitleManager = SubtitleManager(
        context = context,
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        subtitleProviderRepository = subtitleProviderRepository,
        userMessageBus = userMessageBus,
        scope = scope,
        addExternalSubtitle = { addedSubtitles += it },
        getUiState = { state.value },
        updateUiState = { transform -> state.value = transform(state.value) },
        getCurrentItemId = { "item-1" },
        onMediaDetailRefreshed = { refreshedDetails += it },
    )

    @Test
    fun downloadProviderSubtitle_success_sideLoadsUploadsRefreshesAndMarksDownloaded() = runBlocking {
        coEvery { subtitleProviderRepository.downloadExternal(result) } returns Result.success(file)
        coEvery { playbackRepository.uploadSubtitle(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        val detail = MediaDetail(
            item = MediaItem(id = "item-1", name = "Movie", mediaType = MediaType.MOVIE),
            mediaSources = emptyList(),
            chapters = emptyList(),
        )
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(detail)

        manager().downloadProviderSubtitle(result)
        drain()

        // 1. Side-loaded into the live engine for immediate in-session use.
        assertEquals(1, addedSubtitles.size)
        val sideLoaded = addedSubtitles.single()
        assertEquals("eng", sideLoaded.language)
        assertEquals("Movie.en", sideLoaded.label)
        assertEquals("srt", sideLoaded.codec)

        // 2. Uploaded to the Jellyfin server as a persisted MediaStream.
        coVerify(exactly = 1) {
            playbackRepository.uploadSubtitle(
                itemId = "item-1",
                data = match { it.isNotBlank() && it != "null" },
                fileName = "Movie.en.srt",
                language = "eng",
                isForced = false,
                isHearingImpaired = true,
            )
        }

        // 3. Cache invalidated then re-fetched so the stream surfaces; the
        //    refreshed detail is handed back to the VM to rebuild mediaStreams.
        io.mockk.verify(atLeast = 1) { mediaRepository.invalidateDetailCache("item-1") }
        coVerify(atLeast = 1) { mediaRepository.getMediaDetail("item-1") }
        assertEquals(listOf(detail), refreshedDetails)

        // 4. Per-id status reflects success. Status is keyed on the composite
        //    "provider:id".
        val status = state.value.downloadingSubtitles["OPENSUBTITLES:os-42"]
        assertEquals(SubtitleDownloadState.DOWNLOADED, status?.state)
        assertNull(status?.errorMessage)
        coVerify { userMessageBus.info(any<String>()) }
    }

    @Test
    fun downloadProviderSubtitle_uploadFailure_marksFailedAndSkipsRefresh() = runBlocking {
        coEvery { subtitleProviderRepository.downloadExternal(result) } returns Result.success(file)
        coEvery { playbackRepository.uploadSubtitle(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("server rejected"))

        manager().downloadProviderSubtitle(result)
        drain()

        // Still side-loaded (immediate use), but not persisted.
        assertEquals(1, addedSubtitles.size)
        // No refresh should run on failure.
        io.mockk.verify(exactly = 0) { mediaRepository.invalidateDetailCache(any()) }
        assertTrue(refreshedDetails.isEmpty())
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
        // Error surfaced to the user; per-id status FAILED.
        val status = state.value.downloadingSubtitles["OPENSUBTITLES:os-42"]
        assertEquals(SubtitleDownloadState.FAILED, status?.state)
        assertEquals("server rejected", status?.errorMessage)
        coVerify { userMessageBus.error(any<String>()) }
    }

    @Test
    fun downloadProviderSubtitle_downloadFailure_marksFailedAndDoesNotUpload() = runBlocking {
        coEvery { subtitleProviderRepository.downloadExternal(result) } returns
            Result.failure(RuntimeException("provider down"))

        manager().downloadProviderSubtitle(result)
        drain()

        // Nothing side-loaded, nothing uploaded, nothing refreshed.
        assertTrue(addedSubtitles.isEmpty())
        coVerify(exactly = 0) {
            playbackRepository.uploadSubtitle(any(), any(), any(), any(), any(), any())
        }
        io.mockk.verify(exactly = 0) { mediaRepository.invalidateDetailCache(any()) }
        assertTrue(refreshedDetails.isEmpty())
        coVerify { userMessageBus.error(any<String>()) }
        val status = state.value.downloadingSubtitles["OPENSUBTITLES:os-42"]
        assertEquals(SubtitleDownloadState.FAILED, status?.state)
        assertEquals("provider down", status?.errorMessage)
    }

    @Test
    fun downloadProviderSubtitle_jellyfinResult_delegatesToServerDownloadPath() = runBlocking {
        // A Jellyfin row carries jellyfinInfo and must NOT go through the
        // external byte fetch/upload path — it reuses the server-side
        // downloadSubtitle(RemoteSubtitleInfo) flow instead.
        val jellyfinResult = result.copy(
            provider = SubtitleProviderKind.JELLYFIN,
            jellyfinInfo = com.raulshma.jellyplay.core.model.RemoteSubtitleInfo(id = "jelly-1"),
        )

        manager().downloadProviderSubtitle(jellyfinResult)
        drain()

        coVerify(exactly = 0) {
            subtitleProviderRepository.downloadExternal(any())
            playbackRepository.uploadSubtitle(any(), any(), any(), any(), any(), any())
        }
        assertTrue(addedSubtitles.isEmpty())
    }
}
