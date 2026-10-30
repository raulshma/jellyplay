package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings

/**
 * The external-ratings strip's plain values — the shape both
 * rating sources fold into and the shared [RatingsRow] composable renders:
 *
 * 1. The Seerr detail-by-provider-id payload ([SeerrRatings]: RT critics +
 *    audience, IMDb, TMDB — each leg carrying its own deep-link url), taken
 *    when Seerr is connected;
 * 2. the TMDB-only fallback ([com.raulshma.jellyplay.core.network.api.TmdbApiClient.getDetails]:
 *    the public vote aggregate plus the constructed themoviedb.org link),
 *    taken when Seerr is disconnected or its ratings fetch failed/returned
 *    nothing.
 *
 * Every field is nullable: a source the active resolution didn't yield simply
 * renders no chip. [isEmpty] is the strip's admission — all four scores null
 * means nothing to render (silent absence, the detail screen's plugin-never-
 * an-error rule).
 */
@Immutable
data class DetailExternalRatings(
    /** Rotten Tomatoes critics score, 0..100 percent. */
    val critics: Int? = null,
    /** Rotten Tomatoes audience score, 0..100 percent. */
    val audience: Int? = null,
    /** IMDb user rating, 0..10 (rendered with exactly one decimal). */
    val imdb: Float? = null,
    /** TMDB vote average, 0..10 (rendered as a 0..100 percent). */
    val tmdb: Float? = null,
    /** Deep link for the RT chips (one url serves both scores). */
    val rtUrl: String? = null,
    /** Deep link for the IMDb chip. */
    val imdbUrl: String? = null,
    /** Deep link for the TMDB chip. */
    val tmdbUrl: String? = null,
    /**
     * The OMDb awards line (the companion plugin's `omdb-awards` enrichment —
     * e.g. "Won 4 Oscars. 101 wins & 190 nominations."). Rendered as a
     * truncated badge after the score chips; null renders nothing (no empty
     * badge). Not an [isEmpty] leg: the badge supplements the score chips
     * and never stands a strip up alone.
     */
    val awards: String? = null,
) {
    /** True when no chip would render — the strip's silent-absence contract. */
    val isEmpty: Boolean
        get() = critics == null && audience == null && imdb == null && tmdb == null

    companion object {
        /**
         * The Seerr fold: the RT pair from the rt leg, IMDb preferring its
         * critics score (the same precedence the Seerr screen's strip renders),
         * TMDB's own rating; each leg's url rides its chips.
         */
        fun fromSeerr(ratings: SeerrRatings): DetailExternalRatings = DetailExternalRatings(
            critics = ratings.rt?.criticsScore,
            audience = ratings.rt?.audienceScore,
            imdb = ratings.imdb?.criticsScore ?: ratings.imdb?.rating,
            tmdb = ratings.tmdb?.rating,
            rtUrl = ratings.rt?.url,
            imdbUrl = ratings.imdb?.url,
            tmdbUrl = ratings.tmdb?.url,
        )

        /**
         * The TMDB-only fallback: the public vote aggregate plus the
         * constructed themoviedb.org deep link (the
         * [buildDetailExternalLinks] url grammar — `/{movie|tv}/{tmdbId}`).
         * No Seerr means no RT/IMDb scores, so only the TMDB chip can render.
         */
        fun tmdbOnly(voteAverage: Float?, tmdbPathSegment: String, tmdbId: Int): DetailExternalRatings =
            DetailExternalRatings(
                tmdb = voteAverage,
                tmdbUrl = "https://www.themoviedb.org/$tmdbPathSegment/$tmdbId",
            )
    }
}

/**
 * The external-ratings strip's ordered chip plan — the pure expansion the shared
 * [RatingsRow] renders: RT critics, RT audience, IMDb, TMDB, in that order,
 * dropping every source the resolution didn't yield. Empty when [isEmpty].
 * Pure → directly unit-testable (the [DetailExternalLinksTest] convention).
 */
internal fun externalRatingChipSources(ratings: DetailExternalRatings): List<DetailExternalRatings> =
    listOf(
        DetailExternalRatings(critics = ratings.critics, rtUrl = ratings.rtUrl),
        DetailExternalRatings(audience = ratings.audience, rtUrl = ratings.rtUrl),
        DetailExternalRatings(imdb = ratings.imdb, imdbUrl = ratings.imdbUrl),
        DetailExternalRatings(tmdb = ratings.tmdb, tmdbUrl = ratings.tmdbUrl),
    ).filterNot { it.isEmpty }

/**
 * Whether the external-ratings chips render STANDALONE in the header metadata row (after the
 * critic chip): the preference is on, the resolution yielded chips, and the
 * plugin ratings row is ABSENT — when it is present the chips merge into that
 * row's FlowRow instead ([externalRatingsMergedIntoPluginRow]), so the strip
 * never renders in both places. Pure → directly unit-testable.
 */
internal fun externalRatingsStandaloneInHeader(
    showExternalRatings: Boolean,
    hasPluginRatings: Boolean,
    external: DetailExternalRatings,
): Boolean = showExternalRatings && !hasPluginRatings && !external.isEmpty

/**
 * Whether the external-ratings chips merge into the PLUGIN ratings row's FlowRow (plugin
 * sources first, then TMDB/IMDb): the plugin row is present, the resolution
 * yielded chips, and the plugin dedupe does not stand them down. The dedupe is
 * the [DetailUiState.pluginSimilarSuppressesStock] mirror — the enrichment's
 * synchronous Ratings-feature probe ([externalSuppressedByPlugin]) suppresses
 * the external-ratings chips exactly when the plugin row actually has content, so a probe
 * that returned true against an empty plugin row never hides the strip.
 * Pure → directly unit-testable.
 */
internal fun externalRatingsMergedIntoPluginRow(
    hasPluginRatings: Boolean,
    external: DetailExternalRatings,
    externalSuppressedByPlugin: Boolean,
): Boolean = hasPluginRatings && !external.isEmpty && !(externalSuppressedByPlugin && hasPluginRatings)
