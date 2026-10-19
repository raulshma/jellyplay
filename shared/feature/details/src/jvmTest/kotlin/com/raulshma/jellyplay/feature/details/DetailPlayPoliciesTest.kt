package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.playback.VideoQueueItem
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the [DetailPlayPolicies] folds the media-detail callbacks adapter used
 * to open-code: local-origin subtitle-index selection (play + chapter share
 * it, extras never select) and the series mark-played confirmation gate
 * (item-level and season-level).
 */
class DetailPlayPoliciesTest {

    // ── resolvePlayStreamSelection ────────────────────────────────────────

    @Test
    fun localOrigin_usesLocalManifestSubtitleIndex() {
        assertEquals(
            3,
            DetailPlayPolicies.resolvePlayStreamSelection(
                origin = DetailOrigin.LOCAL_OFFLINE_MODE,
                localSubtitleIndex = 3,
                remoteSubtitleIndex = 7,
            ),
        )
        assertEquals(
            1,
            DetailPlayPolicies.resolvePlayStreamSelection(
                origin = DetailOrigin.LOCAL_REMOTE_FAILURE,
                localSubtitleIndex = 1,
                remoteSubtitleIndex = null,
            ),
        )
    }

    @Test
    fun remoteOrigin_usesServerSubtitleIndex() {
        assertEquals(
            7,
            DetailPlayPolicies.resolvePlayStreamSelection(
                origin = DetailOrigin.REMOTE,
                localSubtitleIndex = 3,
                remoteSubtitleIndex = 7,
            ),
        )
    }

    @Test
    fun nullOrigin_fallsBackToServerSubtitleIndex() {
        // Origin not resolved yet — the play dispatch must not silently pick
        // the local manifest.
        assertEquals(
            5,
            DetailPlayPolicies.resolvePlayStreamSelection(
                origin = null,
                localSubtitleIndex = 3,
                remoteSubtitleIndex = 5,
            ),
        )
        assertEquals(
            null,
            DetailPlayPolicies.resolvePlayStreamSelection(
                origin = null,
                localSubtitleIndex = 3,
                remoteSubtitleIndex = null,
            ),
        )
    }

    // ── requiresMarkPlayedConfirmation ────────────────────────────────────

