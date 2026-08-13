package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem

import com.raulshma.jellyplay.core.model.UserInfo

@Immutable
data class HomeUiState(
    val sections: List<HomeSection> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** True while a manual offline→online transition is in progress, so the
     * Go-online affordances can show an inline spinner instead of being silent. */
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
    val homeBackdropEnabled: Boolean = true,
    val performanceMode: Boolean = false,
    val showClock: Boolean = false,
    /**
     * Whether the home top header dock auto-hides on scroll-down and reappears
     * on scroll-up. Default `false` (current pinned behaviour). Read by
     * `HomeTopDockScrim`, which owns the hide animation so the orchestrator
     * never recomposes on scroll.
     */
    val hideTopHeaderOnScroll: Boolean = false,
    val continueWatchingClickBehavior: com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior = com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior.DETAILS,
    val discoverSections: Map<DiscoverSectionType, List<SeerrSearchItem>> = emptyMap(),
    val searchState: HomeSearchState = HomeSearchState(),
    /** True while the search field holds a non-blank query. This is the only
     * search-derived signal read at the [MainHomeContent] orchestrator level:
     * it flips at most twice per search session (blank↔nonblank) instead of
     * once per keystroke, so the ~510-line body recomposes far less. The live
     * query string is read separately in a leaf via the VM's `searchQuery`
     * StateFlow. */
    val isSearchActive: Boolean = false,
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
    /**
     * All users persisted for the current server. Drives the home app-bar
     * quick user switcher : the chip only renders when there are ≥2 users.
     */
    val currentServerUsers: List<UserInfo> = emptyList(),
    /**
     * Mirror of the user's enabled home section types (from prefs). Consumed
     * only by the inline section-config sheet so it can show the current
     * toggle state without reading the store directly.
     */
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    /**
     * Mirror of the user's home section ordering (from prefs). Consumed only
     * by the inline section-config sheet to enable/disable the Move Up/Down
     * buttons relative to the section's current position.
     */
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    /**
     * Mirror of the user's per-library section overrides (from prefs). Keyed by
     * library (folder) id, value is the set of DISABLED [HomeSectionType]s for
     * that library. Consumed by the inline section-config sheet so a per-library
     * LATEST_MEDIA row shows its real toggle state. Mirrors the Settings →
     * Configure Libraries semantics.
     */
    val libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
    /**
     * Non-null while the offline home's advanced "delete downloaded episodes"
     * sheet is open for a series card. Carries the seasons/episodes loaded for
     * the targeted series (only downloaded episodes), the aggregate size, and a
     * brief loading flag while that data is being read from the offline store.
     * Rendered by [MainHomeContent] as a `DeleteDownloadedEpisodesSheet`.
     */
    val seriesDelete: HomeSeriesDeleteState? = null,
)

/**
 * Data backing the offline home's series delete-episodes sheet (the same
 * `DeleteDownloadedEpisodesSheet` the media-detail screen uses). [episodes]
 * holds only downloaded episodes keyed by season id; [totalSizeBytes] is the
 * aggregate on-disk size used for the freed-space summary when the whole
 * series is selected; [episodeSizeBytes] carries the per-episode sizes read
 * from the offline store so the freed-space figure is exact for partial
 * selections too.
 */
@Immutable
data class HomeSeriesDeleteState(
    val seriesId: String,
    val seasons: List<MediaItem>,
    val episodesBySeason: Map<String, List<MediaItem>>,
    val totalSizeBytes: Long,
    val episodeSizeBytes: Map<String, Long> = emptyMap(),
    val isLoading: Boolean,
)

@Immutable
data class HomeSearchState(
    val jellyfinResults: List<MediaItem> = emptyList(),
    val seerrResults: List<SeerrSearchItem> = emptyList(),
    val settingsResults: List<ResolvedSettingsItem> = emptyList(),
    val isSearching: Boolean = false,
) {
    // NOTE: the live query string no longer lives here. It is the per-keystroke
    // value of the search field, and holding it inside this @Immutable state
    // object meant _uiState changed equality on every keystroke, recomposing
    // the ~510-line MainHomeContent body. The source of truth is now the VM's
    // `searchQuery` StateFlow (read in a leaf), with `isSearchActive` carrying
    // only the rarely-changing blank/nonblank signal to the orchestrator.
}

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
