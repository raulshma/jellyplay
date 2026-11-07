package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.image.rememberMediaImageMemoryCacheKey
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

/**
 * The portrait detail body's VIRTUALIZED composition: instead of one
 * non-virtualized LazyColumn item holding the whole section column, each
 * logical section is its own list item (stable [LazyListScope.item] key +
 * contentType), and the compact episode list is individual items in the same
 * list (see [detailSeasonBandItems]). Only the viewport's items compose on
 * open, and a section further down composes when scrolled to rather than
 * eagerly on entry.
 *
 * The layout result is kept identical to the former single-item column:
 *  - the hero slot (poster + title block + action row) paints the same
 *    backdrop-fade gradient at its top;
 *  - every slot paints the same animated body background across its full
 *    bounds, spacing included, so theme-brush variants — where the screen root
 *    paints a brush rather than the flat colour — show through nowhere (the
 *    former body painted one continuous rect);
 *  - the former `spacedBy(24.dp)` section rhythm becomes a per-slot bottom pad
 *    (24; the body's 48 dp tail on the last slot), and an admitted slot whose
 *    content renders empty still occupies its pad — the former empty slot's
 *    gap.
 */

/** Bottom pad between section slots — the former body column's spacedBy gap. */
internal val DETAIL_SECTION_SPACING: Dp = 24.dp

/** Bottom pad of the body's last slot — the former body column's bottom padding. */
internal val DETAIL_BODY_TAIL_SPACING: Dp = 48.dp

/**
 * The portrait body's items: the hero band (poster + the pinned header/action
 * sections), then every remaining admitted section in the registry's emission
 * order, with the seasons slot expanded into its band's items. [state] is the
 * body's merged view (base content + the late-enrichment leaves) so the
 * section modules read the same fields they always did.
 */
internal fun LazyListScope.detailBodyPortraitItems(
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    scrollState: DetailScrollState,
    contentVisible: Boolean,
    contentFocusRequester: FocusRequester?,
    isExpanded: Boolean,
    isTv: Boolean,
    item: MediaItem?,
    detail: MediaDetail?,
    bodyContentPad: Dp,
    /** The body's admitted sections in emission order (empty while nothing is loaded). */
    admittedSections: List<DetailSectionKind>,
    /** The hoisted seasons band (null while nothing is loaded / the tree has no seasons). */
    seasonsBand: SeasonsBandModel?,
) {
    // The hero band owns the body's first slots: the pinned chrome kinds
    // (HEADER, then ACTION_ROW — their canonical pinned order) are what sat
    // directly under the poster overlap, inside the fade band.
    val heroKinds = admittedSections.takeWhile {
        it == DetailSectionKind.HEADER || it == DetailSectionKind.ACTION_ROW
    }
    val remainingSections = admittedSections.drop(heroKinds.size)
    val lastSection = remainingSections.lastOrNull()

    item(key = "detail_hero", contentType = "detail_hero") {
        DetailBodyHeroSlot(
            scrollState = scrollState,
            bottomSpacing = if (remainingSections.isEmpty()) DETAIL_BODY_TAIL_SPACING else DETAIL_SECTION_SPACING,
        ) {
            DetailPortraitHeroContent(
                state = state,
                callbacks = callbacks,
                scrollState = scrollState,
                contentVisible = contentVisible,
                contentFocusRequester = contentFocusRequester,
                isExpanded = isExpanded,
                isTv = isTv,
                item = item,
                detail = detail,
                bodyContentPad = bodyContentPad,
                heroKinds = heroKinds,
            )
        }
    }

    if (detail == null || item == null) return

    remainingSections.forEach { kind ->
        if (kind == DetailSectionKind.SEASONS) {
            detailSeasonBandItems(
                band = seasonsBand,
                scrollState = scrollState,
                sectionTailSpacing = if (kind == lastSection) DETAIL_BODY_TAIL_SPACING else DETAIL_SECTION_SPACING,
            )
        } else {
            item(
                key = "detail_section_${kind.name}",
                contentType = "detail_section_${kind.name}",
            ) {
                DetailBodySectionSlot(
                    scrollState = scrollState,
                    bottomSpacing = if (kind == lastSection) DETAIL_BODY_TAIL_SPACING else DETAIL_SECTION_SPACING,
                ) {
                    // Every kind is module-owned (DetailSectionRegistryTest's
                    // completeness pin), so the dispatch cannot miss.
                    val moduleScope = DetailSectionRenderScope(
                        state = state,
                        callbacks = callbacks,
                        detail = detail,
                        item = item,
                        isLocalOrigin = state.core.origin?.isLocal == true,
                        bodyContentPad = bodyContentPad,
                        contentFocusRequester = contentFocusRequester,
                    )
                    requireNotNull(DetailSectionRegistry.moduleFor(kind)) {
                        "no DetailSectionModule registered for $kind"
                    }.Content(moduleScope)
                }
            }
        }
    }
}

/**
 * The hero band's content: the overlapping poster block and the pinned header
 * + action-row sections, verbatim from the former portrait body column (the
 * poster's overlap offset, the spacer, the content AnimatedVisibility, the
 * `key(section)`-anchored module dispatch).
 */
