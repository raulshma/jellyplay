package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.LocalArtworkColors
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.ThemeVariantColors
import com.raulshma.jellyplay.core.designsystem.theme.rememberIsLightTheme
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

/**
 * Resolved backdrop height (the Dp value) shared between the backdrop box and
 * the scroll-math controller so both agree on the parallax window.
 */
enum class DetailBackdropTier(val dp: Dp) {
    Tv(AdaptiveBackdropHeight.Tv),
    LandscapeExpanded(AdaptiveBackdropHeight.LandscapeExpanded),
    Expanded(AdaptiveBackdropHeight.Expanded),
    Portrait(AdaptiveBackdropHeight.Portrait),
}

/**
 * Hoists the media-detail scroll-driven visual state out of [DetailContent].
 *
 * Owns the scroll-offset/fraction `derivedStateOf` chain, the density-derived
 * backdrop dimensions, the animated background/container/collapse/content-alpha
 * states, and the navigation-bar colour sync side-effect.
 *
 * Per-frame values are exposed ONLY as [State] refs: read them *inside*
 * `graphicsLayer`/`drawBehind` lambdas (or collapse them to booleans via
 * `derivedStateOf` in composition) so a scroll-driven change invalidates the
 * draw phase only, or recomposes just the reading scope on a real value flip.
 * The plain fields carry rare-change data (the backdrop dimensions) that
 * composition may read freely.
 *
 * The holder is reconstructed fresh each recomposition (the underlying
 * `derivedStateOf`/animation states are remembered by their factories), but
 * every field is either a remembered [State] ref or a rare-change `Dp`, so it
 * is structurally equal between scroll frames and composables that take the
 * whole [DetailScrollState] skip while scrolling.
 */
@Immutable
data class DetailScrollState internal constructor(
    val backdropHeight: Dp,
    val baseBackdropHeight: Dp,
    /**
     * Snapshot-read scroll offset. Read this inside a `graphicsLayer`/`drawBehind`
     * lambda so the consumer only re-invalidates the draw phase (not recomposition)
     * while scrolling.
     */
    val scrollOffsetState: State<Float>,
    val scrollFractionState: State<Float>,
    /**
     * Snapshot-read collapse progress, 0..1 (1 = bar collapsed; the animation
     * target flips at scroll fraction 0.7). Read inside a `graphicsLayer`
     * lambda, or behind a `derivedStateOf` threshold in composition.
     */
    val scrollCollapsedState: State<Float>,
    /**
     * Snapshot-read content fade (0 while the detail is loading, 1 once the
     * content exists). Read inside a `graphicsLayer` lambda.
     */
    val contentAlphaState: State<Float>,
    /**
     * Snapshot-read background color. Read inside a `drawBehind` lambda so a
     * scroll-driven color change re-draws without recomposing the subtree.
     */
    val backgroundColorState: State<Color>,
    /**
     * Snapshot-read top-bar container colour: transparent lerped to the
     * background at 0.95 alpha as the bar collapses. Read inside a
     * `drawBehind` lambda (or, for M3 `colors` APIs that take plain Colors,
     * in a scope isolated to the bar itself).
     */
    val containerColorState: State<Color>,
)

/**
 * Remembers a [DetailScrollState] wired to [listState] and the current adaptive
 * layout. All density-derived dimensions are keyed on their inputs so they do
 * not reallocate per recompose and so the `derivedStateOf` blocks capture the
 * current value instead of one frozen at first composition (which was a latent
 * bug on rotation / window resize / TV toggle).
 */
