package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Invariants pinned for the late-enrichment split (the state folds the screen's
 * base/late projections ride):
 *  - [DetailUiState.withoutLateEnrichments] pins every late-landing leaf to its
 *    default while keeping the core content fields verbatim — and two snapshots
 *    that differ ONLY in late leaves project EQUAL, so a root collecting the
 *    base projection is not recomposed by an enrichment landing.
 *  - [DetailUiState.lateEnrichments] carries exactly those leaves off the
 *    snapshot.
 *  - The folds round-trip: a base projection merged with its own snapshot's
 *    late projection is the original snapshot, field for field.
 */
class DetailLateEnrichmentsTest {

    private fun movie(id: String = "m-1") = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)

    /** A snapshot with every late leaf populated and a core payload beside them. */
    private fun loadedState(): DetailUiState {
        val episode = MediaItem(id = "e-1", name = "e-1", mediaType = MediaType.EPISODE)
        return DetailUiState(
            detail = MediaDetail(item = movie(), people = emptyList()),
            loadState = DetailUiLoadState.Loaded,
            origin = null,
            seasons = listOf(MediaItem(id = "s-1", name = "Season 1", mediaType = MediaType.SEASON)),
            episodes = mapOf("s-1" to listOf(episode)),
            fetchedSeasonIds = setOf("s-1"),
            sortedEpisodes = listOf(episode),
            pluginRatings = listOf(
                com.raulshma.jellyplay.core.model.JellyPlayPluginRating(
                    source = "imdb",
                    score = 7.5,
                ),
            ),
            externalRatings = DetailExternalRatings(critics = 82, rtUrl = "https://rt"),
            externalRatingsSuppressedByPlugin = true,
            relatedItems = listOf(movie("rel-1")),
            localRelatedItems = listOf(movie("loc-1")),
            pluginSimilarItems = listOf(movie("psim-1")),
            pluginSimilarSuppressesStock = true,
            specialFeatures = listOf(movie("extra-1")),
            collectionItems = listOf(movie("col-1")),
            franchiseCollections = listOf(
                DetailUiState.FranchiseCollection(id = "box-1", name = "Box", members = listOf(movie("m-2"))),
            ),
            upcomingEpisodes = listOf(MediaItem(id = "up-1", name = "up-1", mediaType = MediaType.EPISODE)),
            trailers = listOf(DetailUiState.TrailerEntry.Remote(com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo(key = "abc"))),
            themeVideoStreamUrl = "https://theme",
            tmdbReviews = listOf(TmdbReview(id = "r-1", author = "a", content = "c")),
            seerrRecommendations = listOf(SeerrSearchItem(id = 1, mediaType = "movie", title = "rec")),
            seerrSimilar = listOf(SeerrSearchItem(id = 2, mediaType = "movie", title = "sim")),
            animeMarkers = mapOf(3 to AnimeBadgeKind.FILLER),
            seasonRatings = mapOf("s-1" to mapOf(1 to com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore(tmdbScore = 7.9))),
            hasIntroSegment = true,
            hasCreditSegment = true,
            smartPlayTarget = DetailUiState.SmartPlayTarget(
                episode = episode,
                label = "Resume",
                startPositionTicks = 10L,
            ),
            book = DetailUiState.BookDetailState(
                format = com.raulshma.jellyplay.core.model.BookFormat.PDF,
                pageCount = 120,
            ),
        )
    }

    // ── Base projection ─────────────────────────────────────────────────────

    @Test
    fun withoutLateEnrichments_keepsCoreFieldsVerbatim() {
        val state = loadedState()
        val base = state.withoutLateEnrichments()

        assertEquals(state.detail, base.detail)
        assertEquals(state.loadState, base.loadState)
        assertEquals(state.seasons, base.seasons)
        assertEquals(state.episodes, base.episodes)
        assertEquals(state.fetchedSeasonIds, base.fetchedSeasonIds)
        assertEquals(state.sortedEpisodes, base.sortedEpisodes)
        assertEquals(state.seerrRequest, base.seerrRequest)
        assertEquals(state.isSeerrConnected, base.isSeerrConnected)
        assertEquals(state.themeMusicPlaying, base.themeMusicPlaying)
        assertEquals(state.hiddenFranchiseCollections, base.hiddenFranchiseCollections)
    }

    @Test
    fun withoutLateEnrichments_pinsEveryLateLeafToItsDefault() {
        val base = loadedState().withoutLateEnrichments()

        assertTrue(base.pluginRatings.isEmpty())
        assertTrue(base.externalRatings.isEmpty)
        assertFalse(base.externalRatingsSuppressedByPlugin)
        assertTrue(base.relatedItems.isEmpty())
        assertTrue(base.localRelatedItems.isEmpty())
        assertTrue(base.pluginSimilarItems.isEmpty())
        assertFalse(base.pluginSimilarSuppressesStock)
        assertTrue(base.specialFeatures.isEmpty())
        assertTrue(base.collectionItems.isEmpty())
        assertTrue(base.franchiseCollections.isEmpty())
        assertTrue(base.upcomingEpisodes.isEmpty())
        assertTrue(base.trailers.isEmpty())
        assertNull(base.themeVideoStreamUrl)
        assertTrue(base.tmdbReviews.isEmpty())
        assertTrue(base.seerrRecommendations.isEmpty())
        assertTrue(base.seerrSimilar.isEmpty())
        assertTrue(base.animeMarkers.isEmpty())
        assertTrue(base.seasonRatings.isEmpty())
        assertFalse(base.hasIntroSegment)
        assertFalse(base.hasCreditSegment)
        assertNull(base.smartPlayTarget)
        assertNull(base.book)
    }

    @Test
    fun baseProjection_isEqual_whenOnlyLateLeavesMove() {
        val loaded = loadedState()
        val afterTrailerLanding = loaded.copy(
            trailers = listOf(
                DetailUiState.TrailerEntry.Remote(
                    com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo(key = "def"),
                ),
            ),
        )
        val afterSimilarLanding = loaded.copy(relatedItems = listOf(movie("rel-2")))

        assertEquals(loaded.withoutLateEnrichments(), afterTrailerLanding.withoutLateEnrichments())
        assertEquals(loaded.withoutLateEnrichments(), afterSimilarLanding.withoutLateEnrichments())
    }

    // ── Late projection ─────────────────────────────────────────────────────

    @Test
    fun lateEnrichments_carriesExactlyTheLateLeaves() {
        val state = loadedState()
        val late = state.lateEnrichments()

        assertEquals(state.pluginRatings, late.pluginRatings)
        assertEquals(state.externalRatings, late.externalRatings)
        assertEquals(state.externalRatingsSuppressedByPlugin, late.externalRatingsSuppressedByPlugin)
        assertEquals(state.relatedItems, late.relatedItems)
        assertEquals(state.localRelatedItems, late.localRelatedItems)
        assertEquals(state.pluginSimilarItems, late.pluginSimilarItems)
        assertEquals(state.pluginSimilarSuppressesStock, late.pluginSimilarSuppressesStock)
        assertEquals(state.specialFeatures, late.specialFeatures)
        assertEquals(state.collectionItems, late.collectionItems)
        assertEquals(state.franchiseCollections, late.franchiseCollections)
        assertEquals(state.upcomingEpisodes, late.upcomingEpisodes)
        assertEquals(state.trailers, late.trailers)
        assertEquals(state.themeVideoStreamUrl, late.themeVideoStreamUrl)
        assertEquals(state.tmdbReviews, late.tmdbReviews)
        assertEquals(state.seerrRecommendations, late.seerrRecommendations)
        assertEquals(state.seerrSimilar, late.seerrSimilar)
        assertEquals(state.animeMarkers, late.animeMarkers)
        assertEquals(state.seasonRatings, late.seasonRatings)
        assertEquals(state.hasIntroSegment, late.hasIntroSegment)
        assertEquals(state.hasCreditSegment, late.hasCreditSegment)
        assertEquals(state.smartPlayTarget, late.smartPlayTarget)
        assertEquals(state.book, late.book)
    }

    @Test
    fun lateProjection_isUnequal_whenALateLeafMoves() {
        val late = loadedState().lateEnrichments()
        val afterLanding = loadedState().copy(
            tmdbReviews = listOf(TmdbReview(id = "r-2", author = "b", content = "c")),
        ).lateEnrichments()

        assertNotEquals(late, afterLanding)
    }

    // ── Round-trip ──────────────────────────────────────────────────────────

    @Test
    fun baseMergedWithLate_rebuildsTheOriginalSnapshot() {
        val state = loadedState()

        assertEquals(state, state.withoutLateEnrichments().mergedWith(state.lateEnrichments()))
    }

    @Test
    fun mergedWith_landsTheLateLeavesOntoTheBaseProjection() {
        val state = loadedState()
        val base = state.withoutLateEnrichments()

        val merged = base.mergedWith(state.lateEnrichments())
        assertEquals(state.relatedItems, merged.relatedItems)
        assertEquals(state.trailers, merged.trailers)
        assertEquals(state.smartPlayTarget, merged.smartPlayTarget)
        assertEquals(state.animeMarkers, merged.animeMarkers)
        // ...and leaves the core fields exactly as the base carried them.
        assertEquals(base.episodes, merged.episodes)
    }
}
