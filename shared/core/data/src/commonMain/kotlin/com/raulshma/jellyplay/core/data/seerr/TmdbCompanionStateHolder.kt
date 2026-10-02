package com.raulshma.jellyplay.core.data.seerr

import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One companion-content leg result, delivered the moment its fetch resolves.
 * Consumers map each landing onto their own ui-state field (the only
 * consumer-local concern left); [TmdbCompanionStateHolder] owns everything
 * about WHEN a landing happens (gates, fork, fan-out, tolerance, limits,
 * staleness).
 */
sealed interface TmdbCompanionLanding {
    /** Related videos (trailers) — the connected fork or the TMDB fallback. */
    data class Videos(val videos: List<SeerrRelatedVideo>) : TmdbCompanionLanding

    /** TMDB reviews (never Seerr-gated). */
    data class Reviews(val reviews: List<TmdbReview>) : TmdbCompanionLanding

    /** Secondary ratings for items whose primary detail carried none. */
    data class Ratings(val ratings: SeerrRatings) : TmdbCompanionLanding

    /** TMDB recommendations row. */
    data class Recommendations(val items: List<SeerrSearchItem>) : TmdbCompanionLanding

    /** TMDB similar row. */
    data class Similar(val items: List<SeerrSearchItem>) : TmdbCompanionLanding
}

/**
 * The holder's single observable snapshot; individual flows stay private
 * ([SeerrRequestStateHolder] shape). Guard-sensitive consumers instead take
 * landings through [TmdbCompanionRequest.onLanding] — see its KDoc for why.
 */
data class TmdbCompanionSnapshot(
    val videos: List<SeerrRelatedVideo> = emptyList(),
    val reviews: List<TmdbReview> = emptyList(),
    val ratings: SeerrRatings? = null,
    val recommendations: List<SeerrSearchItem> = emptyList(),
    val similar: List<SeerrSearchItem> = emptyList(),
)

/**
 * Per-leg take limits; `null` = the row is kept whole. The defaults are the
 * media-detail screen's verified numbers (20 recommendations / 20 similar /
 * 5 reviews; videos were never limited); [NONE] is the Seerr-detail screen's
 * verified behavior (full rows, no takes).
 */
data class TmdbCompanionLimits(
    val recommendations: Int? = DEFAULT_RECOMMENDATIONS,
    val similar: Int? = DEFAULT_SIMILAR,
    val reviews: Int? = DEFAULT_REVIEWS,
) {
    companion object {
        const val DEFAULT_RECOMMENDATIONS = 20
        const val DEFAULT_SIMILAR = 20
        const val DEFAULT_REVIEWS = 5

        /** No take-limits on any leg (the Seerr-detail screen's behavior). */
        val NONE = TmdbCompanionLimits(recommendations = null, similar = null, reviews = null)
    }
}

/**
 * The suspend fetch seams the holder's legs ride — the raw repository calls
 * each consuming ViewModel already made, as constructor lambdas so commonMain
 * stays pure and tests fake them directly. [of] is the production wiring.
 */
class TmdbCompanionFetches(
    /** Movie detail payload — the connected fork's MOVIE source for videos. */
    val movieDetails: suspend (tmdbId: Int) -> Result<SeerrMovieDetails>,
    /** TV detail payload — the connected fork's SERIES source for videos. */
    val tvDetails: suspend (tmdbId: Int) -> Result<SeerrTvDetails>,
    /** TMDB videos fallback, used when Seerr is not connected. */
    val tmdbVideos: suspend (tmdbId: Int, mediaType: MediaType) -> Result<List<SeerrRelatedVideo>>,
    val recommendations: suspend (tmdbId: Int, mediaType: MediaType) -> Result<List<SeerrSearchItem>>,
    val similar: suspend (tmdbId: Int, mediaType: MediaType) -> Result<List<SeerrSearchItem>>,
    val reviews: suspend (tmdbId: Int, mediaType: MediaType) -> Result<List<TmdbReview>>,
    /**
     * Secondary ratings. Takes the wire media-type string ("movie"/"tv") —
     * the repository's own vocabulary for this endpoint, distinct from the
     * [MediaType] the row legs take.
     */
    val ratings: suspend (tmdbId: Int, mediaType: String) -> Result<SeerrRatings>,
) {
    companion object {
        /** Production wiring: every seam straight onto [SeerrRepository]. */
        fun of(repository: SeerrRepository): TmdbCompanionFetches = TmdbCompanionFetches(
            movieDetails = repository::getMovieDetails,
            tvDetails = repository::getTvDetails,
            tmdbVideos = repository::getTmdbVideos,
            recommendations = repository::getRecommendations,
            similar = repository::getSimilar,
            reviews = repository::getTmdbReviews,
            ratings = repository::getRatings,
        )
    }
}

