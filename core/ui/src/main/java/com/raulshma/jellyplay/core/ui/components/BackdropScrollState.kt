package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Parallax scroll state shared by detail screens that float a [BackdropLayer]
 * behind a [LazyListState]. Drives the backdrop's translation/alpha fade and the
 * top bar's container/title fade-in as the user scrolls past the backdrop.
 *
 * The math mirrors the offline detail screen: the first list item is the
 * backdrop spacer (height `backdropHeight/1.2f - 150dp`), so the raw scroll
 * offset maps directly to how far the backdrop has scrolled. The top bar
 * collapses once the user scrolls past ~70% of the backdrop.
 *
 * Usage:
 * ```
 * val scrollState = rememberBackdropScrollState(listState, backdropHeight)
 * BackdropLayer(scrollTranslationY = { -scrollState.scrollOffsetState.value * 0.5f },
 *               scrollAlpha = { 1f - (scrollState.scrollFractionState.value * 0.8f) }, ...)
 * TransparentTopBar(containerColor = scrollState.containerColor,
 *                   titleAlpha = scrollState.titleAlpha,
 *                   scrollCollapsed = scrollState.scrollCollapsed, ...)
 * ```
 *
 * @param listState the screen's [LazyListState]. The first item MUST be the
 *   backdrop spacer with height `(backdropHeight / 1.2f) - 150.dp`.
 * @param backdropHeight the full backdrop height (e.g. [com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight.Portrait]).
 */
@Composable
fun rememberBackdropScrollState(
    listState: LazyListState,
    backdropHeight: Dp,
): BackdropScrollState {
    val density = LocalDensity.current
    val baseBackdropHeight = backdropHeight / 1.2f
    // Key the px computations on density/height so the derived states rebuild if
    // either changes (e.g. window resize, theme density). When these are
    // constant the keys never fire — no extra recomposition — but the capture
    // stays correct instead of going stale.
    val spacerHeightPx = with(density) { (baseBackdropHeight - 150.dp).toPx() }
    val collapsedHeightPx = with(density) { backdropHeight.toPx() }

    val scrollOffsetState = remember(spacerHeightPx) {
        derivedStateOf {
            (if (listState.firstVisibleItemIndex > 0) spacerHeightPx else 0f) +
                listState.firstVisibleItemScrollOffset.toFloat()
        }
    }
    val scrollFractionState = remember(spacerHeightPx, collapsedHeightPx) {
        derivedStateOf { (scrollOffsetState.value / collapsedHeightPx).coerceIn(0f, 1f) }
    }

    // Collapse the top bar once the user has scrolled past ~70% of the backdrop.
    // The threshold is itself derived state, so the composition only invalidates
    // when the boolean flips — not on every scrolled pixel.
    val scrollPastCollapse by remember {
        derivedStateOf { scrollFractionState.value > 0.7f }
    }
    val scrollCollapsed by animateFloatAsState(
        targetValue = if (scrollPastCollapse) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "backdropScrollCollapsed",
    )

    val containerColor = lerp(
        Color.Transparent,
        MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
        scrollCollapsed,
    )

    return BackdropScrollState(
        scrollOffsetState = scrollOffsetState,
        scrollFractionState = scrollFractionState,
        scrollCollapsed = scrollCollapsed,
        containerColor = containerColor,
        titleAlpha = scrollCollapsed,
    )
}

/**
 * Parallax scroll + top-bar collapse state for a detail screen. Produced by
 * [rememberBackdropScrollState].
 *
 * [scrollOffsetState] and [scrollFractionState] are exposed as [State]s so
 * scroll-driven consumers can read `.value` inside graphicsLayer/draw lambdas
 * — the backdrop subtree then updates in the draw phase only, without
 * recomposing on every scrolled pixel.
 *
 * @param scrollOffsetState [State] of the raw px the backdrop spacer has
 *   scrolled (0 until item 0 is fully scrolled away).
 * @param scrollFractionState [State] of [scrollOffsetState] normalized to 0..1
 *   across the backdrop.
 * @param scrollCollapsed animated 0f→1f; crosses 0.5f once past 70% scroll.
 * @param containerColor lerped top-bar container color (transparent → background).
 * @param titleAlpha top-bar title alpha (tracks [scrollCollapsed]).
 */
@Stable
class BackdropScrollState(
    val scrollOffsetState: State<Float>,
    val scrollFractionState: State<Float>,
    val scrollCollapsed: Float,
    val containerColor: Color,
    val titleAlpha: Float,
)
