package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme

/**
 * Expressive motion scheme for Material Design 3 Expressive.
 *
 * Uses bouncier springs for spatial animations (position, size, shape)
 * and snappier tweens for effects (alpha, color) to create fluid,
 * lively motion throughout the app.
 */
@Suppress("UNCHECKED_CAST")
internal val ExpressiveMotionScheme: MotionScheme = object : MotionScheme {
    // Spatial specs — bouncier for expressive feel
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

    // Effects specs — snappy tweens with expressive easing
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
