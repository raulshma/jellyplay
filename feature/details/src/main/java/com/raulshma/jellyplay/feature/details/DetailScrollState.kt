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
import androidx.compose.runtime.getValue
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
 * backdrop dimensions, the animated background/container/title-alpha states,
 * and the navigation-bar colour sync side-effect.
 *
 * Scroll-derived values are exposed both as plain snapshot values (read at the
 * call site to pass into non-lambda APIs) and as [State]s (read *inside*
 * `graphicsLayer`/`drawBehind` lambdas). Reading the [State] form inside a
 * draw-phase lambda defers invalidation to the draw phase only, so scroll-driven
 * *visuals* update without recomposing the subtree.
 *
 * Note: this holder is reconstructed fresh each recomposition (the underlying
 * `derivedStateOf`/animation states are remembered by their factories), and its
 * plain `Float`/`Color` fields change every scroll frame, so it is structurally
 * unequal each frame. Composables that take the whole `DetailScrollState` still
 * recompose while scrolling — they just no longer need to. Fully removing them
 * from scroll recomposition requires passing only the individual derived values
 * (e.g. `contentAlpha`) instead of the whole holder; tracked separately.
 */
@Immutable
data class DetailScrollState internal constructor(
    val backdropHeight: Dp,
    val baseBackdropHeight: Dp,
    val scrollOffset: Float,
    val scrollFraction: Float,
    val scrollCollapsed: Float,
    val contentAlpha: Float,
    val backgroundColor: Color,
    val animatedContainerColor: Color,
    val animatedTitleAlpha: Float,
    /**
     * Snapshot-read scroll offset. Read this inside a `graphicsLayer`/`drawBehind`
     * lambda so the consumer only re-invalidates the draw phase (not recomposition)
     * while scrolling.
     */
    val scrollOffsetState: State<Float>,
    val scrollFractionState: State<Float>,
    /**
     * Snapshot-read background color. Read inside a `drawBehind` lambda so a
     * scroll-driven color change re-draws without recomposing the subtree.
     */
    val backgroundColorState: State<Color>,
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
    val scrollOffset by scrollOffsetState
    val scrollFractionState = remember(collapsedHeight) {
        derivedStateOf {
            (scrollOffset / collapsedHeight).coerceIn(0f, 1f)
        }
    }
    val scrollFraction by scrollFractionState

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
    val backgroundColor by backgroundColorState

    val navBarColor = LocalNavigationBarColor.current
    // Write the nav-bar colour only when the target actually changes — a plain
    // SideEffect runs after every recomposition of this caller, which during
    // scroll is every frame. Keying on the target makes this a no-op except on
    // transitions.
    LaunchedEffect(targetBackgroundColor) {
        if (navBarColor.value != targetBackgroundColor) navBarColor.value = targetBackgroundColor
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "contentAlpha",
    )

    val scrollCollapsed by animateFloatAsState(
        targetValue = if (scrollFraction > 0.7f) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "scrollCollapsed",
    )

    val animatedContainerColor = lerp(
        Color.Transparent,
        backgroundColor.copy(alpha = 0.95f),
        scrollCollapsed,
    )
    val animatedTitleAlpha = scrollCollapsed

    // Constructed fresh each recomposition; the underlying animation + derived
    // states are remembered internally by their respective factories, so this
    // holder is a cheap value bag that simply reflects the current snapshot.
    return DetailScrollState(
        backdropHeight = backdropHeight,
        baseBackdropHeight = baseBackdropHeight,
        scrollOffset = scrollOffset,
        scrollFraction = scrollFraction,
        scrollCollapsed = scrollCollapsed,
        contentAlpha = contentAlpha,
        backgroundColor = backgroundColor,
        animatedContainerColor = animatedContainerColor,
        animatedTitleAlpha = animatedTitleAlpha,
        scrollOffsetState = scrollOffsetState,
        scrollFractionState = scrollFractionState,
        backgroundColorState = backgroundColorState,
    )
}
