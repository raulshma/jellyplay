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
)

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
)

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
data class SeerrKeyword(
    val id: Int = 0,
    val name: String = "",
)

@Immutable
@Serializable
data class SeerrWatchProviderRegion(
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
    val providerId: Int = 0,
    val providerName: String = "",
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
)

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
)

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
)

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
)

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
data class SeerrPreferences(
    val serverUrl: String = "",
    val apiKey: String = "",
    val enabled: Boolean = false,
    val searchEnabled: Boolean = false,
    val recommendationsEnabled: Boolean = false,
)

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
    PENDING_APPROVAL(1),
    APPROVED(2),
    DECLINED(3);

    companion object {
        fun fromValue(value: Int): SeerrRequestStatus =
            entries.find { it.value == value } ?: PENDING_APPROVAL
    }
}
