package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrAggregateCredits
import com.raulshma.jellyplay.core.model.seerr.SeerrCollection
import com.raulshma.jellyplay.core.model.seerr.SeerrContentRating
import com.raulshma.jellyplay.core.model.seerr.SeerrCredits
import com.raulshma.jellyplay.core.model.seerr.SeerrCreator
import com.raulshma.jellyplay.core.model.seerr.SeerrDownloadStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrExternalIds
import com.raulshma.jellyplay.core.model.seerr.SeerrGenre
import com.raulshma.jellyplay.core.model.seerr.SeerrKeyword
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaInfo
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrProductionCompany
import com.raulshma.jellyplay.core.model.seerr.SeerrProductionCountry
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrReleaseDate
import com.raulshma.jellyplay.core.model.seerr.SeerrReleaseDateRegion
import com.raulshma.jellyplay.core.model.seerr.SeerrReleaseDateType
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestMedia
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestPage
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSpokenLanguage
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrUser
import com.raulshma.jellyplay.core.model.seerr.SeerrWatchProvider
import com.raulshma.jellyplay.core.model.seerr.SeerrWatchProviderRegion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The internal wire-DTO set of the Seerr client — the TMDB/Overseerr shapes
 * EXACTLY as they decode, with everything the read models interpret kept raw
 * here: the search/request page envelopes, the raw `status: Int` columns and
 * TMDB release-date `type` ints (interpreted via [SeerrMediaStatus]/
 * [SeerrRequestStatus]/[SeerrReleaseDateType] at the mapper tail), the
 * TMDB-only `genreIds`, the wire-named watch-provider/release-date region
 * families (`iso_3166_1`, `flatrate`, `release_dates`), and the nested
 * payload envelopes (`contentRatings.results`, `releases.results`). Nested
 * types with no wire gap reuse the core/model types directly, so only the
 * interpreting edge is mirrored. The single mapper tail at the bottom of this
 * file is the ONLY place wire ints become enums and envelopes fold — the
 * `JellyfinDtoMappers` pattern for this seam (one internal file, decode twins
 * + mappers together). The jvmShared impl decodes THESE through its lenient
 * Json and maps before returning.
 */

// ── search / discover / trending / recommendations / similar ────────────────

@Serializable
internal data class WireSeerrSearchResponse(
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val results: List<WireSeerrSearchItem> = emptyList(),
) {
    /** The envelope fold: the read side takes the items (no consumer reads the page totals — paging rides the request params). */
    internal fun toModels(): List<SeerrSearchItem> =
        results.map { it.toModel() }
}

@Serializable
internal data class WireSeerrSearchItem(
    val id: Int,
    val mediaType: String = "",
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val voteAverage: Float? = null,
    val voteCount: Int? = null,
    /** TMDB genre ids — the wire's discover-filter vocabulary; no read-side consumer, so it does not cross the seam. */
    val genreIds: List<Int> = emptyList(),
    val popularity: Float? = null,
    val originalLanguage: String? = null,
    val originalTitle: String? = null,
    val originalName: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val adult: Boolean = false,
    val mediaInfo: WireSeerrMediaInfo? = null,
) {
    internal fun toModel(): SeerrSearchItem =
        SeerrSearchItem(
            id = id,
            mediaType = mediaType,
            title = title,
            name = name,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            voteAverage = voteAverage,
            voteCount = voteCount,
            popularity = popularity,
            originalLanguage = originalLanguage,
            originalTitle = originalTitle,
            originalName = originalName,
            releaseDate = releaseDate,
            firstAirDate = firstAirDate,
            adult = adult,
            mediaInfo = mediaInfo?.toModel(),
        )
}

// ── media info / request (the mutually recursive pair) ──────────────────────

