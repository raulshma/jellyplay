package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

@Suppress("UNCHECKED_CAST")
internal val ExpressiveMotionScheme: MotionScheme = object : MotionScheme {
    private val defaultSpatialSpec = spring<Any>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    private val fastSpatialSpec = spring<Any>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh
    )

    private val slowSpatialSpec = spring<Any>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 800f
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
