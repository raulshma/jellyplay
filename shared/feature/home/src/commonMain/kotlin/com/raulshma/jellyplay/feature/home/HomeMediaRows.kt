package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.RatingColors
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.hasPlaybackPosition
import com.raulshma.jellyplay.core.model.isNewForHome
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.model.hasWatchProgress
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences
import com.raulshma.jellyplay.core.ui.components.mouseScroll
import com.raulshma.jellyplay.core.ui.components.localDateFromIsoTimestamp
import com.raulshma.jellyplay.core.ui.components.shortMonthDayYear
import com.raulshma.jellyplay.core.ui.animation.isReducedMotion
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer
import com.raulshma.jellyplay.core.ui.components.HorizontalEdgePullRefreshBox
import com.raulshma.jellyplay.core.ui.components.OfflineMediaCard
import com.raulshma.jellyplay.core.ui.components.PlayButtonWithProgress
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.WideMediaCard
import com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks
import com.raulshma.jellyplay.core.model.progressFraction
import com.raulshma.jellyplay.core.ui.components.rememberEpisodeCardImage
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.CalendarTime
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Dice
import com.composables.icons.tabler.outline.Folders
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import com.raulshma.jellyplay.feature.home.generated.resources.home_airs_date_format
import com.raulshma.jellyplay.feature.home.generated.resources.home_see_all
import com.raulshma.jellyplay.feature.home.generated.resources.Res

/**
 * Horizontal scroller for a home media row on touch devices. When [clippingEnabled]
 * is true (the experimental "Card Clipping" feature) it renders the
 * [HorizontalUncontainedCarousel], which caps/clips items at the row edges for the
 * carousel effect. When false (the default) it falls back to a plain [LazyRow]
 * with `clipToBounds = false` so items and their elevation bleed past the edges.
 */
/**
 * Home row for the offline-derived poster-card sections: the same chrome as
 * [HomeMediaRow] — title, card width, spacing, TV/mobile scroller, focus
 * wiring — but cards render via [OfflineMediaCard] so artwork resolves from
 * local files and no server image URL is ever built (the offline
 * home is the normal home, populated from downloads). Rows are either generic
 * ([HomeSectionType.DOWNLOADED]) or cached-layout mirror rows typed as their
 * online counterparts; the latter pass [onSectionLongClick] so the section
 * configure affordance matches the online rows. The offline Continue Watching
 * / Next Up rows render through [ContinueWatchingRow] with local-file
 * resolvers instead.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun OfflineHomeMediaRow(
    title: String,
    items: List<OfflineMediaItem>,
    onItemClick: (OfflineMediaItem) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onRowFocused: (() -> Unit)? = null,
    clippingEnabled: Boolean = false,
    onSectionLongClick: (() -> Unit)? = null,
    // "See All" pill for the section types that get one online (mirrored rows
    // keep the affordance — same visual row as the online layout).
    onSeeAllClick: (() -> Unit)? = null,
    // Play overlay on the card, mirroring HomeMediaRow's onPlayClick.
    onPlayClick: ((OfflineMediaItem) -> Unit)? = null,
    // Book reading-progress override for the mirrored Continue Reading row —
    // see [HomeMediaRow.progressFractionFor]; the mirror passes the refresher's
    // fraction map with a percent-decode fallback.
    progressFractionFor: ((OfflineMediaItem) -> Float?)? = null,
    // TV-only: reports the D-pad-focused item so the screen's Menu key can
    // open its quick actions (the offline card's own long-press handles touch).
    onFocusedItemChange: ((MediaItem) -> Unit)? = null,
) {
    val isTv = LocalTvMode.current
    val cardPrefs = LocalCardDisplayPreferences.current
    val metrics = homeRowMetrics()
    // Same hide-watched row filter as HomeMediaRow, so mirrored rows behave
    // identically to their online counterparts.
    val effectiveItems = remember(items, cardPrefs.hideWatchedItems) {
        hideWatchedFilter(items, cardPrefs.hideWatchedItems) { it.toMediaItem().isPlayed }
    }
    if (effectiveItems.isEmpty()) return
    // Row-shared bottom scrim, matching the online poster rows.
    val posterSurfaceColor = MaterialTheme.colorScheme.surface
    val posterScrimBrush = remember(posterSurfaceColor) { posterScrim(posterSurfaceColor) }
    // Same TV focus affordance as HomeMediaRow: Up-exits from the row land on
    // the See All pill when one exists.
    val seeAllFocusRequester = remember { FocusRequester() }
    val rowModifier = if (isTv && onSeeAllClick != null) {
        Modifier.seeAllExitOnUp(seeAllFocusRequester)
    } else {
        Modifier
    }

    Column(modifier = modifier) {
        HomeRowTitle(
            title = title,
            contentPad = metrics.contentPad,
            onLongClick = onSectionLongClick,
            onSeeAllClick = onSeeAllClick,
            seeAllFocusRequester = seeAllFocusRequester,
        )
        HomeItemRow(
            items = effectiveItems,
            key = { it.id },
            cardWidth = metrics.cardWidth,
            spacing = metrics.spacing,
            contentPad = metrics.contentPad,
            clippingEnabled = clippingEnabled,
            modifier = rowModifier,
            focusRequester = focusRequester,
            onRowFocused = onRowFocused,
            onFocusedItemChange = { item ->
                onFocusedItemChange?.let { change -> change(item.toMediaItem()) }
            },
        ) { item, mod ->
            // Every item here is downloaded by definition, so the
            // "Downloaded" status badge would be redundant.
            OfflineMediaCard(
                item = item,
                onClick = { onItemClick(item) },
                onPlayClick = onPlayClick?.let { click -> ({ click(item) }) },
                modifier = mod.width(metrics.cardWidth),
                showStatusBadge = false,
                sharedElementKey = "poster_${item.id}",
                clipToShape = clippingEnabled,
                gradientBrush = posterScrimBrush,
                progressFractionOverride = progressFractionFor?.invoke(item),
            )
        }
    }
}


/**
 * Shared poster-row chrome — the pieces [OfflineHomeMediaRow] mirrors from
 * [HomeMediaRow] so offline rows behave identically to their online
 * counterparts. Extracted so the two rows cannot drift apart.
 */

