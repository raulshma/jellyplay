package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem

sealed interface HomeUiEvent {
    data object Refresh : HomeUiEvent
    data object PullToRefresh : HomeUiEvent
    data object ToggleOfflineMode : HomeUiEvent
    data class UpdateSearchQuery(val query: String) : HomeUiEvent
    data object ClearSearch : HomeUiEvent
    data class SelectSeerrRequestItem(val item: SeerrSearchItem?) : HomeUiEvent
    data class RequestSeerrMedia(
        val item: SeerrSearchItem,
        val seasons: List<Int>? = null,
        val serverId: Int? = null,
        val profileId: Int? = null,
        val rootFolder: String? = null,
        val tags: List<Int>? = null,
    ) : HomeUiEvent
    data object ClearRequestResult : HomeUiEvent
    data class LoadSeerrServiceDetails(val mediaType: String) : HomeUiEvent
    data class LoadTvSeasons(val tmdbId: Int) : HomeUiEvent
    data class PrefetchSeerrDetails(val tmdbId: Int, val mediaType: String, val onDone: () -> Unit) : HomeUiEvent
}