@Composable
internal fun rememberDetailScrollState(
    listState: LazyListState,
    contentVisible: Boolean,
): DetailScrollState {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact
    val density = LocalDensity.current

    val backdropTier = when {
        isTv -> DetailBackdropTier.Tv
        adaptiveInfo.isLandscape && isExpanded -> DetailBackdropTier.LandscapeExpanded
        adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded -> DetailBackdropTier.Expanded
        else -> DetailBackdropTier.Portrait
    }
    val backdropHeight = backdropTier.dp

    // Hoist density-derived dimensions into a single remember keyed on their
    // inputs so they don't reallocate per recompose and so the derivedStateOf
    // blocks below capture the current value instead of one frozen at first
    // composition (which was a latent bug on rotation / window resize / TV toggle).
    val (baseBackdropHeight, collapsedHeight, spacerHeightPx) = remember(backdropHeight, density) {
        with(density) {
            val base = (backdropHeight.toPx() / 1.2f).toDp()
            val collapsed = backdropHeight.toPx()
            val spacer = (base - 150.dp).toPx()
            Triple(base, collapsed, spacer)
        }
    }
    val scrollOffsetState = remember(spacerHeightPx) {
        derivedStateOf {
            (if (listState.firstVisibleItemIndex > 0) spacerHeightPx else 0f) + listState.firstVisibleItemScrollOffset.toFloat()
        }
    }
    val scrollFractionState = remember(collapsedHeight) {
        derivedStateOf {
            (scrollOffsetState.value / collapsedHeight).coerceIn(0f, 1f)
        }
    }

    val isLightTheme = rememberIsLightTheme()
    val artworkColors = LocalArtworkColors.current
    // One truth source for variant flags: the resolved variant, not the
    // per-variant booleans (which only cover the three legacy themes).
    val themeVariant = com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant.current
    val isSynthwave = themeVariant == com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant.SYNTHWAVE
    val isSoothing = LocalIsSoothingTheme.current
    val isAurora = themeVariant == com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant.AURORA
    val schemeBackground = MaterialTheme.colorScheme.background

    val baseOverlayColor = artworkColors?.darkMuted
        ?: artworkColors?.dominant
        ?: schemeBackground
    // Depends only on artwork + theme flags, not on scroll state — so memoize
    // it to avoid recomputing the lerp + when on every scroll-driven recompose.
    val targetBackgroundColor = remember(baseOverlayColor, schemeBackground, isSynthwave, isSoothing, isAurora, isLightTheme) {
        when {
            isSynthwave -> ThemeVariantColors.SYNTHWAVE_DETAIL_BG
            isAurora -> ThemeVariantColors.AURORA_DETAIL_BG
            isSoothing -> schemeBackground
            isLightTheme -> schemeBackground
            else -> lerp(baseOverlayColor, Color.Black, 0.65f)
        }
    }
    val backgroundColorState: State<Color> = animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "backgroundColor",
    )

    val navBarColor = LocalNavigationBarColor.current
    // Write the nav-bar colour only when the target actually changes — a plain
    // SideEffect runs after every recomposition of this caller, which during
    // scroll is every frame. Keying on the target makes this a no-op except on
    // transitions.
    LaunchedEffect(targetBackgroundColor) {
        if (navBarColor.value != targetBackgroundColor) navBarColor.value = targetBackgroundColor
    }

    val contentAlphaState = animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "contentAlpha",
    )

    // The animation target flips at the same 0.7 fraction as before, but as a
    // derived boolean: the factory's composition only re-runs on the crossing,
    // while the animation itself drives the exposed State without recomposition.
    val scrollCollapsedTarget = remember(scrollFractionState) {
        derivedStateOf { scrollFractionState.value > 0.7f }
    }
    val scrollCollapsedState = animateFloatAsState(
        targetValue = if (scrollCollapsedTarget.value) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "scrollCollapsed",
    )

    // Container colour derivation of the two animated states, behind
    // derivedStateOf so consumers read it as a State (draw-phase reads) instead
    // of recomposing per animation tick.
    val containerColorState = remember(backgroundColorState, scrollCollapsedState) {
        derivedStateOf {
            lerp(
                Color.Transparent,
                backgroundColorState.value.copy(alpha = 0.95f),
                scrollCollapsedState.value,
            )
        }
    }

    // Constructed fresh each recomposition; the underlying animation + derived
    // states are remembered internally by their respective factories, so this
    // holder is a cheap value bag whose fields are structurally equal between
    // scroll frames.
    return DetailScrollState(
        backdropHeight = backdropHeight,
        baseBackdropHeight = baseBackdropHeight,
        scrollOffsetState = scrollOffsetState,
        scrollFractionState = scrollFractionState,
        scrollCollapsedState = scrollCollapsedState,
        contentAlphaState = contentAlphaState,
        backgroundColorState = backgroundColorState,
        containerColorState = containerColorState,
    )
}
