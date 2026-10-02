package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.animation.isReducedMotion
import kotlin.math.abs

/**
 * Horizontal pull-to-refresh for the two ends of a horizontal list, with a
 * shift-reveal animation: dragging the content rightward past its start (left
 * edge) or leftward past its end (right edge) SLIDES THE CONTENT ITSELF
 * toward the pulled edge — damped, tracking the finger — opening a gap that
 * reveals the spinner chip underneath. Releasing past [threshold] fires
 * [onRefresh]; the content springs to a settle offset and holds there, chip
 * spinning, while [isRefreshing] runs, then glides home when the refresh
 * ends. A release below the threshold glides straight back. Built for the
 * home screen's section rows — each row refetches its own section — but
 * generic over any horizontally scrollable content.
 *
 * Animation phases (one [Animatable] drives the content's `translationX`,
 * so the shift never relayouts — it is a pure draw-time transform):
 *  - DRAG: the connection accumulates the raw pull; a `snapshotFlow` bridge
 *    snaps the offset to `pull × [DragFollowRatio]` (damped, capped) every
 *    frame the finger is down. Direct follows, no tween — the content must
 *    feel attached to the finger.
 *  - RELEASE (below threshold): spring glide home. The launch is
 *    non-blocking (a fresh grab re-takes the row mid-glide — the drag bridge's
 *    `snapTo` supersedes the running spring, as [Animatable] calls are
 *    mutually exclusive).
 *  - REFRESH: spring to [RefreshSettle] (twice triggered, once by the
 *    release path and once by the `isRefreshing` collector — converging on
 *    the same target, so the hand-off is seamless), hold, then spring home on
 *    the true→false transition.
 *  - Reduced motion: every phase snaps instead of animating.
 *
 * Mechanics and input scoping (unchanged from the accumulate-only version):
 * a [NestedScrollConnection] accumulates the horizontal deltas the child
 * list leaves unconsumed in `onPostScroll` — a list at either end leaves
 * exactly those leftovers, and the sign encodes the side. Dragging back in
 * the scroll direction cancels the pull (opposing CONSUMED motion shrinks
 * the accumulation). Accumulation requires a touch pointer down (the
 * observational technique of [PullToRefreshBox]'s WheelPullGuard), so flings
 * releasing into an edge and programmatic scrolls never trigger, and
 * desktop mouse drag/wheel ride `dispatchRawDelta` below nested scroll —
 * unreachable by construction. The child's overscroll effect is disabled
 * while [enabled] (via [LocalOverscrollFactory]) — Android's stretch would
 * consume the same leftovers one node below this connection and starve it;
 * the shift + chip replace the stretch as the edge feedback, scoped to this
 * box's content only. Vertical deltas are untouched — the enclosing vertical
 * list and any vertical pull-to-refresh above are unaffected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalEdgePullRefreshBox(
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    threshold: Dp = 80.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!enabled) {
        // Pass-through: no connection, no overscroll override, no chip —
        // TV (D-pad rows) and every non-refreshable section render exactly as
        // before this wrapper existed.
        Box(modifier = modifier, propagateMinConstraints = true, content = content)
        return
    }

    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val settlePx = with(density) { RefreshSettle.toPx() }
    val reducedMotion = isReducedMotion()

    // Net accumulated pull in px: positive = pulled toward the start (left
    // edge), negative = toward the end. The chip's side reads the sign.
    val rawPull = remember { mutableFloatStateOf(0f) }
    // The content's draw-time x-offset — the one animated value every phase
    // moves (drag follow, release glide, refresh settle, refresh hide).
    val offset = remember { Animatable(0f) }
    // Side the in-flight refresh was triggered from — held while refreshing
    // so the settled content and chip don't flip if a later pull would
    // re-accumulate.
    var refreshSideIsStart by remember { mutableStateOf(true) }
    // Release hand-off: the pointer loop (which cannot launch coroutines —
    // AwaitPointerEventScope is not a CoroutineScope) records the pull at
    // release and bumps the generation; the animation collector below
    // consumes each release fire-and-forget, so the gesture loop never
    // blocks on a spring and a fresh grab mid-glide is observed instantly.
    var releaseGeneration by remember { mutableIntStateOf(0) }
    val releasePull = remember { mutableFloatStateOf(0f) }
    val isTouchDown = remember { mutableStateOf(false) }
    val activeOnRefresh by rememberUpdatedState(onRefresh)
    val isRefreshingState = rememberUpdatedState(isRefreshing)

    val connection = remember(thresholdPx) {
        EdgePullConnection(rawPull, isTouchDown, isRefreshingState, thresholdPx * MaxPullRatio)
    }

    // DRAG follow: while the finger is down, the content snaps to the damped
    // pull each frame (no tween — attached-to-finger feel). The guard
    // re-reads isTouchDown at collect time so a stale in-flight emission can
    // never snapTo over a release glide (snapTo would cancel the animation).
    // Keyed on reducedMotion so a mid-lifetime toggle can't leave the
    // collectors holding a stale captured value.
    LaunchedEffect(reducedMotion) {
        snapshotFlow { rawPull.floatValue }
            .collect { pull ->
                if (isTouchDown.value) {
                    offset.snapTo(
                        (pull * DragFollowRatio).coerceIn(-MaxDragRatio * settlePx, MaxDragRatio * settlePx),
                    )
                }
            }
    }

    // REFRESH lifecycle: slide to the settle offset while the refresh runs
    // (covers external starts too), glide home on the true→false transition.
    LaunchedEffect(reducedMotion) {
        var wasRefreshing = isRefreshingState.value
        snapshotFlow { isRefreshingState.value }
            .collect { refreshing ->
                when {
                    refreshing -> offset.animateReveal(
                        settlePx * if (refreshSideIsStart) 1f else -1f,
                        reducedMotion,
                        SettleSpring,
                    )
                    wasRefreshing -> offset.animateReveal(0f, reducedMotion, HideSpring)
                }
                wasRefreshing = refreshing
            }
    }

    // RELEASE: consume each release hand-off — a past-threshold pull springs
    // the content to the settle offset (the refresh collector converges on
    // the same target once isRefreshing lands); anything else glides home.
    // While a refresh is in flight the below-threshold glide is skipped: the
    // accumulator is blocked then, so a mid-refresh tap releases with
    // pulled == 0, and gliding home would collapse the settle offset the
    // refresh collector is holding — leaving the chip at full reveal
    // overlapping the content that just slid back over it.
    LaunchedEffect(reducedMotion) {
        var consumedGeneration = releaseGeneration
        snapshotFlow { releaseGeneration }
            .collect { generation ->
                if (generation == consumedGeneration) return@collect
                consumedGeneration = generation
                val pulled = releasePull.floatValue
                if (abs(pulled) >= thresholdPx) {
                    offset.animateReveal(
                        settlePx * if (pulled > 0f) 1f else -1f,
                        reducedMotion,
                        SettleSpring,
                    )
                } else if (!isRefreshingState.value) {
                    offset.animateReveal(0f, reducedMotion, ReleaseSpring)
                }
            }
    }

    Box(
        modifier = modifier
            .nestedScroll(connection)
            .pointerInput(Unit) {
                awaitEachGesture {
                    isTouchDown.value = false
                    while (true) {
                        val event = awaitPointerEvent()
                        isTouchDown.value =
                            event.changes.any { it.pressed && it.type == PointerType.Touch }
                        if (event.changes.none { it.pressed }) break
                    }
                    // All pointers up: the drag ended. Record the release for
                    // the animation collector, fire the refresh when the pull
                    // cleared the threshold, and always reset the raw
                    // accumulator. Nothing here blocks the gesture loop.
                    val pulled = rawPull.floatValue
                    if (abs(pulled) >= thresholdPx && !isRefreshingState.value) {
                        refreshSideIsStart = pulled > 0f
                        releasePull.floatValue = pulled
                        activeOnRefresh()
                    } else {
                        releasePull.floatValue = pulled
                    }
                    rawPull.floatValue = 0f
                    releaseGeneration++
                }
            },
        propagateMinConstraints = true,
    ) {
        val shift = offset.value
        val reveal = (abs(shift) / settlePx).coerceIn(0f, 1f)
        val sideIsStart = if (isRefreshing) refreshSideIsStart else shift >= 0f
        if (reveal > 0f || isRefreshing) {
            EdgePullIndicator(
                reveal = if (isRefreshing) 1f else reveal,
                progress = (abs(rawPull.floatValue) / thresholdPx).coerceIn(0f, 1f),
                refreshing = isRefreshing,
                modifier = Modifier.align(if (sideIsStart) Alignment.CenterStart else Alignment.CenterEnd),
            )
        }
        Box(
            modifier = Modifier.graphicsLayer { translationX = shift },
            propagateMinConstraints = true,
        ) {
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                content()
            }
        }
    }
}

/**
 * The spinner chip revealed in the vacated edge gap: scales/fades in with
 * [reveal], the expressive wavy ring's amplitude tracking the pull
 * ([progress]) while dragging, indeterminate while the section's refetch
 * runs. A hairline border keeps it legible over the cards sliding past it.
 */
