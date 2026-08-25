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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import org.jetbrains.compose.resources.stringResource
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertCircle
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMediaItem
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
import com.raulshma.jellyplay.feature.home.generated.resources.home_sections_load_failed
import com.raulshma.jellyplay.feature.home.generated.resources.home_retry
import com.raulshma.jellyplay.feature.home.generated.resources.home_resume_or_details
import com.raulshma.jellyplay.feature.home.generated.resources.home_resume
import com.raulshma.jellyplay.feature.home.generated.resources.home_refresh
import com.raulshma.jellyplay.feature.home.generated.resources.home_no_content_description
import com.raulshma.jellyplay.feature.home.generated.resources.home_no_content_available
import com.raulshma.jellyplay.feature.home.generated.resources.home_discover
import com.raulshma.jellyplay.feature.home.generated.resources.home_details
import com.raulshma.jellyplay.feature.home.generated.resources.home_coming_soon
import com.raulshma.jellyplay.feature.home.generated.resources.home_because_you_watched
import com.raulshma.jellyplay.feature.home.generated.resources.Res
import androidx.compose.ui.platform.LocalDensity

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
    val backgroundColor: Color,
    val contentPad: Dp,
    val headerHeight: Dp,
    val isLightTheme: Boolean,
    val continueWatchingClickBehavior: ContinueWatchingClickBehavior,
    val discoverRows: List<List<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem>>,
    val allDiscoverItems: List<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem>,
    val recentlyGrabbed: List<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem>,
    val photoFolderChildUrls: Map<String, List<String>>,
    val offlineLibrary: List<OfflineMediaItem>,
)

@Immutable
internal data class HomeContentCallbacks(
    val onRetrySectionLoad: () -> Unit,
    val onDismissNewsletterBanner: () -> Unit,
    val onNewsletterClick: () -> Unit,
    val onOfflineLibraryClick: () -> Unit,
    val onItemClick: (String) -> Unit,
    val onFocusChange: (Boolean) -> Unit,
    val mediaOnItemClick: (MediaItem) -> Unit,
    val mediaOnPlayClick: (MediaItem) -> Unit,
    val mediaImageUrlBuilder: (MediaItem) -> String,
    val mediaBackdropUrlBuilder: (MediaItem) -> String,
    val getImageUrl: (String) -> String,
    val getBackdropUrl: (String) -> String,
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
    // the LazyColumn's LazyListScope, where the window-info read isn't typed).
    // KMP replacement for the Android-only LocalConfiguration.screenWidthDp:
    // the window container width in dp (settings AudioSettingsScreen precedent).
    val discoverScreenWidth = with(LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp()
    }
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
                title = stringResource(Res.string.home_no_content_available),
                description = stringResource(Res.string.home_no_content_description),
                actionLabel = stringResource(Res.string.home_refresh),
                onAction = callbacks.onRetrySectionLoad,
                modifier = Modifier.padding(horizontal = state.contentPad),
            )
        }
    } else {
        // De-duplicate the downloaded row against the online sections so a title
        // that already appears in Continue Watching / Latest / Recently Added
        // isn't shown twice.
        val dedupedOfflineLibrary = remember(state.offlineLibrary, sections) {
            if (state.offlineLibrary.isEmpty()) state.offlineLibrary
            else {
                val onlineIds = buildSet {
                    for (section in sections) for (item in section.items) add(item.id)
                }
                if (onlineIds.isEmpty()) state.offlineLibrary else state.offlineLibrary.filter { it.id !in onlineIds }
            }
        }

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
                    AnimatedHeroHeader(
                        featuredItem = state.featuredItem,
                        getBackdropUrl = remember(callbacks.getBackdropUrl) { { callbacks.getBackdropUrl(it) } },
                        height = state.headerHeight,
                        backgroundColor = state.backgroundColor,
                        contentPadding = state.contentPad,
                        homeBackdropEnabled = state.homeBackdropEnabled,
                        listState = listState,
                        onItemClick = callbacks.onItemClick,
                        onDetailsClick = callbacks.onItemClick,
                        requestInitialFocus = !savedRowIsValid,
                        onFocusChange = callbacks.onFocusChange,
                        focusRequester = heroFocusRequester,
                    )
                }
            } else {
                item(key = "hero_spacer") { Spacer(Modifier.height(100.dp)) }
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
                            text = stringResource(Res.string.home_sections_load_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = callbacks.onRetrySectionLoad) {
                            Text(stringResource(Res.string.home_retry))
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
                    stringResource(Res.string.home_because_you_watched, seedItem.name)
                } else {
                    section.title
                }

                if (section.type == HomeSectionType.CONTINUE_WATCHING || section.type == HomeSectionType.NEXT_UP) {
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
                    SectionHeader(
                        text = stringResource(Res.string.home_discover),
                        backgroundColor = state.backgroundColor,
                        contentPad = state.contentPad,
                        homeBackdropEnabled = state.homeBackdropEnabled,
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
                    SectionHeader(
                        text = stringResource(Res.string.home_coming_soon),
                        backgroundColor = state.backgroundColor,
                        contentPad = state.contentPad,
                        homeBackdropEnabled = state.homeBackdropEnabled,
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

            if (dedupedOfflineLibrary.isNotEmpty()) {
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
            text = { Text(stringResource(Res.string.home_resume_or_details)) },
            confirmButton = {
                TextButton(onClick = {
                    askContinueItem = null
                    callbacks.mediaOnPlayClick(askItem)
                }) { Text(stringResource(Res.string.home_resume)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askContinueItem = null
                    callbacks.mediaOnItemClick(askItem)
                }) { Text(stringResource(Res.string.home_details)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(
    text: String,
    backgroundColor: Color,
    contentPad: Dp,
    homeBackdropEnabled: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            // Transparent over the ambient backdrop so it shows through headers;
            // opaque flat fill otherwise.
            .then(if (homeBackdropEnabled) Modifier else Modifier.background(backgroundColor))
            .padding(start = contentPad, top = 24.dp, bottom = 8.dp),
    )
}