/** Drops watched items when the pref is on, so the whole card slot disappears. */
private fun <T> hideWatchedFilter(
    items: List<T>,
    hideWatched: Boolean,
    isPlayed: (T) -> Boolean,
): List<T> = if (hideWatched) items.filterNot(isPlayed) else items

/** The row-shared poster bottom scrim over [surfaceColor]. */
private fun posterScrim(surfaceColor: Color): Brush =
    Brush.verticalGradient(listOf(Color.Transparent, surfaceColor.copy(alpha = 0.45f)))

/**
 * TV focus routing for rows with a "See All" pill: the pill hugs the header's
 * trailing edge, so geometric focus search from the cards below rarely ranks
 * it — redirect Up-exits from the row to the pill explicitly.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.seeAllExitOnUp(seeAllFocusRequester: FocusRequester): Modifier =
    focusProperties {
        exit = { direction ->
            if (direction == FocusDirection.Up) seeAllFocusRequester
            else FocusRequester.Default
        }
    }

/**
 * The row chrome metrics every home row derives from the adaptive info —
 * card width, content padding, item spacing — as ONE value so the rows stop
 * repeating the three-line preamble (four copies when [DownloadedSection]
 * counted). A non-null [widthScale] selects a wide-card row (Continue
 * Watching / Next Up scale ~1.6× with a 260 dp floor); null (the default) is
 * the plain poster width.
 */
@Immutable
internal data class HomeRowMetrics(
    val cardWidth: Dp,
    val contentPad: Dp,
    val spacing: Dp,
)

@Composable
internal fun homeRowMetrics(widthScale: Float? = null): HomeRowMetrics {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val baseWidth = adaptiveInfo.rowCardWidth(isTv)
    return HomeRowMetrics(
        cardWidth = widthScale
            ?.let { scale -> (baseWidth * scale).coerceAtLeast(260.dp) }
            ?: baseWidth,
        contentPad = adaptiveInfo.contentPadding(isTv),
        spacing = adaptiveInfo.itemSpacing(isTv),
    )
}

/**
 * The home rows' TV/touch chassis — the ONE implementation of the
 * [TvFocusableItemRow] (TV) / [HorizontalMediaScroller] (touch) branch that
 * every home row shares, with the card as a slot. The slot
 * receives the focus modifier on TV and an empty one on touch; append
 * [cardWidth] to it so both branches size identically. Card-rendering
 * or scroller behavior lands in one place.
 */
