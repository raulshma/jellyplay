package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

import com.raulshma.jellyplay.core.model.UserInfo

@Immutable
data class HomeUiState(
    val sections: List<HomeSection> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** True while a manual offline→online transition is in progress, so the
     *  Go-online affordances can show an inline spinner instead of being silent. */
    val isGoingOnline: Boolean = false,
    val error: String? = null,
    /** Non-blocking notice shown when some (not all) home sections failed to load. */
    val partialLoadError: Boolean = false,
    val homeMode: HomeMode = HomeMode.VIDEO,
    val dynamicTheming: Boolean = true,
    val oledMode: Boolean = false,
    val colorStyle: com.raulshma.jellyplay.core.model.ColorStyle = com.raulshma.jellyplay.core.model.ColorStyle.TONAL_SPOT,
    val accentColorSwatch: String = "dynamic",
    val offlineMode: OfflineMode = OfflineMode.ONLINE,
    val offlineLibrary: List<OfflineMediaItem> = emptyList(),
    val discoverEnabled: Boolean = false,
    val homeHeroEnabled: Boolean = true,
    val showClock: Boolean = false,
    val continueWatchingClickBehavior: com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior = com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior.DETAILS,
    val discoverSections: Map<DiscoverSectionType, List<SeerrSearchItem>> = emptyMap(),
    val searchState: HomeSearchState = HomeSearchState(),
    /** Whether to include settings results in the home search bar. Driven by Appearance prefs. */
    val showSettingsInHomeSearch: Boolean = true,
    val seerrRequestState: SeerrRequestState = SeerrRequestState(),
    val newsletterBannerVisible: Boolean = false,
    val experimentalCardClippingEnabled: Boolean = false,
    /** Direct *arr "Recently Grabbed / Coming Soon" calendar row; empty when the flag is off or no *arr is configured. */
    val recentlyGrabbed: List<SeerrSearchItem> = emptyList(),
    /** Whether the DIRECT_ARR_INTEGRATION experimental flag is enabled. */
    val directArrEnabled: Boolean = false,
    val currentUser: UserInfo? = null,
)

@Immutable
data class HomeSearchState(
    val query: String = "",
    val jellyfinResults: List<MediaItem> = emptyList(),
    val seerrResults: List<SeerrSearchItem> = emptyList(),
    val settingsResults: List<SettingsSearchItem> = emptyList(),
    val isSearching: Boolean = false,
)

@Immutable
data class SeerrRequestState(
    val requestItem: SeerrSearchItem? = null,
    val result: SeerrRequestResult? = null,
    val radarrServers: List<SeerrRadarrServiceDetail> = emptyList(),
    val sonarrServers: List<SeerrSonarrServiceDetail> = emptyList(),
    val isLoadingServices: Boolean = false,
    val tvSeasons: List<SeerrSeason> = emptyList(),
)

@Immutable
data class HomeScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)