/**
 * One companion-content load. The booleans are FACTS (connection flags,
 * preferences, whether the primary detail already carried ratings) and leg
 * enablement; the holder owns the gate EXPRESSIONS and their order so the two
 * consuming screens cannot drift apart again.
 */
class TmdbCompanionRequest(
    val tmdbId: Int,
    val mediaType: MediaType,
    /**
     * Seerr connection fact. Gates the videos fork (details' trailers vs the
     * TMDB fallback) and the recommendations/similar gate. Screens with no
     * connection concept (the Seerr-detail screen only exists behind a
     * connected Seerr) pass `true`.
     */
    val connected: Boolean,
    /**
     * Recommendations preference fact. With [connected], gates the
     * recommendations/similar legs; screens with no such preference pass
     * `true`.
     */
    val recommendationsEnabled: Boolean,
    /**
     * True when the primary detail already carried external ratings — skips
     * the ratings leg (the fetch would be redundant).
     */
    val hasRatings: Boolean = false,
    /** Leg enablement. */
    val loadVideos: Boolean = false,
    val loadReviews: Boolean = false,
    val loadRatings: Boolean = false,
    val loadRecommendations: Boolean = false,
    val limits: TmdbCompanionLimits = TmdbCompanionLimits(),
    /**
     * Staleness guard, re-checked at the same points the former hand-copied
     * choreography checked its load epoch: before the reset, before the
     * recommendations legs start, and atomically with every landing. A
     * superseded flight path drops its results instead of publishing them.
     * Screens without an epoch pass the default (always current).
     */
    val isCurrent: () -> Boolean = { true },
    /**
     * Landing seam: invoked synchronously after each guard-passed landing, in
     * the same dispatcher step as the guard check — so a guard-sensitive
     * consumer folding into its ui state gets the SAME check-then-write
     * atomicity the hand-copied code had (a collected [TmdbCompanionStateHolder.state]
     * would interleave a navigation between the two). Consumers that only read
     * the snapshot omit it.
     */
    val onLanding: (TmdbCompanionLanding) -> Unit = {},
)

/**
 * Deep module for the TMDB-companion choreography that [DetailViewModel] and
 * [SeerrDetailViewModel] used to hand-copy in parallel (the
 * [SeerrRequestStateHolder] template): the movie/tv fork for related videos,
 * the connected × recommendations-enabled × staleness gate for
 * recommendations/similar, the has-ratings skip for the secondary-ratings
 * leg, the bounded async fan-out, per-leg error tolerance, and the take
 * limits. Two copies whose gate order and take-limits could drift; here it is
 * one fold, and a new leg behavior is one edit instead of two re-writes.
 *
 * Consumers stay thin: each captures its own staleness guard into the
 * request's [TmdbCompanionRequest.isCurrent] (mirroring how startInstantMix
 * passes its guard into [com.raulshma.jellyplay.core.data.playback.InstantMixStateHolder]'s
 * seam) and maps landings onto its ui-state fields.
 */
