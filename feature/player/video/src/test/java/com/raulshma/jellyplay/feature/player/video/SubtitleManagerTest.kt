package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SubtitleManager] — the in-player subtitle download / search /
 * upload / cultures workflow extracted from [VideoPlayerViewModel].
 *
 * Mirrors the harness style of [TrackSelectionHelperTest]: a real
 * [MutableStateFlow]<[VideoPlayerUiState]> wired to the collaborator's
 * `getUiState` / `updateUiState` lambdas, an [UnconfinedTestDispatcher] scope so
 * the collaborator's `scope.launch` blocks run to completion synchronously, and
 * mockk for the repositories. The media-detail refresh coupling is captured via
 * an instrumentation flag on the `onMediaDetailRefreshed` callback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleManagerTest {

    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var mediaRepository: MediaRepository
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
        userMessageBus = mockk(relaxed = true)
        state = MutableStateFlow(VideoPlayerUiState())
        addedSubtitles = mutableListOf()
        refreshedDetails.clear()

        manager = SubtitleManager(
            context = mockk(relaxed = true),
            playbackRepository = playbackRepository,
            mediaRepository = mediaRepository,
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
    fun downloadSubtitle_triggersMediaDetailRefresh() {
        val detail = mediaDetail("item-1")
        coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(detail)

        manager.downloadSubtitle(RemoteSubtitleInfo(id = "s1"))

        coVerify(exactly = 1) { playbackRepository.downloadSubtitle("item-1", "s1") }
        assertEquals(listOf(detail), refreshedDetails)
    }

    // Note: addLocalSubtitle() is thin string→SubtitleSource mapping that calls
    // Uri.toString(); verifying it requires an Android/Robolectric Uri, so it is
    // covered by instrumentation rather than these pure-JVM tests.

    private fun managerWithItemId(id: String?): SubtitleManager = SubtitleManager(
        context = mockk(relaxed = true),
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        userMessageBus = userMessageBus,
        scope = scope,
        addExternalSubtitle = { addedSubtitles += it },
        getUiState = { state.value },
        updateUiState = { transform -> state.value = transform(state.value) },
        getCurrentItemId = { id },
        onMediaDetailRefreshed = { refreshedDetails += it },
    )

    private fun mediaDetail(id: String): MediaDetail = MediaDetail(
        item = MediaItem(id = id, name = "Movie", mediaType = MediaType.MOVIE),
        mediaSources = emptyList(),
        chapters = emptyList(),
    )
}
