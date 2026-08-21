package com.raulshma.jellyplay.core.model.seerr

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SeerrSearchResponse(
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val results: List<SeerrSearchItem> = emptyList(),
)

@Immutable
@Serializable
data class SeerrSearchItem(
    val id: Int,
    val mediaType: String = "",
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val voteAverage: Float? = null,
    val voteCount: Int? = null,
    val genreIds: List<Int> = emptyList(),
    val popularity: Float? = null,
    val originalLanguage: String? = null,
    val originalTitle: String? = null,
    val originalName: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val adult: Boolean = false,
    val mediaInfo: SeerrMediaInfo? = null,
) {
    val displayName: String get() = title ?: name ?: ""
    val year: Int?
        get() = try {
            (releaseDate ?: firstAirDate)?.take(4)?.toInt()
        } catch (_: Exception) { null }

    val posterUrl: String? = buildPosterUrl(posterPath)
    val backdropUrl: String? = buildBackdropUrl(backdropPath)
}

@Immutable
@Serializable
data class SeerrMediaInfo(
    val id: Int = 0,
    val tmdbId: Int = 0,
    val tvdbId: Int? = null,
    val status: Int = 0,
    val requests: List<SeerrMediaRequest> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Immutable
@Serializable
data class SeerrMediaRequest(
    val id: Int = 0,
    val status: Int = 0,
    val media: SeerrMediaInfo? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val requestedBy: SeerrUser? = null,
    val modifiedBy: SeerrUser? = null,
    val is4k: Boolean = false,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
)

@Immutable
@Serializable
data class SeerrUser(
    val id: Int = 0,
    val email: String = "",
    val username: String? = null,
    val avatar: String? = null,
    val permissions: Long = 0,
)

@Immutable
@Serializable
data class SeerrMovieDetails(
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
    val mediaInfo: SeerrMediaInfo? = null,
    val relatedVideos: List<SeerrRelatedVideo> = emptyList(),
    val ratings: SeerrRatings? = null,
    val keywords: List<SeerrKeyword> = emptyList(),
    val watchProviders: List<SeerrWatchProviderRegion> = emptyList(),
    val releases: SeerrReleases? = null,
) {
    val posterUrl: String? = buildPosterUrl(posterPath)
    val backdropUrl: String? = buildBackdropUrl(backdropPath)
}

@Immutable
@Serializable
data class SeerrTvDetails(
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
    val mediaInfo: SeerrMediaInfo? = null,
    val networks: List<SeerrProductionCompany> = emptyList(),
    val ratings: SeerrRatings? = null,
    val keywords: List<SeerrKeyword> = emptyList(),
    val relatedVideos: List<SeerrRelatedVideo> = emptyList(),
    val watchProviders: List<SeerrWatchProviderRegion> = emptyList(),
    val contentRatings: SeerrContentRatingsResponse? = null,
) {
    val posterUrl: String? = buildPosterUrl(posterPath)
    val backdropUrl: String? = buildBackdropUrl(backdropPath)

    /**
     * TMDB keyword 210024 ("anime") — the same signal Overseerr/Jellyseerr use
     * to switch request defaults to the per-server anime profile/directory/tags.
     */
    val isAnime: Boolean get() = keywords.any { it.id == SEERR_ANIME_KEYWORD_ID }
}

/** TMDB keyword id Jellyseerr treats as the anime marker. */
const val SEERR_ANIME_KEYWORD_ID = 210024

@Immutable
@Serializable
data class SeerrContentRatingsResponse(
    val results: List<SeerrContentRating> = emptyList(),
)

@Immutable
@Serializable
data class SeerrContentRating(
    val iso31661: String = "",
    val rating: String = "",
)

@Immutable
@Serializable
data class SeerrReleases(
    val results: List<SeerrReleaseDateRegion> = emptyList(),
)

@Immutable
@Serializable
data class SeerrReleaseDateRegion(
    @SerialName("iso_3166_1")
    val iso31661: String = "",
    @SerialName("release_dates")
    val releaseDates: List<SeerrReleaseDate> = emptyList(),
)

@Immutable
@Serializable
data class SeerrReleaseDate(
    val certification: String = "",
    @SerialName("release_date")
    val releaseDate: String = "",
    val type: Int = 0,
    val note: String? = null,
)

@Immutable
@Serializable
data class SeerrKeyword(
    val id: Int = 0,
    val name: String = "",
)

@Immutable
@Serializable
data class SeerrWatchProviderRegion(
    @SerialName("iso_3166_1")
    val iso31661: String = "",
    val link: String? = null,
    val flatrate: List<SeerrWatchProvider> = emptyList(),
    val buy: List<SeerrWatchProvider> = emptyList(),
    val rent: List<SeerrWatchProvider> = emptyList(),
)

@Immutable
@Serializable
data class SeerrWatchProvider(
    val displayPriority: Int = 0,
    val logoPath: String? = null,
    val id: Int = 0,
    val name: String = "",
)

@Immutable
@Serializable
data class SeerrAggregateCredits(
    val cast: List<SeerrAggregateCast> = emptyList(),
    val crew: List<SeerrAggregateCrew> = emptyList(),
)

@Immutable
@Serializable
data class SeerrAggregateCast(
    val id: Int = 0,
    val name: String = "",
    val originalName: String? = null,
    val profilePath: String? = null,
    val roles: List<SeerrRole> = emptyList(),
    val totalEpisodeCount: Int = 0,
    val order: Int = 0,
) {
    val profileUrl: String? = buildProfileUrl(profilePath)
}

@Immutable
@Serializable
data class SeerrAggregateCrew(
    val id: Int = 0,
    val name: String = "",
    val originalName: String? = null,
    val profilePath: String? = null,
    val jobs: List<SeerrJob> = emptyList(),
    val totalEpisodeCount: Int = 0,
    val department: String? = null,
) {
    val profileUrl: String? = buildProfileUrl(profilePath)
}

@Immutable
@Serializable
data class SeerrRole(
    val creditId: String = "",
    val character: String = "",
    val episodeCount: Int = 0,
)

@Immutable
@Serializable
data class SeerrJob(
    val creditId: String = "",
    val job: String = "",
    val episodeCount: Int = 0,
)

@Immutable
@Serializable
data class SeerrProductionCountry(
    val iso31661: String = "",
    val name: String = "",
)

@Immutable
@Serializable
data class SeerrRatings(
    val imdb: SeerrImdbRating? = null,
    val rt: SeerrRtRating? = null,
    val tmdb: SeerrTmdbRating? = null,
)

@Immutable
@Serializable
data class SeerrTmdbRating(
    val title: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    val url: String? = null,
)

@Immutable
@Serializable
data class SeerrImdbRating(
    val title: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    val url: String? = null,
    val criticsScore: Float? = null,
    val criticsScoreCount: Int? = null,
)

@Immutable
@Serializable
data class SeerrRtRating(
    val title: String? = null,
    val criticsRating: String? = null,
    val criticsScore: Int? = null,
    val audienceRating: String? = null,
    val audienceScore: Int? = null,
    val url: String? = null,
)

@Immutable
@Serializable
data class SeerrGenre(
    val id: Int = 0,
    val name: String = "",
)

@Immutable
@Serializable
data class SeerrProductionCompany(
    val id: Int = 0,
    val name: String = "",
    val logoPath: String? = null,
    val originCountry: String? = null,
)

@Immutable
@Serializable
data class SeerrSpokenLanguage(
    val englishName: String? = null,
    val iso6391: String = "",
    val name: String = "",
)

@Immutable
@Serializable
data class SeerrCredits(
    val cast: List<SeerrCast> = emptyList(),
    val crew: List<SeerrCrew> = emptyList(),
)

@Immutable
@Serializable
data class SeerrCast(
    val id: Int = 0,
    val castId: Int = 0,
    val character: String? = null,
    val creditId: String? = null,
    val gender: Int = 0,
    val name: String = "",
    val order: Int = 0,
    val profilePath: String? = null,
) {
    val profileUrl: String? = buildProfileUrl(profilePath)
}

@Immutable
@Serializable
data class SeerrCrew(
    val id: Int = 0,
    val creditId: String? = null,
    val gender: Int = 0,
    val name: String = "",
    val job: String? = null,
    val department: String? = null,
    val profilePath: String? = null,
) {
    val profileUrl: String? = buildProfileUrl(profilePath)
}

@Immutable
@Serializable
data class SeerrCollection(
    val id: Int = 0,
    val name: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
)

@Immutable
@Serializable
data class SeerrExternalIds(
    val facebookId: String? = null,
    val imdbId: String? = null,
    val instagramId: String? = null,
    val tvdbId: Int? = null,
    val twitterId: String? = null,
)

@Immutable
@Serializable
data class SeerrSeason(
    val id: Int = 0,
    val airDate: String? = null,
    val episodeCount: Int = 0,
    val name: String = "",
    val overview: String? = null,
    val posterPath: String? = null,
    val seasonNumber: Int = 0,
) {
    val posterUrl: String? = buildPosterUrl(posterPath)
}

@Immutable
@Serializable
data class SeerrSeasonDetail(
    val id: Int = 0,
    val airDate: String? = null,
    val name: String = "",
    val overview: String? = null,
    val posterPath: String? = null,
    val seasonNumber: Int = 0,
    val episodes: List<SeerrEpisode> = emptyList(),
)

@Immutable
@Serializable
data class SeerrEpisode(
    val id: Int = 0,
    val name: String = "",
    val overview: String? = null,
    val episodeNumber: Int = 0,
    val seasonNumber: Int = 0,
    val airDate: String? = null,
    val stillPath: String? = null,
    val runtime: Int? = null,
    val voteAverage: Float? = null,
    val voteCount: Int = 0,
    val crew: List<SeerrEpisodeCrew> = emptyList(),
    val guestStars: List<SeerrEpisodeGuestStar> = emptyList(),
) {
    val stillUrl: String? get() = buildStillUrl(stillPath)
}

@Immutable
@Serializable
data class SeerrEpisodeCrew(
    val id: Int = 0,
    val name: String = "",
    val job: String? = null,
    val department: String? = null,
    val profilePath: String? = null,
)

@Immutable
@Serializable
data class SeerrEpisodeGuestStar(
    val id: Int = 0,
    val name: String = "",
    val character: String? = null,
    val profilePath: String? = null,
)

@Immutable
@Serializable
data class SeerrCreator(
    val id: Int = 0,
    val name: String = "",
    val gender: Int = 0,
    val profilePath: String? = null,
)

@Immutable
@Serializable
data class SeerrRelatedVideo(
    val url: String? = null,
    val key: String? = null,
    val name: String? = null,
    val size: Int = 0,
    val type: String? = null,
    val site: String? = null,
)

@Immutable
@Serializable
data class SeerrStatusResponse(
    val version: String = "",
    val commitTag: String? = null,
    val updateAvailable: Boolean = false,
    val commitsBehind: Int = 0,
    val restartRequired: Boolean = false,
)

@Immutable
@Serializable
data class SeerrRadarrSettings(
    val id: Int,
    val name: String,
    val hostname: String,
    val port: Int,
    val apiKey: String,
    val useSsl: Boolean = false,
    val baseUrl: String? = null,
    val activeProfileId: Int? = null,
    val activeDirectory: String? = null,
    val is4k: Boolean = false,
    val isDefault: Boolean = false,
    val externalUrl: String? = null,
) {
    fun getFullUrl(): String {
        if (!externalUrl.isNullOrBlank()) return externalUrl.trimEnd('/')
        
        val protocol = if (useSsl) "https" else "http"
        val base = baseUrl?.trim('/')?.let { if (it.isNotEmpty()) "/$it" else "" } ?: ""
        return "$protocol://$hostname:$port$base"
    }
}

@Immutable
@Serializable
data class SeerrSonarrSettings(
    val id: Int,
    val name: String,
    val hostname: String,
    val port: Int,
    val apiKey: String,
    val useSsl: Boolean = false,
    val baseUrl: String? = null,
    val activeProfileId: Int? = null,
    val activeDirectory: String? = null,
    val is4k: Boolean = false,
    val isDefault: Boolean = false,
    val externalUrl: String? = null,
) {
    fun getFullUrl(): String {
        if (!externalUrl.isNullOrBlank()) return externalUrl.trimEnd('/')
        
        val protocol = if (useSsl) "https" else "http"
        val base = baseUrl?.trim('/')?.let { if (it.isNotEmpty()) "/$it" else "" } ?: ""
        return "$protocol://$hostname:$port$base"
    }
}

@Immutable
@Serializable
data class SeerrRequestPayload(
    val mediaType: String,
    val mediaId: Int,
    val tvdbId: Int? = null,
    val seasons: List<Int>? = null,
    val is4k: Boolean = false,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val tags: List<Int>? = null,
)

@Immutable
@Serializable
data class SeerrServiceProfile(
    val id: Int = 0,
    val name: String = "",
)

@Immutable
@Serializable
data class SeerrServiceRootFolder(
    val id: Int = 0,
    val path: String = "",
    val freeSpace: Long? = null,
    val totalSpace: Long? = null,
)

@Immutable
@Serializable
data class SeerrServiceTag(
    val id: Int = 0,
    val label: String = "",
)

@Immutable
@Serializable
data class SeerrRadarrServiceDetail(
    val id: Int = 0,
    val name: String = "",
    val hostname: String = "",
    val port: Int = 0,
    val apiKey: String = "",
    val useSsl: Boolean = false,
    val baseUrl: String? = null,
    val activeProfileId: Int? = null,
    val activeDirectory: String? = null,
    val is4k: Boolean = false,
    val isDefault: Boolean = false,
    val externalUrl: String? = null,
    val profiles: List<SeerrServiceProfile> = emptyList(),
    val rootFolders: List<SeerrServiceRootFolder> = emptyList(),
    val tags: List<SeerrServiceTag> = emptyList(),
    /** Nested server object from /service/ endpoint with default settings. */
    val server: SeerrServiceServerDefaults? = null,
)

@Immutable
@Serializable
data class SeerrSonarrServiceDetail(
    val id: Int = 0,
    val name: String = "",
    val hostname: String = "",
    val port: Int = 0,
    val apiKey: String = "",
    val useSsl: Boolean = false,
    val baseUrl: String? = null,
    val activeProfileId: Int? = null,
    val activeDirectory: String? = null,
    val is4k: Boolean = false,
    val isDefault: Boolean = false,
    val externalUrl: String? = null,
    val profiles: List<SeerrServiceProfile> = emptyList(),
    val rootFolders: List<SeerrServiceRootFolder> = emptyList(),
    val tags: List<SeerrServiceTag> = emptyList(),
    /** Nested server object from /service/ endpoint with default settings. */
    val server: SeerrServiceServerDefaults? = null,
    val languageProfiles: List<SeerrServiceLanguageProfile> = emptyList(),
)

@Immutable
@Serializable
data class SeerrServiceServer(
    val id: Int = 0,
    val name: String = "",
    val isDefault: Boolean = false,
    val is4k: Boolean = false,
)

@Immutable
@Serializable
data class SeerrServiceServerDefaults(
    val id: Int = 0,
    val name: String = "",
    val isDefault: Boolean = false,
    val is4k: Boolean = false,
    val activeProfileId: Int? = null,
    val activeAnimeProfileId: Int? = null,
    val activeDirectory: String? = null,
    val activeAnimeDirectory: String? = null,
    val activeTags: List<Int> = emptyList(),
    val activeAnimeTags: List<Int> = emptyList(),
    val activeLanguageProfileId: Int? = null,
    val activeAnimeLanguageProfileId: Int? = null,
)

@Immutable
@Serializable
data class SeerrServiceLanguageProfile(
    val id: Int = 0,
    val name: String = "",
)

@Immutable
@Serializable
enum class SeerrAuthMethod {
    API_KEY,
    JELLYFIN,
    LOCAL,
}

sealed class SeerrCredentials {
    data class ApiKey(val apiKey: String) : SeerrCredentials()
    data class SessionCookie(val cookie: String) : SeerrCredentials()
}

@Immutable
@Serializable
data class SeerrAuthJellyfinRequest(
    val username: String,
    val password: String,
)

@Immutable
@Serializable
data class SeerrAuthLocalRequest(
    val email: String,
    val password: String,
)

@Immutable
@Serializable
data class SeerrPreferences(
    val serverUrl: String = "",
    val authMethod: SeerrAuthMethod = SeerrAuthMethod.API_KEY,
    val username: String = "",
    val email: String = "",
    val enabled: Boolean = false,
    val searchEnabled: Boolean = false,
    val recommendationsEnabled: Boolean = false,
    val discoverEnabled: Boolean = false,
    val discoverTrending: Boolean = true,
    val discoverPopularMovies: Boolean = true,
    val discoverPopularTv: Boolean = true,
    val discoverUpcomingMovies: Boolean = true,
    val discoverUpcomingTv: Boolean = true,
    val streamingRegion: String = "US",
    val discoverRegion: String = "US",
)

@Immutable
@Serializable
enum class DiscoverSectionType {
    TRENDING,
    POPULAR_MOVIES,
    POPULAR_TV,
    UPCOMING_MOVIES,
    UPCOMING_TV,
}

@Immutable
@Serializable
enum class SeerrMediaStatus(val value: Int) {
    UNKNOWN(1),
    PENDING(2),
    PROCESSING(3),
    PARTIALLY_AVAILABLE(4),
    AVAILABLE(5),
    DELETED(6);

    companion object {
        fun fromValue(value: Int): SeerrMediaStatus =
            entries.find { it.value == value } ?: UNKNOWN
    }
}

@Immutable
@Serializable
enum class SeerrRequestStatus(val value: Int) {
    PENDING(1),
    APPROVED(2),
    DECLINED(3),
    FAILED(4),
    COMPLETED(5);

    companion object {
        fun fromValue(value: Int): SeerrRequestStatus =
            entries.find { it.value == value } ?: PENDING
    }
}

@Immutable
@Serializable
data class SeerrRequestListResponse(
    val pageInfo: SeerrPageInfo = SeerrPageInfo(),
    val results: List<SeerrRequestItem> = emptyList(),
)

@Immutable
@Serializable
data class SeerrPageInfo(
    val pages: Int = 0,
    val results: Int = 0,
)

@Immutable
@Serializable
data class SeerrRequestItem(
    val id: Int = 0,
    val status: Int = 0,
    val type: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val media: SeerrRequestMedia = SeerrRequestMedia(),
    val requestedBy: SeerrUser = SeerrUser(),
    val modifiedBy: SeerrUser? = null,
    val is4k: Boolean = false,
    val canRemove: Boolean = false,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val profileName: String? = null,
    val rootFolder: String? = null,
    val seasons: List<SeerrRequestSeason> = emptyList(),
)

@Immutable
@Serializable
data class SeerrRequestMedia(
    val id: Int = 0,
    val tmdbId: Int = 0,
    val tvdbId: Int? = null,
    val status: Int = 0,
    val status4k: Int = 0,
    val mediaUrl: String? = null,
    val serviceUrl: String? = null,
    val downloadStatus: List<SeerrDownloadStatus> = emptyList(),
    val downloadStatus4k: List<SeerrDownloadStatus> = emptyList(),
)

@Immutable
@Serializable
data class SeerrRequestSeason(
    val id: Int = 0,
    val seasonNumber: Int = 0,
)

@Immutable
@Serializable
data class SeerrDownloadStatus(
    val externalId: String? = null,
    val status: String? = null,
)

@Immutable
@Serializable
data class SeerrRequestCount(
    val pending: Int = 0,
    val approved: Int = 0,
    val declined: Int = 0,
    val processing: Int = 0,
    val available: Int = 0,
)

@Immutable
@Serializable
data class SeerrCurrentUser(
    val id: Int = 0,
    val email: String = "",
    val username: String? = null,
    val displayName: String? = null,
    val avatar: String? = null,
    val permissions: Long = 0,
    val userType: Int = 0,
) {
    val canManageRequests: Boolean get() = isAdmin || (permissions and PERMISSION_MANAGE_REQUESTS) != 0L
    val canViewRequests: Boolean get() = isAdmin || (permissions and PERMISSION_REQUEST_VIEW) != 0L
    val canRequestAdvanced: Boolean get() = isAdmin || (permissions and PERMISSION_REQUEST_ADVANCED) != 0L
    val isAdmin: Boolean get() = (permissions and PERMISSION_ADMIN) != 0L

    companion object {
        const val PERMISSION_ADMIN = 2L
        const val PERMISSION_MANAGE_REQUESTS = 16L
        const val PERMISSION_REQUEST_VIEW = 16384L
        const val PERMISSION_REQUEST_ADVANCED = 8192L
    }
}

@Immutable
@Serializable
enum class SeerrRequestFilter(val value: String) {
    ALL("all"),
    PENDING("pending"),
    APPROVED("approved"),
    PROCESSING("processing"),
    AVAILABLE("available"),
    UNAVAILABLE("unavailable"),
    FAILED("failed"),
}

@Immutable
data class SeerrRequestResult(
    val isLoading: Boolean = false,
    val success: Boolean? = null,
    val error: String? = null,
)

@Immutable
@Serializable
enum class SeerrRequestSort(val value: String) {
    ADDED("added"),
    MODIFIED("modified"),
}

@Immutable
@Serializable
data class SeerrEditRequestPayload(
    val mediaType: String,
    val mediaId: Int,
    val is4k: Boolean = false,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val tags: List<Int>? = null,
    val seasons: List<Int>? = null,
)