@Composable
internal fun <T> HomeItemRow(
    items: List<T>,
    key: (T) -> Any,
    cardWidth: Dp,
    spacing: Dp,
    contentPad: Dp,
    clippingEnabled: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onRowFocused: (() -> Unit)? = null,
    // TV-only: reports the D-pad-focused item so the screen's Menu key can
    // open its quick actions (the card's own long-press handles touch).
    onFocusedItemChange: ((T) -> Unit)? = null,
    // Touch-only: edge-pull refresh wiring for the row — null disables the
    // gesture (TV rows and the non-refreshable/offline/Seerr sections pass
    // null and render exactly as before).
    edgeRefresh: EdgeRefreshContext? = null,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val isTv = LocalTvMode.current
    if (isTv) {
        TvFocusableItemRow(
            items = items,
            key = key,
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier,
            focusRequester = focusRequester,
            onRowFocused = onRowFocused,
            clipToBounds = clippingEnabled,
            onFocusedIndexChange = { index ->
                onFocusedItemChange?.let { change -> items.getOrNull(index)?.let(change) }
            },
        ) { _, item, focusModifier ->
            itemContent(item, focusModifier)
        }
    } else {
        // The pull gesture wraps the scroller (a Box overlay): the child
        // list's edge leftovers feed the accumulator, and the edge spinner
        // overlays the row. The scroller's own mouseScroll stays inside and
        // unaffected (dispatchRawDelta bypasses nested scroll, so mouse input
        // can never trigger the pull).
        HorizontalEdgePullRefreshBox(
            enabled = edgeRefresh != null,
            isRefreshing = edgeRefresh?.inProgress == true,
            onRefresh = { edgeRefresh?.onRefresh() },
            modifier = modifier.tvFocusRestorer(),
        ) {
            HorizontalMediaScroller(
                itemCount = items.size,
                itemWidth = cardWidth,
                spacing = spacing,
                contentPad = contentPad,
                clippingEnabled = clippingEnabled,
                key = { index -> key(items[index]) },
            ) { index ->
                itemContent(items[index], Modifier)
            }
        }
    }
}

@Composable
private fun HorizontalMediaScroller(

    itemCount: Int,
    itemWidth: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    contentPad: androidx.compose.ui.unit.Dp,
    clippingEnabled: Boolean,
    modifier: Modifier = Modifier,
    key: ((index: Int) -> Any)? = null,
    itemContent: @Composable (index: Int) -> Unit,
) {
    // Hoist the carousel state out of the clipping branch so toggling
    // clippingEnabled (the experimental card-clipping flag) doesn't recreate
    // the CarouselState and reset scroll position. It's only consumed in the
    // carousel branch below.
    val carouselState = rememberCarouselState { itemCount }
    if (clippingEnabled) {
        // The carousel implements contentPadding by SHRINKING every item's mask
        // (Strategy.createShiftedKeylineListForContentPadding divides the padding
        // across all non-anchor keylines) instead of adding real space. At scroll
        // rest this masks the leading card(s) below itemWidth on BOTH sides, so
        // even with enough room the first/next cards look clipped. Move the
        // horizontal inset to the container Modifier so it's real padding, and
        // pass zero contentPadding — then no shrink step is generated and the
        // first card renders full at the start inset.
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = itemWidth,
            itemSpacing = spacing,
            contentPadding = PaddingValues(0.dp),
            modifier = modifier
                .mouseScroll(carouselState, Orientation.Horizontal)
                .padding(horizontal = contentPad),
        ) { index -> itemContent(index) }
    } else {
        // Hoisted so the desktop mouse-drag / wheel-scroll modifiers below can
        // drive the same state the touch gestures use.
        val lazyListState = rememberLazyListState()
        LazyRow(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier
                .mouseScroll(lazyListState, Orientation.Horizontal),
        ) {
            // Stable keys + contentType let Compose preserve scroll position
            // across recompositions and reuse item slots efficiently.
            items(
                count = itemCount,
                key = key,
                contentType = { "mediaCard" },
            ) { index ->
                // animateItem (LazyItemScope) animates placement. Placement spec
                // routes through lazyItemPlacementSpec() so it snaps under
                // reduce-motion. Wrapping itemContent in a Box is required because
                // itemContent has no modifier param.
                val placementSpec = lazyItemPlacementSpec()
                Box(modifier = Modifier.animateItem(placementSpec = placementSpec)) {
                    itemContent(index)
                }
            }
        }
    }
}

/**
 * The wide-card Continue Watching / Next Up row, generic over the item type:
 * online rows pass [MediaItem] with server image-URL builders, offline-derived
 * rows (typed as their online counterparts by [buildOfflineHomeSections])
 * pass [OfflineMediaItem] with resolvers that lift to [MediaItem] and read the
 * local poster/backdrop file paths. Same chrome for both sources: 16:9
 * [WideMediaCard] at ~1.6× the poster width, hoisted scrim, progress + meta
 * footer. [onPlayClick] mirrors the row's play affordance for both sources —
 * the video player resolves the local download (`PlayerSessionManager.loadMedia`
 * → `PlaybackSourceResolver`), so offline playback needs no dedicated intent.
 */