@Composable
private fun EdgePullIndicator(
    reveal: Float,
    progress: Float,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 10.dp)
            .graphicsLayer {
                scaleX = 0.55f + 0.45f * reveal
                scaleY = 0.55f + 0.45f * reveal
                alpha = ((reveal - 0.2f) / 0.8f).coerceIn(0f, 1f)
            }
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
            .padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            JellyPlayCircularProgressIndicator(modifier = Modifier.size(22.dp))
        } else {
            JellyPlayCircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * The accumulating [NestedScrollConnection] — raw-pull arithmetic only; the
 * offset animation and the release decision live in the composable above.
 */
private class EdgePullConnection(
    private val pullDistance: MutableFloatState,
    private val isTouchDown: State<Boolean>,
    private val isRefreshing: State<Boolean>,
    private val maxPullPx: Float,
) : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        // User-input drags only, touch only, not while a refetch runs.
        if (source != NestedScrollSource.UserInput || !isTouchDown.value || isRefreshing.value) {
            return Offset.Zero
        }
        val current = pullDistance.floatValue
        // Opposing CONSUMED motion cancels the pull: while pulled toward the
        // start, the child consuming forward (negative-x) delta means the user
        // dragged back into the row — shrink instead of accumulating against.
        val cancelDx = when {
            current > 0f -> minOf(0f, consumed.x)
            current < 0f -> maxOf(0f, consumed.x)
            else -> 0f
        }
        val next = current + available.x + cancelDx
        pullDistance.floatValue = next.coerceIn(-maxPullPx, maxPullPx)
        // Nothing is consumed: the leftover keeps its normal nested-scroll fate
        // (the child overscroll is disabled under `enabled`, so nothing above
        // or below is disturbed).
        return Offset.Zero
    }
}

