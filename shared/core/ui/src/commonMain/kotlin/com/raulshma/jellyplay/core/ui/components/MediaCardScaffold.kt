package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.cardBorder
import com.raulshma.jellyplay.core.designsystem.theme.rememberThemeCardBorder
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.animation.isReducedMotion
import com.raulshma.jellyplay.core.ui.animation.pressScale
import com.raulshma.jellyplay.core.ui.preview.MediaPreview
import com.raulshma.jellyplay.core.ui.preview.rememberMediaPeek
import com.raulshma.jellyplay.core.ui.preview.rememberReleaseDismiss
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
 * The deep module behind the media-card family.
 *
 * Owns the card scaffold that every poster/wide card re-implemented by hand:
 * the [Card] container, the unified focus + press feedback ([rememberJellyFocusableInteraction]
 * + [pressScale] — the same system [PosterCard] used), the themed border
 * ([ThemeVariant.cardBorder]), the bottom scrim, the shared-element transition
 * wiring, the press-and-hold peek plumbing, the play button, and the progress
 * bar. Variant cards ([PosterCard], [WideMediaCard], [SeerrMediaCard]) are now
 * thin specializations that supply content via the slots and never touch this
 * chrome directly — so a visual change (scrim, border, focus treatment) is a
 * one-file edit instead of three.
 *
 * @param image renders the poster/backdrop art, given an [imageModifier] that
 * already carries the [aspectRatio] and (when [sharedElementKey] is set) the
 * shared-element transition. The caller renders its [com.raulshma.jellyplay.core.ui.image.MediaImage]
 * / placeholder with that modifier.
 * @param aspectRatio image aspect ratio — 2:3 for posters, 16:9 for wide cards.
 * @param previewFactory when non-null, wires the press-and-hold peek preview;
 * receives the card's captured bounds so it can populate
 * [MediaPreview.sourceBounds]. Pass `null` (the default) to disable peek.
 * @param onLongPress when non-null, long-press fires this instead of the peek
 * preview (e.g. a quick-action sheet wired by the host screen).
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
    onPlayClick: (() -> Unit)? = null,
    playButtonDominantColor: Color = MaterialTheme.colorScheme.primary,
    playButtonSize: Dp = 36.dp,
    sharedElementKey: String? = null,
    scrimBrush: Brush? = null,
    scrimHeight: Dp = 60.dp,
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
    val focusInteraction = rememberJellyFocusableInteraction()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val focusScale = focusInteraction.scale
    val themeVariant = LocalThemeVariant.current
    val cardShape = ShapeCache.smooth12

    // Press-and-hold "peek" preview. The scaffold owns interactionSource, so it
    // owns the peek wiring end-to-end — variants just supply the factory.
    val peek = if (previewFactory != null) {
        rememberMediaPeek(previewFactory = previewFactory)
    } else null
    if (previewFactory != null) rememberReleaseDismiss(isPressed)

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
    val resolvedScrimBrush = scrimBrush ?: remember(surfaceColor) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.45f),
            ),
        )
    }

    val columnModifier = if (cardWidth != null) modifier.width(cardWidth) else modifier

    Column(modifier = columnModifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(focusInteraction.modifier)
                .then(peek?.boundsModifier ?: Modifier)
                .pressScale(interactionSource = interactionSource)
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
                .jellyFocusIndicator(focusInteraction, cardShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = if (isReducedMotion()) {
                        androidx.compose.foundation.LocalIndication.current
                    } else {
                        null
                    },
                    onClick = onClick,
                    onLongClick = onLongPress ?: peek?.onLongClick,
                    enabled = enabled,
                ),
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
                        .height(scrimHeight)
                        .background(resolvedScrimBrush)
                )

                overlays()

                if (onPlayClick != null) {
                    PlayButtonWithProgress(
                        progressPercent = if (showProgress) progressFraction else 0f,
                        dominantColor = playButtonDominantColor,
                        onClick = onPlayClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp),
                        buttonSize = playButtonSize,
                    )
                }

                if (showProgress && progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
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
                modifier = Modifier.enableMarqueeOnFocus(focused = focusInteraction.isFocused),
            )
            footer()
        }
    }
}

