package com.raulshma.jellyplay.core.data.seerr

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the [TmdbCompanionStateHolder] choreography the media-detail and
 * Seerr-detail VMs used to hand-copy in parallel: the movie/tv videos fork,
 * the connected × recommendations-enabled gate, the has-ratings skip, the
 * per-leg error tolerance, and the take-limits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TmdbCompanionStateHolderTest {

    /**
     * Fake fetch seams: records every leg invocation ("calls") and returns the
     * per-leg canned results. Default legs succeed empty so a test only stubs
     * the legs it asserts on.
     */
    private class FetchRecorder(
        val movieDetails: suspend (Int) -> Result<SeerrMovieDetails> =
            { Result.success(SeerrMovieDetails(id = it)) },
        val tvDetails: suspend (Int) -> Result<SeerrTvDetails> =
            { Result.success(SeerrTvDetails(id = it)) },
        val tmdbVideos: suspend (Int, MediaType) -> Result<List<SeerrRelatedVideo>> =
            { _, _ -> Result.success(emptyList()) },
        val recommendations: suspend (Int, MediaType) -> Result<List<SeerrSearchItem>> =
            { _, _ -> Result.success(emptyList()) },
        val similar: suspend (Int, MediaType) -> Result<List<SeerrSearchItem>> =
            { _, _ -> Result.success(emptyList()) },
        val reviews: suspend (Int, MediaType) -> Result<List<TmdbReview>> =
            { _, _ -> Result.success(emptyList()) },
        val ratings: suspend (Int, String) -> Result<SeerrRatings> =
            { _, _ -> Result.success(SeerrRatings()) },
    ) {
        val calls = mutableListOf<String>()

        val seams = TmdbCompanionFetches(
            movieDetails = { id -> calls += "movieDetails($id)"; movieDetails(id) },
            tvDetails = { id -> calls += "tvDetails($id)"; tvDetails(id) },
            tmdbVideos = { id, type -> calls += "tmdbVideos($id,$type)"; tmdbVideos(id, type) },
            recommendations = { id, type -> calls += "recommendations($id,$type)"; recommendations(id, type) },
            similar = { id, type -> calls += "similar($id,$type)"; similar(id, type) },
            reviews = { id, type -> calls += "reviews($id,$type)"; reviews(id, type) },
            ratings = { id, type -> calls += "ratings($id,$type)"; ratings(id, type) },
        )
    }

    private fun request(
        tmdbId: Int = 123,
        mediaType: MediaType = MediaType.MOVIE,
        connected: Boolean = false,
        recommendationsEnabled: Boolean = false,
        hasRatings: Boolean = false,
        loadVideos: Boolean = false,
        loadReviews: Boolean = false,
        loadRatings: Boolean = false,
        loadRecommendations: Boolean = false,
        limits: TmdbCompanionLimits = TmdbCompanionLimits(),
        isCurrent: () -> Boolean = { true },
        onLanding: (TmdbCompanionLanding) -> Unit = {},
    ) = TmdbCompanionRequest(
        tmdbId = tmdbId,
        mediaType = mediaType,
        connected = connected,
        recommendationsEnabled = recommendationsEnabled,
        hasRatings = hasRatings,
        loadVideos = loadVideos,
        loadReviews = loadReviews,
        loadRatings = loadRatings,
        loadRecommendations = loadRecommendations,
        limits = limits,
        isCurrent = isCurrent,
        onLanding = onLanding,
    )

    // ── Videos fork ──────────────────────────────────────────────────────

    @Test
    fun `connected movie load forks videos to the movie detail payload`() = runTest {
        val trailer = SeerrRelatedVideo(key = "trailer")
        val recorder = FetchRecorder(
            movieDetails = { Result.success(SeerrMovieDetails(id = it, relatedVideos = listOf(trailer))) },
        )
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(request(connected = true, loadVideos = true))
        advanceUntilIdle()

        assertEquals(listOf("movieDetails(123)"), recorder.calls)
        assertEquals(listOf(trailer), holder.state.value.videos)
    }

    @Test
    fun `connected series load forks videos to the tv detail payload`() = runTest {
        val trailer = SeerrRelatedVideo(key = "trailer")
        val recorder = FetchRecorder(
            tvDetails = { Result.success(SeerrTvDetails(id = it, relatedVideos = listOf(trailer))) },
        )
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(request(mediaType = MediaType.SERIES, connected = true, loadVideos = true))
        advanceUntilIdle()

        assertEquals(listOf("tvDetails(123)"), recorder.calls)
        assertEquals(listOf(trailer), holder.state.value.videos)
    }

    @Test
    fun `disconnected load falls back to the tmdb videos fetch`() = runTest {
        val fallback = SeerrRelatedVideo(key = "tmdb")
        val recorder = FetchRecorder(
            tmdbVideos = { _, _ -> Result.success(listOf(fallback)) },
        )
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(request(connected = false, loadVideos = true))
        advanceUntilIdle()

        assertEquals(listOf("tmdbVideos(123,MOVIE)"), recorder.calls)
        assertEquals(listOf(fallback), holder.state.value.videos)
    }

    // ── Recommendations gate ─────────────────────────────────────────────

    @Test
    fun `disconnected load never fetches recommendations or similar`() = runTest {
        val recorder = FetchRecorder()
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(
            request(connected = false, recommendationsEnabled = true, loadRecommendations = true),
        )
        advanceUntilIdle()

        assertTrue(recorder.calls.none { it.startsWith("recommendations") })
        assertTrue(recorder.calls.none { it.startsWith("similar") })
        assertTrue(holder.state.value.recommendations.isEmpty())
    }

    @Test
    fun `recommendations-disabled load never fetches recommendations or similar`() = runTest {
        val recorder = FetchRecorder()
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(
            request(connected = true, recommendationsEnabled = false, loadRecommendations = true),
        )
        advanceUntilIdle()

        assertTrue(recorder.calls.none { it.startsWith("recommendations") })
        assertTrue(recorder.calls.none { it.startsWith("similar") })
    }

    @Test
    fun `connected and enabled load fetches both rows with the normalized media type`() = runTest {
        val rec = SeerrSearchItem(id = 1, title = "Rec")
        val sim = SeerrSearchItem(id = 2, title = "Sim")
        val recorder = FetchRecorder(
            recommendations = { _, _ -> Result.success(listOf(rec)) },
            similar = { _, _ -> Result.success(listOf(sim)) },
        )
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(
            request(
                mediaType = MediaType.SERIES,
                connected = true,
                recommendationsEnabled = true,
                loadRecommendations = true,
            ),
        )
        advanceUntilIdle()

        assertTrue("recommendations(123,SERIES)" in recorder.calls)
        assertTrue("similar(123,SERIES)" in recorder.calls)
        assertEquals(listOf(rec), holder.state.value.recommendations)
        assertEquals(listOf(sim), holder.state.value.similar)
    }

    @Test
    fun `recommendations legs are not started when the load flag is off`() = runTest {
        // Gate facts pass, but the consumer never asked for the legs.
        val recorder = FetchRecorder()
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(request(connected = true, recommendationsEnabled = true))
        advanceUntilIdle()

        assertTrue(recorder.calls.isEmpty())
    }

    // ── Ratings leg ──────────────────────────────────────────────────────

    @Test
    fun `ratings leg is skipped when the primary detail already carried ratings`() = runTest {
        val recorder = FetchRecorder()
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(request(loadRatings = true, hasRatings = true))
        advanceUntilIdle()

        assertTrue(recorder.calls.none { it.startsWith("ratings") })
        assertNull(holder.state.value.ratings)
    }

    @Test
    fun `ratings leg rides the wire media-type string`() = runTest {
        val ratings = SeerrRatings()
        val recorder = FetchRecorder(ratings = { _, _ -> Result.success(ratings) })
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(request(mediaType = MediaType.SERIES, loadRatings = true))
        advanceUntilIdle()

        assertEquals(listOf("ratings(123,tv)"), recorder.calls)
        assertEquals(ratings, holder.state.value.ratings)
    }

    @Test
    fun `failed ratings fetch lands nothing so the primary score survives`() = runTest {
        val recorder = FetchRecorder(
            ratings = { _, _ -> Result.failure(IllegalStateException("boom")) },
            recommendations = { _, _ -> Result.success(listOf(SeerrSearchItem(id = 1))) },
        )
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(request(loadRatings = true, loadRecommendations = true, recommendationsEnabled = true, connected = true))
        advanceUntilIdle()

        assertNull(holder.state.value.ratings)
        // The sibling legs still land through the failure.
        assertEquals(1, holder.state.value.recommendations.single().id)
    }

    // ── Per-leg error tolerance ──────────────────────────────────────────

    @Test
    fun `a failed row leg lands an empty row while its siblings still land`() = runTest {
        val trailer = SeerrRelatedVideo(key = "v")
        val sim = SeerrSearchItem(id = 2, title = "Sim")
        val review = TmdbReview(id = "r1", author = "a", content = "c")
        val recorder = FetchRecorder(
            movieDetails = { Result.success(SeerrMovieDetails(id = it, relatedVideos = listOf(trailer))) },
            recommendations = { _, _ -> Result.failure(IllegalStateException("boom")) },
            similar = { _, _ -> Result.success(listOf(sim)) },
            reviews = { _, _ -> Result.success(listOf(review)) },
        )
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(
            request(
                connected = true,
                recommendationsEnabled = true,
                loadVideos = true,
                loadReviews = true,
                loadRecommendations = true,
            ),
        )
        advanceUntilIdle()

        assertTrue(holder.state.value.recommendations.isEmpty())
        assertEquals(listOf(sim), holder.state.value.similar)
        assertEquals(listOf(trailer), holder.state.value.videos)
        assertEquals(listOf(review), holder.state.value.reviews)
    }

    // ── Take-limits ──────────────────────────────────────────────────────

    @Test
    fun `default limits pin 20 recommendations 20 similar 5 reviews`() = runTest {
        val items = (1..25).map { SeerrSearchItem(id = it) }
        val reviews = (1..7).map { TmdbReview(id = "$it") }
        val recorder = FetchRecorder(
            recommendations = { _, _ -> Result.success(items) },
            similar = { _, _ -> Result.success(items) },
            reviews = { _, _ -> Result.success(reviews) },
        )
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(
            request(
                connected = true,
                recommendationsEnabled = true,
                loadReviews = true,
                loadRecommendations = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(20, holder.state.value.recommendations.size)
        assertEquals(20, holder.state.value.similar.size)
        assertEquals(5, holder.state.value.reviews.size)
    }

    @Test
    fun `NONE limits keep the rows whole`() = runTest {
        val items = (1..25).map { SeerrSearchItem(id = it) }
        val reviews = (1..7).map { TmdbReview(id = "$it") }
        val recorder = FetchRecorder(
            recommendations = { _, _ -> Result.success(items) },
            similar = { _, _ -> Result.success(items) },
            reviews = { _, _ -> Result.success(reviews) },
        )
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(
            request(
                connected = true,
                recommendationsEnabled = true,
                loadReviews = true,
                loadRecommendations = true,
                limits = TmdbCompanionLimits.NONE,
            ),
        )
        advanceUntilIdle()

        assertEquals(25, holder.state.value.recommendations.size)
        assertEquals(25, holder.state.value.similar.size)
        assertEquals(7, holder.state.value.reviews.size)
    }

    // ── Staleness guard ──────────────────────────────────────────────────

    @Test
    fun `already-stale load starts nothing and lands nothing`() = runTest {
        val recorder = FetchRecorder()
        val holder = TmdbCompanionStateHolder(this, recorder.seams)
        val landings = mutableListOf<TmdbCompanionLanding>()

        holder.load(
            request(
                connected = true,
                recommendationsEnabled = true,
                loadVideos = true,
                loadReviews = true,
                loadRatings = true,
                loadRecommendations = true,
                isCurrent = { false },
                onLanding = { landings += it },
            ),
        )
        advanceUntilIdle()

        assertTrue(recorder.calls.isEmpty())
        assertTrue(landings.isEmpty())
        assertEquals(TmdbCompanionSnapshot(), holder.state.value)
    }

    @Test
    fun `landing that resolves after the guard expires is dropped`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var current = true
        val recorder = FetchRecorder(
            tmdbVideos = { _, _ ->
                gate.await()
                Result.success(listOf(SeerrRelatedVideo(key = "stale")))
            },
        )
        val holder = TmdbCompanionStateHolder(this, recorder.seams)
        val landings = mutableListOf<TmdbCompanionLanding>()

        holder.load(
            request(
                connected = false,
                loadVideos = true,
                isCurrent = { current },
                onLanding = { landings += it },
            ),
        )
        advanceUntilIdle() // parked inside the videos fetch

        current = false // navigation expires the captured epoch
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(landings.isEmpty())
        assertTrue(holder.state.value.videos.isEmpty())
    }

    // ── Reset + landing seam ─────────────────────────────────────────────

    @Test
    fun `a second load resets the snapshot before its own legs land`() = runTest {
        val first = SeerrSearchItem(id = 1)
        var second = false
        val recorder = FetchRecorder(
            recommendations = { _, _ ->
                if (second) Result.success(emptyList()) else Result.success(listOf(first))
            },
            similar = { _, _ -> Result.success(emptyList()) },
        )
        val holder = TmdbCompanionStateHolder(this, recorder.seams)

        holder.load(
            request(connected = true, recommendationsEnabled = true, loadRecommendations = true),
        )
        advanceUntilIdle()
        assertEquals(listOf(first), holder.state.value.recommendations)

        second = true
        holder.load(
            request(connected = true, recommendationsEnabled = true, loadRecommendations = true),
        )
        advanceUntilIdle()

        assertTrue(holder.state.value.recommendations.isEmpty())
    }

    @Test
    fun `landings stream to the consumer as each leg resolves`() = runTest {
        val rec = SeerrSearchItem(id = 1, title = "Rec")
        val review = TmdbReview(id = "r1", author = "a", content = "c")
        val recorder = FetchRecorder(
            recommendations = { _, _ -> Result.success(listOf(rec)) },
            reviews = { _, _ -> Result.success(listOf(review)) },
        )
        val holder = TmdbCompanionStateHolder(this, recorder.seams)
        val landings = mutableListOf<TmdbCompanionLanding>()

        holder.load(
            request(
                connected = true,
                recommendationsEnabled = true,
                loadReviews = true,
                loadRecommendations = true,
                onLanding = { landings += it },
            ),
        )
        advanceUntilIdle()

        assertTrue(TmdbCompanionLanding.Recommendations(listOf(rec)) in landings)
        assertTrue(TmdbCompanionLanding.Reviews(listOf(review)) in landings)
        assertNull(holder.state.value.ratings)
    }
}