@Composable
internal fun <T> ContinueWatchingRow(
    title: String,
    items: List<T>,
    toMediaItem: (T) -> MediaItem,
    imageUrl: (T) -> String,
    backdropUrl: (T) -> String,
    onItemClick: (T) -> Unit,
    onPlayClick: ((T) -> Unit)? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onRowFocused: (() -> Unit)? = null,
    clippingEnabled: Boolean = false,
    onSectionLongClick: (() -> Unit)? = null,
    // Row identity for the lazy list — defaults to the lifted MediaItem's id;
    // callers whose T already carries the id pass it directly so key lookups
    // don't allocate a MediaItem per item.
    key: (T) -> Any = { toMediaItem(it).id },
    // TV-only: reports the D-pad-focused item so the screen's Menu key can open
    // its quick actions (the wide card's own long-press handles touch).
    onFocusedItemChange: ((MediaItem) -> Unit)? = null,
    // Touch-only edge-pull refresh wiring — forwarded to [HomeItemRow]; null
    // (offline rows) keeps the gesture off.
    edgeRefresh: EdgeRefreshContext? = null,
) {
    // Continue-watching cards are wide (landscape thumbnails + progress/metadata), so they
    // scale ~1.6× the portrait poster-card width rather than using [rowCardWidth] directly.
    val metrics = homeRowMetrics(widthScale = 1.6f)
    val cardWidth = metrics.cardWidth
    // The bottom scrim gradient is identical across every card in the same theme
    // state, so compute it once per row instead of allocating a Brush per card
    // as cards scroll in/out of view.
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceScrimBrush = remember(surfaceColor) {
        Brush.verticalGradient(listOf(Color.Transparent, surfaceColor.copy(alpha = 0.4f)))
    }

    Column(modifier = modifier) {
        HomeRowTitle(
            title = title,
            contentPad = metrics.contentPad,
            onLongClick = onSectionLongClick,
        )
        HomeItemRow(
            items = items,
            key = key,
            cardWidth = metrics.cardWidth,
            spacing = metrics.spacing,
            contentPad = metrics.contentPad,
            clippingEnabled = clippingEnabled,
            focusRequester = focusRequester,
            onRowFocused = onRowFocused,
            onFocusedItemChange = { item -> onFocusedItemChange?.invoke(toMediaItem(item)) },
            edgeRefresh = edgeRefresh,
        ) { item, mod ->
            val mediaItem = toMediaItem(item)
            val memoizedClick = remember(item) { { onItemClick(item) } }
            val memoizedPlayClick = onPlayClick?.let { click -> remember(item, click) { { click(item) } } }
            // The slot tracks its own TV focus for the trickplay peek.
            // The loader's gate (pref ∧ TV ∧ ¬performance ∧ projected
            // Trickplay ∧ resume position) keeps it inert everywhere else, so
            // touch and suppressed cards render exactly as before.
            val peekFocused = remember { mutableStateOf(false) }
            val trickplayPeek = rememberWideCardTrickplayPeek(
                item = mediaItem,
                isFocused = { peekFocused.value },
            )
            WideMediaCard(
                item = mediaItem,
                imageUrl = imageUrl(item),
                backdropUrl = backdropUrl(item),
                onClick = memoizedClick,
                onPlayClick = memoizedPlayClick,
                cardWidth = cardWidth,
                surfaceScrimBrush = surfaceScrimBrush,
                modifier = mod.onFocusChanged { peekFocused.value = it.hasFocus },
                clipToShape = clippingEnabled,
                trickplayPeek = trickplayPeek,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomeMediaRow(
    title: String,
    items: List<MediaItem>,
    imageUrlBuilder: (MediaItem) -> String,
    fallbackImageUrlBuilder: (MediaItem) -> List<String>,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    photoFolderChildUrlsFor: (String) -> Flow<List<String>> = { flowOf(emptyList()) },
    focusRequester: FocusRequester? = null,
    onRowFocused: (() -> Unit)? = null,
    clippingEnabled: Boolean = false,
    showEpisodeSeriesBadge: Boolean = false,
    /**
     * NEW-badge seam: only the arrival rows (LATEST_MEDIA /
     * RECENTLY_ADDED — UPCOMING joins here when its row lands) pass true;
     * [homeLastVisitEpochMs] carries the PREVIOUS visit's stamp (the
     * session's own stamp must not clear the badges it just showed).
     * Together with the card prefs' global `showNewBadges` toggle and the
     * per-item predicate this decides each card's [NewBadge].
     */
    showNewBadges: Boolean = false,
    homeLastVisitEpochMs: Long = 0L,
    /**
     * Renders each card's air-date chip ([HomeAirsDateChip], the details
     * screen's "Airs <date>" vocabulary) in the poster's top-start
     * corner. The Upcoming row is the only caller — its items are unaired
     * episodes whose [MediaItem.premiereDate] is the point of the row.
     */
    showAirDateChips: Boolean = false,
    onSectionLongClick: (() -> Unit)? = null,
    onSeeAllClick: (() -> Unit)? = null,
    /**
     * Per-item progress override for rows whose items carry no usable
     * `runTimeTicks` — the Continue Reading row passes the refresher's
     * book-fraction decodes (paged page fractions need the TOC cache's page
     * count, which [MediaItem.progressFraction] cannot know). Null (default)
     * keeps the video position/runtime math.
     */
    progressFractionFor: ((MediaItem) -> Float?)? = null,
    // TV-only: reports the D-pad-focused item so the screen's Menu key can open
    // its quick actions
    onFocusedItemChange: ((MediaItem) -> Unit)? = null,
    // Resolves the series' poster URL FROM the item (series id + its image
    // tag) — used so episode cards render the show's poster instead of the
    // episode's own primary image (a landscape scene grab), and so a
    // re-uploaded series poster mints a new URL on these cards. See
    // [EpisodePosterResolver] usage below.
    seriesPosterResolver: (MediaItem) -> String = { "" },
    // Resolves a series' backdrop URL by id — middle fallback when a series has
    // no Primary image (common for freshly-added series whose library scan has
    // not yet generated a poster). See [EpisodePosterResolver].
    seriesBackdropResolver: (String) -> String = { "" },
    // Dice affordance for RANDOM-sorted custom discover rows — renders in the
    // row header next to the title. While the re-fetch is in flight the icon
    // tumbles and further taps are ignored.
    onRollClick: (() -> Unit)? = null,
    rollInProgress: Boolean = false,
    rollAccessibilityLabel: String? = null,
    // Touch-only edge-pull refresh wiring — forwarded to [HomeItemRow]; null
    // (offline/Seerr rows, non-refreshable types) keeps the gesture off.
    edgeRefresh: EdgeRefreshContext? = null,
) {
    val isTv = LocalTvMode.current
    val cardPrefs = LocalCardDisplayPreferences.current
    val metrics = homeRowMetrics()
    val cardWidth = metrics.cardWidth
    // Apply "hide watched items" filter at the row wrapper so the entire card
    // (and its slot in the scroller) disappears rather than leaving a gap.
    val effectiveItems = remember(items, cardPrefs.hideWatchedItems) {
        hideWatchedFilter(items, cardPrefs.hideWatchedItems) { it.isPlayed }
    }
    // When the filter empties a row, hide the whole row (header included) so
    // the user doesn't see section titles for fully-watched content.
    if (effectiveItems.isEmpty()) return
    // The poster bottom-scrim gradient is identical across every card in the
    // same theme state, so compute it once per row instead of per card.
    val posterSurfaceColor = MaterialTheme.colorScheme.surface
    val posterScrimBrush = remember(posterSurfaceColor) { posterScrim(posterSurfaceColor) }

    // The See All pill hugs the header's trailing edge, so geometric focus search
    // from the cards below rarely ranks it. Redirect Up-exits from the row to the
    // pill explicitly (sections without one keep the default search).
    val seeAllFocusRequester = remember { FocusRequester() }
    val rowModifier = if (isTv && onSeeAllClick != null) {
        Modifier.seeAllExitOnUp(seeAllFocusRequester)
    } else {
        Modifier
    }

    Column(modifier = modifier) {
        HomeRowTitle(
            title = title,
            contentPad = metrics.contentPad,
            onLongClick = onSectionLongClick,
            onSeeAllClick = onSeeAllClick,
            seeAllFocusRequester = seeAllFocusRequester,
            onRollClick = onRollClick,
            rollInProgress = rollInProgress,
            accessibilityLabel = rollAccessibilityLabel,
        )
        HomeItemRow(
            items = effectiveItems,
            key = { it.id },
            cardWidth = cardWidth,
            spacing = metrics.spacing,
            contentPad = metrics.contentPad,
            clippingEnabled = clippingEnabled,
            modifier = rowModifier,
            focusRequester = focusRequester,
            onRowFocused = onRowFocused,
            onFocusedItemChange = { item -> onFocusedItemChange?.invoke(item) },
            edgeRefresh = edgeRefresh,
        ) { item, mod ->
            val memoizedClick = remember(item) { { onItemClick(item) } }
            val memoizedPlayClick = onPlayClick?.let { click -> remember(item, click) { { click(item) } } }
            // The per-card NEW decision — the row's section flag ∧ the
            // card prefs' global toggle ∧ the item's arrival-vs-previous-visit
            // predicate. Resolved here (not in the card) so the card stays a
            // pure renderer and the remember slots stay in the row.
            val isNewArrival = remember(item.id, item.dateCreatedEpochMs, homeLastVisitEpochMs) {
                item.isNewForHome(homeLastVisitEpochMs)
            }
            val showNewBadge = showNewBadges && cardPrefs.showNewBadges && isNewArrival
            // Memoize progress so per-card arithmetic runs only when ticks change,
            // not on every scroll/animation-frame recompose (matches WideMediaCard).
            // The override path skips the memo: a map lookup, cheaper than the
            // remember slots it would need.
            val progressPercent = if (progressFractionFor != null) {
                progressFractionFor(item) ?: 0f
            } else {
                remember(item.id, item.playbackPositionTicks, item.runTimeTicks) {
                    item.progressFraction() ?: 0f
                }
            }
            // Per-item collection: only photo-folder cards subscribe, and only
            // the affected card recomposes on a prefetch merge.
            val photoFolderChildImageUrls by if (item.mediaType == MediaType.PHOTO_FOLDER) {
                remember(item.id) { photoFolderChildUrlsFor(item.id) }
                    .collectAsStateWithLifecycle(emptyList())
            } else {
                remember { mutableStateOf(emptyList()) }
            }
            val cardImage = rememberEpisodeCardImage(
                item = item,
                itemImageUrl = remember(item) { imageUrlBuilder(item) },
                fallbackImageUrls = remember(item) { fallbackImageUrlBuilder(item) },
                seriesPosterResolver = seriesPosterResolver,
                seriesBackdropResolver = seriesBackdropResolver,
                showEpisodeSeriesBadge = showEpisodeSeriesBadge,
            )
            // Upcoming air-date badge — rendered in the poster's top-left
            // overlay stack via [PosterCard.topStartBadge].
            val airDate = if (showAirDateChips) {
                remember(item.id, item.premiereDate) { upcomingAirDate(item.premiereDate) }
            } else null
            PosterCard(
                item = item,
                imageUrl = cardImage.imageUrl,
                fallbackUrls = cardImage.fallbackUrls,
                onClick = memoizedClick,
                modifier = mod.width(cardWidth),
                showProgress = item.hasPlaybackPosition,
                progressPercent = progressPercent,
                // Continue Reading rows decode the book fraction against the
                // TOC cache's page count; hand the same value to the footer so
                // the "% complete" label matches the card's progress bar.
                // Book admission lives in bookFooterPercent.
                bookProgressFractionOverride = progressPercent,
                blurHash = cardImage.blurHash,
                onPlayClick = memoizedPlayClick,
                sharedElementKey = "poster_${item.id}",
                photoFolderChildImageUrls = photoFolderChildImageUrls,
                clipToShape = clippingEnabled,
                showEpisodeSeriesBadge = cardImage.showSeriesBadge,
                gradientBrush = posterScrimBrush,
                showNewBadge = showNewBadge,
                topStartBadge = if (airDate != null) {
                    { HomeAirsDateChip(date = airDate) }
                } else null,
            )
        }
    }
}

/**
 * The My Media tiles row — home's only non-poster shape. One landscape
 * tile per Jellyfin library ([HomeLibraryTile]): the library's Primary image
 * over a legibility scrim, the library name, and — when a payload ever
 * carries one — the item count. Everything else mirrors [HomeMediaRow]
 * exactly: the [HomeItemRow] chassis (TV focus / touch scroller), the shared
 * row metrics (wide-card scale — the tiles are landscape), the
 * [HomeRowTitle] with the section long-press. The items are the assembler's
 * FOLDER-typed library tiles; there is no edge refresh (the row assembles the
 * batch's shared folders read — a single-row refetch would re-run what the
 * batch just fetched) and no offline arm (library art is a server artifact;
 * the offline mirror drops the row).
 *
 * @param onTileClick routes into the library screen (the dock drawer's
 *   construction — id + name — through the `Route.LibrarySection` carrier).
 * @param onTileLongClick the tile-level curation affordance, mirroring the
 *   row long-press pattern: opens the inline section-config sheet for
 *   My Media scoped to the long-pressed library.
 */
@Composable
internal fun HomeLibraryTilesRow(
    title: String,
    items: List<MediaItem>,
    imageUrlFor: (MediaItem) -> String,
    onTileClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onRowFocused: (() -> Unit)? = null,
    clippingEnabled: Boolean = false,
    onTileLongClick: ((MediaItem) -> Unit)? = null,
    onSectionLongClick: (() -> Unit)? = null,
    // TV-only: reports the D-pad-focused tile so the screen's Menu key can
    // open its quick actions.
    onFocusedItemChange: ((MediaItem) -> Unit)? = null,
) {
    val metrics = homeRowMetrics(widthScale = 1.6f)
    if (items.isEmpty()) return
    Column(modifier = modifier) {
        HomeRowTitle(
            title = title,
            contentPad = metrics.contentPad,
            onLongClick = onSectionLongClick,
        )
        HomeItemRow(
            items = items,
            key = { it.id },
            cardWidth = metrics.cardWidth,
            spacing = metrics.spacing,
            contentPad = metrics.contentPad,
            clippingEnabled = clippingEnabled,
            focusRequester = focusRequester,
            onRowFocused = onRowFocused,
            onFocusedItemChange = { item -> onFocusedItemChange?.invoke(item) },
        ) { item, mod ->
            val memoizedClick = remember(item, onTileClick) { { onTileClick(item) } }
            val memoizedLongClick = onTileLongClick?.let { click ->
                remember(item, click) { { click(item) } }
            }
            HomeLibraryTile(
                item = item,
                imageUrl = remember(item) { imageUrlFor(item) },
                onClick = memoizedClick,
                onLongClick = memoizedLongClick,
                modifier = mod.width(metrics.cardWidth),
            )
        }
    }
}

/**
 * One library tile: the library's Primary image (the same id-based resolver
 * every server card uses) filling a 16:9 smooth-corner slot, a bottom scrim
 * for legibility, the library name bottom-start, and the item count
 * bottom-end when the payload carries one (the folders read doesn't today —
 * the slot stays reserved for when it does). `combinedClickable` gives both
 * form factors their gesture: tap routes into the library, long-press opens
 * the tile's curation sheet.
 */
@Composable
private fun HomeLibraryTile(
    item: MediaItem,
    imageUrl: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        MediaImage(
            url = imageUrl,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            placeholderIcon = Tabler.Outline.Folders,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                    ),
                ),
        )
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
        )
        // Item count — rendered only when a payload carries one; the current
        // folders read projects no counts, so tiles render name-only.
        val itemCount = item.recursiveItemCount ?: item.childCount
        if (itemCount != null && itemCount > 0) {
            Text(
                text = itemCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 8.dp),
            )
        }
    }
}

