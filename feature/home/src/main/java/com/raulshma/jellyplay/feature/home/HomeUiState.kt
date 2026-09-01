package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSnapshot
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem

import com.raulshma.jellyplay.core.model.UserInfo

/**
 * The appearance/theme slice of [HomeUiState] — the five fields that always
 * change together (one [AppearanceSlice] emission) and are read together (the
 * ArtworkThemeWrapper + backdrop pair). Embedded as one value, following the
 * [SeerrRequestState] precedent: the prefs fold stays a one-line copy instead
 * of a per-field hand-sync.
 */
@Immutable
data class AppearanceUiState(
    val dynamicTheming: Boolean = true,
    val oledMode: Boolean = false,
    val colorStyle: com.raulshma.jellyplay.core.model.ColorStyle = com.raulshma.jellyplay.core.model.ColorStyle.TONAL_SPOT,
    val accentColorSwatch: String = "dynamic",
    val performanceMode: Boolean = false,
)

/**
 * The inline section-config sheet's whole input surface — the three pref
 * mirrors it reads (see [sectionConfigCapabilities]). Embedded as one value
 * so the prefs fold hands the sheet one slice instead of re-mirroring
 * [HomeSectionPrefs] field by field.
 */
@Immutable
data class SectionConfigState(
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    /** Per-library DISABLED types keyed by library (folder) id. */
    val libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
)

@Immutable
data class HomeUiState(
    val sections: List<HomeSection> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** True while a manual offline→online transition is in progress, so the
     * Go-online affordances can show an inline spinner instead of being silent. */
    val isGoingOnline: Boolean = false,
    val error: String? = null,
    /** Non-blocking notice shown when some (not all) home sections failed to load. */
    val partialLoadError: Boolean = false,
    val homeMode: HomeMode = HomeMode.VIDEO,
    /** The appearance/theme quintet — see [AppearanceUiState]. */
    val appearance: AppearanceUiState = AppearanceUiState(),
    val offlineMode: OfflineMode = OfflineMode.ONLINE,
    val offlineLibrary: List<OfflineMediaItem> = emptyList(),
    /**
     * Downloaded episodes (playback state joined, local artwork resolved),
     * collected under the same gate as [offlineLibrary]. Episodes are excluded
     * from the library itself by design — the offline library browse shows
     * series — so this is the offline home's only episode source, feeding the
     * Continue Watching and Next Up rows via [buildOfflineHomeSections].
     */
    val offlineEpisodes: List<OfflineMediaItem> = emptyList(),
    /**
     * The cached ONLINE home layout (issue #147): section types, titles,
     * per-library rows and order from the last successful online fetch,
     * collected under the same gate as [offlineLibrary]. The offline home
     * mirrors it filtered to downloaded items (see buildOfflineHomeSections);
     * empty when no snapshot exists, in which case the generic derived rows
     * render instead.
     */
    val offlineLayoutSections: List<HomeSection> = emptyList(),
    /**
     * The CW/NextUp prefs the offline home rows must honor (enabled flags,
     * hidden CW items, excluded Next Up series, CW+NextUp merge). Built from
     * the same prefs snapshot that drives the online section query so the
     * offline home never contradicts the user's online home layout.
     */
    val offlineSectionPrefs: OfflineHomeSectionPrefs = OfflineHomeSectionPrefs(),
    /**
     * The single offline-render predicate, computed once per gate/library
     * emission by [computeHomeRenderSource] (see [HomeRenderSource]). The
     * screen branches and the VM's downloads-rendering gate read this value —
     * no site re-derives the predicate from [offlineMode] + error/sections.
     */
    val renderSource: HomeRenderSource = HomeRenderSource.Online,
    val discoverEnabled: Boolean = false,
    val homeHeroEnabled: Boolean = true,
    val homeBackdropEnabled: Boolean = true,
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
     * The inline section-config sheet's three pref mirrors — see
     * [SectionConfigState]. Read only by the sheet's capabilities derivation.
     */
    val sectionConfig: SectionConfigState = SectionConfigState(),
    /**
     * Non-null while the offline home's advanced "delete downloaded episodes"
     * sheet is open for a series card. Carries the seasons/episodes loaded for
     * the targeted series (only downloaded episodes), the aggregate size, and a
     * brief loading flag while that data is being read from the offline store.
     * Rendered by [MainHomeContent] as a `DeleteDownloadedEpisodesSheet`.
     */
    val seriesDelete: HomeSeriesDeleteState? = null,

    /**
     * Non-null while the series download sheet is open for a series card's
     * quick-action Download. Carries the seasons/episodes loaded for the
     * targeted series (server catalogue) plus which episodes are already
     * downloaded. Rendered by [MainHomeContent] as a `SeriesDownloadSheet` —
     * the same sheet the media-detail screen hosts.
     */
    val seriesDownload: HomeSeriesDownloadState? = null,
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

/**
 * Data backing the home series download sheet (the `SeriesDownloadSheet` the
 * media-detail screen also hosts). [episodesBySeason] is keyed by season id;
 * [loadingSeasons] carries the initial-load sentinel (the series id) plus any
 * lazily expanding season while the snapshot loads.
 */
@Immutable
data class HomeSeriesDownloadState(
    val seriesId: String,
    val seasons: List<MediaItem>,
    val episodesBySeason: Map<String, List<MediaItem>>,
    val loadingSeasons: Set<String>,
    val downloadedEpisodeIds: Set<String>,
)

@Immutable
data class HomeSearchState(
    val jellyfinResults: List<MediaItem> = emptyList(),
    val seerrResults: List<SeerrSearchItem> = emptyList(),
    val isSearching: Boolean = false,
) {
    // NOTE: the live query string no longer lives here. It is the per-keystroke
    // value of the search field, and holding it inside this @Immutable state
    // object meant _uiState changed equality on every keystroke, recomposing
    // the ~510-line MainHomeContent body. The source of truth is now the VM's
    // `searchQuery` StateFlow (read in a leaf), with `isSearchActive` carrying
    // only the rarely-changing blank/nonblank signal to the orchestrator.
    //
    // NOTE: settings results also no longer live here — the local
    // settings-search pipeline is computed in the UI layer
    // (HomeTopDockScrim via `settingsSearchResults`), which owns the Android
    // Context needed to resolve the registry's @StringRes ids.
}

/**
 * The request-sheet slice: the locally-chosen item plus the shared holder's
 * [SeerrRequestSnapshot] (request result, services, seasons, anime flag).
 * The snapshot is embedded rather than field-mirrored so the
 * SeerrRequestStateHolder fold stays a one-line copy — no per-field hand-sync.
 */
@Immutable
data class SeerrRequestState(
    val requestItem: SeerrSearchItem? = null,
    val snapshot: SeerrRequestSnapshot = SeerrRequestSnapshot(),
)

@Immutable
data class HomeScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)
