package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPlay
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_play
import com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.cardBorder
import com.raulshma.jellyplay.core.designsystem.theme.rememberThemeCardBorder
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.preview.MediaPreview
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus

/**
 * Border override for [MediaCardScaffold]. When [alpha] is non-null the
 * stroke is drawn inside a graphics layer whose alpha this lambda resolves at
 * draw time (instead of being passed to the Card as a fixed stroke) — lets
 * callers animate a border glow without recomposing; the layer-property read
 * invalidates drawing only.
 */
class AnimatedCardBorder(
    val stroke: BorderStroke,
    val alpha: (() -> Float)? = null,
)

/**
 * The card's play (or, for books, read) affordance bundled into one value —
 * the scaffold interface grows one slot per concern, not one per knob. The
 * nullable knobs resolve to the scaffold's historical defaults at render time
 * (they need composition to read the theme / string resources).
 *
 * @param onClick invoked when the affordance is tapped.
 * @param dominantColor progress-ring color; null = [MaterialTheme.colorScheme.primary].
 * @param buttonSize null = 36.dp.
 * @param icon null = [Tabler.Outline.PlayerPlay].
 * @param contentDescription null = the localized "Play" string.
 */
@Immutable
data class PlayAffordance(
    val onClick: () -> Unit,
    val dominantColor: Color? = null,
    val buttonSize: Dp? = null,
    val icon: ImageVector? = null,
    val contentDescription: String? = null,
)

/**
 * The bottom legibility scrim inside the card's image box, bundled (brush +
 * height always move together). Null [brush] = the scaffold's default
 * transparent→surface gradient; [height] defaults to [DefaultCardScrimHeight].
 */
@Immutable
data class CardScrim(
    val brush: Brush? = null,
    val height: Dp = DefaultCardScrimHeight,
)

internal val DefaultCardScrimHeight = 60.dp

/**
 * The deep module behind the media-card family.
 *
 * Owns the card scaffold that every poster/wide card re-implemented by hand:
 * the [Card] container, the themed border ([ThemeVariant.cardBorder]), the
 * bottom scrim, the shared-element transition wiring, the play affordance,
 * the progress bar, and the Column layout (image + title + footer). The
 * interaction chrome itself — focus + press feedback, combined click, the
 * press-and-hold peek plumbing — lives one layer down in [CardChrome]
 * (see CardChrome.kt), so differently-shaped cards (the library ThumbCard,
 * the details episode card) seat on the same chrome without inheriting this
 * Column layout, and a visual change (scrim, border, focus treatment) is a
 * one-file edit instead of three.
 *
 * Variant cards ([PosterCard], [WideMediaCard], [SeerrMediaCard]) are thin
 * specializations that supply content via the slots and never touch the
 * chrome directly.
 *
 * @param image renders the poster/backdrop art, given an [imageModifier] that
 * already carries the [aspectRatio] and (when [sharedElementKey] is set) the
 * shared-element transition. The caller renders its [com.raulshma.jellyplay.core.ui.image.MediaImage]
 * / placeholder with that modifier.
 * @param aspectRatio image aspect ratio — 2:3 for posters, 16:9 for wide cards.
 * @param play the card's primary affordance ([PlayAffordance]); null renders none.
 * @param previewFactory when non-null, wires the press-and-hold peek preview;
 * receives the card's captured bounds so it can populate
 * [MediaPreview.sourceBounds]. Pass `null` (the default) to disable peek.
 * @param onLongPress when non-null, long-press fires this instead of the peek
 * preview (e.g. a quick-action sheet wired by the host screen).
 * @param scrim the bottom legibility scrim ([CardScrim]); null = default
 * gradient at [DefaultCardScrimHeight].
 * @param overlays badges, chips, shimmer, brightness tints — anything drawn
 * on top of the image (z-order: image → scrim → overlays → play → progress).
 * @param footer the meta row beneath the title (year • runtime, series info…).
 */
