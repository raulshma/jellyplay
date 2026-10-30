package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.seerr.SeerrAggregateCast
import com.raulshma.jellyplay.core.model.seerr.SeerrAggregateCredits
import com.raulshma.jellyplay.core.model.seerr.SeerrCast
import com.raulshma.jellyplay.core.model.seerr.SeerrContentRating
import com.raulshma.jellyplay.core.model.seerr.SeerrCredits
import com.raulshma.jellyplay.core.model.seerr.SeerrExternalIds
import com.raulshma.jellyplay.core.model.seerr.SeerrKeyword
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaInfo
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrProductionCompany
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrRole
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.toSeerrSearchItem
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins [SeerrDetailView] — the movie/tv union fold the Seerr detail screen
 * and its sections read instead of hand-written `movie?.X ?: tv?.Y` ladders.
 * The table under test is the PRECEDENCE: content fields movie-first, cast
 * tv-first (with the exact elvis fall-through shape of the former ladder),
 * tv-only fields null/empty on the movie leg, and the asymmetric id arms.
 * The [toSeerrSearchItem] adapters the request-dialog handoff uses are pinned
 * beside them (they live in core:model; the mapping was extracted verbatim
 * from the screen's hand-built item).
 */
class SeerrDetailViewTest {

    private fun movie(
        id: Int = 42,
        title: String = "Movie",
        backdropPath: String? = "/m-backdrop.jpg",
        posterPath: String? = "/m-poster.jpg",
        overview: String? = "Movie overview",
        releaseDate: String? = "2024-01-01",
        runtime: Int? = 120,
        imdbId: String? = "im_movie",
        status: String? = "Released",
        originalLanguage: String? = "en",
        credits: SeerrCredits? = null,
        mediaInfo: SeerrMediaInfo? = null,
    ) = SeerrMovieDetails(
        id = id,
        title = title,
        backdropPath = backdropPath,
        posterPath = posterPath,
        overview = overview,
        releaseDate = releaseDate,
        runtime = runtime,
        imdbId = imdbId,
        status = status,
        originalLanguage = originalLanguage,
        credits = credits,
        mediaInfo = mediaInfo,
    )

    private fun tv(
        id: Int = 7,
        name: String = "Show",
        posterPath: String? = "/t-poster.jpg",
        overview: String? = "Show overview",
        firstAirDate: String? = "2020-05-06",
        episodeRunTime: List<Int> = emptyList(),
        externalIds: SeerrExternalIds? = null,
        contentRatings: List<SeerrContentRating> = emptyList(),
        aggregateCredits: SeerrAggregateCredits? = null,
        seasons: List<SeerrSeason> = emptyList(),
        mediaInfo: SeerrMediaInfo? = null,
    ) = SeerrTvDetails(
        id = id,
        name = name,
        posterPath = posterPath,
        overview = overview,
        firstAirDate = firstAirDate,
        episodeRunTime = episodeRunTime,
        externalIds = externalIds,
        contentRatings = contentRatings,
        aggregateCredits = aggregateCredits,
        seasons = seasons,
        mediaInfo = mediaInfo,
    )

    // ── Movie-only fold ────────────────────────────────────────────────

    @Test
    fun `movie-only fold reads the movie leg and leaves tv-only fields empty`() {
        val view = SeerrDetailView.of(
            movie = movie(credits = SeerrCredits(cast = listOf(SeerrCast(id = 1, name = "Movie Actor", character = "M")))),
            tv = null,
        )

        assertTrue(view.isMovie)
        assertEquals("Movie", view.title)
        assertEquals("https://image.tmdb.org/t/p/w1280/m-backdrop.jpg", view.backdropUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/m-poster.jpg", view.posterUrl)
        assertEquals("Movie overview", view.overview)
        assertEquals("2024-01-01", view.releaseDate)
        assertEquals(120, view.runtime)
        assertEquals(42, view.tmdbId)
        assertEquals("im_movie", view.imdbId)
        assertEquals("Released", view.status)
        assertEquals("en", view.language)
        // Cast mapped through the movie credits arm.
        assertEquals(listOf(SeerrCastMember(1, "Movie Actor", "M", null)), view.cast)
        // TV-only fields are null/empty — no seasons row, no rating badge.
        assertTrue(view.seasons.isEmpty())
        assertNull(view.seasonsPosterUrl)
        assertNull(view.tvdbId)
        assertNull(view.usContentRating)
        // Movie-only info-panel fields ride through.
        assertEquals(1_000_000L, SeerrDetailView.of(movie = movie().copy(revenue = 1_000_000L), tv = null).revenue)
    }

    // ── TV-only fold ───────────────────────────────────────────────────

    @Test
    fun `tv-only fold reads the tv leg`() {
        val view = SeerrDetailView.of(
            movie = null,
            tv = tv(
                episodeRunTime = listOf(42, 30),
                externalIds = SeerrExternalIds(imdbId = "im_tv", tvdbId = 99),
                contentRatings = listOf(SeerrContentRating(iso31661 = "US", rating = "TV-14")),
                aggregateCredits = SeerrAggregateCredits(
                    cast = listOf(SeerrAggregateCast(id = 2, name = "Tv Actor", roles = listOf(SeerrRole(character = "T")))),
                ),
                seasons = listOf(SeerrSeason(seasonNumber = 1, episodeCount = 8)),
            ),
        )

        assertFalse(view.isMovie)
        assertEquals("Show", view.title)
        assertEquals("https://image.tmdb.org/t/p/w500/t-poster.jpg", view.posterUrl)
        assertEquals("2020-05-06", view.releaseDate)
        assertEquals(42, view.runtime)
        assertEquals(7, view.tmdbId)
        // The imdbId arm reads externalIds on the tv leg.
        assertEquals("im_tv", view.imdbId)
        assertEquals(99, view.tvdbId)
        assertEquals("TV-14", view.usContentRating)
        assertEquals(listOf(SeerrCastMember(2, "Tv Actor", "T", null)), view.cast)
        assertEquals(listOf(SeerrSeason(seasonNumber = 1, episodeCount = 8)), view.seasons)
        assertEquals("https://image.tmdb.org/t/p/w500/t-poster.jpg", view.seasonsPosterUrl)
        // Movie-only info-panel fields stay empty/null on the tv leg.
        assertNull(SeerrDetailView.of(movie = null, tv = tv()).revenue)
        assertTrue(SeerrDetailView.of(movie = null, tv = tv()).productionCountries.isEmpty())
    }

    // ── Both present: the precedence pins ──────────────────────────────

    @Test
    fun `both present - content fields stay movie-first`() {
        val view = SeerrDetailView.of(
            movie = movie(mediaInfo = SeerrMediaInfo(status = SeerrMediaStatus.AVAILABLE)),
            tv = tv(mediaInfo = SeerrMediaInfo(status = SeerrMediaStatus.PENDING)),
        )

        assertTrue(view.isMovie)
        assertEquals("Movie", view.title)
        assertEquals("Movie overview", view.overview)
        assertEquals("2024-01-01", view.releaseDate)
        assertEquals(120, view.runtime)
        assertEquals("im_movie", view.imdbId)
        // mediaInfo is movie-first — the request button state reads this arm.
        assertEquals(SeerrMediaStatus.AVAILABLE, view.mediaInfo?.status)
    }

    @Test
    fun `both present - tv aggregate cast wins over movie credits`() {
        val view = SeerrDetailView.of(
            movie = movie(credits = SeerrCredits(cast = listOf(SeerrCast(id = 1, name = "Movie Actor", character = "M")))),
            tv = tv(
                aggregateCredits = SeerrAggregateCredits(
                    cast = listOf(SeerrAggregateCast(id = 2, name = "Tv Actor", roles = listOf(SeerrRole(character = "T")))),
                ),
            ),
        )

        assertEquals(listOf(SeerrCastMember(2, "Tv Actor", "T", null)), view.cast)
    }

    @Test
    fun `tv with null aggregateCredits falls through to movie credits`() {
        val view = SeerrDetailView.of(
            movie = movie(credits = SeerrCredits(cast = listOf(SeerrCast(id = 1, name = "Movie Actor", character = "M")))),
            tv = tv(aggregateCredits = null),
        )

        assertEquals(listOf(SeerrCastMember(1, "Movie Actor", "M", null)), view.cast)
    }

    @Test
    fun `tv with an EMPTY aggregate cast does not fall through to movie credits`() {
        // The former ladder's exact elvis shape: the mapped empty list is
        // non-null, so the elvis never fires — an empty tv cast stays empty
        // even when a movie detail carries credits.
        val view = SeerrDetailView.of(
            movie = movie(credits = SeerrCredits(cast = listOf(SeerrCast(id = 1, name = "Movie Actor", character = "M")))),
            tv = tv(aggregateCredits = SeerrAggregateCredits(cast = emptyList())),
        )

        assertTrue(view.cast.isEmpty())
    }

    @Test
    fun `both present - tv-only fields still read the tv leg`() {
        val view = SeerrDetailView.of(
            movie = movie(),
            tv = tv(
                seasons = listOf(SeerrSeason(seasonNumber = 2)),
                externalIds = SeerrExternalIds(tvdbId = 5),
                contentRatings = listOf(SeerrContentRating(iso31661 = "US", rating = "TV-PG")),
            ),
        )

        assertEquals(listOf(SeerrSeason(seasonNumber = 2)), view.seasons)
        assertEquals(5, view.tvdbId)
        assertEquals("TV-PG", view.usContentRating)
    }

    // ── Neither present: the loading-gate defaults ─────────────────────

    @Test
    fun `neither present folds to the former ladder defaults`() {
        val view = SeerrDetailView.of(movie = null, tv = null)

        // The full table — the loading gate can compose the content with both
        // legs null, and it must render exactly what the ladders rendered.
        assertEquals(
            SeerrDetailView(
                isMovie = false,
                backdropUrl = null,
                posterUrl = null,
                title = "",
                tagline = null,
                overview = "",
                keywords = emptyList(),
                relatedVideos = emptyList(),
                mediaInfo = null,
                cast = emptyList(),
                seasons = emptyList(),
                seasonsPosterUrl = null,
                tvdbId = null,
                usContentRating = null,
                tmdbId = 0,
                imdbId = null,
                releaseDate = null,
                runtime = null,
                genres = emptyList(),
                status = null,
                language = null,
                productionCountries = emptyList(),
                studios = emptyList(),
                watchProviders = emptyList(),
                releases = emptyList(),
                revenue = null,
                budget = null,
            ),
            view,
        )
    }

    // ── usContentRating arm ────────────────────────────────────────────

    @Test
    fun `usContentRating picks the US row and ignores other regions`() {
        val view = SeerrDetailView.of(
            movie = null,
            tv = tv(contentRatings = listOf(
                SeerrContentRating(iso31661 = "GB", rating = "15"),
                SeerrContentRating(iso31661 = "US", rating = "TV-14"),
            )),
        )
        assertEquals("TV-14", view.usContentRating)

        val noUs = SeerrDetailView.of(
            movie = null,
            tv = tv(contentRatings = listOf(SeerrContentRating(iso31661 = "GB", rating = "15"))),
        )
        assertNull(noUs.usContentRating)
    }

    // ── Studios fold (companies vs networks) ───────────────────────────

    @Test
    fun `studios fold reads companies on the movie leg and networks on the tv leg`() {
        val movieView = SeerrDetailView.of(
            movie = movie().copy(productionCompanies = listOf(SeerrProductionCompany(name = "Studio A"))),
            tv = null,
        )
        assertEquals(listOf("Studio A"), movieView.studios)

        val tvView = SeerrDetailView.of(
            movie = null,
            tv = tv().copy(networks = listOf(SeerrProductionCompany(name = "Network N"))),
        )
        assertEquals(listOf("Network N"), tvView.studios)

        val both = SeerrDetailView.of(
            movie = movie().copy(productionCompanies = listOf(SeerrProductionCompany(name = "Studio A"))),
            tv = tv().copy(networks = listOf(SeerrProductionCompany(name = "Network N"))),
        )
        assertEquals(listOf("Studio A"), both.studios)
    }

    // ── Keyword/video list folds ───────────────────────────────────────

    @Test
    fun `keywords and related videos fold movie-first`() {
        val view = SeerrDetailView.of(
            movie = movie().copy(
                keywords = listOf(SeerrKeyword(1, "space")),
                relatedVideos = listOf(SeerrRelatedVideo(key = "m", site = "YouTube")),
            ),
            tv = tv().copy(
                keywords = listOf(SeerrKeyword(2, "ship")),
                relatedVideos = listOf(SeerrRelatedVideo(key = "t", site = "YouTube")),
            ),
        )

        assertEquals(listOf(SeerrKeyword(1, "space")), view.keywords)
        assertEquals(listOf(SeerrRelatedVideo(key = "m", site = "YouTube")), view.relatedVideos)
    }

    // ── toSearchItem mapping (core:model, request-dialog handoff) ──────

    @Test
    fun `movie toSeerrSearchItem maps the dialog fields`() {
        val mediaInfo = SeerrMediaInfo(status = SeerrMediaStatus.AVAILABLE)
        val item = movie(mediaInfo = mediaInfo).toSeerrSearchItem()

        assertEquals(42, item.id)
        assertEquals("movie", item.mediaType)
        assertEquals("Movie", item.title)
        assertEquals("Movie overview", item.overview)
        assertEquals("/m-poster.jpg", item.posterPath)
        assertEquals("2024-01-01", item.releaseDate)
        assertEquals(mediaInfo, item.mediaInfo)
        // The movie shape fills only its own columns — name/firstAirDate stay
        // null so displayName picks the title.
        assertNull(item.name)
        assertNull(item.firstAirDate)
    }

    @Test
    fun `tv toSeerrSearchItem maps the dialog fields`() {
        val mediaInfo = SeerrMediaInfo(status = SeerrMediaStatus.PENDING)
        val item = tv(mediaInfo = mediaInfo).toSeerrSearchItem()

        assertEquals(7, item.id)
        assertEquals("tv", item.mediaType)
        assertEquals("Show", item.name)
        assertEquals("Show overview", item.overview)
        assertEquals("/t-poster.jpg", item.posterPath)
        assertEquals("2020-05-06", item.firstAirDate)
        assertEquals(mediaInfo, item.mediaInfo)
        assertNull(item.title)
        assertNull(item.releaseDate)
    }
}
