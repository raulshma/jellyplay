package com.raulshma.jellyplay.core.data.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrMediaRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSnapshot
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Deep module for the Seerr request lifecycle: the ONLY state it exposes is
 * the [snapshot] (per-VM `stateIn`'d via [snapshotIn]) plus the commands
 * that drive it. The individual `MutableStateFlow`s are deliberately private
 * — consumers used to hand-sync six mirror fields out of them, which every
 * new holder field forced them to re-write; the snapshot makes that a single
 * fold. Post-request side effects (e.g. the optimistic PENDING flip on the
 * loaded detail) ride the [requestMedia] [onSuccess] hook instead of a
 * consumer re-implementing the loading/success/error choreography.
 */
class SeerrRequestStateHolder(
    private val scope: CoroutineScope,
    private val delegate: SeerrRequestDelegate,
) {
    private val _requestResult = MutableStateFlow<SeerrRequestResult?>(null)

    private val _radarrServers = MutableStateFlow<List<SeerrRadarrServiceDetail>>(emptyList())

    private val _sonarrServers = MutableStateFlow<List<SeerrSonarrServiceDetail>>(emptyList())

    private val _isLoadingServices = MutableStateFlow(false)

    private val _tvSeasons = MutableStateFlow<List<SeerrSeason>>(emptyList())

    /** True when the current TV item is anime (TMDB keyword 210024), driving anime request defaults. */
    private val _tvIsAnime = MutableStateFlow(false)

    /** The item the request dialog is open for (null = closed). */
    private val _dialogItem = MutableStateFlow<SeerrSearchItem?>(null)

    /**
     * All seven state flows combined into one [SeerrRequestSnapshot] emission so
     * a consumer folds ONE flow into its ui state. Concurrent emissions
     * (e.g. loading services + seasons firing together when the request
     * dialog opens) coalesce into a single update instead of seven
     * back-to-back ones. Cold on purpose: [stateIn]-ing here would pin a
     * never-ending child coroutine onto whatever scope constructed the
     * holder (breaks `runTest` scopes); consumers [snapshotIn] or collect on
     * their own scope. Data-class equality plus [distinctUntilChanged] dedupes
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
            _dialogItem,
        ) { (result, radarr, sonarr), loading, seasons, isAnime, dialogItem ->
            SeerrRequestSnapshot(
                requestResult = result,
                radarrServers = radarr,
                sonarrServers = sonarr,
                isLoadingServices = loading,
                tvSeasons = seasons,
                tvIsAnime = isAnime,
                dialogItem = dialogItem,
            )
        }.distinctUntilChanged()

    /**
     * The snapshot `stateIn`'d onto [scope] — the shape every ViewModel
     * consumer wants (idle 5 s after the last subscriber, empty
     * [SeerrRequestSnapshot] initial value). See [snapshot] for why the
     * holder itself stays cold.
     */
    fun snapshotIn(scope: CoroutineScope): StateFlow<SeerrRequestSnapshot> =
        snapshot.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SeerrRequestSnapshot())

    /**
     * Requests [item] through the delegate and owns the whole result
     * choreography: loading → success/error [SeerrRequestResult] on the
     * snapshot. [onSuccess] fires after the success result is set, handing
     * the caller the resolved [SeerrMediaRequest] for post-request side
     * effects (e.g. flipping the loaded detail to PENDING); it is NOT
     * invoked on failure.
     */
    fun requestMedia(
        item: SeerrSearchItem,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
        onSuccess: ((SeerrMediaRequest) -> Unit)? = null,
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
            ).onSuccess { request ->
                _requestResult.value = SeerrRequestResult(success = true)
                onSuccess?.invoke(request)
            }.onFailure {
                _requestResult.value = SeerrRequestResult(error = it.message ?: "Request failed")
            }
        }
    }

    fun clearRequestResult() {
        _requestResult.value = null
    }

    /**
     * Opens the Seerr request dialog for [item]: the pending item lands on
     * the snapshot ([SeerrRequestSnapshot.dialogItem] — the screens' `?.let`
     * render gate) AND the open cascade fires here, not at the call site:
     * service details for the item's media type, plus the season list for TV
     * only (case-insensitive; Seerr reports "tv" lowercase but the casing is
     * not contractual). Three screens used to hand-copy this choreography as
     * LaunchedEffect bodies — reachable by no test.
     */
    fun openRequestDialog(item: SeerrSearchItem) {
        _dialogItem.value = item
        loadServiceDetails(item.mediaType)
        if (item.mediaType.equals("tv", ignoreCase = true)) {
            loadTvSeasons(item.id)
        }
    }

    /**
     * The ONE dialog teardown: drop the pending request item (closing the
     * dialog) THEN clear the last request result — in that order, so the
     * result banner never outlives the dialog it belongs to (same rule as
     * HomeDialogSession.dismissSeerrRequest).
     */
    fun dismissRequestDialog() {
        _dialogItem.value = null
        clearRequestResult()
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