class TmdbCompanionStateHolder(
    private val scope: CoroutineScope,
    private val fetches: TmdbCompanionFetches,
) {
    private val _state = MutableStateFlow(TmdbCompanionSnapshot())
    val state: StateFlow<TmdbCompanionSnapshot> = _state.asStateFlow()

    /**
     * Starts one companion load on [scope] and returns its Job — join it when
     * the caller's loading flag must stay up until every leg settles (the
     * Seerr-detail screen's spinner), fire-and-forget when landings stream in
     * independently (the media-detail screen).
     *
     * Sequence, per the former hand-copied choreography: entry staleness
     * check, snapshot reset, then every enabled leg fetched concurrently —
     * each landing as soon as it resolves. The recommendations/similar pair
     * additionally re-checks staleness at fan-out time (a navigation landing
     * between the entry check and the gate must not start new fetches).
     */
    fun load(request: TmdbCompanionRequest): Job = scope.launch {
        if (!request.isCurrent()) return@launch
        _state.value = TmdbCompanionSnapshot()
        coroutineScope {
            if (request.loadVideos) launch { videosLeg(request) }
            // Reviews fire unconditionally (neither the Seerr connection
            // nor the recommendations preference gates them) — the former
            // separate-launch semantics preserved: their landing is
            // staleness-checked, their start is not.
            if (request.loadReviews) launch { reviewsLeg(request) }
            if (request.loadRatings && !request.hasRatings) launch { ratingsLeg(request) }
            if (loadRecommendations(request)) {
                launch { recommendationsLeg(request) }
                launch { similarLeg(request) }
            }
        } // coroutineScope joins every leg before load() completes
    }

    /**
     * The recommendations gate, in the former hand-copied order: connection
     * flag, then recommendations preference, then staleness — pinned here so
     * the two screens cannot re-order it.
     */
    private fun loadRecommendations(request: TmdbCompanionRequest): Boolean =
        request.loadRecommendations &&
            request.connected &&
            request.recommendationsEnabled &&
            request.isCurrent()

    /**
     * The videos fork: connected → the Seerr detail payload's own trailers
     * (movie/tv branch on the media type); disconnected → the straight TMDB
     * fetch. Tolerates a failed leg with an empty row, as before.
     */
    private suspend fun videosLeg(request: TmdbCompanionRequest) {
        // The former pre-fan-out epoch re-check: a stale flight path must not
        // even START the videos fetch.
        if (!request.isCurrent()) return
        val videos = if (request.connected) {
            if (request.mediaType == MediaType.MOVIE) {
                fetches.movieDetails(request.tmdbId).map { it.relatedVideos }
            } else {
                fetches.tvDetails(request.tmdbId).map { it.relatedVideos }
            }
        } else {
            fetches.tmdbVideos(request.tmdbId, request.mediaType)
        }
        land(request, TmdbCompanionLanding.Videos(videos.getOrElse { emptyList() }))
    }

    private suspend fun reviewsLeg(request: TmdbCompanionRequest) {
        val reviews = fetches.reviews(request.tmdbId, request.mediaType)
            .getOrElse { emptyList() }
        land(request, TmdbCompanionLanding.Reviews(reviews.takeUpTo(request.limits.reviews)))
    }

    /**
     * Secondary ratings. Unlike the row legs, a FAILED ratings fetch lands
     * nothing: the consumer merges ratings into its existing state, and an
     * empty [SeerrRatings] would clobber the primary detail's own score —
     * the former `getOrNull()?.let { ... }` semantics preserved.
     */
    private suspend fun ratingsLeg(request: TmdbCompanionRequest) {
        fetches.ratings(request.tmdbId, request.mediaType.wireName)
            .getOrNull()
            ?.let { land(request, TmdbCompanionLanding.Ratings(it)) }
    }

    private suspend fun recommendationsLeg(request: TmdbCompanionRequest) {
        val items = fetches.recommendations(request.tmdbId, request.mediaType)
            .getOrElse { emptyList() }
        land(request, TmdbCompanionLanding.Recommendations(items.takeUpTo(request.limits.recommendations)))
    }

    private suspend fun similarLeg(request: TmdbCompanionRequest) {
        val items = fetches.similar(request.tmdbId, request.mediaType)
            .getOrElse { emptyList() }
        land(request, TmdbCompanionLanding.Similar(items.takeUpTo(request.limits.similar)))
    }

    /**
     * One guard-checked landing: the staleness check, the snapshot fold, and
     * the consumer's [TmdbCompanionRequest.onLanding] run contiguously in this
     * dispatcher step — a navigation cannot interleave between the check and
     * the consumer's ui-state write.
     */
    private fun land(request: TmdbCompanionRequest, landing: TmdbCompanionLanding) {
        if (!request.isCurrent()) return
        _state.update { it.fold(landing) }
        request.onLanding(landing)
    }
}

/** Seerr's wire vocabulary for the ratings endpoint. */
private val MediaType.wireName: String
    get() = if (this == MediaType.MOVIE) "movie" else "tv"

private fun <T> List<T>.takeUpTo(limit: Int?): List<T> =
    if (limit == null) this else take(limit)

private fun TmdbCompanionSnapshot.fold(landing: TmdbCompanionLanding): TmdbCompanionSnapshot =
    when (landing) {
        is TmdbCompanionLanding.Videos -> copy(videos = landing.videos)
        is TmdbCompanionLanding.Reviews -> copy(reviews = landing.reviews)
        is TmdbCompanionLanding.Ratings -> copy(ratings = landing.ratings)
        is TmdbCompanionLanding.Recommendations -> copy(recommendations = landing.items)
        is TmdbCompanionLanding.Similar -> copy(similar = landing.items)
    }