    @Test
    fun seriesRequiresConfirmation() {
        assertTrue(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.SERIES))
    }

    @Test
    fun singleItemsFlipImmediately() {
        assertFalse(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.MOVIE))
        assertFalse(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.EPISODE))
        assertFalse(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.SEASON))
        assertFalse(DetailPlayPolicies.requiresMarkPlayedConfirmation(null))
    }

    @Test
    fun seasonActionsAlwaysConfirm() {
        // A season mark is series-scoped by construction — it confirms even
        // when the parent item's type has not resolved (null).
        assertTrue(DetailPlayPolicies.requiresMarkPlayedConfirmation(null, isSeasonAction = true))
        assertTrue(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.MOVIE, isSeasonAction = true))
        assertTrue(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.SERIES, isSeasonAction = true))
    }

    // ── resolveDetailPlayDispatch ─────────────────────────────────────────

    @Test
    fun dispatch_localOrigin_carriesLocalSubtitleAndPassesAudioThrough() {
        assertEquals(
            DetailPlayDispatch(subtitleStreamIndex = 3, audioStreamIndex = 2),
            DetailPlayPolicies.resolveDetailPlayDispatch(
                origin = DetailOrigin.LOCAL_OFFLINE_MODE,
                localSubtitleIndex = 3,
                remoteSubtitleIndex = 7,
                audioStreamIndex = 2,
            ),
        )
        assertEquals(
            DetailPlayDispatch(subtitleStreamIndex = 1, audioStreamIndex = 4),
            DetailPlayPolicies.resolveDetailPlayDispatch(
                origin = DetailOrigin.LOCAL_REMOTE_FAILURE,
                localSubtitleIndex = 1,
                remoteSubtitleIndex = null,
                audioStreamIndex = 4,
            ),
        )
    }

    @Test
    fun dispatch_remoteOrUnresolvedOrigin_carriesServerSubtitleAndPassesAudioThrough() {
        assertEquals(
            DetailPlayDispatch(subtitleStreamIndex = 7, audioStreamIndex = 4),
            DetailPlayPolicies.resolveDetailPlayDispatch(
                origin = DetailOrigin.REMOTE,
                localSubtitleIndex = 3,
                remoteSubtitleIndex = 7,
                audioStreamIndex = 4,
            ),
        )
        // Origin not resolved yet — the dispatch must not silently pick the
        // local manifest; null audio threads through untouched.
        assertEquals(
            DetailPlayDispatch(subtitleStreamIndex = null, audioStreamIndex = null),
            DetailPlayPolicies.resolveDetailPlayDispatch(
                origin = null,
                localSubtitleIndex = 3,
                remoteSubtitleIndex = null,
                audioStreamIndex = null,
            ),
        )
    }

    @Test
    fun dispatch_forceCinemaMode_recordsTheOverrideWithoutTouchingTheStreamTail() {
        // "Play (with pre-rolls)": the override record rides the dispatch
        // and defaults false — every default construction reads identical.
        assertFalse(
            DetailPlayPolicies.resolveDetailPlayDispatch(
                origin = DetailOrigin.REMOTE,
                localSubtitleIndex = null,
                remoteSubtitleIndex = 7,
                audioStreamIndex = 2,
            ).forceCinemaMode,
        )
        assertEquals(
            DetailPlayDispatch(subtitleStreamIndex = 7, audioStreamIndex = 2, forceCinemaMode = true),
            DetailPlayPolicies.resolveDetailPlayDispatch(
                origin = DetailOrigin.REMOTE,
                localSubtitleIndex = null,
                remoteSubtitleIndex = 7,
                audioStreamIndex = 2,
                forceCinemaMode = true,
            ),
        )
    }

    // ── dispatchMarkPlayedAction table (mediaType × season action → confirm?) ──

    /** Runs the gate once and reports which branch fired. */
    private fun gateBranch(mediaType: MediaType?, isSeasonAction: Boolean): String {
        var branch = "none"
        DetailPlayPolicies.dispatchMarkPlayedAction(
            mediaType = mediaType,
            isSeasonAction = isSeasonAction,
            confirm = { branch = "confirm" },
            action = { branch = "action" },
        )
        return branch
    }

    @Test
    fun gate_itemLevel_onlySeriesConfirms() {
        assertEquals("confirm", gateBranch(MediaType.SERIES, isSeasonAction = false))
        assertEquals("action", gateBranch(MediaType.MOVIE, isSeasonAction = false))
        assertEquals("action", gateBranch(MediaType.EPISODE, isSeasonAction = false))
        assertEquals("action", gateBranch(MediaType.SEASON, isSeasonAction = false))
        assertEquals("action", gateBranch(null, isSeasonAction = false))
    }

    @Test
    fun gate_seasonActions_alwaysConfirm() {
        for (mediaType in listOf(MediaType.SERIES, MediaType.MOVIE, MediaType.EPISODE, MediaType.SEASON, null)) {
            assertEquals("confirm", gateBranch(mediaType, isSeasonAction = true), "mediaType=$mediaType")
        }
    }

    @Test
    fun gate_firesExactlyOneBranch() {
        // Both branches supplied, exactly one runs — the gate never falls
        // through and never double-dispatches.
        var fired = 0
        DetailPlayPolicies.dispatchMarkPlayedAction(
            mediaType = MediaType.SERIES,
            isSeasonAction = false,
            confirm = { fired += 1 },
            action = { fired += 100 },
        )
        assertEquals(1, fired)
    }

    // ── DetailPlaySequencer ───────────────────────────────────────────────

    private fun movie(id: String = "movie-1") = MediaItem(
        id = id,
        name = "The Movie",
        mediaType = MediaType.MOVIE,
        year = 2024,
        runTimeTicks = 7_200_000_000L,
    )

    private fun localTrailer(id: String = "trailer-1") =
        DetailUiState.TrailerEntry.Local(MediaItem(id = id, name = "Trailer", mediaType = MediaType.MUSIC_VIDEO))

    private fun context(
        requestedItemId: String = "movie-1",
        currentItem: MediaItem? = movie(),
        trailers: List<DetailUiState.TrailerEntry> = listOf(localTrailer()),
        playTrailerBeforeMovie: Boolean = true,
        startPositionTicks: Long = 0L,
        isSyncPlayActive: Boolean = false,
    ) = DetailPlaySequencer.DetailPlayContext(
        requestedItemId = requestedItemId,
        requestedMediaSourceId = "source-9",
        requestedStartPositionTicks = startPositionTicks,
        currentItem = currentItem,
        origin = DetailOrigin.REMOTE,
        localSubtitleIndex = null,
        remoteSubtitleIndex = 4,
        audioStreamIndex = 2,
        trailers = trailers,
        currentItemPosterUrl = "http://poster",
        playTrailerBeforeMovie = playTrailerBeforeMovie,
        isSyncPlayActive = isSyncPlayActive,
    )

    @Test
    fun prerollArm_seedsFeatureAtQueueHead_thenStartsOnTheTrailer_withNoStreamTail() {
        val effects = DetailPlaySequencer.sequence(context())
        assertEquals(2, effects.size)
        // The manual queue drains AFTER the current item, so the FEATURE is
        // seeded at the queue head and the session STARTS on the trailer.
        val seed = effects[0] as DetailPlaySequencer.DetailPlayEffect.EnqueueNext
        assertEquals("movie-1", (seed.item as VideoQueueItem).itemId)
        val session = effects[1] as DetailPlaySequencer.DetailPlayEffect.PlaySession
        assertEquals("trailer-1", session.itemId)
        // The prerolled trailer session carries NO stream selection and
        // starts at 0 — the seeded feature's selections ride the queue
        // advance, not this dispatch.
        assertEquals(null, session.mediaSourceId)
        assertEquals(0L, session.startPositionTicks)
        assertEquals(null, session.subtitleStreamIndex)
        assertEquals(null, session.audioStreamIndex)
    }

    @Test
    fun directArm_singleSession_withTheResolvedStreamTail() {
        val effects = DetailPlaySequencer.sequence(context(playTrailerBeforeMovie = false))
        assertEquals(1, effects.size)
        val session = effects.single() as DetailPlaySequencer.DetailPlayEffect.PlaySession
        assertEquals("movie-1", session.itemId)
        assertEquals("source-9", session.mediaSourceId)
        assertEquals(4, session.subtitleStreamIndex)
        assertEquals(2, session.audioStreamIndex)
    }

    @Test
    fun playOfAnotherItem_neverTakesTheCurrentItemsTrailer() {
        // An extra / version-picked re-dispatch targets a different item —
        // the preroll gate is for the loaded item's own play.
        val effects = DetailPlaySequencer.sequence(context(requestedItemId = "extra-1"))
        val session = effects.single() as DetailPlaySequencer.DetailPlayEffect.PlaySession
        assertEquals("extra-1", session.itemId)
        assertEquals("source-9", session.mediaSourceId)
    }

    @Test
    fun resumeAndSyncPlay_requests_skipThePrerollArm() {
        // A resume (start != 0) and an active SyncPlay session both gate the
        // trailer off — each lands as a plain direct-arm session.
        val resume = DetailPlaySequencer.sequence(context(startPositionTicks = 600_000_000L))
        val sync = DetailPlaySequencer.sequence(context(isSyncPlayActive = true))
        assertEquals(1, resume.size)
        assertEquals(1, sync.size)
        assertTrue(resume.single() is DetailPlaySequencer.DetailPlayEffect.PlaySession)
        assertTrue(sync.single() is DetailPlaySequencer.DetailPlayEffect.PlaySession)
    }
}
