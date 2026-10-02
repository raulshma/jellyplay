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
    // EITHER flag is on. This is the same fold the theme applies via
    // LocalPerformanceMode/LocalReduceMotionEnabled — pinned here as a pure
    // function so the selection itself (not just the scheme contents) has a
    // regression guard.
    // ------------------------------------------------------------------

    @Test
    fun selection_default_isExpressive() {
        assertTrue(motionSchemeFor(performanceMode = false, reduceMotion = false) === ExpressiveMotionScheme)
    }

    @Test
    fun selection_performanceModeAlone_selectsReduced() {
        assertTrue(motionSchemeFor(performanceMode = true, reduceMotion = false) === ReducedMotionScheme)
    }

    @Test
    fun selection_reduceMotionAlone_selectsReduced() {
        assertTrue(motionSchemeFor(performanceMode = false, reduceMotion = true) === ReducedMotionScheme)
    }

    @Test
    fun selection_bothFlags_selectsReduced() {
        assertTrue(motionSchemeFor(performanceMode = true, reduceMotion = true) === ReducedMotionScheme)
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
