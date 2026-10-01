package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.animation.isReducedMotion
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.preview.MediaPeekHandle
import com.raulshma.jellyplay.core.ui.preview.MediaPreview
import com.raulshma.jellyplay.core.ui.preview.rememberMediaPeek
import com.raulshma.jellyplay.core.ui.preview.rememberReleaseDismiss

/**
 * The card chrome layer — the ONE implementation of a media card's interaction
 * chrome: focus tracking ([rememberJellyFocusableInteraction]), the animated
 * press scale, the combined click (with long-press resolving to an explicit
 * [rememberCardChrome] `onLongPress` or the press-and-hold peek), and the peek
 * wiring end-to-end (bounds tracking + release-to-dismiss, since the chrome
 * owns the interaction source the peek system keys off).
 *
 * [MediaCardScaffold] consumes this layer for its own Column layout (image +
 * title + footer); differently-shaped cards — the library ThumbCard, the
 * details episode card — seat on it directly and keep their own layout, so a
 * chrome change (focus treatment, press scale, peek plumbing) is a one-file
 * edit instead of a per-card re-implementation.
 *
 * Assemble the chrome into a layout's container modifier in the canonical
 * order (the one [MediaCardScaffold] uses):
 * ```
 * Modifier
 *     .then(chrome.focus.modifier)                 // focus tracking
 *     .then(chrome.peek?.boundsModifier ?: Modifier)
 *     .then(chrome.pressScale)                     // animated press scale
 *     .jellyFocusIndicator(chrome.focus, shape)    // focus border/glow
 *     .then(chrome.clickModifier(onClick = ...))   // combined click
 * ```
 * (Layouts with leading decoration — a themed border, a background — put those
 * first, exactly as the details episode card does.)
 */
@Stable
class CardChrome(
    /** Drives the press-scale animation and the click gesture. */
    val interactionSource: MutableInteractionSource,
    /** Current press state — the peek release-dismiss consumes it. */
    val isPressed: Boolean,
    /** Unified focus handle: tracking modifier, focus state, scale. */
    val focus: JellyFocusableInteraction,
    /** Peek handle when [rememberCardChrome] got a preview factory, else null. */
    val peek: MediaPeekHandle?,
    /** The animated press-scale [graphicsLayer] (draw-phase — no recomposition). */
    val pressScale: Modifier,
)

/**
 * Builds the card chrome. All parameters default to the values
 * [MediaCardScaffold] has always used; the differently-shaped reseats pass
 * their historical values explicitly so the chrome stays byte-identical to the
 * hand copies it replaced.
 *
 * @param previewFactory when non-null, wires the press-and-hold peek preview
 *   (bounds tracking + long-press + release-to-dismiss) exactly as the
 *   scaffold does. Pass `null` to disable peek.
 * @param focusedScale nominal focus scale handed to the focus interaction
 *   (the underlying [com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState]
 *   renders focus as border + glow, not scale — the value is carried so each
 *   site's declared intent is preserved).
 * @param pressScaleValue target scale while pressed.
 * @param pressScaleSpec animation spec for the press scale; null = the theme
 *   motion scheme's default spatial spec (the scaffold's).
 * @param pressScaleOverridesReducedMotion when false (default), reduced motion
 *   / performance mode flattens the press scale to 1f — the scaffold's arm.
 *   The details episode card historically scaled even under reduced motion;
 *   it passes true to keep that.
 */
@Composable
fun rememberCardChrome(
    previewFactory: ((sourceBounds: Rect?) -> MediaPreview)? = null,
    focusedScale: Float = LocalJellyPlayUi.current.focus.focusedScale,
    pressScaleValue: Float = AnimationTokens.CardPressScale,
    pressScaleSpec: FiniteAnimationSpec<Float>? = null,
    pressScaleOverridesReducedMotion: Boolean = false,
): CardChrome {
    val focusInteraction = rememberJellyFocusableInteraction(focusedScale = focusedScale)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // The chrome owns interactionSource, so it owns the peek wiring
    // end-to-end — layouts just supply the factory (or none).
    val peek = if (previewFactory != null) {
        rememberMediaPeek(previewFactory = previewFactory)
    } else null
    if (previewFactory != null) rememberReleaseDismiss(isPressed)

    // Animated press feedback through the motion scheme so reduced motion /
    // performance mode flatten the transition via the theme (the same contract
    // Modifier.pressScale documents); the override arm exists for the episode
    // card's historical always-scale behavior.
    val targetPressScale = when {
        !pressScaleOverridesReducedMotion && isReducedMotion() -> 1f
        isPressed -> pressScaleValue
        else -> 1f
    }
    val animatedPressScale by animateFloatAsState(
        targetValue = targetPressScale,
        animationSpec = pressScaleSpec ?: MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "cardChromePressScale",
    )
    val pressScaleModifier = Modifier.graphicsLayer {
        scaleX = animatedPressScale
        scaleY = animatedPressScale
    }

    return CardChrome(
        interactionSource = interactionSource,
        isPressed = isPressed,
        focus = focusInteraction,
        peek = peek,
        pressScale = pressScaleModifier,
    )
}

/**
 * The card's click chrome: [combinedClickable] on the chrome's own
 * interactionSource, with the long-press resolution rule every card shared —
 * an explicit [onLongPress] (e.g. the quick-action sheet) supersedes the peek
 * preview's long-press.
 *
 * @param useReducedMotionIndication the scaffold's arm: under reduced motion
 *   the platform indication returns so taps aren't invisible (the press scale
 *   having flattened). The ThumbCard / details episode card kept their
 *   indication hard-null in every mode; they pass false.
 */
@Composable
fun CardChrome.clickModifier(
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    useReducedMotionIndication: Boolean = true,
): Modifier = Modifier.combinedClickable(
    interactionSource = interactionSource,
    indication = if (useReducedMotionIndication && isReducedMotion()) {
        LocalIndication.current
    } else {
        null
    },
    onClick = onClick,
    onLongClick = onLongPress ?: peek?.onLongClick,
    enabled = enabled,
)

/**
 * The media card's watch-progress bar — the shared renderer behind the
 * scaffold's bottom-center bar and the details episode card's bottom-start
 * one. Track + fill as two plain boxes (square, full-bleed edges by design —
 * not a Material [androidx.compose.material3.LinearProgressIndicator]).
 *
 * @param trackColor the full-width backing bar; null renders the fill only.
 */
@Composable
fun MediaCardProgressOverlay(
    progressFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color? = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
) {
    Box(modifier = modifier.height(height)) {
        if (trackColor != null) {
            Box(modifier = Modifier.fillMaxSize().background(trackColor))
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                .background(fillColor)
        )
    }
}