@Serializable
internal data class WireSeerrMediaInfo(
    val id: Int = 0,
    val tmdbId: Int = 0,
    val tvdbId: Int? = null,
    val status: Int = 0,
    val requests: List<WireSeerrMediaRequest> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    internal fun toModel(): SeerrMediaInfo =
        SeerrMediaInfo(
            id = id,
            tmdbId = tmdbId,
            tvdbId = tvdbId,
            status = SeerrMediaStatus.fromValue(status),
            requests = requests.map { it.toModel() },
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}

@Serializable
internal data class WireSeerrMediaRequest(
    val id: Int = 0,
    val status: Int = 0,
    val media: WireSeerrMediaInfo? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val requestedBy: SeerrUser? = null,
    val modifiedBy: SeerrUser? = null,
    val is4k: Boolean = false,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
) {
    internal fun toModel(): SeerrMediaRequest =
        SeerrMediaRequest(
            id = id,
            status = SeerrRequestStatus.fromValue(status),
            media = media?.toModel(),
            createdAt = createdAt,
            updatedAt = updatedAt,
            requestedBy = requestedBy,
            modifiedBy = modifiedBy,
            is4k = is4k,
            serverId = serverId,
            profileId = profileId,
            rootFolder = rootFolder,
        )
}

// ── the requests list page ──────────────────────────────────────────────────

@Serializable
internal data class WireSeerrRequestListResponse(
    val pageInfo: WireSeerrPageInfo = WireSeerrPageInfo(),
    val results: List<WireSeerrRequestItem> = emptyList(),
) {
    /** The envelope fold: items + the two paging totals, flattened (no nested page-info on the read model). */
    internal fun toModel(): SeerrRequestPage =
        SeerrRequestPage(
            items = results.map { it.toModel() },
            totalResults = pageInfo.results,
            totalPages = pageInfo.pages,
        )
}

@Serializable
internal data class WireSeerrPageInfo(
    val pages: Int = 0,
    val results: Int = 0,
)

@Serializable
internal data class WireSeerrRequestItem(
    val id: Int = 0,
    val status: Int = 0,
    val type: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val media: WireSeerrRequestMedia = WireSeerrRequestMedia(),
    val requestedBy: SeerrUser = SeerrUser(),
    val modifiedBy: SeerrUser? = null,
    val is4k: Boolean = false,
    val canRemove: Boolean = false,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val profileName: String? = null,
    val rootFolder: String? = null,
    val seasons: List<SeerrRequestSeason> = emptyList(),
) {
    internal fun toModel(): SeerrRequestItem =
        SeerrRequestItem(
            id = id,
            status = SeerrRequestStatus.fromValue(status),
            type = type,
            createdAt = createdAt,
            updatedAt = updatedAt,
            media = media.toModel(),
            requestedBy = requestedBy,
            modifiedBy = modifiedBy,
            is4k = is4k,
            canRemove = canRemove,
            serverId = serverId,
            profileId = profileId,
            profileName = profileName,
            rootFolder = rootFolder,
            seasons = seasons,
        )
}

@Serializable
internal data class WireSeerrRequestMedia(
    val id: Int = 0,
    val tmdbId: Int = 0,
    val tvdbId: Int? = null,
    val status: Int = 0,
    val status4k: Int = 0,
    val mediaUrl: String? = null,
    val serviceUrl: String? = null,
    val downloadStatus: List<SeerrDownloadStatus> = emptyList(),
    val downloadStatus4k: List<SeerrDownloadStatus> = emptyList(),
) {
    internal fun toModel(): SeerrRequestMedia =
        SeerrRequestMedia(
            id = id,
            tmdbId = tmdbId,
            tvdbId = tvdbId,
            status = SeerrMediaStatus.fromValue(status),
            status4k = SeerrMediaStatus.fromValue(status4k),
            mediaUrl = mediaUrl,
            serviceUrl = serviceUrl,
            downloadStatus = downloadStatus,
            downloadStatus4k = downloadStatus4k,
        )
}

// ── movie / tv details (only the interpreting edge mirrored) ────────────────

@Serializable
internal data class WireSeerrMovieDetails(
    val id: Int = 0,
    val imdbId: String? = null,
    val adult: Boolean = false,
    val backdropPath: String? = null,
    val posterPath: String? = null,
    val budget: Long? = null,
    val genres: List<SeerrGenre> = emptyList(),
    val homepage: String? = null,
    val originalLanguage: String? = null,
    val originalTitle: String? = null,
    val overview: String? = null,
    val popularity: Float? = null,
    val productionCompanies: List<SeerrProductionCompany> = emptyList(),
    val productionCountries: List<SeerrProductionCountry> = emptyList(),
    val releaseDate: String? = null,
    val digitalReleaseDate: String? = null,
    val revenue: Long? = null,
    val runtime: Int? = null,
    val spokenLanguages: List<SeerrSpokenLanguage> = emptyList(),
    val status: String? = null,
    val tagline: String? = null,
    val title: String = "",
    val video: Boolean = false,
    val voteAverage: Float? = null,
    val voteCount: Int? = null,
    val credits: SeerrCredits? = null,
    val collection: SeerrCollection? = null,
    val externalIds: SeerrExternalIds? = null,
    val mediaInfo: WireSeerrMediaInfo? = null,
    val relatedVideos: List<SeerrRelatedVideo> = emptyList(),
    val ratings: SeerrRatings? = null,
    val keywords: List<SeerrKeyword> = emptyList(),
    val watchProviders: List<WireSeerrWatchProviderRegion> = emptyList(),
    val releases: WireSeerrReleases? = null,
) {
    internal fun toModel(): SeerrMovieDetails =
        SeerrMovieDetails(
            id = id,
            imdbId = imdbId,
            adult = adult,
            backdropPath = backdropPath,
            posterPath = posterPath,
            budget = budget,
            genres = genres,
            homepage = homepage,
            originalLanguage = originalLanguage,
            originalTitle = originalTitle,
            overview = overview,
            popularity = popularity,
            productionCompanies = productionCompanies,
            productionCountries = productionCountries,
            releaseDate = releaseDate,
            digitalReleaseDate = digitalReleaseDate,
            revenue = revenue,
            runtime = runtime,
            spokenLanguages = spokenLanguages,
            status = status,
            tagline = tagline,
            title = title,
            video = video,
            voteAverage = voteAverage,
            voteCount = voteCount,
            credits = credits,
            collection = collection,
            externalIds = externalIds,
            mediaInfo = mediaInfo?.toModel(),
            relatedVideos = relatedVideos,
            ratings = ratings,
            keywords = keywords,
            watchProviders = watchProviders.map { it.toModel() },
            releases = releases?.results.orEmpty().map { it.toModel() },
        )
}

@Serializable
internal data class WireSeerrReleases(
    val results: List<WireSeerrReleaseDateRegion> = emptyList(),
)

@Serializable
internal data class WireSeerrTvDetails(
    val id: Int = 0,
    val backdropPath: String? = null,
    val posterPath: String? = null,
    val createdBy: List<SeerrCreator> = emptyList(),
    val episodeRunTime: List<Int> = emptyList(),
    val firstAirDate: String? = null,
    val genres: List<SeerrGenre> = emptyList(),
    val homepage: String? = null,
    val inProduction: Boolean = false,
    val languages: List<String> = emptyList(),
    val lastAirDate: String? = null,
    val name: String = "",
    val numberOfEpisodes: Int = 0,
    val numberOfSeasons: Int = 0,
    val originCountry: List<String> = emptyList(),
    val originalLanguage: String? = null,
    val originalName: String? = null,
    val overview: String? = null,
    val popularity: Float? = null,
    val productionCompanies: List<SeerrProductionCompany> = emptyList(),
    val seasons: List<SeerrSeason> = emptyList(),
    val status: String? = null,
    val tagline: String? = null,
    val voteAverage: Float? = null,
    val voteCount: Int? = null,
    val credits: SeerrCredits? = null,
    val aggregateCredits: SeerrAggregateCredits? = null,
    val externalIds: SeerrExternalIds? = null,
    val mediaInfo: WireSeerrMediaInfo? = null,
    val networks: List<SeerrProductionCompany> = emptyList(),
    val ratings: SeerrRatings? = null,
    val keywords: List<SeerrKeyword> = emptyList(),
    val relatedVideos: List<SeerrRelatedVideo> = emptyList(),
    val watchProviders: List<WireSeerrWatchProviderRegion> = emptyList(),
    val contentRatings: WireSeerrContentRatingsResponse? = null,
) {
    internal fun toModel(): SeerrTvDetails =
        SeerrTvDetails(
            id = id,
            backdropPath = backdropPath,
            posterPath = posterPath,
            createdBy = createdBy,
            episodeRunTime = episodeRunTime,
            firstAirDate = firstAirDate,
            genres = genres,
            homepage = homepage,
            inProduction = inProduction,
            languages = languages,
            lastAirDate = lastAirDate,
            name = name,
            numberOfEpisodes = numberOfEpisodes,
            numberOfSeasons = numberOfSeasons,
            originCountry = originCountry,
            originalLanguage = originalLanguage,
            originalName = originalName,
            overview = overview,
            popularity = popularity,
            productionCompanies = productionCompanies,
            seasons = seasons,
            status = status,
            tagline = tagline,
            voteAverage = voteAverage,
            voteCount = voteCount,
            credits = credits,
            aggregateCredits = aggregateCredits,
            externalIds = externalIds,
            mediaInfo = mediaInfo?.toModel(),
            networks = networks,
            ratings = ratings,
            keywords = keywords,
            relatedVideos = relatedVideos,
            watchProviders = watchProviders.map { it.toModel() },
            contentRatings = contentRatings?.results ?: emptyList(),
        )
}

@Serializable
internal data class WireSeerrContentRatingsResponse(
    val results: List<SeerrContentRating> = emptyList(),
)

// ── watch providers / release dates (the TMDB region families) ──────────────

@Serializable
internal data class WireSeerrWatchProviderRegion(
    @SerialName("iso_3166_1")
    val iso31661: String = "",
    val link: String? = null,
    val flatrate: List<SeerrWatchProvider> = emptyList(),
    val buy: List<SeerrWatchProvider> = emptyList(),
    val rent: List<SeerrWatchProvider> = emptyList(),
) {
    /** The seam rename: the wire's `flatrate` column is the read model's streaming list. */
    internal fun toModel(): SeerrWatchProviderRegion =
        SeerrWatchProviderRegion(
            iso31661 = iso31661,
            link = link,
            streaming = flatrate,
            buy = buy,
            rent = rent,
        )
}

@Serializable
internal data class WireSeerrReleaseDateRegion(
    @SerialName("iso_3166_1")
    val iso31661: String = "",
    @SerialName("release_dates")
    val releaseDates: List<WireSeerrReleaseDate> = emptyList(),
) {
    internal fun toModel(): SeerrReleaseDateRegion =
        SeerrReleaseDateRegion(
            iso31661 = iso31661,
            releaseDates = releaseDates.map { it.toModel() },
        )
}

@Serializable
internal data class WireSeerrReleaseDate(
    val certification: String = "",
    @SerialName("release_date")
    val releaseDate: String = "",
    val type: Int = 0,
    val note: String? = null,
) {
    internal fun toModel(): SeerrReleaseDate =
        SeerrReleaseDate(
            certification = certification,
            releaseDate = releaseDate,
            type = SeerrReleaseDateType.fromValue(type),
            note = note,
        )
}

// The season detail carries no interpreting edge (no status ints, no
// envelopes beyond its own list-typed fields) — the impl decodes
// SeerrSeasonDetail straight through and this seam maps nothing for it.
