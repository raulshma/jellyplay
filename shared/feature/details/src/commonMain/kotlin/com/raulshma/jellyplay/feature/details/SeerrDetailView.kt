package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.seerr.SeerrGenre
import com.raulshma.jellyplay.core.model.seerr.SeerrKeyword
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaInfo
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrProductionCountry
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrReleaseDateRegion
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrWatchProviderRegion

/**
 * The Seerr detail's content fold — the `SeerrMovieDetails`/`SeerrTvDetails`
 * union derived ONCE instead of re-derived at every rendering site. The
 * screen and its sections used to carry ~14 hand-written
 * `movieDetail?.X ?: tvDetail?.Y` ladders (backdrop, title, poster, videos —
 * twice inside the same body for the two layout branches — overview,
 * keywords, tagline, mediaInfo, ids, ratings, and the sections' info-panel
 * folds), plus the cast fold computed twice verbatim. Each copy was a fresh
 * chance to drift from its twins; [of] pins the precedence once:
 *
 *  - Content fields are MOVIE-FIRST (`movie?.X ?: tv?.Y`) with the same
 *    defaults the former sites applied — `title` folds to `""` (the wire
 *    default), `overview` to `""`, list fields to `emptyList()` — so a
 *    neither-present fold (the loading gate lets this compose) renders
 *    exactly what the ladders rendered.
 *  - Cast is TV-FIRST: the aggregateCredits arm wins even when a movie detail
 *    is also present, and — the former ladder's exact elvis shape — a TV
 *    detail whose aggregateCredits carries an EMPTY cast does NOT fall
 *    through to the movie arm (the mapped empty list is non-null, so the
 *    elvis never fires); only a null aggregateCredits arm falls through.
 *    The [SeerrCastMember] mapping moved INTO this fold; the renderers keep
 *    only their display-side filtering (distinctBy).
 *  - TV-only fields — [seasons], [seasonsPosterUrl], [tvdbId],
 *    [usContentRating] — are null/empty for a movie, which is what keeps the
 *    SeasonsSection gate (`seasons.isNotEmpty()`) and the rating badge
 *    absent without a separate type check.
 *  - Ids: [tmdbId] is movie-first (the former `?: 0` floor kept);
 *    [imdbId] reads the movie's own `imdbId` but the TV's
 *    `externalIds.imdbId` — the wire carries them in different places, so
 *    the arms are not symmetric and must not be "regularized".
 *
 * Pure data — no Compose types — so [SeerrDetailViewTest] pins the
 * precedence table without a composition. internal because
 * [SeerrCastMember] is: every consumer (screen, sections, test) is this
 * module.
 */
internal data class SeerrDetailView(
    /** True when the loaded detail is the movie leg — the mediaType discriminator (external links, info-panel movie arm). */
    val isMovie: Boolean,
    // ── Header / hero ────────────────────────────────────────────────────
    val backdropUrl: String?,
    val posterUrl: String?,
    /** Movie title or show name — never null (folds to ""). */
    val title: String,
    val tagline: String?,
    /** Movie or show overview — never null (folds to ""). */
    val overview: String,
    // ── Body sections ────────────────────────────────────────────────────
    val keywords: List<SeerrKeyword>,
    /** The related-videos list both the trailer picker and VideosSection read. */
    val relatedVideos: List<SeerrRelatedVideo>,
    /** Request/availability info the action button and dialog state fold through. */
    val mediaInfo: SeerrMediaInfo?,
    /** TV aggregate cast first, movie credits fallback — mapped, render-ready. */
    val cast: List<SeerrCastMember>,
    // ── TV-only (null/empty for a movie) ─────────────────────────────────
    val seasons: List<SeerrSeason>,
    /** The show's own poster — the SeasonsSection fallback for poster-less seasons. */
    val seasonsPosterUrl: String?,
    val tvdbId: Int?,
    /** The US contentRatings row's rating, or null when unrated / movie leg. */
    val usContentRating: String?,
    // ── Ids ──────────────────────────────────────────────────────────────
    val tmdbId: Int,
    val imdbId: String?,
    // ── Condensed info line (MediaInfoCondensed) ─────────────────────────
    /** Movie release date or show first-air date. */
    val releaseDate: String?,
    /** Movie runtime or the show's first episode runtime. */
    val runtime: Int?,
    val genres: List<SeerrGenre>,
    // ── Media-information panel ──────────────────────────────────────────
    val status: String?,
    val language: String?,
    /** Movie-leg only (empty for TV — the panel's country row is movie-scoped). */
    val productionCountries: List<SeerrProductionCountry>,
    /** Movie production companies or TV network names — the panel's studios row. */
    val studios: List<String>,
    val watchProviders: List<SeerrWatchProviderRegion>,
    /** Movie-leg release-date regions (empty for TV). */
    val releases: List<SeerrReleaseDateRegion>,
    /** Movie-leg only (null for TV — the panel's revenue/budget rows are movie-scoped). */
    val revenue: Long?,
    val budget: Long?,
) {
    companion object {
        /**
         * The one precedence table. Every former ladder site reads a field of
         * the returned value instead of re-deriving its arm pair; the arms
         * below are the former sites' expressions verbatim, so the fold is a
         * consolidation and not a behavior change. Computed once, at
         * SeerrDetailScreen's root — the composition that holds the raw
         * movie/tv pair — and passed down from there; no descendant
         * re-derives it.
         */
        fun of(movie: SeerrMovieDetails?, tv: SeerrTvDetails?): SeerrDetailView = SeerrDetailView(
            isMovie = movie != null,
            backdropUrl = movie?.backdropUrl ?: tv?.backdropUrl,
            posterUrl = movie?.posterUrl ?: tv?.posterUrl,
            title = movie?.title ?: tv?.name ?: "",
            tagline = movie?.tagline ?: tv?.tagline,
            overview = movie?.overview ?: tv?.overview ?: "",
            keywords = movie?.keywords ?: tv?.keywords ?: emptyList(),
            relatedVideos = movie?.relatedVideos ?: tv?.relatedVideos ?: emptyList(),
            mediaInfo = movie?.mediaInfo ?: tv?.mediaInfo,
            cast = tv?.aggregateCredits?.cast?.toAggregateCastMembers()
                ?: movie?.credits?.cast?.toCastMembers()
                ?: emptyList(),
            seasons = tv?.seasons ?: emptyList(),
            seasonsPosterUrl = tv?.posterUrl,
            tvdbId = tv?.externalIds?.tvdbId,
            usContentRating = tv?.contentRatings?.find { it.iso31661 == "US" }?.rating,
            tmdbId = movie?.id ?: tv?.id ?: 0,
            imdbId = movie?.imdbId ?: tv?.externalIds?.imdbId,
            releaseDate = movie?.releaseDate ?: tv?.firstAirDate,
            runtime = movie?.runtime ?: tv?.episodeRunTime?.firstOrNull(),
            genres = movie?.genres ?: tv?.genres ?: emptyList(),
            status = movie?.status ?: tv?.status,
            language = movie?.originalLanguage ?: tv?.originalLanguage,
            productionCountries = movie?.productionCountries ?: emptyList(),
            studios = movie?.productionCompanies?.map { it.name }
                ?: tv?.networks?.map { it.name }
                ?: emptyList(),
            watchProviders = movie?.watchProviders ?: tv?.watchProviders ?: emptyList(),
            releases = movie?.releases ?: emptyList(),
            revenue = movie?.revenue,
            budget = movie?.budget,
        )
    }
}
