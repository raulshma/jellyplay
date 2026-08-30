package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.subtitle.SubtitleFile
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.state.ReadySubtitleHint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM port of the legacy Android test of the same name (the migration
 * dropped the Robolectric harness). Adaptations mirror [SubtitleManagerTest]:
 * the legacy `Context`/`RuntimeEnvironment` becomes the [SubtitleContentGateway]
 * fake, and the Hilt `UserMessageBus` becomes a recording
 * [PlayerVideoMessageBus] fake. Carries the upstream v0.10.6 additions: the
 * ready-subtitle hint pins and the #144 attach-never-lands failure contract.
 *
 * Covers the external-provider download path end to end: durable save, engine
 * side-load, best-effort server upload, forced detail refresh, per-id status.
 * Upload failure is non-fatal — the durable on-device copy still backs the
 * subtitle, so the id is marked [SubtitleDownloadState.DOWNLOADED_DEVICE_ONLY]
 * and a warning is surfaced (no refresh, since no server stream surfaced). A
 * download failure still marks the id FAILED. And per the #144 verify-then-mark
 * contract shared with the Jellyfin remote path: when the engine silently drops
 * the side-load, the row reports FAILED instead of a usable status backed by a
 * dead "Use" button.
 */
class SubtitleManagerProviderDownloadTest {

    /** Records [PlayerVideoMessageBus] emissions — the test's feedback probe. */
    private class RecordingMessageBus : PlayerVideoMessageBus {
        val errors = mutableListOf<String>()
        val infos = mutableListOf<String>()

        override fun info(message: String) {
            infos += message
        }

        override fun error(message: String) {
            errors += message
        }

        override fun info(message: PlayerVideoMessage) {
            infos += message.toString()
        }
    }

    private val contentGateway = object : SubtitleContentGateway {
        override fun queryFileSizeBytes(uri: String): Long = 0L
        override fun readBytes(uri: String): ByteArray = ByteArray(0)
    }

    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var subtitleProviderRepository: SubtitleProviderRepository
    private lateinit var streamingSubtitleStore: StreamingSubtitleStore
    private lateinit var userMessageBus: RecordingMessageBus
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
        playbackRepository = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        subtitleProviderRepository = mockk(relaxed = true)
        // No-op store: the durable persistence contract (save/load round-trip,
        // manifest survival) is covered by StreamingSubtitleStoreImplTest in
        // shared :core:data's jvmTest. These tests focus on the SubtitleManager
        // status/upload contract, so a no-op store keeps them free of the
        // serialization dep.
        streamingSubtitleStore = noOpStreamingSubtitleStore()
        userMessageBus = RecordingMessageBus()
        addedSubtitles = mutableListOf()
        refreshedDetails = mutableListOf()
    }

    private fun manager(
        scope: CoroutineScope = this.scope,
        isSubtitleTrackAttached: (ReadySubtitleHint) -> Boolean = { true },
    ): SubtitleManager = SubtitleManager(
        contentGateway = contentGateway,
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        subtitleProviderRepository = subtitleProviderRepository,
        streamingSubtitleStore = streamingSubtitleStore,
        userMessageBus = userMessageBus,
        scope = scope,
        addExternalSubtitle = { addedSubtitles += it },
        getMediaStreams = { emptyList() },
        getCurrentItemId = { "item-1" },
        onMediaDetailRefreshed = { refresh -> refreshedDetails += refresh.detail },
        getCurrentMediaDetail = { null },
        // [isSubtitleTrackAttached] models the VM-side track-picker resolution
        // the verify-then-mark flow observes.
        isSubtitleTrackAttached = isSubtitleTrackAttached,
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
        // 6. The side-loaded track id is recorded so "Use" can activate it.
        assertEquals(
            addedSubtitles.single().id,
            m.state.value.readySubtitles["OPENSUBTITLES:os-42"]?.trackId,
        )
        assertTrue(userMessageBus.infos.isNotEmpty())
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
        assertTrue(userMessageBus.infos.isNotEmpty())
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
        assertTrue(userMessageBus.errors.isNotEmpty())
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
            jellyfinInfo = RemoteSubtitleInfo(id = "jelly-1"),
        )

        // Both suspend calls the delegated path makes must be stubbed
        // explicitly: a relaxed mock answers them with mistyped success values
        // (the Result<T> payload decodes as Object), and the resulting CCE
        // crashes the downloadSubtitle coroutine after the delegation verify —
        // escaping the SupervisorJob scope as an uncaught leak that fails the
        // NEXT test class to enter runTest.
        coEvery { playbackRepository.downloadSubtitle("item-1", "jelly-1") } returns Result.success(Unit)
        // The appear-poll reads the forced detail. The first read is the
        // pre-download snapshot seed (only the pre-existing sidecar at index 0);
        // the polls then see the downloaded stream appended at index 2, which
        // the seeded snapshot correctly distinguishes from the pre-existing
        // index — so the delegated row settles DOWNLOADED without the ~9 s
        // poll budget.
        fun detailWithStreams(streams: List<MediaStream>) = MediaDetail(
            item = MediaItem(id = "item-1", name = "Movie", mediaType = MediaType.MOVIE),
            mediaSources = listOf(
                MediaSource(
                    id = "ms-1",
                    name = "Movie",
                    mediaStreams = streams,
                ),
            ),
            chapters = emptyList(),
        )
        mediaRepository.stubDetailReads(
            "item-1",
            detailWithStreams(listOf(MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng"))),
            detailWithStreams(
                listOf(
                    MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng"),
                    MediaStream(index = 2, type = StreamType.SUBTITLE, language = "eng"),
                ),
            ),
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
        assertEquals(2, m.state.value.readySubtitles["jelly-1"]?.serverStreamIndex)
    }

    @Test
    fun downloadProviderSubtitle_attachNeverLands_marksFailedInsteadOfDeadUse() = runTest {
        // Same #144 contract as the Jellyfin remote path: the engine silently
        // dropped the side-load (unmappable codec, failed fetch), so neither
        // DOWNLOADED nor DOWNLOADED_DEVICE_ONLY may present a dead "Use" — the
        // row reports FAILED even though save + upload both succeeded.
        coEvery { subtitleProviderRepository.downloadExternal(result) } returns Result.success(file)
        coEvery { playbackRepository.uploadSubtitle(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        // The post-upload refresh reads the detail; relaxed mocks mistype the
        // Result payload (see the delegation test), so stub it explicitly.
        coEvery { mediaRepository.getMediaDetail("item-1", any()) } returns Result.success(
            MediaDetail(
                item = MediaItem(id = "item-1", name = "Movie", mediaType = MediaType.MOVIE),
                mediaSources = emptyList(),
                chapters = emptyList(),
            ),
        )

        val m = manager(scope = this, isSubtitleTrackAttached = { false })
        m.downloadProviderSubtitle(result)
        advanceUntilIdle()

        assertTrue(addedSubtitles.isNotEmpty())
        val status = m.state.value.downloadingSubtitles["OPENSUBTITLES:os-42"]
        assertEquals(SubtitleDownloadState.FAILED, status?.state)
        assertEquals("Subtitle could not be attached to playback", status?.errorMessage)
        assertTrue(userMessageBus.errors.isNotEmpty())
    }
}
