package com.raulshma.jellyplay.core.data.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class SeerrRequestStateHolder(
    private val scope: CoroutineScope,
    private val delegate: SeerrRequestDelegate,
) {
    private val _requestResult = MutableStateFlow<SeerrRequestResult?>(null)
    val requestResult: StateFlow<SeerrRequestResult?> = _requestResult.asStateFlow()

    private val _radarrServers = MutableStateFlow<List<SeerrRadarrServiceDetail>>(emptyList())
    val radarrServers: StateFlow<List<SeerrRadarrServiceDetail>> = _radarrServers.asStateFlow()

    private val _sonarrServers = MutableStateFlow<List<SeerrSonarrServiceDetail>>(emptyList())
    val sonarrServers: StateFlow<List<SeerrSonarrServiceDetail>> = _sonarrServers.asStateFlow()

    private val _isLoadingServices = MutableStateFlow(false)
    val isLoadingServices: StateFlow<Boolean> = _isLoadingServices.asStateFlow()

    private val _tvSeasons = MutableStateFlow<List<SeerrSeason>>(emptyList())
    val tvSeasons: StateFlow<List<SeerrSeason>> = _tvSeasons.asStateFlow()

    /** True when the current TV item is anime (TMDB keyword 210024), driving anime request defaults. */
    private val _tvIsAnime = MutableStateFlow(false)
    val tvIsAnime: StateFlow<Boolean> = _tvIsAnime.asStateFlow()

    /**
     * All six state flows combined into one [SeerrRequestSnapshot] emission so
     * a consumer folds ONE flow into its ui state. Concurrent emissions
     * (e.g. loading services + seasons firing together when the request
     * dialog opens) coalesce into a single update instead of six
     * back-to-back ones. Cold on purpose: [stateIn]-ing here would pin a
     * never-ending child coroutine onto whatever scope constructed the
     * holder (breaks `runTest` scopes); consumers `stateIn`/collect on their
     * own scope. Data-class equality plus [distinctUntilChanged] dedupes
     * combinations that carry no change.
     */
    val snapshot: Flow<SeerrRequestSnapshot> =
        combine(
            combine(_requestResult, _radarrServers, _sonarrServers) { result, radarr, sonarr ->
                Triple(result, radarr, sonarr)
            },
            _isLoadingServices,
            _tvSeasons,
            _tvIsAnime,
        ) { (result, radarr, sonarr), loading, seasons, isAnime ->
            SeerrRequestSnapshot(
                requestResult = result,
                radarrServers = radarr,
                sonarrServers = sonarr,
                isLoadingServices = loading,
                tvSeasons = seasons,
                tvIsAnime = isAnime,
            )
        }.distinctUntilChanged()

    fun requestMedia(
        item: SeerrSearchItem,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ) {
        scope.launch {
            _requestResult.value = SeerrRequestResult(isLoading = true)
            delegate.requestMedia(
                mediaType = item.mediaType,
                tmdbId = item.id,
                seasons = seasons,
                serverId = serverId,
                profileId = profileId,
                rootFolder = rootFolder,
                tags = tags,
            ).onSuccess {
                _requestResult.value = SeerrRequestResult(success = true)
            }.onFailure {
                _requestResult.value = SeerrRequestResult(error = it.message ?: "Request failed")
            }
        }
    }

    fun clearRequestResult() {
        _requestResult.value = null
    }

    fun setRequestResult(result: SeerrRequestResult?) {
        _requestResult.value = result
    }

    fun prefetchDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit = {}) {
        scope.launch {
            delegate.prefetchDetails(tmdbId, mediaType)
            onDone()
        }
    }

    fun loadServiceDetails(mediaType: String) {
        scope.launch {
            _isLoadingServices.value = true
            try {
                val result = delegate.fetchServiceDetails(mediaType)
                _radarrServers.value = result.radarrServers
                _sonarrServers.value = result.sonarrServers
            } finally {
                _isLoadingServices.value = false
            }
        }
    }

    fun loadTvSeasons(tmdbId: Int) {
        scope.launch {
            _tvSeasons.value = emptyList()
            _tvIsAnime.value = false
            val tvDetails = delegate.fetchTvDetails(tmdbId)
            _tvSeasons.value = tvDetails
                ?.seasons?.filter { it.seasonNumber > 0 }
                ?: emptyList()
            _tvIsAnime.value = tvDetails?.isAnime == true
        }
    }
}
