package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail

@Immutable
data class HomeUiState(
    val sections: List<HomeSection> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val homeMode: HomeMode = HomeMode.VIDEO,
    val dynamicTheming: Boolean = true,
    val oledMode: Boolean = false,
    val offlineMode: OfflineMode = OfflineMode.ONLINE,
    val offlineLibrary: List<OfflineMediaItem> = emptyList(),
    val discoverEnabled: Boolean = false,
    val homeHeroEnabled: Boolean = true,
    val discoverSections: Map<DiscoverSectionType, List<SeerrSearchItem>> = emptyMap(),
    val searchState: HomeSearchState = HomeSearchState(),
    val seerrRequestState: SeerrRequestState = SeerrRequestState(),
    val newsletterBannerVisible: Boolean = false,
)

@Immutable
data class HomeSearchState(
    val query: String = "",
    val jellyfinResults: List<MediaItem> = emptyList(),
    val seerrResults: List<SeerrSearchItem> = emptyList(),
    val isSearching: Boolean = false,
)

@Immutable
data class SeerrRequestState(
    val requestItem: SeerrSearchItem? = null,
    val result: DiscoverRequestResult? = null,
    val radarrServers: List<SeerrRadarrServiceDetail> = emptyList(),
    val sonarrServers: List<SeerrSonarrServiceDetail> = emptyList(),
    val isLoadingServices: Boolean = false,
    val tvSeasons: List<SeerrSeason> = emptyList(),
)

@Immutable
data class DiscoverRequestResult(
    val isLoading: Boolean = false,
    val success: Boolean? = null,
    val error: String? = null,
)

@Immutable
data class HomeScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)

@Immutable
data class HomeFocusPosition(
    val sectionIndex: Int = 0,
    val itemIndex: Int = 0,
)
