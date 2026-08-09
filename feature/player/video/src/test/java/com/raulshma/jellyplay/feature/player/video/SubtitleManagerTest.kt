package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SubtitleManager] — the in-player subtitle download / search /
 * stream-management state machine. Tests verify state transitions, interaction
 * with [PlaybackRepository] / [MediaRepository], flow updates emitted to
 * `getUiState` / `updateUiState` lambdas, an [UnconfinedTestDispatcher] scope so
 * the collaborator's `scope.launch` blocks run to completion synchronously, and
 * mockk for the repositories. The media-detail refresh coupling is captured via
 * an instrumentation flag on the `onMediaDetailRefreshed` callback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleManagerTest {

    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var subtitleProviderRepository: SubtitleProviderRepository
    private lateinit var userMessageBus: UserMessageBus
    private lateinit var state: MutableStateFlow<VideoPlayerUiState>
    private lateinit var addedSubtitles: MutableList<SubtitleSource>
    private var refreshedDetails: MutableList<MediaDetail> = mutableListOf()
    private lateinit var manager: SubtitleManager

    // An unconfined scope makes manager's scope.launch blocks run to completion
    // synchronously, keeping assertions deterministic.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        playbackRepository = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        subtitleProviderRepository = mockk(relaxed = true)
        userMessageBus = mockk(relaxed = true)
        state = MutableStateFlow(VideoPlayerUiState())
        addedSubtitles = mutableListOf()
        refreshedDetails.clear()

        manager = SubtitleManager(
            context = mockk(relaxed = true),
            playbackRepository = playbackRepository,
            mediaRepository = mediaRepository,
            subtitleProviderRepository = subtitleProviderRepository,
            streamingSubtitleStore = noOpStreamingSubtitleStore(),
            userMessageBus = userMessageBus,
            scope = scope,
            addExternalSubtitle = { addedSubtitles += it },
            getUiState = { state.value },
            updateUiState = { transform -> state.value = transform(state.value) },
            getCurrentItemId = { "item-1" },
            onMediaDetailRefreshed = { refreshedDetails += it },
        )
    }

    @Test
    fun loadRemoteSubtitles_populatesListAndClearsLoadingFlag() {
        val subs = listOf(RemoteSubtitleInfo(id = "s1", name = "English"))
        coEvery { playbackRepository.getRemoteSubtitles("item-1") } returns Result.success(subs)

        manager.loadRemoteSubtitles()

        assertTrue(state.value.isLoadingRemoteSubtitles.not())
        assertEquals(subs, state.value.remoteSubtitles)
    }

    @Test
    fun loadRemoteSubtitles_failureYieldsEmptyListWithoutCrashing() {
        coEvery { playbackRepository.getRemoteSubtitles("item-1") } returns Result.failure(RuntimeException("boom"))

        manager.loadRemoteSubtitles()

        assertTrue(state.value.remoteSubtitles.isEmpty())
        assertFalse(state.value.isLoadingRemoteSubtitles)
    }

    @Test
    fun loadRemoteSubtitles_noCurrentItem_isNoOp() {
        manager = managerWithItemId(null)
        coEvery { playbackRepository.getRemoteSubtitles(any()) } returns Result.success(listOf(RemoteSubtitleInfo(id = "x")))

        manager.loadRemoteSubtitles()

        coVerify(exactly = 0) { playbackRepository.getRemoteSubtitles(any()) }
    }

    @Test
    fun searchRemoteSubtitles_successPopulatesResults() {
        val subs = listOf(RemoteSubtitleInfo(id = "os1", name = "OpenSub en"))
        coEvery { playbackRepository.searchRemoteSubtitles("item-1", "eng") } returns Result.success(subs)

        manager.searchRemoteSubtitles("eng")

        assertEquals(subs, state.value.searchedSubtitles)
        assertTrue(state.value.hasSearchedSubtitles)
        assertFalse(state.value.isSearchingSubtitles)
        assertNull(state.value.subtitleSearchError)
    }

    @Test
    fun searchRemoteSubtitles_failureSurfacesErrorDistinctFromEmpty() {
        coEvery { playbackRepository.searchRemoteSubtitles("item-1", "eng") } returns Result.failure(RuntimeException("rate limited"))

        manager.searchRemoteSubtitles("eng")

        // A failure must set subtitleSearchError (so the UI invites retry) and
        // must NOT claim a search completed with an empty result.
        assertEquals("rate limited", state.value.subtitleSearchError)
        assertFalse(state.value.hasSearchedSubtitles)
        assertFalse(state.value.isSearchingSubtitles)
        assertTrue(state.value.searchedSubtitles.isEmpty())
    }

    @Test
    fun loadSubtitleCultures_isIdempotentWhenAlreadyPopulated() {
        state.value = state.value.copy(subtitleCultures = listOf(CultureInfo(name = "eng")))
        coEvery { playbackRepository.getSubtitleCultures("item-1") } returns Result.success(listOf(CultureInfo(name = "deu")))

        manager.loadSubtitleCultures()

        // Already-populated cultures must not be re-fetched (idempotent guard).
        coVerify(exactly = 0) { playbackRepository.getSubtitleCultures(any()) }
        assertEquals(listOf(CultureInfo(name = "eng")), state.value.subtitleCultures)
    }

    @Test
    fun loadSubtitleCultures_populatesOnFirstCall() {
        coEvery { playbackRepository.getSubtitleCultures("item-1") } returns Result.success(listOf(CultureInfo(name = "deu", displayName = "German")))

        manager.loadSubtitleCultures()

        assertEquals(listOf(CultureInfo(name = "deu", displayName = "German")), state.value.subtitleCultures)
    }

    @Test
    fun resetSubtitleManagerState_clearsTheWholeSlice() {
        state.value = state.value.copy(
            searchedSubtitles = listOf(RemoteSubtitleInfo(id = "x")),
            hasSearchedSubtitles = true,
            isSearchingSubtitles = true,
            subtitleSearchError = "err",
            subtitleCultures = listOf(CultureInfo(name = "eng")),
        )

        manager.resetSubtitleManagerState()

        assertTrue(state.value.searchedSubtitles.isEmpty())
        assertFalse(state.value.hasSearchedSubtitles)
        assertFalse(state.value.isSearchingSubtitles)
        assertNull(state.value.subtitleSearchError)
        assertTrue(state.value.subtitleCultures.isEmpty())
    }

    @Test
    fun downloadSubtitle_successMarksDownloadedAndRefreshesMediaDetail() =
        runTest(UnconfinedTestDispatcher()) {
            // The downloaded subtitle surfaces as a new SUBTITLE stream on the
            // first cache-busted poll.
            val detail = mediaDetailWithSubtitle("item-1", streamIndex = 2, language = "eng")
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(detail)

            managerInScope(this).downloadSubtitle(
                RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"),
            )

            coVerify(exactly = 1) { playbackRepository.downloadSubtitle("item-1", "s1") }
            // The poll must bust the stale detail cache before each fetch.
            io.mockk.verify(atLeast = 1) { mediaRepository.invalidateDetailCache("item-1") }
            assertEquals(listOf(detail), refreshedDetails)
            assertEquals(
                SubtitleDownloadState.DOWNLOADED,
                state.value.downloadingSubtitles["s1"]?.state,
            )
        }

    @Test
    fun downloadSubtitle_neverAppears_marksDelayedNotFailed() =
        runTest(UnconfinedTestDispatcher()) {
            // The stream never surfaces (server still queued) → soft DELAYED.
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(mediaDetail("item-1"))

            managerInScope(this).downloadSubtitle(
                RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"),
            )
            // Advance the virtual clock past the poll loop's inter-attempt delays
            // so it exhausts its budget and lands on DELAYED.
            advanceUntilIdle()

            assertEquals(
                SubtitleDownloadState.DELAYED,
                state.value.downloadingSubtitles["s1"]?.state,
            )
            io.mockk.verify { userMessageBus.info(any<String>()) }
        }

    @Test
    fun downloadSubtitle_apiFailure_marksFailedAndErrors() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.failure(RuntimeException("network"))

            managerInScope(this).downloadSubtitle(
                RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"),
            )

            val status = state.value.downloadingSubtitles["s1"]
            assertEquals(SubtitleDownloadState.FAILED, status?.state)
            assertEquals("network", status?.errorMessage)
            io.mockk.verify { userMessageBus.error(any<String>()) }
        }

    @Test
    fun downloadSubtitle_doesNotMatchPreExistingSameLanguageStream() =
        runTest(UnconfinedTestDispatcher()) {
            // An eng subtitle already exists at index 2 before the download. The
            // post-download detail still only carries that same index-2 stream —
            // no genuinely new stream — so this must NOT short-circuit success;
            // it falls through to DELAYED (guard against a false positive).
            state.value = state.value.copy(
                mediaStreams = listOf(
                    MediaStream(index = 2, type = StreamType.SUBTITLE, language = "eng"),
                ),
            )
            val detail = mediaDetailWithSubtitle("item-1", streamIndex = 2, language = "eng")
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(detail)

            managerInScope(this).downloadSubtitle(
                RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"),
            )
            // The detail carries only the pre-existing index-2 stream, so the
            // false-positive guard keeps it from matching → poll must exhaust its
            // budget and land on DELAYED.
            advanceUntilIdle()

            assertEquals(
                SubtitleDownloadState.DELAYED,
                state.value.downloadingSubtitles["s1"]?.state,
            )
            // No detail should have been applied since nothing genuinely appeared.
            assertTrue(refreshedDetails.isEmpty())
        }

    @Test
    fun downloadSubtitle_marksStatusForId() =
        runTest(UnconfinedTestDispatcher()) {
            // The stream never appears, so the final state is DELAYED — but the
            // point is that a status entry exists for the id (DOWNLOADING was set
            // synchronously on entry, then transitioned through the poll).
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(mediaDetail("item-1"))

            managerInScope(this).downloadSubtitle(
                RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"),
            )

            assertTrue(state.value.downloadingSubtitles.containsKey("s1"))
        }

    @Test
    fun resetSubtitleManagerState_clearsDownloads() {
        state.value = state.value.copy(
            downloadingSubtitles = mapOf(
                "s1" to SubtitleDownloadStatus("s1", SubtitleDownloadState.DOWNLOADED),
            ),
        )

        manager.resetSubtitleManagerState()

        assertTrue(state.value.downloadingSubtitles.isEmpty())
    }

    // Note: addLocalSubtitle() is thin string→SubtitleSource mapping that calls
    // Uri.toString(); verifying it requires an Android/Robolectric Uri, so it is
    // covered by instrumentation rather than these pure-JVM tests.

    private fun managerWithItemId(id: String?): SubtitleManager = SubtitleManager(
        context = mockk(relaxed = true),
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        subtitleProviderRepository = subtitleProviderRepository,
        streamingSubtitleStore = noOpStreamingSubtitleStore(),
        userMessageBus = userMessageBus,
        scope = scope,
        addExternalSubtitle = { addedSubtitles += it },
        getUiState = { state.value },
        updateUiState = { transform -> state.value = transform(state.value) },
        getCurrentItemId = { id },
        onMediaDetailRefreshed = { refreshedDetails += it },
    )

    /**
     * A [SubtitleManager] bound to [testScope] so the post-download poll's
     * [kotlinx.coroutines.delay] advances under the test scheduler's virtual
     * time. Shares the same mocked repos + state fixture as [manager].
     */
    private fun managerInScope(testScope: CoroutineScope): SubtitleManager = SubtitleManager(
        context = mockk(relaxed = true),
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        subtitleProviderRepository = subtitleProviderRepository,
        streamingSubtitleStore = noOpStreamingSubtitleStore(),
        userMessageBus = userMessageBus,
        scope = testScope,
        addExternalSubtitle = { addedSubtitles += it },
        getUiState = { state.value },
        updateUiState = { transform -> state.value = transform(state.value) },
        getCurrentItemId = { "item-1" },
        onMediaDetailRefreshed = { refreshedDetails += it },
    )

    private fun mediaDetail(id: String): MediaDetail = MediaDetail(
        item = MediaItem(id = id, name = "Movie", mediaType = MediaType.MOVIE),
        mediaSources = emptyList(),
        chapters = emptyList(),
    )

    /** A [MediaDetail] carrying a single subtitle stream (the downloaded one). */
    private fun mediaDetailWithSubtitle(id: String, streamIndex: Int, language: String): MediaDetail =
        mediaDetail(id).copy(
            mediaSources = listOf(
                MediaSource(
                    id = "$id-source",
                    name = "Source",
                    mediaStreams = listOf(
                        MediaStream(index = streamIndex, type = StreamType.SUBTITLE, language = language),
                    ),
                ),
            ),
        )
}
