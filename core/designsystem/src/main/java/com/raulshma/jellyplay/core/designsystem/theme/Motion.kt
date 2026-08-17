package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

@Suppress("UNCHECKED_CAST")
internal val ExpressiveMotionScheme: MotionScheme = object : MotionScheme {
    private val defaultSpatialSpec = spring<Any>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    private val fastSpatialSpec = spring<Any>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    private val slowSpatialSpec = spring<Any>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    private val defaultEffectsSpec = tween<Any>(
        durationMillis = 350,
        easing = FancyTransitionEasing
    )

    private val fastEffectsSpec = tween<Any>(
        durationMillis = 250,
        easing = FastInvokeEasing
    )

    private val slowEffectsSpec = tween<Any>(
        durationMillis = 500,
        easing = FancyTransitionEasing
    )

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        defaultSpatialSpec as FiniteAnimationSpec<T>

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        fastSpatialSpec as FiniteAnimationSpec<T>

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        slowSpatialSpec as FiniteAnimationSpec<T>

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        defaultEffectsSpec as FiniteAnimationSpec<T>

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        fastEffectsSpec as FiniteAnimationSpec<T>

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        slowEffectsSpec as FiniteAnimationSpec<T>
}

@Suppress("UNCHECKED_CAST")
internal val ReducedMotionScheme: MotionScheme = object : MotionScheme {
    private val instantSpec = snap<Any>()

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        instantSpec as FiniteAnimationSpec<T>

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        instantSpec as FiniteAnimationSpec<T>

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        instantSpec as FiniteAnimationSpec<T>

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        tween<Any>(durationMillis = 0) as FiniteAnimationSpec<T>

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        tween<Any>(durationMillis = 0) as FiniteAnimationSpec<T>

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        tween<Any>(durationMillis = 0) as FiniteAnimationSpec<T>
}

@Composable
fun defaultSpatialSpring() = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

@Composable
fun fastSpatialSpring() = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

@Composable
fun slowSpatialSpring() = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

@Composable
fun defaultEffectsTween() = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

@Composable
fun fastEffectsTween() = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

@Composable
fun slowEffectsTween() = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

@Composable
fun defaultSpatialSpringDp() = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()

@Composable
fun fastSpatialSpringDp() = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()

@Composable
fun defaultContentSizeSpec() = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

// ---------------------------------------------------------------------------
// Expressive spec helpers.
//
// MotionScheme is a fixed Material 3 interface (6 methods) and cannot be
// extended with new tokens. These standalone helpers provide the richer specs
// the NavTransitionPolicy mapper needs. They are only reached in the expressive
// (non-reduced-motion) path — the policy returns INSTANT otherwise — so they do
// not need their own reduce-motion guard.
// ---------------------------------------------------------------------------

/** Deliberate horizontal-slide easing (previously-unused OverslideEasing). */
fun expressiveSlideSpec(): FiniteAnimationSpec<IntOffset> =
    tween(
        durationMillis = 300,
        easing = OverslideEasing,
    )

/** Spring for the modal vertical slide — visible settle. */
fun modalSpringSpec(): FiniteAnimationSpec<IntOffset> =
    spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

/**
 * Spring for shared-element bounds transforms — physical, slightly bouncy match.
 * Use as [androidx.compose.animation.SharedTransitionScope.BoundsTransform].
 */
fun sharedElementBoundsSpec(): androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.geometry.Rect> =
    spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

/**
 * Entrance reveal driven by a shared progress scalar: alpha follows [progress]
 * and the element slides up from 1/[slideDivisor] of its own height.
 *
 * [progress] is a read lambda so the value is read inside the graphicsLayer
 * block (draw phase) — cells composed later during scroll just see the
 * settled `1f` and render immediately, with no per-element state, coroutine,
 * or extra recomposition. The progress itself comes from ONE `Animatable`
 * shared by the whole list/grid — see [rememberDetailEntrance].
 */
fun Modifier.detailEntrance(
    progress: () -> Float,
    slideDivisor: Float = 8f,
): Modifier = graphicsLayer {
    val value = progress()
    alpha = value
    translationY = (1f - value) * size.height / slideDivisor
}

/**
 * Creates and drives the ONE [Animatable] behind a screen-level
 * [Modifier.detailEntrance] reveal: starts at 0f and animates to 1f with the
 * default spatial spring once [start] is (or becomes) true, replacing the
 * per-cell `mutableStateOf + LaunchedEffect + AnimatedVisibility` triple
 * (per-cell state, coroutine, and animation node; two recompositions per
 * cell) with a single shared scalar. Call once per screen/list — not per
 * cell — and pass `{ it.value }` into [Modifier.detailEntrance].
 *
 * [start] gates the reveal on data arrival (e.g. only once the loaded state
 * is non-null); the default animates as soon as the call site composes, so
 * re-mounting the branch (empty -> non-empty) gets a fresh reveal.
 */
@Composable
fun rememberDetailEntrance(start: Boolean = true): Animatable<Float, AnimationVector1D> {
    val entrance = remember { Animatable(0f) }
    // Read the spec in composable scope — the effect lambda below is not
    // composable.
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(start) {
        if (start) {
            // One scalar drives both alpha and translationY; the spatial spec
            // matches the original slide component (the fade rides along).
            entrance.animateTo(1f, spec)
        }
    }
    return entrance
}
