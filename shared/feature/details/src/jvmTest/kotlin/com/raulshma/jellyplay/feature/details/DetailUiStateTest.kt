package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.DetailPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Invariants pinned for the detail screen's content-core state surface:
 *  - [DetailUiState] defaults to an unloaded, capability-less state: no
 *    detail, [DetailUiLoadState.Loaded] (matching the former all-false
 *    default), all-false [DetailUiState.DefaultCapabilities], empty content
 *    collections.
 *  - [DetailUiState.SmartPlayTarget.isNextUpOrResume] is true for an explicit
 *    RESUME/NEXT_UP label OR a started-but-unplayed episode, and false for a
 *    plain play target, a replay, and any played episode — the predicate that
 *    keeps the Up Next card honest.
 *  - [DetailUiLoadState] states are mutually exclusive singletons (plus the
 *    Error payload), so the screen renders exactly one overlay.
 *  - [DetailContentState]'s unified-provider fields default to the
 *    remote-rendering path unchanged: origin null, default capabilities,
 *    resync idle, no downloaded episodes, picker closed.
 */
class DetailUiStateTest {

    private fun episode(
        id: String = "ep-1",
        played: Boolean = false,
        ticks: Long? = null,
    ) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
        playbackPositionTicks = ticks,
        isPlayed = played,
    )

    private fun target(
        episode: MediaItem = episode(),
        labelKind: LabelKind? = null,
        startPositionTicks: Long = 0L,
    ) = DetailUiState.SmartPlayTarget(
        episode = episode,
        label = "Play",
        startPositionTicks = startPositionTicks,
        labelKind = labelKind,
    )

    // ── DetailUiState defaults ──────────────────────────────────────────────

    @Test
    fun defaults_areUnloadedRemotePlain() {
        val state = DetailUiState()

        assertNull(state.detail)
        assertEquals(DetailUiLoadState.Loaded, state.loadState)
        assertNull(state.origin)
        assertNull(state.detailContext)
        assertEquals(DetailUiState.DefaultCapabilities, state.capabilities)
        assertEquals(0, state.seasons.size)
        assertEquals(0, state.episodes.size)
        assertEquals(0, state.sortedEpisodes.size)
        assertEquals(0, state.albumTracks.size)
        assertEquals(0, state.collectionItems.size)
        assertEquals(0, state.relatedItems.size)
        assertEquals(0, state.specialFeatures.size)
        assertEquals(0, state.localRelatedItems.size)
        assertFalse(state.sonarrServersResolved)
        assertFalse(state.hasIntroSegment)
        assertFalse(state.hasCreditSegment)
        assertNull(state.selectedSubtitleIndex)
        assertNull(state.selectedAudioIndex)
        assertNull(state.selectedLocalSubtitleIndex)
    }

    @Test
    fun defaultCapabilities_areAllFalse_nothingOfferedBeforeSnapshot() {
        val capabilities = DetailUiState.DefaultCapabilities

        assertFalse(capabilities.remoteDiscovery)
        assertFalse(capabilities.remoteStreamSelection)
        assertFalse(capabilities.localSubtitleSelection)
        assertFalse(capabilities.localStreamInfo)
        assertFalse(capabilities.personNavigation)
        assertFalse(capabilities.studioNavigation)
        assertFalse(capabilities.smartPlay)
        assertFalse(capabilities.remoteWorkAllowed)
        assertFalse(capabilities.localDownloadManagement)
        assertFalse(capabilities.tagNavigation)
        assertFalse(capabilities.chapters)
    }

    // ── SmartPlayTarget.isNextUpOrResume ────────────────────────────────────

    @Test
    fun isNextUpOrResume_explicitResumeLabel_isTrue() {
        assertTrue(target(labelKind = LabelKind.RESUME_EPISODE).isNextUpOrResume)
    }

    @Test
    fun isNextUpOrResume_explicitNextUpLabel_isTrue() {
        assertTrue(target(labelKind = LabelKind.NEXT_UP_EPISODE).isNextUpOrResume)
    }

    @Test
    fun isNextUpOrResume_startedButUnplayedWithNullLabel_isTrue() {
        // A started episode without a resolved label kind still reads as
        // resumable — position ticks carry the intent.
        assertTrue(
            target(
                episode = episode(ticks = 600_000_000L, played = false),
                startPositionTicks = 600_000_000L,
            ).isNextUpOrResume,
        )
    }

    @Test
    fun isNextUpOrResume_plainPlayTarget_isFalse() {
        assertFalse(target(labelKind = LabelKind.PLAY_EPISODE).isNextUpOrResume)
    }

    @Test
    fun isNextUpOrResume_replayTarget_isFalse() {
        assertFalse(target(labelKind = LabelKind.REPLAY_EPISODE).isNextUpOrResume)
    }

    @Test
    fun isNextUpOrResume_playedEpisodeWithTicks_isFalse() {
        // A finished episode with leftover ticks must not resurface the card.
        assertFalse(
            target(
                episode = episode(ticks = 600_000_000L, played = true),
                startPositionTicks = 600_000_000L,
            ).isNextUpOrResume,
        )
    }

    // ── DetailUiLoadState ───────────────────────────────────────────────────

    @Test
    fun loadStates_areDistinctSingletons() {
        assertSame(DetailUiLoadState.Loading, DetailUiLoadState.Loading)
        assertSame(DetailUiLoadState.Refreshing, DetailUiLoadState.Refreshing)
        assertSame(DetailUiLoadState.Loaded, DetailUiLoadState.Loaded)
        assertTrue(DetailUiLoadState.Loading != DetailUiLoadState.Refreshing)
    }

    @Test
    fun errorState_carriesMessageAndFlags() {
        val denied = DetailUiLoadState.Error(message = "403", accessDenied = true)
        val offline = DetailUiLoadState.Error(
            message = "offline",
            accessDenied = false,
            unavailableOffline = true,
        )

        assertEquals("403", denied.message)
        assertTrue(denied.accessDenied)
        assertFalse(denied.unavailableOffline, "unavailableOffline defaults off")
        assertTrue(offline.unavailableOffline)
        assertFalse(offline.accessDenied)
    }

    // ── DetailContentState unified-provider defaults ────────────────────────

    @Test
    fun detailContentState_defaultsKeepTheRemoteRenderingPathUnchanged() {
        val state = DetailContentState(
            itemId = "m1",
            detail = null,
            seasons = emptyList(),
            episodes = emptyMap(),
            fetchedSeasonIds = emptySet(),
            smartPlayTarget = null,
            selectedSubtitleIndex = null,
            selectedAudioIndex = null,
            isDownloading = false,
            isDownloadingSeries = false,
            activeDownload = null,
            loadState = DetailUiLoadState.Loaded,
            albumTracks = emptyList(),
            collectionItems = emptyList(),
            relatedItems = emptyList(),
            relatedVideos = emptyList(),
            seerrRecommendations = emptyList(),
            seerrSimilar = emptyList(),
            isSeerrConnected = false,
            isSeerrRecommendationsEnabled = false,
            preferences = DetailPreferences(),
            canManageSeries = false,
        )

        assertNull(state.origin, "no snapshot landed yet")
        assertNull(state.detailContext)
        assertEquals(DetailUiState.DefaultCapabilities, state.capabilities)
        assertEquals(ResyncUiState.Idle, state.resyncState)
        assertNull(state.persistedSeasonId)
        assertNull(state.selectedLocalSubtitleIndex)
        assertTrue(state.localSubtitles.isEmpty())
        assertTrue(state.downloadedEpisodeIds.isEmpty(), "no per-episode delete badges without downloads")
        assertFalse(state.hasIntroSegment)
        assertFalse(state.hasCreditSegment)
        assertTrue(state.tmdbReviews.isEmpty())
        assertEquals(DownloadPickerState(), state.downloadPicker, "picker starts closed")
    }
}
