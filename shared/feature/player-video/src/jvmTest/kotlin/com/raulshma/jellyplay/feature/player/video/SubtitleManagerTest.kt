package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.MergedSubtitleSearch
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
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.state.ReadySubtitleHint
import com.raulshma.jellyplay.feature.player.video.state.SubtitleState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
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
 * Pure-JVM port of the legacy Android test of the same name (the migration
 * dropped the Robolectric harness). Adaptations: the legacy `Context` mock
 * becomes the [SubtitleContentGateway] fake (the KMP seam for SAF reads) and
 * the legacy Hilt `UserMessageBus` becomes a recording
 * [PlayerVideoMessageBus] fake (the KMP one-shot feedback seam); every
 * `io.mockk.verify { userMessageBus… }` becomes an assertion on the recorded
 * emissions. Carries all upstream v0.10.6 additions: the inline Get-tab fetch
 * error state, the offline fetch skip, and the #144 attach-verify /
 * attribution suite.
 *
 * Unit tests for [SubtitleManager] — the in-player subtitle download / search /
 * stream-management state machine. After state ownership moved into the manager
 * the test surface is the manager's [SubtitleState] flow — no
 * [VideoPlayerUiState], no ViewModel. Tests verify state transitions and
 * interaction with [PlaybackRepository] / [MediaRepository], an
 * [UnconfinedTestDispatcher] scope so the collaborator's `scope.launch` blocks
 * run to completion synchronously, and mockk for the repositories. The
 * media-detail refresh coupling is captured via an instrumentation flag on the
 * `onMediaDetailRefreshed` callback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleManagerTest {

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
    private lateinit var userMessageBus: RecordingMessageBus
    private lateinit var addedSubtitles: MutableList<SubtitleSource>
    private var refreshedDetails: MutableList<MediaDetail> = mutableListOf()
    /** The new-subtitle stream index handed to the VM with each refreshed detail. */
    private var refreshedIndexes: MutableList<Int?> = mutableListOf()
    private var currentDetail: MediaDetail? = null
    private lateinit var manager: SubtitleManager
    private var mediaStreams: List<MediaStream> = emptyList()

    // An unconfined scope makes manager's scope.launch blocks run to completion
    // synchronously, keeping assertions deterministic.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        playbackRepository = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        subtitleProviderRepository = mockk(relaxed = true)
        userMessageBus = RecordingMessageBus()
        addedSubtitles = mutableListOf()
        refreshedDetails.clear()
        refreshedIndexes.clear()
        currentDetail = null
        mediaStreams = emptyList()

        // Default detail stub: the offline snapshot seed reads this on every
        // downloadSubtitle call, so an unstubbed relaxed mock would leak a
        // failing coroutine into the next test. Individual tests override.
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns
            Result.failure(RuntimeException("no detail seeded"))

        manager = SubtitleManager(
            contentGateway = contentGateway,
            playbackRepository = playbackRepository,
            mediaRepository = mediaRepository,
            subtitleProviderRepository = subtitleProviderRepository,
            streamingSubtitleStore = noOpStreamingSubtitleStore(),
            userMessageBus = userMessageBus,
            scope = scope,
            addExternalSubtitle = { addedSubtitles += it },
            getMediaStreams = { mediaStreams },
            getCurrentItemId = { "item-1" },
            onMediaDetailRefreshed = { refresh ->
                refreshedDetails += refresh.detail
                refreshedIndexes += refresh.newSubtitleStreamIndex
            },
            getCurrentMediaDetail = { currentDetail },
        )
    }

    @Test
    fun loadRemoteSubtitles_populatesListAndClearsLoadingFlag() {
        val subs = listOf(RemoteSubtitleInfo(id = "s1", name = "English"))
        coEvery { playbackRepository.getRemoteSubtitles("item-1") } returns Result.success(subs)

        manager.loadRemoteSubtitles()

        assertTrue(manager.state.value.isLoadingRemoteSubtitles.not())
        assertEquals(subs, manager.state.value.remoteSubtitles)
    }

    @Test
    fun loadRemoteSubtitles_failureYieldsEmptyListAndInlineError() {
        coEvery { playbackRepository.getRemoteSubtitles("item-1") } returns Result.failure(RuntimeException("boom"))

        manager.loadRemoteSubtitles()

        assertTrue(manager.state.value.remoteSubtitles.isEmpty())
        assertFalse(manager.state.value.isLoadingRemoteSubtitles)
        assertEquals("boom", manager.state.value.remoteSubtitlesError)
        assertTrue("inline error, never a global toast", userMessageBus.errors.isEmpty())
    }

    @Test
    fun loadRemoteSubtitles_successClearsPriorError() {
        coEvery { playbackRepository.getRemoteSubtitles("item-1") } returns Result.failure(RuntimeException("boom"))
        manager.loadRemoteSubtitles()
        assertEquals("boom", manager.state.value.remoteSubtitlesError)

        val subs = listOf(RemoteSubtitleInfo(id = "s1", name = "English"))
        coEvery { playbackRepository.getRemoteSubtitles("item-1") } returns Result.success(subs)
        manager.loadRemoteSubtitles()

        assertEquals(subs, manager.state.value.remoteSubtitles)
        assertNull(manager.state.value.remoteSubtitlesError)
    }

    @Test
    fun loadRemoteSubtitles_noCurrentItem_isNoOp() {
        manager = managerWithItemId(null)
        coEvery { playbackRepository.getRemoteSubtitles(any()) } returns Result.success(listOf(RemoteSubtitleInfo(id = "x")))

        manager.loadRemoteSubtitles()

        coVerify(exactly = 0) { playbackRepository.getRemoteSubtitles(any()) }
    }

    @Test
    fun loadRemoteSubtitles_offline_skipsFetchAndToastsNothing() {
        // Offline the server read can never succeed — and its retry chain's
        // late failure used to surface as a global "Could not load subtitle
        // list" toast long after the hub (or even the player) was closed. The
        // fetch must be skipped silently: empty list, no loading flag, no repo
        // call, no toast.
        var offline = true
        manager = managerWithItemId("item-1", isOffline = { offline })
        coEvery { playbackRepository.getRemoteSubtitles("item-1") } returns Result.success(
            listOf(RemoteSubtitleInfo(id = "s1")),
        )

        manager.loadRemoteSubtitles()

        coVerify(exactly = 0) { playbackRepository.getRemoteSubtitles(any()) }
        assertTrue(manager.state.value.remoteSubtitles.isEmpty())
        assertFalse(manager.state.value.isLoadingRemoteSubtitles)
        assertTrue(userMessageBus.errors.isEmpty())
    }

    @Test
    fun loadRemoteSubtitles_offlineSkipClearsStaleError() {
        // A failed online attempt leaves the inline error; the next offline
        // skip must clear it, or the Get tab would show a stale failure for a
        // fetch that was never made.
        coEvery { playbackRepository.getRemoteSubtitles("item-1") } returns Result.failure(RuntimeException("boom"))
        manager.loadRemoteSubtitles()
        assertEquals("boom", manager.state.value.remoteSubtitlesError)

        manager = managerWithItemId("item-1", isOffline = { true })
        manager.loadRemoteSubtitles()

        assertNull(manager.state.value.remoteSubtitlesError)
        assertTrue(manager.state.value.remoteSubtitles.isEmpty())
        assertFalse(manager.state.value.isLoadingRemoteSubtitles)
    }

    @Test
    fun loadRemoteSubtitles_backOnlineAfterOfflineFetchesNormally() {
        // The offline skip must not be sticky: leaving offline mode restores
        // the normal fetch on the next hub open.
        var offline = true
        manager = managerWithItemId("item-1", isOffline = { offline })
        val subs = listOf(RemoteSubtitleInfo(id = "s1", name = "English"))
        coEvery { playbackRepository.getRemoteSubtitles("item-1") } returns Result.success(subs)

        manager.loadRemoteSubtitles()
        assertTrue(manager.state.value.remoteSubtitles.isEmpty())

        offline = false
        manager.loadRemoteSubtitles()

        assertEquals(subs, manager.state.value.remoteSubtitles)
        assertFalse(manager.state.value.isLoadingRemoteSubtitles)
    }

    @Test
    fun searchRemoteSubtitles_successPopulatesResults() {
        val subs = listOf(RemoteSubtitleInfo(id = "os1", name = "OpenSub en"))
        coEvery { playbackRepository.searchRemoteSubtitles("item-1", "eng") } returns Result.success(subs)

        manager.searchRemoteSubtitles("eng")

        assertEquals(subs, manager.state.value.searchedSubtitles)
        assertTrue(manager.state.value.hasSearchedSubtitles)
        assertFalse(manager.state.value.isSearchingSubtitles)
        assertNull(manager.state.value.subtitleSearchError)
    }

    @Test
    fun searchRemoteSubtitles_failureSurfacesErrorDistinctFromEmpty() {
        coEvery { playbackRepository.searchRemoteSubtitles("item-1", "eng") } returns Result.failure(RuntimeException("rate limited"))

        manager.searchRemoteSubtitles("eng")

        // A failure must set subtitleSearchError (so the UI invites retry) and
        // must NOT claim a search completed with an empty result.
        assertEquals("rate limited", manager.state.value.subtitleSearchError)
        assertFalse(manager.state.value.hasSearchedSubtitles)
        assertFalse(manager.state.value.isSearchingSubtitles)
        assertTrue(manager.state.value.searchedSubtitles.isEmpty())
    }

    @Test
    fun searchAllProviders_usesInMemoryDetailAndDoesNotHitNetwork() {
        // The VM already holds the detail from playback start; search must reuse
        // it rather than re-fetching, so a transient server outage mid-search
        // never reaches getMediaDetail.
        currentDetail = mediaDetail("item-1")
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.failure(RuntimeException("offline"))
        coEvery {
            subtitleProviderRepository.searchAllStreaming(any(), "item-1", "eng", any())
        } returns MergedSubtitleSearch(results = emptyList(), errors = emptyMap())

        manager.searchAllProviders("eng")

        // Network detail fetch never happens when the in-memory snapshot exists.
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
        assertTrue(manager.state.value.hasSearchedSubtitles)
        assertFalse(manager.state.value.isSearchingSubtitles)
    }

    @Test
    fun searchAllProviders_serverDownStillRunsExternalProvidersAndSurfacesNoItemDetailsError() {
        // No in-memory snapshot (e.g. playback started without one) AND the
        // detail fetch fails. The previous code bailed out with a single
        // "Could not load item details" chip and never queried Wyzie /
        // OpenSubtitles. The fix still calls searchAllStreaming so external
        // providers run; the Jellyfin failure surfaces only as an empty merge,
        // never as a hard abort.
        currentDetail = null
        coEvery { mediaRepository.getMediaDetail("item-1", any()) } returns Result.failure(RuntimeException("offline"))
        val wyzieResult = SubtitleSearchResult(
            provider = SubtitleProviderKind.WYZIE,
            id = "w1",
            language = "eng",
            displayName = "Wyzie EN",
        )
        coEvery {
            subtitleProviderRepository.searchAllStreaming(any(), "item-1", "eng", any())
        } returns MergedSubtitleSearch(results = listOf(wyzieResult), errors = emptyMap())

        manager.searchAllProviders("eng")

        assertEquals(listOf(wyzieResult), manager.state.value.providerSearchResults)
        // No fatal "Could not load item details" — external results came through.
        assertTrue(manager.state.value.providerSearchErrors.isEmpty())
        assertTrue(manager.state.value.hasSearchedSubtitles)
        assertFalse(manager.state.value.isSearchingSubtitles)
    }

    @Test
    fun searchAllProviders_streamsPartialResultsToStateMidFlight() {
        // The streaming contract: searchAllStreaming invokes its callback with
        // each provider's results as it resolves. SubtitleManager must push those
        // partials into providerSearchResults immediately — a slow provider can
        // no longer gate a fast one. Here the callback fires once with an
        // OpenSubtitles-only snapshot, then the final return adds Wyzie.
        currentDetail = mediaDetail("item-1")
        val osResult = SubtitleSearchResult(
            provider = SubtitleProviderKind.OPENSUBTITLES,
            id = "os1",
            language = "eng",
            displayName = "OS EN",
        )
        val wyzieResult = SubtitleSearchResult(
            provider = SubtitleProviderKind.WYZIE,
            id = "w1",
            language = "eng",
            displayName = "Wyzie EN",
        )
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.failure(RuntimeException("offline"))
        coEvery {
            subtitleProviderRepository.searchAllStreaming(any(), "item-1", "eng", any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val callback = args[3] as (MergedSubtitleSearch) -> Unit
            // First provider resolves: emit OpenSubtitles-only partial.
            callback(MergedSubtitleSearch(results = listOf(osResult), errors = emptyMap()))
            // Then the final snapshot includes both.
            MergedSubtitleSearch(results = listOf(osResult, wyzieResult), errors = emptyMap())
        }

        manager.searchAllProviders("eng")

        // Final state reflects the merged snapshot.
        assertEquals(2, manager.state.value.providerSearchResults.size)
        assertTrue(manager.state.value.hasSearchedSubtitles)
        assertFalse(manager.state.value.isSearchingSubtitles)
    }

    @Test
    fun loadSubtitleCultures_isIdempotentWhenAlreadyPopulated() {
        coEvery { playbackRepository.getSubtitleCultures("item-1") } returns Result.success(listOf(CultureInfo(name = "deu")))
        manager.loadSubtitleCultures()
        assertEquals(listOf(CultureInfo(name = "deu")), manager.state.value.subtitleCultures)

        // A second, different result must NOT re-fetch or replace the populated
        // cultures (idempotent guard).
        coEvery { playbackRepository.getSubtitleCultures("item-1") } returns Result.success(listOf(CultureInfo(name = "eng")))
        manager.loadSubtitleCultures()

        coVerify(exactly = 1) { playbackRepository.getSubtitleCultures(any()) }
        assertEquals(listOf(CultureInfo(name = "deu")), manager.state.value.subtitleCultures)
    }

    @Test
    fun loadSubtitleCultures_populatesOnFirstCall() {
        coEvery { playbackRepository.getSubtitleCultures("item-1") } returns Result.success(listOf(CultureInfo(name = "deu", displayName = "German")))

        manager.loadSubtitleCultures()

        assertEquals(listOf(CultureInfo(name = "deu", displayName = "German")), manager.state.value.subtitleCultures)
    }

    @Test
    fun resetSubtitleManagerState_clearsTheWholeSlice() {
        coEvery { playbackRepository.searchRemoteSubtitles("item-1", "eng") } returns Result.failure(RuntimeException("err"))
        manager.searchRemoteSubtitles("eng")
        assertTrue(manager.state.value.hasSearchedSubtitles.not())
        assertEquals("err", manager.state.value.subtitleSearchError)

        manager.resetSubtitleManagerState()

        assertTrue(manager.state.value.searchedSubtitles.isEmpty())
        assertFalse(manager.state.value.hasSearchedSubtitles)
        assertFalse(manager.state.value.isSearchingSubtitles)
        assertNull(manager.state.value.subtitleSearchError)
        assertTrue(manager.state.value.subtitleCultures.isEmpty())
    }

    @Test
    fun downloadSubtitle_successMarksDownloadedAndRefreshesMediaDetail() =
        runTest(UnconfinedTestDispatcher()) {
            // The downloaded subtitle surfaces as a new SUBTITLE stream on the
            // first cache-busted poll. The FIRST detail read is the offline
            // snapshot seed (pre-download state, no subtitle yet); the poll
            // reads then return the post-download detail.
            val detail = mediaDetailWithSubtitle("item-1", streamIndex = 2, language = "eng")
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            mediaRepository.stubDetailReads("item-1", mediaDetail("item-1"), detail)

            val m = managerInScope(this)
            m.downloadSubtitle(
                RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"),
            )

            coVerify(exactly = 1) { playbackRepository.downloadSubtitle("item-1", "s1") }
            // The poll must bypass the stale detail cache on every fetch (the
            // force-read freshness seam).
            io.mockk.coVerify(atLeast = 1) { mediaRepository.getMediaDetail("item-1", force = true) }
            assertEquals(listOf(detail), refreshedDetails)
            // The new stream's index rides along so the VM can select it.
            assertEquals(listOf<Int?>(2), refreshedIndexes)
            // The "Use" hints land in state: side-loaded track id + server index.
            assertEquals("external:2", m.state.value.readySubtitles["s1"]?.trackId)
            assertEquals(2, m.state.value.readySubtitles["s1"]?.serverStreamIndex)
            // Success is surfaced to the user.
            assertTrue(userMessageBus.infos.isNotEmpty())
        }

    @Test
    fun downloadSubtitle_lateSurfacing_completesSuccessPathAfterDelayed() =
        runTest(UnconfinedTestDispatcher()) {
            // The server is slow: the fast poll budget exhausts (row flips
            // DELAYED) but the stream surfaces during the slower follow-up
            // phase. The full success path must still run — refresh + hints +
            // DOWNLOADED — so the user never has to leave playback to see the
            // subtitle (the old behaviour stopped at DELAYED).
            val fresh = mediaDetailWithSubtitle("item-1", streamIndex = 2, language = "eng")
            var calls = 0
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            coEvery { mediaRepository.getMediaDetail("item-1", any()) } coAnswers {
                calls++
                if (calls <= 8) Result.success(mediaDetail("item-1")) else Result.success(fresh)
            }

            val m = managerInScope(this)
            m.downloadSubtitle(RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"))
            advanceUntilIdle()

            assertEquals(
                SubtitleDownloadState.DOWNLOADED,
                m.state.value.downloadingSubtitles["s1"]?.state,
            )
            assertEquals(listOf(fresh), refreshedDetails)
            assertEquals(2, m.state.value.readySubtitles["s1"]?.serverStreamIndex)
            assertTrue(userMessageBus.infos.isNotEmpty())
        }

    @Test
    fun downloadSubtitle_neverAppears_marksDelayedNotFailed() =
        runTest(UnconfinedTestDispatcher()) {
            // The stream never surfaces (server still queued) → soft DELAYED.
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            coEvery { mediaRepository.getMediaDetail("item-1", any()) } returns Result.success(mediaDetail("item-1"))

            val m = managerInScope(this)
            m.downloadSubtitle(
                RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"),
            )
            // Advance the virtual clock past the poll loop's inter-attempt delays
            // so it exhausts its budget and lands on DELAYED.
            advanceUntilIdle()

            assertEquals(
                SubtitleDownloadState.DELAYED,
                m.state.value.downloadingSubtitles["s1"]?.state,
            )
            assertTrue(userMessageBus.infos.isNotEmpty())
        }

    @Test
    fun downloadSubtitle_apiFailure_marksFailedAndErrors() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.failure(RuntimeException("network"))

            val m = managerInScope(this)
            m.downloadSubtitle(
                RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"),
            )

            val status = m.state.value.downloadingSubtitles["s1"]
            assertEquals(SubtitleDownloadState.FAILED, status?.state)
            assertEquals("network", status?.errorMessage)
            assertTrue(userMessageBus.errors.isNotEmpty())
        }

    @Test
    fun downloadSubtitle_doesNotMatchPreExistingSameLanguageStream() =
        runTest(UnconfinedTestDispatcher()) {
            // An eng subtitle already exists at index 2 before the download. The
            // post-download detail still only carries that same index-2 stream —
            // no genuinely new stream — so this must NOT short-circuit success;
            // it falls through to DELAYED (guard against a false positive).
            mediaStreams = listOf(
                MediaStream(index = 2, type = StreamType.SUBTITLE, language = "eng"),
            )
            val detail = mediaDetailWithSubtitle("item-1", streamIndex = 2, language = "eng")
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            coEvery { mediaRepository.getMediaDetail("item-1", any()) } returns Result.success(detail)

            val m = managerInScope(this)
            m.downloadSubtitle(
                RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"),
            )
            // The detail carries only the pre-existing index-2 stream, so the
            // false-positive guard keeps it from matching → poll must exhaust its
            // budget and land on DELAYED.
            advanceUntilIdle()

            assertEquals(
                SubtitleDownloadState.DELAYED,
                m.state.value.downloadingSubtitles["s1"]?.state,
            )
            // No detail should have been applied since nothing genuinely appeared.
            assertTrue(refreshedDetails.isEmpty())
        }

    @Test
    fun downloadSubtitle_offlineSeedsPreDownloadSnapshot_attributesNewStreamNotSidecar() =
        runTest(UnconfinedTestDispatcher()) {
            // #144 offline scenario: the session plays a download, so the live
            // UI-state stream list is empty. The server already carries a
            // same-language sidecar at index 7; the download lands at index 3
            // (server re-indexed after a deletion — the new sub is NOT the
            // highest index). Without the pre-download seed the poll's plain
            // language match would attribute the SIDECAR (highest index wins);
            // with it, only the genuinely new index qualifies.
            mediaStreams = emptyList()
            val preDownload = mediaDetailWithSubtitles(
                "item-1",
                listOf(stream(index = 7, language = "eng")),
            )
            val postDownload = mediaDetailWithSubtitles(
                "item-1",
                listOf(stream(index = 7, language = "eng"), stream(index = 3, language = "eng")),
            )
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            mediaRepository.stubDetailReads("item-1", preDownload, postDownload)

            val m = managerInScope(this)
            m.downloadSubtitle(RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"))
            advanceUntilIdle()

            assertEquals(
                SubtitleDownloadState.DOWNLOADED,
                m.state.value.downloadingSubtitles["s1"]?.state,
            )
            assertEquals(3, m.state.value.readySubtitles["s1"]?.serverStreamIndex)
            assertEquals("external:3", m.state.value.readySubtitles["s1"]?.trackId)
            assertEquals(listOf<Int?>(3), refreshedIndexes)
        }

    @Test
    fun downloadSubtitle_attachNeverLands_marksFailedInsteadOfDeadUse() =
        runTest(UnconfinedTestDispatcher()) {
            // #144: the stream surfaces on the server and the side-load is
            // fired, but the engine silently drops it (unmappable codec, failed
            // fetch). The row must NOT flip to a "Use"-able DOWNLOADED — it
            // reports FAILED so the dead button can never strand the user.
            mediaStreams = emptyList()
            val postDownload = mediaDetailWithSubtitle("item-1", streamIndex = 2, language = "eng")
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            mediaRepository.stubDetailReads("item-1", mediaDetail("item-1"), postDownload)

            val m = managerInScope(this, isSubtitleTrackAttached = { false })
            m.downloadSubtitle(RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"))
            // Burn the bounded attachment wait (virtual time).
            advanceUntilIdle()

            val status = m.state.value.downloadingSubtitles["s1"]
            assertEquals(SubtitleDownloadState.FAILED, status?.state)
            assertTrue(!status?.errorMessage.isNullOrBlank())
            // The attach was still attempted and the hint recorded — a late
            // landing track (or a reload) can still resolve "Use".
            assertEquals(listOf<Int?>(2), refreshedIndexes)
            assertEquals(2, m.state.value.readySubtitles["s1"]?.serverStreamIndex)
            assertTrue(userMessageBus.errors.isNotEmpty())
        }

    @Test
    fun downloadSubtitle_attachLandsLate_marksDownloaded() =
        runTest(UnconfinedTestDispatcher()) {
            // The side-load → track-list republish round-trip is async: the
            // first attachment polls miss, a later one hits. DOWNLOADED must
            // wait for that landing, not fire on the server-side save.
            mediaStreams = emptyList()
            val postDownload = mediaDetailWithSubtitle("item-1", streamIndex = 2, language = "eng")
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            mediaRepository.stubDetailReads("item-1", mediaDetail("item-1"), postDownload)
            var attachPolls = 0
            val m = managerInScope(this) {
                attachPolls++
                attachPolls > 3
            }
            m.downloadSubtitle(RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"))
            advanceUntilIdle()

            assertEquals(
                SubtitleDownloadState.DOWNLOADED,
                m.state.value.downloadingSubtitles["s1"]?.state,
            )
            assertTrue(userMessageBus.infos.isNotEmpty())
        }

    @Test
    fun downloadSubtitle_retryOfDownloadedId_matchesRecordedStreamIndex() =
        runTest(UnconfinedTestDispatcher()) {
            // Retrying an id downloaded earlier in this session: the stream is
            // already on the server, so the snapshot contains its index and the
            // "genuinely new index" rule can never fire. The recorded hint's
            // index stays attributable — the retry re-completes instead of
            // hanging in DELAYED forever.
            mediaStreams = emptyList()
            val detailWithSub = mediaDetailWithSubtitle("item-1", streamIndex = 2, language = "eng")
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            mediaRepository.stubDetailReads("item-1", mediaDetail("item-1"), detailWithSub)

            val m = managerInScope(this)
            m.downloadSubtitle(RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"))
            advanceUntilIdle()
            assertEquals(SubtitleDownloadState.DOWNLOADED, m.state.value.downloadingSubtitles["s1"]?.state)

            // Retry: the seed now returns the detail that already carries the
            // stream (every read returns it), snapshot = {2}, prior hint = 2.
            m.downloadSubtitle(RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"))
            advanceUntilIdle()

            assertEquals(
                SubtitleDownloadState.DOWNLOADED,
                m.state.value.downloadingSubtitles["s1"]?.state,
            )
            assertEquals(2, m.state.value.readySubtitles["s1"]?.serverStreamIndex)
        }

    @Test
    fun downloadSubtitle_marksStatusForId() =
        runTest(UnconfinedTestDispatcher()) {
            // The stream never appears, so the final state is DELAYED — but the
            // point is that a status entry exists for the id (DOWNLOADING was set
            // synchronously on entry, then transitioned through the poll).
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.success(Unit)
            coEvery { mediaRepository.getMediaDetail("item-1", any()) } returns Result.success(mediaDetail("item-1"))

            val m = managerInScope(this)
            m.downloadSubtitle(
                RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"),
            )

            assertTrue(m.state.value.downloadingSubtitles.containsKey("s1"))
        }

    @Test
    fun resetSubtitleManagerState_clearsDownloads() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.failure(RuntimeException("network"))
            val m = managerInScope(this)
            m.downloadSubtitle(RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"))
            assertTrue(m.state.value.downloadingSubtitles.isNotEmpty())

            m.resetSubtitleManagerState()

            assertTrue(m.state.value.downloadingSubtitles.isEmpty())
        }

    // ─── Item-switch semantics ─────────────────────────────────────────────────

    /**
     * The workflow state is per-item: resetForItem clears search/download state
     * (and cancels in-flight jobs) — the explicit form of the implicit reset the
     * former UiState rebuild performed (none of these fields were whitelisted).
     */
    @Test
    fun `resetForItem clears search and download status`() {
        coEvery { playbackRepository.searchRemoteSubtitles("item-1", "eng") } returns Result.failure(RuntimeException("err"))
        coEvery { playbackRepository.downloadSubtitle("item-1", "s1") } returns Result.failure(RuntimeException("network"))
        // A configured-provider emission must also be wiped by the reset.
        every { subtitleProviderRepository.configuredProviders() } returns flowOf(
            setOf(SubtitleProviderKind.JELLYFIN)
        )
        manager.searchRemoteSubtitles("eng")
        manager.downloadSubtitle(RemoteSubtitleInfo(id = "s1", threeLetterISOLanguageName = "eng"))
        manager.loadConfiguredProviders()
        assertTrue(manager.state.value.downloadingSubtitles.isNotEmpty())
        assertEquals("err", manager.state.value.subtitleSearchError)
        assertTrue(manager.state.value.configuredSubtitleProviders.isNotEmpty())

        manager.resetForItem()

        assertEquals(SubtitleState(), manager.state.value)
    }

    /** Prefs seed (the former SettingsProjector projection of the default search language). */
    @Test
    fun `seedDefaultSearchLanguage updates only when different`() {
        assertEquals("eng", manager.state.value.defaultSearchLanguage)

        val before = manager.state.value
        manager.seedDefaultSearchLanguage("eng")
        assertTrue(before === manager.state.value)

        manager.seedDefaultSearchLanguage("spa")
        assertEquals("spa", manager.state.value.defaultSearchLanguage)
    }

    // Note: addLocalSubtitle() is covered by [SubtitleManagerLocalSubtitleTest]
    // in this source set (the contentGateway seam needs no Android Uri).

    private fun managerWithItemId(
        id: String?,
        isOffline: () -> Boolean = { false },
    ): SubtitleManager = SubtitleManager(
        contentGateway = contentGateway,
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        subtitleProviderRepository = subtitleProviderRepository,
        streamingSubtitleStore = noOpStreamingSubtitleStore(),
        userMessageBus = userMessageBus,
        scope = scope,
        addExternalSubtitle = { addedSubtitles += it },
        getMediaStreams = { mediaStreams },
        getCurrentItemId = { id },
        onMediaDetailRefreshed = { refresh ->
            refreshedDetails += refresh.detail
            refreshedIndexes += refresh.newSubtitleStreamIndex
        },
        getCurrentMediaDetail = { currentDetail },
        isOffline = isOffline,
    )

    /**
     * A [SubtitleManager] bound to [testScope] so the post-download poll's
     * [kotlinx.coroutines.delay] advances under the test scheduler's virtual
     * time. Shares the same mocked repos + state fixture as [manager].
     * [isSubtitleTrackAttached] models the VM-side track-picker resolution the
     * verify-then-mark flow observes.
     */
    private fun managerInScope(
        testScope: CoroutineScope,
        isSubtitleTrackAttached: (ReadySubtitleHint) -> Boolean = { true },
    ): SubtitleManager = SubtitleManager(
        contentGateway = contentGateway,
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        subtitleProviderRepository = subtitleProviderRepository,
        streamingSubtitleStore = noOpStreamingSubtitleStore(),
        userMessageBus = userMessageBus,
        scope = testScope,
        addExternalSubtitle = { addedSubtitles += it },
        getMediaStreams = { mediaStreams },
        getCurrentItemId = { "item-1" },
        onMediaDetailRefreshed = { refresh ->
            refreshedDetails += refresh.detail
            refreshedIndexes += refresh.newSubtitleStreamIndex
        },
        getCurrentMediaDetail = { currentDetail },
        isSubtitleTrackAttached = isSubtitleTrackAttached,
    )

    private fun mediaDetail(id: String): MediaDetail = MediaDetail(
        item = MediaItem(id = id, name = "Movie", mediaType = MediaType.MOVIE),
        mediaSources = emptyList(),
        chapters = emptyList(),
    )

    /** A [MediaDetail] carrying a single subtitle stream (the downloaded one). */
    private fun mediaDetailWithSubtitle(id: String, streamIndex: Int, language: String): MediaDetail =
        mediaDetailWithSubtitles(id, listOf(stream(streamIndex, language)))

    /** A [MediaDetail] carrying several subtitle streams on one source. */
    private fun mediaDetailWithSubtitles(id: String, streams: List<MediaStream>): MediaDetail =
        mediaDetail(id).copy(
            mediaSources = listOf(
                MediaSource(
                    id = "$id-source",
                    name = "Source",
                    mediaStreams = streams,
                ),
            ),
        )

    private fun stream(index: Int, language: String): MediaStream =
        MediaStream(index = index, type = StreamType.SUBTITLE, language = language)
}