/**
 * The air-date chip's pure input: the Upcoming card's premiere stamp
 * parsed to a [LocalDate], or null (absent / unparseable) — the chip simply
 * doesn't render, never lies. Shares the core-ui date decoders the details
 * screen's MissingEpisodeBadge.Airs uses, so both surfaces agree on what a
 * premiere stamp means. Pinned by [HomeMediaRowsTest].
 */
internal fun upcomingAirDate(premiereDate: String?): LocalDate? =
    premiereDate?.let(::localDateFromIsoTimestamp)

/**
 * The "Airs <date>" chip on an Upcoming card — a replica of the details
 * screen's `MissingEpisodeBadge.Airs` tag (the same dark-scrim Surface, the
 * same labelSmall bold label) so the two surfaces read as one vocabulary;
 * the details module is not imported (home replicates the look, it does not
 * depend on it). Overlay-only: rendered in the poster's top-start corner,
 * never inline with the card text.
 */
@Composable
internal fun HomeAirsDateChip(
    date: LocalDate,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White.copy(alpha = 0.9f),
        shape = ShapeCache.smooth12,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.CalendarTime,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = stringResource(Res.string.home_airs_date_format, shortMonthDayYear(date)),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Music fallback-art candidates for a home card: an AUDIO/MUSIC track's own
 * primary image is usually blank, so the card falls back to its parent
 * (album) artwork first, then to the first listed artist's artwork —
 * [PosterCard] tries the candidates in order. Non-audio items carry no
 * fallback chain (empty list). Pure and Compose-free — pinned by
 * [HomeMediaRowsTest]; HomeScreen's rememberFallbackUrls is only a remembered
 * wrapper over this (keeps the builder's remember(viewModel) stability there).
 * Lives beside the row code because it is the card-slot's image policy (this
 * file is the module's Compose-free pure-helper home, alongside
 * hideWatchedFilter/posterScrim).
 */
internal fun fallbackImageUrls(
    item: MediaItem,
    getImageUrl: (String) -> String,
): List<String> =
    if (item.mediaType == MediaType.AUDIO || item.mediaType == MediaType.MUSIC) {
        listOfNotNull(
            item.parentId?.let(getImageUrl),
            item.artistItems.firstOrNull()?.id?.let(getImageUrl),
        )
    } else {
        emptyList()
    }

/**
 * The photo-folder prefetch input ([HomeUiEvent.PrefetchPhotoFolderChildUrls]):
 * only photo-folder cards consume child-image URLs, so the screen narrows each
 * sections emission to the PHOTO_FOLDER items across all sections — keeping
 * both the per-emission allocation and the prefetch LaunchedEffect key
 * proportional to the number of photo folders rather than to every item on
 * home. Order preserved (folders keep their section order). Pure and
 * Compose-free — pinned by [HomeMediaRowsTest]; lives with the other
 * MediaItem-collection policies in this file rather than inside the
 * HomeScreen orchestrator.
 */
internal fun photoFolderPrefetchTargets(sections: List<HomeSection>): List<MediaItem> =
    sections.asSequence().flatMap { it.items }.filter { it.mediaType == MediaType.PHOTO_FOLDER }.toList()

/**
 * THE home row title — one implementation for every row-ish header in the
 * module (section rows, the discover and *arr headers, the inline Downloaded
 * row). Renders the heading typography and the optional affordances:
 * - a long-press ([onLongClick]) to configure the section (toggle visibility
 *   / reorder / open Home Layout settings), and
 * - a "See All" affordance ([onSeeAllClick]) that opens the full library
 *   screen for that section with pre-applied filters.
 *
 * On touch, the title surface still only intercepts long-press so the row's own
 * horizontal scroll and item clicks are unaffected; "See All" is a separate
 * trailing pill so it can be tapped without intercepting the title. On TV the
 * "See All" pill is focusable (D-pad); the long-press is touch-only — section
 * configuration on TV goes through Settings → Home Layout. The title surface
 * must NOT be focusable on TV: a full-width indication-less clickable between
 * the rows is an invisible focus trap that also steals focus from the pill.
 *
 * [standalone] distinguishes the standalone headers ([HomeContentList]'s
 * discover and *arr rows and the inline Downloaded row) from section-row
 * titles: they get 24 dp of top spacing (section rows take 8 dp, the row
 * already adds its own) and keep the titleLarge style on every form factor,
 * as their former bespoke implementations did.
 */
@Composable
internal fun HomeRowTitle(
    title: String,
    contentPad: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    standalone: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onSeeAllClick: (() -> Unit)? = null,
    seeAllFocusRequester: FocusRequester? = null,
    /** Dice affordance for RANDOM-sorted custom discover rows: re-rolls the row's items. */
    onRollClick: (() -> Unit)? = null,
    /** True while that re-roll's fetch is in flight — tumbles the dice icon and absorbs taps. */
    rollInProgress: Boolean = false,
    accessibilityLabel: String? = null,
) {
    val isTv = LocalTvMode.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null && !isTv) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {},
                        onLongClick = onLongClick,
                    )
                } else Modifier,
            )
            .padding(
                start = contentPad,
                end = contentPad,
                top = if (standalone) 24.dp else 8.dp,
                bottom = 8.dp,
            ),
    ) {
        Text(
            text = title,
            style = (
                if (isTv && !standalone) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.titleLarge
                ).copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onRollClick != null) {
            Spacer(modifier = Modifier.width(12.dp))
            // The dice tumbles while the re-fetch runs — the only affordance
            // feedback a roll gets (no spinner, content stays). Reduced-motion
            // users keep the static icon; the tap guard below still applies.
            val diceRotation = if (rollInProgress && !isReducedMotion()) {
                val transition = rememberInfiniteTransition(label = "diceRoll")
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = LinearEasing),
                    ),
                    label = "diceTumble",
                ).value
            } else {
                0f
            }
            IconButton(
                onClick = onRollClick,
                enabled = !rollInProgress,
            ) {
                Icon(
                    imageVector = Tabler.Outline.Dice,
                    contentDescription = accessibilityLabel,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(diceRotation),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (onSeeAllClick != null) {
            Spacer(modifier = Modifier.width(12.dp))
            SeeAllPill(
                onClick = onSeeAllClick,
                modifier = seeAllFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
            )
        }
    }
}

/**
 * The trailing "See All ›" affordance on a home section title. A compact glass
 * pill matching the [GlassPill] / GlassFilterChip idiom (shape morph + press
 * scale + TV focus, via [ExpressiveChipContainer]), with a trailing chevron so
 * it reads as navigational.
 */
@Composable
private fun SeeAllPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = LocalIsLightTheme.current
    val bgColor = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
    val contentColor = MaterialTheme.colorScheme.onSurface
    ExpressiveChipContainer(
        onClick = onClick,
        modifier = modifier,
        containerColor = bgColor,
        contentPadding = PaddingValues(6.dp),
    ) {
        Icon(
            imageVector = Tabler.Outline.ChevronRight,
            contentDescription = stringResource(Res.string.home_see_all),
            modifier = Modifier.size(18.dp),
            tint = contentColor,
        )
    }
}