@Composable
private fun DetailPortraitHeroContent(
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    scrollState: DetailScrollState,
    contentVisible: Boolean,
    contentFocusRequester: FocusRequester?,
    isExpanded: Boolean,
    isTv: Boolean,
    item: MediaItem?,
    detail: MediaDetail?,
    bodyContentPad: Dp,
    heroKinds: List<DetailSectionKind>,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    // Books on compact phones: the hero backdrop already shows the cover
    // sharp edge-to-edge (BookCoverHero.SHARP), so the poster card would be
    // a duplicate. Tablet/TV/desktop keep the poster next to the blurred hero.
    val hideBookPoster = item?.mediaType == MediaType.BOOK && !isTv && !isExpanded

    Column(modifier = Modifier.padding(top = 0.dp)) {
        if (!hideBookPoster) {
            val posterWidth = when {
                isTv -> 160.dp
                isExpanded -> 140.dp
                else -> 120.dp
            }
            val posterHeight = posterWidth * 1.2f
            val overlap = 40.dp
            val boxHeight = posterHeight - overlap
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(boxHeight),
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = adaptiveInfo.contentPadding(isTv))
                        .offset(y = -overlap),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = EnterTransition.None,
                        exit = ExitTransition.None,
                    ) {
                        FadingItem(
                            modifier = Modifier
                                .width(posterWidth)
                                .requiredHeight(posterHeight),
                        ) {
                            // The list→detail handoff: the grid tile the user
                            // tapped (PosterCard) decoded this same poster URL
                            // at 360×540 — pass that request's memory-cache key
                            // so Coil paints the tile's cached bitmap as the
                            // placeholder while this larger decode runs. The
                            // loaded item's id IS state.itemId, so the resolver
                            // tags the URL with the item's own Primary tag.
                            val posterUrl = state.core.assets.posterPath
                                ?: item?.let { callbacks.artwork.urls.poster(it) }
                                ?: ""
                            MediaImage(
                                // Prefer the on-disk poster (DetailAssets.posterPath)
                                // for a LOCAL origin before the server fallback.
                                url = posterUrl,
                                contentDescription = null,
                                blurHash = item?.blurHashes?.primary,
                                placeholderMemoryCacheKey = rememberMediaImageMemoryCacheKey(
                                    url = posterUrl,
                                    size = coil3.size.Size(360, 540),
                                ),
                                // Portrait poster (~120 dp × 3× ≈ 432 px) — decode a
                                // right-sized thumbnail instead of the default 512×512
                                // (or larger) bitmap.
                                size = coil3.size.Size(480, 600),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(ShapeCache.smooth8)
                                    .graphicsLayer { alpha = scrollState.contentAlphaState.value },
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (detail != null && item != null) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DETAIL_SECTION_SPACING),
                ) {
                    heroKinds.forEach { kind ->
                        // Key each slot by its kind so a membership change on item
                        // switch keeps per-section state anchored to its identity
                        // (the body column's former key(section) discipline).
                        key(kind) {
                            val moduleScope = DetailSectionRenderScope(
                                state = state,
                                callbacks = callbacks,
                                detail = detail,
                                item = item,
                                isLocalOrigin = state.core.origin?.isLocal == true,
                                bodyContentPad = bodyContentPad,
                                contentFocusRequester = contentFocusRequester,
                            )
                            requireNotNull(DetailSectionRegistry.moduleFor(kind)) {
                                "no DetailSectionModule registered for $kind"
                            }.Content(moduleScope)
                        }
                    }
                }
            }
        } else {
            // The portrait body's inline error slot (the full-screen overlay
            // paints beneath the list; this is the visible error surface).
            val err = state.core.loadState as? DetailUiLoadState.Error
            if (err != null) {
                ErrorScreen(
                    message = err.message,
                    onRetry = if (err.accessDenied) null else callbacks.screen.onRetry,
                )
            }
        }
    }
}

/**
 * The hero band's slot: paints the body's backdrop-fade gradient (transparent
 * at the body's top edge, resolved to the background colour 150 dp down — the
 * exact brush the former single body item painted) across the slot's full
 * bounds, spacing included. The colour is read inside drawBehind so
 * colour-animation frames rebuild the brush without recomposing anything.
 */
@Composable
private fun DetailBodyHeroSlot(
    scrollState: DetailScrollState,
    bottomSpacing: Dp,
    content: @Composable () -> Unit,
) {
    val gradientEndPx = with(LocalDensity.current) { 150.dp.toPx() }
    val fadeColor by scrollState.backgroundColorState
    val fadeBrush = remember(fadeColor, gradientEndPx) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                fadeColor.copy(alpha = 0.9f),
                fadeColor,
            ),
            startY = 0f,
            endY = gradientEndPx,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(fadeBrush) }
            .padding(bottom = bottomSpacing),
    ) {
        content()
    }
}

/**
 * One section slot's background: the animated body background colour across
 * the slot's full bounds (the bottom spacing included). Read inside drawBehind
 * so a scroll-driven colour change re-draws without recomposing the slot.
 */
@Composable
internal fun DetailBodySectionSlot(
    scrollState: DetailScrollState,
    bottomSpacing: Dp,
    content: @Composable () -> Unit,
) {
    val backgroundColorState = scrollState.backgroundColorState
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(backgroundColorState.value) }
            .padding(bottom = bottomSpacing),
    ) {
        content()
    }
}
