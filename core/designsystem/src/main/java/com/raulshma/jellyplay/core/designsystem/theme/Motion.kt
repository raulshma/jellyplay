package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme

@Suppress("UNCHECKED_CAST")
internal val CustomMotionScheme: MotionScheme = object : MotionScheme {
    private val SpringDefaultSpatialDamping = 0.8f
    private val SpringDefaultSpatialStiffness = 380.0f
    private val SpringDefaultEffectsDamping = 1.0f
    private val SpringDefaultEffectsStiffness = 1600.0f
    private val SpringFastSpatialDamping = 0.6f
    private val SpringFastSpatialStiffness = 800.0f
    private val SpringFastEffectsDamping = 1.0f
    private val SpringFastEffectsStiffness = 3800.0f
    private val SpringSlowSpatialDamping = 0.8f
    private val SpringSlowSpatialStiffness = 200.0f
    private val SpringSlowEffectsDamping = 1.0f
    private val SpringSlowEffectsStiffness = 800.0f

    private val defaultSpatialSpec =
        tween<Any>(
            durationMillis = 400,
            easing = FancyTransitionEasing
        )

    private val fastSpatialSpec =
        spring<Any>(
            dampingRatio = SpringFastSpatialDamping,
            stiffness = SpringFastSpatialStiffness
        )

    private val slowSpatialSpec =
        spring<Any>(
            dampingRatio = SpringSlowSpatialDamping,
            stiffness = SpringSlowSpatialStiffness
        )

    private val defaultEffectsSpec =
        spring<Any>(
            dampingRatio = SpringDefaultEffectsDamping,
            stiffness = SpringDefaultEffectsStiffness
        )

    private val fastEffectsSpec =
        tween<Any>(
            durationMillis = 300,
            easing = FancyTransitionEasing
        )

    private val slowEffectsSpec =
        tween<Any>(
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