/** Phase-agnostic move: snap under reduced motion, otherwise the given spring. */
private suspend fun Animatable<Float, *>.animateReveal(
    target: Float,
    reducedMotion: Boolean,
    spec: AnimationSpec<Float>,
) {
    if (reducedMotion) snapTo(target) else animateTo(target, spec)
}

/** Where the content rests while a refresh runs — enough to reveal the chip. */
private val RefreshSettle = 48.dp

/**
 * How far the raw pull may accumulate past the fire [threshold] (as a ratio
 * of it) — headroom so the release decision, which fires at 1×, has travel
 * room beyond the point that triggers.
 */
private const val MaxPullRatio = 1.5f

/**
 * How far past the settle offset the drag follow may shift the content (as a
 * ratio of the settle distance) — over-pulling keeps compressing up to here,
 * then holds.
 */
private const val MaxDragRatio = 1.6f

/** The damping on the drag follow — sub-1 so the shift feels elastic, not rigid. */
private const val DragFollowRatio = 0.62f

/** Release below the threshold: a quick, slightly lively glide home. */
private val ReleaseSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 350f)

/** Trigger: a gentle, non-bouncy settle into the refresh offset. */
private val SettleSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** Refresh done: a slower, softer glide home — the row visibly "lands". */
private val HideSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 300f)