@Composable
fun MediaCardScaffold(
    onClick: () -> Unit,
    image: @Composable BoxScope.(imageModifier: Modifier) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f / 3f,
    enabled: Boolean = true,
    clipToShape: Boolean = false,
    cardWidth: Dp? = null,
    play: PlayAffordance? = null,
    sharedElementKey: String? = null,
    scrim: CardScrim? = null,
    border: AnimatedCardBorder? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    previewFactory: ((sourceBounds: Rect?) -> MediaPreview)? = null,
    onLongPress: (() -> Unit)? = null,
    overlays: @Composable BoxScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
    showProgress: Boolean = false,
    progressFraction: Float = 0f,
) {
    val isTv = LocalJellyPlayUi.current.isTv
    // Interaction chrome (focus tracking, press scale, peek wiring, click) is
    // the shared layer; this scaffold is one of its layouts.
    val chrome = rememberCardChrome(previewFactory = previewFactory)
    val focusScale = chrome.focus.scale
    val themeVariant = LocalThemeVariant.current
    val cardShape = ShapeCache.smooth12

    // Shared-element transition: wrap the image when a key and both scopes are
    // present. Only PosterCard participates today; Wide/Seerr gain it for free
    // when a caller passes a sharedElementKey.
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val canShareElement = sharedElementKey != null &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null
    @OptIn(ExperimentalSharedTransitionApi::class)
    val sharedImageModifier = if (canShareElement) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = sharedElementKey),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(aspectRatio)
        .then(sharedImageModifier)

    val resolvedBorder = border?.stroke ?: rememberThemeCardBorder(themeVariant)
    val borderAlpha = border?.alpha
    val surfaceColor = MaterialTheme.colorScheme.surface
    val resolvedScrimBrush = scrim?.brush ?: remember(surfaceColor) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.45f),
            ),
        )
    }
    val resolvedScrimHeight = scrim?.height ?: DefaultCardScrimHeight

    val columnModifier = if (cardWidth != null) modifier.width(cardWidth) else modifier

    Column(modifier = columnModifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(chrome.focus.modifier)
                .then(chrome.peek?.boundsModifier ?: Modifier)
                .then(chrome.pressScale)
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                    shadowElevation = 0f
                    clip = clipToShape
                    shape = cardShape
                }
                .then(
                    if (borderAlpha != null && resolvedBorder != null) {
                        Modifier
                            .graphicsLayer { alpha = borderAlpha().coerceIn(0f, 1f) }
                            .border(resolvedBorder, cardShape)
                    } else Modifier
                )
                .jellyFocusIndicator(chrome.focus, cardShape)
                .then(chrome.clickModifier(onClick = onClick, enabled = enabled, onLongPress = onLongPress)),
            shape = cardShape,
            border = if (borderAlpha != null) null else resolvedBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box {
                image(imageModifier)

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(resolvedScrimHeight)
                        .background(resolvedScrimBrush)
                )

                overlays()

                play?.let {
                    PlayButtonWithProgress(
                        progressPercent = if (showProgress) progressFraction else 0f,
                        dominantColor = it.dominantColor ?: MaterialTheme.colorScheme.primary,
                        onClick = it.onClick,
                        icon = it.icon ?: Tabler.Outline.PlayerPlay,
                        contentDescription = it.contentDescription
                            ?: stringResource(Res.string.core_ui_play),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp),
                        buttonSize = it.buttonSize ?: 36.dp,
                    )
                }

                if (showProgress && progressFraction > 0f) {
                    MediaCardProgressOverlay(
                        progressFraction = progressFraction,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(
                start = 4.dp,
                end = 4.dp,
                top = if (isTv) 8.dp else 6.dp,
            ),
        ) {
            Text(
                text = title,
                style = if (isTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = titleColor,
                modifier = Modifier.enableMarqueeOnFocus(focused = chrome.focus.isFocused),
            )
            footer()
        }
    }
}
