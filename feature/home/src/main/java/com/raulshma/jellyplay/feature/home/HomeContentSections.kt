package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertCircle
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.SeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberInt

/**
 * Bundles the (previously 38) flat parameters of the home content list into a
 * single `@Immutable` value so the composable is skippable without relying on
 * every caller `remember`-ing dozens of unstable lambdas. Lambdas that must
 * remain stable across recompositions (image/url builders, click handlers) are
 * grouped into [HomeContentCallbacks].
 */
@Immutable
internal data class HomeContentState(
    val isLoading: Boolean,
    val homeHeroEnabled: Boolean,
    val homeBackdropEnabled: Boolean,
    val newsletterBannerVisible: Boolean,
    val discoverEnabled: Boolean,
    val experimentalCardClippingEnabled: Boolean,
    val sections: List<HomeSection>,
    val partialLoadError: Boolean,
    val featuredItem: MediaItem?,
    /**
     * True when [featuredItem] is derived from the offline library (offline or
     * implicit-offline rendering): the hero resolves artwork through
     * [HomeContentCallbacks.heroBackdropUrlBuilder] and routes clicks to the
     * offline detail screen instead of the online one.
     */
    val featuredIsOffline: Boolean = false,
    val backgroundColor: Color,
    val contentPad: Dp,
    val headerHeight: Dp,
    val isLightTheme: Boolean,
    val continueWatchingClickBehavior: ContinueWatchingClickBehavior,
    val discoverRows: List<List<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem>>,
    val allDiscoverItems: List<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem>,
    val recentlyGrabbed: List<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem>,
    val photoFolderChildUrls: Map<String, List<String>>,
    /**
     * The offline home's whole render model (see [OfflineHomeContent]) —
     * sections already derived, id→item lookup already built. Null while the
     * online home renders: download-progress ticks then never invalidate this
     * list. Non-null iff the caller renders offline content (offline or
     * implicit-offline).
     */
    val offlineContent: OfflineHomeContent? = null,
    /**
     * Non-blocking informational banner (e.g. the implicit-offline
     * "couldn't reach the server — showing your downloads" notice). Null hides it.
     */
    val statusBanner: String? = null,
)

@Immutable
internal data class HomeContentCallbacks(
    val onRetrySectionLoad: () -> Unit,
    val onDismissNewsletterBanner: () -> Unit,
    val onNewsletterClick: () -> Unit,
    val onOfflineLibraryClick: () -> Unit,
    /** Open a downloaded item's detail from an offline-derived section row. */
    val onOfflineItemClick: (itemId: String) -> Unit = {},
    val onItemClick: (String) -> Unit,
    val onFocusChange: (Boolean) -> Unit,
    val mediaOnItemClick: (MediaItem) -> Unit,
    val mediaOnPlayClick: (MediaItem) -> Unit,
    val mediaImageUrlBuilder: (MediaItem) -> String,
    val mediaBackdropUrlBuilder: (MediaItem) -> String,
    val getImageUrl: (String) -> String,
    val getBackdropUrl: (String) -> String,
    /**
     * Backdrop builder for the HERO only — the caller resolves online-vs-offline
     * once (offline items resolve to their local backdrop/poster file path), so
     * the list never re-branches. Row artwork keeps using [getBackdropUrl].
     */
    val heroBackdropUrlBuilder: (String) -> String,
    val fallbackImageUrlBuilder: (MediaItem) -> List<String>,
    val onSeerrItemClick: (Int, String) -> Unit,
    val onSeerrRequest: (com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem) -> Unit,
    val seerrPrefetch: (Int, String, () -> Unit) -> Unit,
    /** Open the inline section-config sheet for a given section. Carries the
     * optional [libraryId] for per-library sections (LATEST_MEDIA) so the sheet
     * can apply a per-library override instead of a global toggle. */
    val onConfigureSection: (HomeSectionType, String?) -> Unit,
    /** Deep-link into Settings → Home Screen Layout (global ordering / presets). */
    val onConfigureHomeLayout: () -> Unit,
    /** Deep-link into Settings → Configure Libraries (per-library overrides). */
    val onConfigureLibraries: () -> Unit,
    /** Open the full library screen for a home-section "See All" action. */
    val onSeeAllClick: (sectionType: HomeSectionType, libraryId: String?, collectionType: String?, title: String) -> Unit = { _, _, _, _ -> },
    /** TV-only: reports the D-pad-focused item so the Menu key can open its
     * quick actions */
    val onFocusedMediaItem: (MediaItem) -> Unit = {},
)

