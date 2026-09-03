package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.seerr.SeerrEpisode
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Invariants pinned for the per-screen three-state UI models and the Seerr
 * detail snapshot (no other suite constructs them directly):
 *  - [CastAndCrewUiState], [PersonDetailUiState], [CollectionDetailUiState]
 *    and [MediaInfoUiState] are sealed three-state models: `Loading` is a
 *    singleton, `Success` carries the screen payload, `Error` carries a
 *    resolved message — and the three states are mutually distinguishable.
 *  - [PersonDetailUiState.Success]'s biography/profile image default to null
 *    (a person without either still renders).
 *  - [SeerrDetailUiState] defaults to a clean, not-loading, unerrored
 *    snapshot: no details, no ratings, empty recommendations/similar/episode
 *    cache, no season selected, no resolved Jellyfin id.
 */
class DetailScreenUiStatesTest {

    private fun movie(id: String = "m1") = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)

    private fun person(id: String, type: String = "Actor") = PersonInfo(
        id = id,
        name = "Person $id",
        type = type,
    )

    // ── CastAndCrewUiState ──────────────────────────────────────────────────

    @Test
    fun castAndCrew_statesAreDistinct_andCarryTheirPayloads() {
        val loading = CastAndCrewUiState.Loading
        val success = CastAndCrewUiState.Success(
            title = "The Movie",
            cast = listOf(person("p1")),
            crew = listOf(person("p2", type = "Director")),
        )
        val error = CastAndCrewUiState.Error("failed")

        assertNotEquals<CastAndCrewUiState>(loading, success)
        assertNotEquals<CastAndCrewUiState>(success, error)
        assertEquals("The Movie", success.title)
        assertEquals(listOf(person("p1")), success.cast)
        assertEquals(listOf(person("p2", type = "Director")), success.crew)
        assertEquals("failed", error.message)
        assertTrue(CastAndCrewUiState.Loading == loading, "Loading is a singleton data object")
    }

    @Test
    fun castAndCrew_success_allowsEmptySides() {
        val success = CastAndCrewUiState.Success(title = "Empty", cast = emptyList(), crew = emptyList())

        assertTrue(success.cast.isEmpty() && success.crew.isEmpty())
    }

    // ── PersonDetailUiState ─────────────────────────────────────────────────

    @Test
    fun personDetail_success_defaultsToNoBiographyOrImage() {
        val success = PersonDetailUiState.Success(
            name = "Jane",
            filmography = listOf(movie()),
        )

        assertEquals("Jane", success.name)
        assertNull(success.biography)
        assertNull(success.profileImageUrl)
        assertEquals(listOf(movie()), success.filmography)
    }

    @Test
    fun personDetail_statesAreDistinct() {
        assertNotEquals(PersonDetailUiState.Loading as PersonDetailUiState, PersonDetailUiState.Error("x"))
        assertNotEquals(
            PersonDetailUiState.Success(name = "A", filmography = emptyList()),
            PersonDetailUiState.Success(name = "B", filmography = emptyList()),
        )
    }

    // ── CollectionDetailUiState ─────────────────────────────────────────────

    @Test
    fun collectionDetail_success_carriesDetailAndItems() {
        val detail = MediaDetail(item = movie("box-1"))
        val items = listOf(movie("m2"), movie("m3"))
        val success = CollectionDetailUiState.Success(detail = detail, items = items)

        assertEquals(detail, success.detail)
        assertEquals(items, success.items)
        assertNotEquals(CollectionDetailUiState.Loading as CollectionDetailUiState, success)
    }

    @Test
    fun collectionDetail_error_carriesMessage() {
        assertEquals("404", CollectionDetailUiState.Error("404").message)
    }

    // ── MediaInfoUiState ────────────────────────────────────────────────────

    @Test
    fun mediaInfo_success_carriesDetail_errorCarriesMessage() {
        val detail = MediaDetail(item = movie())
        val success = MediaInfoUiState.Success(detail)
        val error = MediaInfoUiState.Error("boom")

        assertEquals(detail, success.detail)
        assertEquals("boom", error.message)
        assertNotEquals<MediaInfoUiState>(success, error)
    }

    // ── SeerrDetailUiState ──────────────────────────────────────────────────

    @Test
    fun seerrDetail_defaults_areCleanAndNotLoading() {
        val state = SeerrDetailUiState()

        assertNull(state.movieDetails)
        assertNull(state.tvDetails)
        assertNull(state.ratings)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertTrue(state.recommendations.isEmpty())
        assertTrue(state.similar.isEmpty())
        assertNull(state.selectedSeasonNumber)
        assertTrue(state.episodesBySeason.isEmpty())
        assertFalse(state.isLoadingEpisodes)
        assertNull(state.jellyfinItemId, "no library resolution before it runs")
    }

    @Test
    fun seerrDetail_episodeCache_isKeyedBySeasonNumber() {
        val episode = SeerrEpisode(
            id = 1,
            seasonNumber = 2,
            episodeNumber = 3,
            name = "Pilot",
        )
        val state = SeerrDetailUiState(
            selectedSeasonNumber = 2,
            episodesBySeason = mapOf(2 to listOf(episode)),
            isLoadingEpisodes = true,
        )

        assertEquals(listOf(episode), state.episodesBySeason[2])
        assertTrue(state.episodesBySeason[1].isNullOrEmpty(), "other seasons are not cached yet")
        assertTrue(state.isLoadingEpisodes)
    }

    @Test
    fun seerrDetail_jellyfinItemId_marksAnAvailableItem() {
        val base = SeerrDetailUiState()
        assertNull(base.jellyfinItemId)

        val resolved = base.copy(jellyfinItemId = "jf-1")
        assertEquals("jf-1", resolved.jellyfinItemId)
        assertFalse(resolved.isLoading)
    }

    @Test
    fun seerrDetail_recommendationsAndSimilar_areIndependentLists() {
        val item = SeerrSearchItem(id = 1, mediaType = "movie", title = "Similar")
        val state = SeerrDetailUiState(recommendations = listOf(item))

        assertTrue(state.similar.isEmpty(), "populating recommendations must not touch similar")
        assertEquals(listOf(item), state.recommendations)
    }
}
