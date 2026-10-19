package com.raulshma.jellyplay.core.data.seerr

import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaKind
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.pick
import com.raulshma.jellyplay.core.model.seerr.toMediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class SeerrServiceDetailsResult(
    val radarrServers: List<SeerrRadarrServiceDetail> = emptyList(),
    val sonarrServers: List<SeerrSonarrServiceDetail> = emptyList(),
)

class SeerrRequestDelegate(
    private val seerrRepository: SeerrRepository,
) {
    suspend fun requestMedia(
        mediaType: String,
        tmdbId: Int,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ): Result<SeerrMediaRequest> = seerrRepository.requestMedia(
        mediaType = mediaType,
        tmdbId = tmdbId,
        seasons = seasons,
        serverId = serverId,
        profileId = profileId,
        rootFolder = rootFolder,
        tags = tags,
    )

    suspend fun fetchServiceDetails(mediaType: String): SeerrServiceDetailsResult = coroutineScope {
        // The kind folds through the model's SeerrMediaKind (one ignoreCase
        // parse) and forks on its [pick] — the model's one movie/TV fork,
        // same as [prefetchDetails]: the former `== "movie"` compare was
        // case-SENSITIVE, so a "Movie" payload misrouted to the sonarr leg —
        // routing through the model fixes that (declared delta of this fold).
        SeerrMediaKind.fromWire(mediaType).pick(
            movie = {
                val radarrServers = seerrRepository.getServiceRadarrServers().getOrNull()?.let { servers ->
                    servers.map { server ->
                        // The kind fold returns the sealed parent; the filter
                        // narrows back to the typed list this result carries.
                        async { seerrRepository.getServiceDetail(server.id, ArrServiceKind.RADARR).getOrNull() }
                    }.awaitAll().filterIsInstance<SeerrRadarrServiceDetail>()
                } ?: emptyList()
                SeerrServiceDetailsResult(radarrServers = radarrServers)
            },
            tv = {
                val sonarrServers = seerrRepository.getServiceSonarrServers().getOrNull()?.let { servers ->
                    servers.map { server ->
                        async { seerrRepository.getServiceDetail(server.id, ArrServiceKind.SONARR).getOrNull() }
                    }.awaitAll().filterIsInstance<SeerrSonarrServiceDetail>()
                } ?: emptyList()
                SeerrServiceDetailsResult(sonarrServers = sonarrServers)
            },
        )
    }

    suspend fun fetchTvSeasons(tmdbId: Int): List<SeerrSeason> {
        return fetchTvDetails(tmdbId)
            ?.seasons?.filter { it.seasonNumber > 0 }
            ?: emptyList()
    }

    suspend fun fetchTvDetails(tmdbId: Int): SeerrTvDetails? {
        return seerrRepository.getTvDetails(tmdbId).getOrNull()
    }

    suspend fun prefetchDetails(tmdbId: Int, mediaType: String) {
        try {
            // The kind fold + fetch fork are the model's (one ignoreCase
            // parse; the former `== "movie"` compares were case-sensitive —
            // the same declared fix as [fetchServiceDetails]).
            val kind = SeerrMediaKind.fromWire(mediaType)
            coroutineScope {
                kind.pick(
                    movie = { seerrRepository.getMovieDetails(tmdbId) },
                    tv = { seerrRepository.getTvDetails(tmdbId) },
                )
                val type = kind.toMediaType()
                launch { seerrRepository.getRatings(tmdbId, mediaType) }
                launch { seerrRepository.getRecommendations(tmdbId, type) }
                launch { seerrRepository.getSimilar(tmdbId, type) }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Never swallow cancellation — breaks structured concurrency.
            throw ce
        } catch (_: Exception) {
        }
    }
}
