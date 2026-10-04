package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.ui.feedback.rememberSelectionTickHaptic
import kotlin.math.roundToInt

/**
 * Pure geometry/threshold policy for [SwipeActionBox] — Compose-free so the
 * commit math is assertable JVM-side (SwipeActionPolicyTest).
 */
object SwipeActionPolicy {
    /** Swipe-reveal distance as a fraction of the row width. */
    const val RevealFraction = 0.35f

    /**
     * Fraction of the total travel past which release commits the action
     * (feeds AnchoredDraggableState's positionalThreshold).
     */
    const val PositionalThresholdFraction = 0.55f

    fun revealDistance(rowWidthPx: Float): Float = rowWidthPx * RevealFraction

    fun positionalThreshold(totalDistancePx: Float): Float =
        totalDistancePx * PositionalThresholdFraction
}

/**
 * House swipe-to-action row: drag toward the end edge reveals a colored action
 * background; releasing past the threshold commits [onAction] and the row
 * springs back. The gesture itself is user-driven input and stays enabled under
 * reduced motion; only the settle animation snaps.
 *
 * Offset is read in the layout phase (`Modifier.offset` lambda) and the
 * background icon in the draw phase (`graphicsLayer` lambda), so an in-flight
 * drag never recomposes the row content.
 *
 * @param onAction fired once the reveal commits; the row animates back on its
 *   own — remove the item from state if the action deletes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeActionBox(
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    actionIcon: ImageVector = Tabler.Outline.Trash,
    actionContentDescription: String? = null,
    actionColor: Color = MaterialTheme.colorScheme.errorContainer,
    actionIconTint: Color = MaterialTheme.colorScheme.onErrorContainer,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val selectionTick = rememberSelectionTickHaptic()
    // confirmValueChange below lives inside a remembered state; keep the
    // action callback and haptic lambda current without recreating the state
    // (a runtime flip of LocalHapticsEnabled must reach the drag).
    val currentOnAction by rememberUpdatedState(onAction)
    val currentTick by rememberUpdatedState(selectionTick)

    // Settle spec routes through the motion scheme: expressive theme = the
    // no-bouncy default spring, reduce-motion/performance theme = snap. The
    // user-driven drag itself is unaffected either way.
    val settleSpec: AnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec()
    val decaySpec = remember { exponentialDecay<Float>() }

    // Keyed on density too: the velocity threshold lambda converts dp→px, and a
    // config-driven density change (window moved across monitors) must rebuild
    // the state with the fresh conversion.
    val state = remember(settleSpec, density) {
        AnchoredDraggableState(
            initialValue = SwipeAnchor.Settled,
            anchors = DraggableAnchors { },
            positionalThreshold = { totalDistance -> SwipeActionPolicy.positionalThreshold(totalDistance) },
            velocityThreshold = { with(density) { AnimationTokens.SwipeVelocityThresholdDp.dp.toPx() } },
            snapAnimationSpec = settleSpec,
            decayAnimationSpec = decaySpec,
        )
    }

    // Commit on RELEASE: settledValue only leaves Settled once the gesture ends
    // (or a fling decays) past the positional threshold. Firing here — not in
    // confirmValueChange — means a slow drag that crosses the threshold but
    // retreats never commits. The settle-back animates frame-by-frame through
    // the house settle spec (snap under reduced motion) via dragTo — this
    // foundation version exposes no animateTo on the drag scope.
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { settled ->
            if (settled == SwipeAnchor.Revealed) {
                currentTick()
                currentOnAction()
                val startOffset = state.requireOffset()
                state.anchoredDrag {
                    animate(
                        initialValue = startOffset,
                        targetValue = 0f,
                        animationSpec = settleSpec,
                    ) { value, _ ->
                        dragTo(value)
                    }
                }
            }
        }
    }

    // revealDistance is layout state; the background icon reads it (plus the
    // drag offset) inside its graphicsLayer — draw-phase invalidation only.
    var revealDistance by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .clip(shape)
            .clipToBounds()
            .onSizeChanged { size ->
                val reveal = SwipeActionPolicy.revealDistance(size.width.toFloat())
                revealDistance = reveal
                state.updateAnchors(
                    DraggableAnchors {
                        SwipeAnchor.Settled at 0f
                        SwipeAnchor.Revealed at -reveal
                    },
                )
            },
    ) {
        // Action background, revealed under the retreating content.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(actionColor),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                imageVector = actionIcon,
                contentDescription = actionContentDescription,
                tint = actionIconTint,
                modifier = Modifier
                    .padding(end = AnimationTokens.SwipeActionIconEndPaddingDp.dp)
                    .graphicsLayer {
                        val distance = revealDistance
                        if (distance <= 0f) return@graphicsLayer
                        val fraction = ((-currentOffset(state)) / distance).coerceIn(0f, 1f)
                        alpha = fraction
                        scaleX = 0.6f + 0.4f * fraction
                        scaleY = 0.6f + 0.4f * fraction
                    },
            )
        }

        Box(
            modifier = Modifier
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                )
                .offsetCompat(state),
        ) {
            content()
        }
    }
}

private enum class SwipeAnchor { Settled, Revealed }

/** Current drag offset, safe before the first anchors update. */
private fun currentOffset(state: AnchoredDraggableState<SwipeAnchor>): Float = try {
    state.requireOffset()
} catch (_: IllegalStateException) {
    0f
}

/** Layout-phase offset read (mirrors the M3 SwipeToDismissBox pattern). */
private fun Modifier.offsetCompat(state: AnchoredDraggableState<SwipeAnchor>): Modifier =
    offset {
        IntOffset(currentOffset(state).roundToInt(), 0)
    }
