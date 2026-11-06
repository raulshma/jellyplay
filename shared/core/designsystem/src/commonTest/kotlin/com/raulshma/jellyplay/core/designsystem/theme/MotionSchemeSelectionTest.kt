package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class MotionSchemeSelectionTest {

    @Test
    fun expressiveScheme_spatialSpecs_areSprings() {
        val spec = ExpressiveMotionScheme.defaultSpatialSpec<Float>()
        // Expressive scheme must use a spring (not snap) for spatial animation.
        assertTrue(
spec !is SnapSpec<*>,
"Expressive spatial spec should be a spring, was $spec",
)
    }

    @Test
    fun expressiveScheme_effectsSpecs_haveNonZeroDuration() {
        val spec = ExpressiveMotionScheme.defaultEffectsSpec<Float>()
        assertTrue(
spec is TweenSpec<*>,
"Expressive effects spec should be a TweenSpec",
)
        val tween = spec as TweenSpec<*>
        assertTrue(
tween.durationMillis > 0,
"Expressive effects duration should be > 0, was ${tween.durationMillis}",
)
    }

    @Test
    fun reducedScheme_spatialSpec_isSnap() {
        val spec = ReducedMotionScheme.defaultSpatialSpec<Float>()
        assertTrue(
spec is SnapSpec<*>,
"Reduced spatial spec should be snap(), was $spec",
)
    }

    // ------------------------------------------------------------------
    // The selection fold (motionSchemeFor): the reduced scheme wins when
    // EITHER input is on. The inputs are the RESOLVED posture's pair —
    // LocalPerformanceConfig.suppressAnimations + the raw reduce-motion
    // accessibility flag (LocalReduceMotionEnabled) — pinned here as a pure
    // function so the selection itself (not just the scheme contents) has a
    // regression guard.
    // ------------------------------------------------------------------

    @Test
    fun selection_default_isExpressive() {
        assertTrue(motionSchemeFor(suppressAnimations = false, reduceMotion = false) === ExpressiveMotionScheme)
    }

    @Test
    fun selection_suppressAnimationsAlone_selectsReduced() {
        assertTrue(motionSchemeFor(suppressAnimations = true, reduceMotion = false) === ReducedMotionScheme)
    }

    @Test
    fun selection_reduceMotionAlone_selectsReduced() {
        assertTrue(motionSchemeFor(suppressAnimations = false, reduceMotion = true) === ReducedMotionScheme)
    }

    @Test
    fun selection_bothFlags_selectsReduced() {
        assertTrue(motionSchemeFor(suppressAnimations = true, reduceMotion = true) === ReducedMotionScheme)
    }

    @Test
    fun selection_resolvedDefaultConfig_selectsExpressive() {
        // The fresh-install posture (STANDARD, all-AUTO, reduce-motion off)
        // must keep the expressive scheme — the compatibility anchor for the
        // whole config migration.
        assertTrue(
            com.raulshma.jellyplay.core.model.ResolvedPerformanceConfig.DEFAULT
                .let { motionSchemeFor(suppressAnimations = it.suppressAnimations, reduceMotion = false) }
                === ExpressiveMotionScheme,
        )
    }

    @Test
    fun selection_resolvedPerformanceLevel_selectsReduced() {
        // The PERFORMANCE level's all-AUTO fold suppresses animations — the
        // historical performance-mode-ON posture — so the scheme must collapse.
        val performance = com.raulshma.jellyplay.core.model.PerformanceConfigResolver.resolve(
            level = com.raulshma.jellyplay.core.model.PerformanceLevel.PERFORMANCE,
            reduceMotionEnabled = false,
            overrides = com.raulshma.jellyplay.core.model.PerformanceOverrides(),
        )
        assertTrue(
            motionSchemeFor(suppressAnimations = performance.suppressAnimations, reduceMotion = false)
                === ReducedMotionScheme,
        )
    }

    @Test
    fun reducedScheme_allTokens_areInstant() {
        // Every token in the reduced scheme must be the instant/snap variant.
        // Spatial tokens -> snap; effects tokens -> 0ms tween.
        assertTrue(ReducedMotionScheme.defaultSpatialSpec<Float>() is SnapSpec<*>)
        assertTrue(ReducedMotionScheme.fastSpatialSpec<Float>() is SnapSpec<*>)
        assertTrue(ReducedMotionScheme.slowSpatialSpec<Float>() is SnapSpec<*>)
        // Effects: reduced uses tween(0). Assert each reduced effects token is a
        // TweenSpec with durationMillis == 0.
        val reducedDefault = ReducedMotionScheme.defaultEffectsSpec<Float>()
        assertTrue(reducedDefault is TweenSpec<*>)
        assertEquals(0, (reducedDefault as TweenSpec<*>).durationMillis)
        val reducedFast = ReducedMotionScheme.fastEffectsSpec<Float>()
        assertTrue(reducedFast is TweenSpec<*>)
        assertEquals(0, (reducedFast as TweenSpec<*>).durationMillis)
    }
}
