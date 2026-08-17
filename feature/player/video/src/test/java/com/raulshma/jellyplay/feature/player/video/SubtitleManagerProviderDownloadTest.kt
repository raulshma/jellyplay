package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.StreamType
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
 *  1. the bytes are persisted durably to the on-device streaming-subtitle store;
 *  2. the bytes are side-loaded into the live engine (`addExternalSubtitle`);
 *  3. the bytes are uploaded to the Jellyfin server (`uploadSubtitle`);
 *  4. the media-detail is force-re-fetched so the new stream surfaces
 *     (`getMediaDetail(force = true)` + `onMediaDetailRefreshed`);
 *  5. the per-id status lands on [SubtitleDownloadState.DOWNLOADED].
 *
 * And for the failure branches: an upload failure (e.g. server unreachable) is
 * non-fatal — the durable on-device copy still backs the subtitle, so the id is
 * marked [SubtitleDownloadState.DOWNLOADED_DEVICE_ONLY] and a warning is surfaced
 * (no refresh, since no server stream surfaced). A download failure still marks
 * the id FAILED.
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleManagerProviderDownloadTest {

    private lateinit var context: Context
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var subtitleProviderRepository: SubtitleProviderRepository
    private lateinit var streamingSubtitleStore: StreamingSubtitleStore
    private lateinit var userMessageBus: UserMessageBus
    private lateinit var addedSubtitles: MutableList<SubtitleSource>
    private lateinit var refreshedDetails: MutableList<MediaDetail>

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
        // No-op store: the durable persistence contract (save/load round-trip,
        // manifest survival) is covered by StreamingSubtitleStoreImplTest in
        // core:data. These tests focus on the SubtitleManager status/upload
        // contract, so a no-op store keeps them free of the serialization dep.
        streamingSubtitleStore = noOpStreamingSubtitleStore()
        userMessageBus = mockk(relaxed = true)
        addedSubtitles = mutableListOf()
        refreshedDetails = mutableListOf()
    }

    private fun manager(): SubtitleManager = SubtitleManager(
        context = context,
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        subtitleProviderRepository = subtitleProviderRepository,
        streamingSubtitleStore = streamingSubtitleStore,
        userMessageBus = userMessageBus,
        scope = scope,
        addExternalSubtitle = { addedSubtitles += it },
        getMediaStreams = { emptyList() },
        getCurrentItemId = { "item-1" },
        onMediaDetailRefreshed = { refreshedDetails += it },
        getCurrentMediaDetail = { null },
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
        coEvery { mediaRepository.getMediaDetail("item-1", any()) } returns Result.success(detail)

        val m = manager()
        m.downloadProviderSubtitle(result)
        drain()

        // 1. Persisted durably to the on-device store (survives replay/offline).
        //    (Persistence round-trip is covered by StreamingSubtitleStoreImplTest;
        //    here we verify the save call was made via the store hook.)
        assertEquals(1, streamingSubtitleStore.loadAll("item-1").size)

        // 2. Side-loaded into the live engine for immediate in-session use.
        assertEquals(1, addedSubtitles.size)
        val sideLoaded = addedSubtitles.single()
        assertEquals("eng", sideLoaded.language)
        assertEquals("Movie.en", sideLoaded.label)
        assertEquals("srt", sideLoaded.codec)

        // 3. Uploaded to the Jellyfin server as a persisted MediaStream.
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

        // 4. Force-re-fetched so the stream surfaces; the refreshed detail is
        //    handed back to the VM to rebuild mediaStreams.
        io.mockk.coVerify(atLeast = 1) { mediaRepository.getMediaDetail("item-1", force = true) }
        assertEquals(listOf(detail), refreshedDetails)

        // 5. Per-id status reflects success. Status is keyed on the composite
        //    "provider:id".
        val status = m.state.value.downloadingSubtitles["OPENSUBTITLES:os-42"]
        assertEquals(SubtitleDownloadState.DOWNLOADED, status?.state)
        assertNull(status?.errorMessage)
        coVerify { userMessageBus.info(any<String>()) }
    }

    @Test
    fun downloadProviderSubtitle_uploadFailure_marksDeviceOnlyAndPersists() = runBlocking {
        coEvery { subtitleProviderRepository.downloadExternal(result) } returns Result.success(file)
        coEvery { playbackRepository.uploadSubtitle(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("server rejected"))

        val m = manager()
        m.downloadProviderSubtitle(result)
        drain()

        // Still side-loaded (immediate use)…
        assertEquals(1, addedSubtitles.size)
        // …and persisted durably, so the subtitle survives even though the
        // server upload failed (the offline/offline-first contract).
        assertEquals(1, streamingSubtitleStore.loadAll("item-1").size)
        // No refresh should run: no server stream surfaced.
        assertTrue(refreshedDetails.isEmpty())
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
        // Non-fatal: device-only status + info note (not a hard error).
        val status = m.state.value.downloadingSubtitles["OPENSUBTITLES:os-42"]
        assertEquals(SubtitleDownloadState.DOWNLOADED_DEVICE_ONLY, status?.state)
        assertEquals("server rejected", status?.errorMessage)
        coVerify { userMessageBus.info(any<String>()) }
    }

    @Test
    fun downloadProviderSubtitle_downloadFailure_marksFailedAndDoesNotUpload() = runBlocking {
        coEvery { subtitleProviderRepository.downloadExternal(result) } returns
            Result.failure(RuntimeException("provider down"))

        val m = manager()
        m.downloadProviderSubtitle(result)
        drain()

        // Nothing side-loaded, nothing uploaded, nothing refreshed, nothing persisted.
        assertTrue(addedSubtitles.isEmpty())
        assertTrue(streamingSubtitleStore.loadAll("item-1").isEmpty())
        coVerify(exactly = 0) {
            playbackRepository.uploadSubtitle(any(), any(), any(), any(), any(), any())
        }
        assertTrue(refreshedDetails.isEmpty())
        coVerify { userMessageBus.error(any<String>()) }
        val status = m.state.value.downloadingSubtitles["OPENSUBTITLES:os-42"]
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

        // Both suspend calls the delegated path makes must be stubbed
        // explicitly: a relaxed mock answers them with mistyped success values
        // (the Result<T> payload decodes as Object), and the resulting CCE
        // crashes the downloadSubtitle coroutine after the delegation verify —
        // escaping the SupervisorJob scope as an uncaught leak that fails the
        // NEXT test class to enter runTest.
        coEvery { playbackRepository.downloadSubtitle("item-1", "jelly-1") } returns Result.success(Unit)
        // The appear-poll reads the forced detail; one source carrying a new
        // SUBTITLE stream satisfies it on the first attempt, so the delegated
        // row settles DOWNLOADED without the ~9 s poll budget.
        coEvery { mediaRepository.getMediaDetail("item-1", any()) } returns Result.success(
            MediaDetail(
                item = MediaItem(id = "item-1", name = "Movie", mediaType = MediaType.MOVIE),
                mediaSources = listOf(
                    MediaSource(
                        id = "ms-1",
                        name = "Movie",
                        mediaStreams = listOf(
                            MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng"),
                        ),
                    ),
                ),
                chapters = emptyList(),
            )
        )

        val m = manager()
        m.downloadProviderSubtitle(jellyfinResult)
        drain()

        coVerify(exactly = 1) { playbackRepository.downloadSubtitle("item-1", "jelly-1") }
        coVerify(exactly = 0) {
            subtitleProviderRepository.downloadExternal(any())
            playbackRepository.uploadSubtitle(any(), any(), any(), any(), any(), any())
        }
        assertTrue(addedSubtitles.isEmpty())
        // Delegation is end-to-end: the forced refresh surfaced the new stream
        // and the row settled DOWNLOADED through the server path (keyed on the
        // plain RemoteSubtitleInfo id, not the composite provider key).
        assertEquals("item-1", refreshedDetails.single().item.id)
        assertEquals(SubtitleDownloadState.DOWNLOADED, m.state.value.downloadingSubtitles["jelly-1"]?.state)
    }
}