/**
 * The home LazyColumn: hero, partial-load / newsletter banners, media rows,
 * Seerr discover grid, *arr "Recently Grabbed" row, downloaded row, and the
 * continue-watching "Resume vs Details" dialog.
 *
 * Behaviour is identical to the former `HomeContentList`; extracted here and
 * refactored to (a) take a [HomeContentState] + [HomeContentCallbacks] bundle,
 * (b) render the discover + recently-grabbed rows via the shared
 * [SeerrDiscoverRow], and (c) route focus restoration through
 * [RestoreHomeRowFocus].
 */
@Composable
internal fun HomeContentList(
    state: HomeContentState,
    callbacks: HomeContentCallbacks,
    listState: LazyListState,
    density: Density,
    seerrCardLoadingState: SeerrCardLoadingState,
    heroFocusRequester: FocusRequester?,
) {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val sections = state.sections

    // Discover-row dimensions computed once at the composable scope (not inside
    // the LazyColumn's LazyListScope, where LocalConfiguration isn't readable).
    val discoverScreenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val discoverRowWidth = discoverScreenWidth - state.contentPad * 2
    val discoverSpacing = adaptiveInfo.itemSpacing(isTv)
    val discoverPattern = if (adaptiveInfo.windowSizeClass == WindowSizeClass.Compact) COMPACT_DISCOVER_PATTERN else EXPANDED_DISCOVER_PATTERN

    var askContinueItem by remember { mutableStateOf<MediaItem?>(null) }

    // Per-row focus requesters so D-pad navigation can target each content row.
    var homeFocusRow by rememberInt(-1)
    val savedRowIsValid = homeFocusRow in 0..sections.lastIndex
    // Key on the sections list itself (part of the @Immutable HomeContentState,
    // so structural equality is cheap) rather than on sections.size: a refresh
    // that drops one section and adds another can produce a same-size list and
    // reallocate these requesters, losing any attached D-pad focus.
    val rowFocusRequesters = remember(sections) { List(sections.size) { FocusRequester() } }
    RestoreHomeRowFocus(
        listState = listState,
        savedRow = homeFocusRow,
        sectionCount = sections.size,
        newsletterBannerVisible = state.newsletterBannerVisible,
        rowFocusRequesters = { rowFocusRequesters },
    )

    if (sections.isEmpty()) {
        if (state.isLoading) {
            // Initial online fetch with nothing to show yet — a real loading state
            // instead of a blank screen. Delayed so fast loads don't flicker.
            DelayedLoadingScreen(modifier = Modifier.padding(horizontal = state.contentPad))
        } else {
            ScreenEmptyState(
                icon = Tabler.Outline.Movie,
                title = stringResource(R.string.home_no_content_available),
                description = stringResource(R.string.home_no_content_description),
                actionLabel = stringResource(R.string.home_refresh),
                onAction = callbacks.onRetrySectionLoad,
                modifier = Modifier.padding(horizontal = state.contentPad),
            )
        }
    } else {
        // De-duplicate the downloaded row against the online sections so a title
        // that already appears in Continue Watching / Latest / Recently Added
        // isn't shown twice. Reads the aggregate's (already mode-filtered)
        // library slice.
        val offlineContent = state.offlineContent
        val dedupedOfflineLibrary = remember(offlineContent, sections) {
            val library = offlineContent?.library.orEmpty()
            if (library.isEmpty()) library
            else {
                val onlineIds = buildSet {
                    for (section in sections) for (item in section.items) add(item.id)
                }
                if (onlineIds.isEmpty()) library else library.filter { it.id !in onlineIds }
            }
        }

        // Id→offline-item lookup for the offline hero's click routing and the
        // DOWNLOADED section rows — prebuilt inside [OfflineHomeContent] (one
        // build per offline emission, shared with the screen's hero backdrop
        // resolver). Read via [currentOfflineById] so the hero's click lambda
        // keeps its identity across download-progress emissions.
        val currentOfflineById by rememberUpdatedState(offlineContent?.itemsById ?: emptyMap())

        // Hoisted out of the items() lambda so the gradient brush is built once
        // per (backgroundColor, density) change rather than re-instantiated for
        // every section item in the list, even though only the first section
        // actually applies it.
        val heroTransitionBrush = remember(state.backgroundColor, density) {
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, state.backgroundColor),
                startY = 0f,
                endY = with(density) { 10.dp.toPx() },
            )
        }

        // Standalone headers (discover / *arr) keep their flat fill when the
        // ambient backdrop is off — the behavior of the former private
        // SectionHeader.
        val headerModifier = if (state.homeBackdropEnabled) Modifier else Modifier.background(state.backgroundColor)

        CompositionLocalProvider(
            com.raulshma.jellyplay.core.ui.components.LocalScrollIdle provides
                remember(listState) { { !listState.isScrollInProgress } }
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = adaptiveInfo.bottomPadding(isTv)),
        ) {
            if (state.featuredItem != null && state.homeHeroEnabled) {
                item(key = "hero") {
                    val featured = state.featuredItem
                    // Offline hero clicks open the offline detail screen (the
                    // online routing would hit the server for item metadata);
                    // the unified detail tree needs only the id.
                    val heroItemClick = remember(state.featuredIsOffline, callbacks) {
                        { id: String ->
                            if (state.featuredIsOffline) {
                                callbacks.onOfflineItemClick(id)
                            } else {
                                callbacks.onItemClick(id)
                            }
                        }
                    }
                    AnimatedHeroHeader(
                        featuredItem = featured,
                        getBackdropUrl = remember(callbacks.heroBackdropUrlBuilder) {
                            { id: String -> callbacks.heroBackdropUrlBuilder(id) }
                        },
                        height = state.headerHeight,
                        backgroundColor = state.backgroundColor,
                        contentPadding = state.contentPad,
                        homeBackdropEnabled = state.homeBackdropEnabled,
                        listState = listState,
                        onItemClick = heroItemClick,
                        onDetailsClick = heroItemClick,
                        requestInitialFocus = !savedRowIsValid,
                        onFocusChange = callbacks.onFocusChange,
                        focusRequester = heroFocusRequester,
                    )
                }
            } else {
                item(key = "hero_spacer") { Spacer(Modifier.height(100.dp)) }
            }

            // Non-blocking informational banner (implicit-offline fallback notice).
            if (state.statusBanner != null) {
                item(key = "status_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = state.contentPad, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Tabler.Outline.AlertCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = state.statusBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // Non-blocking notice when some home sections failed to load.
            if (state.partialLoadError) {
                item(key = "partial_load_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = state.contentPad, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Tabler.Outline.AlertCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.home_sections_load_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = callbacks.onRetrySectionLoad) {
                            Text(stringResource(R.string.home_retry))
                        }
                    }
                }
            }

            if (state.newsletterBannerVisible) {
                item(key = "newsletter_banner") {
                    NewsletterBanner(
                        onClick = callbacks.onNewsletterClick,
                        onDismiss = callbacks.onDismissNewsletterBanner,
                    )
                }
            }

            items(count = sections.size, key = { sections[it].id }, contentType = { "homeSection_${sections[it].type}" }) { index ->
                val section = sections[index]
                val isFirstAfterHero = index == 0 && state.featuredItem != null && state.homeHeroEnabled
                val sectionIndexInList = index + (if (state.featuredItem != null && state.homeHeroEnabled) 1 else 0)
                // Per-item visibility: a single shared IntRange derivedStateOf
                // invalidated every composed section item on every boundary
                // crossing (the range value changed even for items whose own
                // visibility didn't). Reading each item's own visibility as a
                // Boolean means only the item that entered/left recomposes.
                //
                // Keyed on sectionIndexInList: if the hero presence toggles
                // (featuredItem becomes null, or homeHeroEnabled flips), every
                // item's offset shifts by 1 and the captured index would
                // otherwise track the wrong item's visibility. The key discards
                // the stale derivedStateOf and rebuilds it for the new offset.
                val isCurrentlyVisible by remember(sectionIndexInList) {
                    derivedStateOf {
                        listState.layoutInfo.visibleItemsInfo.any { it.index == sectionIndexInList }
                    }
                }

                var hasBeenVisible by rememberSaveable { mutableStateOf(false) }
                // Fold the one-shot visibility latch into a LaunchedEffect so the
                // state write happens as a side effect, not during composition —
                // writing to MutableState during composition triggers a redundant
                // recomposition of this item on the next frame.
                LaunchedEffect(isCurrentlyVisible) {
                    if (isCurrentlyVisible && !hasBeenVisible) hasBeenVisible = true
                }

                val sectionAnimation by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (hasBeenVisible) 1f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "sectionAnimation",
                )

                val placementSpec = lazyItemPlacementSpec()
                val sectionModifier = Modifier
                    .animateItem(placementSpec = placementSpec)
                    .fillMaxWidth()
                    .then(
                        when {
                            // With the ambient backdrop on, sections stay transparent so the
                            // backdrop shows through between cards; the hero itself fades to
                            // transparent so no merge gradient is needed here.
                            state.homeBackdropEnabled -> Modifier
                            isFirstAfterHero -> Modifier.background(heroTransitionBrush)
                            else -> Modifier.background(state.backgroundColor)
                        }
                    )
                    .padding(top = if (isFirstAfterHero) 0.dp else 16.dp)
                    .graphicsLayer {
                        alpha = sectionAnimation
                        translationY = (1f - sectionAnimation) * 16.dp.toPx()
                    }

                val seedItem = section.seedItem
                val sectionTitle = if (section.type == HomeSectionType.RECOMMENDATIONS && seedItem != null) {
                    stringResource(R.string.home_because_you_watched, seedItem.name)
                } else {
                    section.title
                }

                if (section.type == HomeSectionType.DOWNLOADED) {
                    // Offline-derived section (see buildOfflineHomeSections):
                    // re-resolve the offline originals by id (shared lookup
                    // above) so the cards render local artwork and clicks route
                    // to the offline detail.
                    val byId = currentOfflineById
                    val offlineItems = remember(section, byId) {
                        section.items.mapNotNull { byId[it.id] }
                    }
                    OfflineHomeMediaRow(
                        title = sectionTitle,
                        items = offlineItems,
                        onItemClick = remember(callbacks) {
                            { item -> callbacks.onOfflineItemClick(item.id) }
                        },
                        modifier = sectionModifier,
                        focusRequester = rowFocusRequesters[index],
                        onRowFocused = { homeFocusRow = index },
                        clippingEnabled = state.experimentalCardClippingEnabled,
                        onFocusedItemChange = callbacks.onFocusedMediaItem,
                    )
                } else if (section.type == HomeSectionType.CONTINUE_WATCHING || section.type == HomeSectionType.NEXT_UP) {
                    val rowItemClick: (MediaItem) -> Unit = remember(
                        section.type, state.continueWatchingClickBehavior, callbacks.mediaOnItemClick, callbacks.mediaOnPlayClick,
                    ) {
                        { item ->
                            if (section.type == HomeSectionType.CONTINUE_WATCHING) {
                                when (state.continueWatchingClickBehavior) {
                                    ContinueWatchingClickBehavior.DETAILS -> callbacks.mediaOnItemClick(item)
                                    ContinueWatchingClickBehavior.PLAY -> callbacks.mediaOnPlayClick(item)
                                    ContinueWatchingClickBehavior.ASK -> { askContinueItem = item }
                                }
                            } else {
                                callbacks.mediaOnItemClick(item)
                            }
                        }
                    }
                    // Long-press affordance only for user-configurable section
                    // types; non-configurable rows (FAVORITES, PINNED, …) are
                    // managed from their own surfaces and pass null.
                    val sectionLongClick = remember(section.type, section.libraryId, callbacks) {
                        if (section.type.isConfigurable) {
                            { callbacks.onConfigureSection(section.type, section.libraryId) }
                        } else null
                    }
                    ContinueWatchingRow(
                        title = sectionTitle,
                        items = section.items,
                        imageUrlBuilder = callbacks.mediaImageUrlBuilder,
                        backdropUrlBuilder = callbacks.mediaBackdropUrlBuilder,
                        onItemClick = rowItemClick,
                        onPlayClick = callbacks.mediaOnPlayClick,
                        modifier = sectionModifier,
                        focusRequester = rowFocusRequesters[index],
                        onRowFocused = { homeFocusRow = index },
                        clippingEnabled = state.experimentalCardClippingEnabled,
                        onSectionLongClick = sectionLongClick,
                        onFocusedItemChange = callbacks.onFocusedMediaItem,
                    )
                } else {
                    val sectionLongClick = remember(section.type, section.libraryId, callbacks) {
                        if (section.type.isConfigurable) {
                            { callbacks.onConfigureSection(section.type, section.libraryId) }
                        } else null
                    }
                    HomeMediaRow(
                        title = sectionTitle,
                        items = section.items,
                        imageUrlBuilder = callbacks.mediaImageUrlBuilder,
                        fallbackImageUrlBuilder = callbacks.fallbackImageUrlBuilder,
                        onItemClick = callbacks.mediaOnItemClick,
                        onPlayClick = callbacks.mediaOnPlayClick,
                        modifier = sectionModifier,
                        photoFolderChildUrls = state.photoFolderChildUrls,
                        focusRequester = rowFocusRequesters[index],
                        onRowFocused = { homeFocusRow = index },
                        clippingEnabled = state.experimentalCardClippingEnabled,
                        showEpisodeSeriesBadge = section.type == HomeSectionType.LATEST_MEDIA,
                        onSectionLongClick = sectionLongClick,
                        onSeeAllClick = remember(callbacks, section.type, section.libraryId, section.collectionType, sectionTitle) {
                            if (section.type == HomeSectionType.RECENTLY_ADDED || section.type == HomeSectionType.LATEST_MEDIA) {
                                { callbacks.onSeeAllClick(section.type, section.libraryId, section.collectionType, sectionTitle) }
                            } else null
                        },
                        onFocusedItemChange = callbacks.onFocusedMediaItem,
                        seriesPosterResolver = remember(callbacks.getImageUrl) { { id: String -> callbacks.getImageUrl(id) } },
                        seriesBackdropResolver = remember(callbacks.getBackdropUrl) { { id: String -> callbacks.getBackdropUrl(id) } },
                    )
                }
            }

            if (state.discoverEnabled && state.allDiscoverItems.isNotEmpty()) {
                item(key = "seerr_discover_header") {
                    HomeRowTitle(
                        title = stringResource(R.string.home_discover),
                        contentPad = state.contentPad,
                        modifier = headerModifier,
                        standalone = true,
                    )
                }

                items(
                    count = state.discoverRows.size,
                    key = { rowIndex -> "seerr_row_${state.discoverRows[rowIndex].firstOrNull()?.id ?: 0}" },
                    contentType = { "seerrRow" },
                ) { rowIndex ->
                    val rowItems = state.discoverRows[rowIndex]
                    val targetSize = discoverPattern[rowIndex % discoverPattern.size]
                    val itemWidth = remember(discoverRowWidth, discoverSpacing, targetSize) {
                        (discoverRowWidth - discoverSpacing * (targetSize - 1)) / targetSize.toFloat()
                    }
                    SeerrDiscoverRow(
                        items = rowItems,
                        itemWidth = itemWidth,
                        rowHorizontalPadding = state.contentPad,
                        spacing = discoverSpacing,
                        backgroundColor = state.backgroundColor,
                        homeBackdropEnabled = state.homeBackdropEnabled,
                        clippingEnabled = state.experimentalCardClippingEnabled,
                        seerrCardLoadingState = seerrCardLoadingState,
                        seerrPrefetch = callbacks.seerrPrefetch,
                        onSeerrItemClick = callbacks.onSeerrItemClick,
                        onSeerrRequest = callbacks.onSeerrRequest,
                    )
                }
            }

            // ── Direct *arr "Recently Grabbed / Coming Soon" calendar row ──
            // Reuses SeerrMediaCard (TMDB-keyed) so no new card UI is needed.
            // Empty (and thus hidden) when the DIRECT_ARR_INTEGRATION flag is
            // off, no *arr is configured, or the calendar window is empty.
            if (state.recentlyGrabbed.isNotEmpty()) {
                item(key = "arr_recently_grabbed_header") {
                    HomeRowTitle(
                        title = stringResource(R.string.home_coming_soon),
                        contentPad = state.contentPad,
                        modifier = headerModifier,
                        standalone = true,
                    )
                }
                item(key = "arr_recently_grabbed_row") {
                    val targetSize = discoverPattern[0]
                    val itemWidth = remember(discoverRowWidth, discoverSpacing, targetSize) {
                        (discoverRowWidth - discoverSpacing * (targetSize - 1)) / targetSize.toFloat()
                    }
                    SeerrDiscoverRow(
                        items = state.recentlyGrabbed,
                        itemWidth = itemWidth,
                        rowHorizontalPadding = state.contentPad,
                        spacing = discoverSpacing,
                        backgroundColor = state.backgroundColor,
                        homeBackdropEnabled = state.homeBackdropEnabled,
                        clippingEnabled = state.experimentalCardClippingEnabled,
                        seerrCardLoadingState = seerrCardLoadingState,
                        seerrPrefetch = callbacks.seerrPrefetch,
                        onSeerrItemClick = callbacks.onSeerrItemClick,
                        onSeerrRequest = callbacks.onSeerrRequest,
                    )
                }
            }

            // While offline, the offline library IS the section list above, so
            // the inline Downloaded row would duplicate every title.
            val rendersOfflineSections = sections.any { it.type == HomeSectionType.DOWNLOADED }
            if (dedupedOfflineLibrary.isNotEmpty() && !rendersOfflineSections) {
                item(key = "downloaded_row") {
                    // DownloadedSection renders its own "Downloaded" header, so we
                    // intentionally don't emit a separate header item here.
                    DownloadedSection(
                        offlineLibrary = dedupedOfflineLibrary,
                        onOfflineLibraryClick = callbacks.onOfflineLibraryClick,
                        contentPad = state.contentPad,
                        backgroundColor = state.backgroundColor,
                    )
                }
            }
        }
        } // end CompositionLocalProvider(LocalScrollIdle)
    }

    val askItem = askContinueItem
    if (askItem != null) {
        AlertDialog(
            onDismissRequest = { askContinueItem = null },
            icon = { Icon(Tabler.Outline.PlayerPlay, contentDescription = null) },
            title = { Text(askItem.name) },
            text = { Text(stringResource(R.string.home_resume_or_details)) },
            confirmButton = {
                TextButton(onClick = {
                    askContinueItem = null
                    callbacks.mediaOnPlayClick(askItem)
                }) { Text(stringResource(R.string.home_resume)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askContinueItem = null
                    callbacks.mediaOnItemClick(askItem)
                }) { Text(stringResource(R.string.home_details)) }
            },
        )
    }
}
