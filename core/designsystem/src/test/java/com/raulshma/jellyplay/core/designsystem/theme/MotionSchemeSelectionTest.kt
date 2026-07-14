package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSchemeSelectionTest {

    @Test
    fun expressiveScheme_spatialSpecs_areSprings() {
        val spec = ExpressiveMotionScheme.defaultSpatialSpec<Float>()
        // Expressive scheme must use a spring (not snap) for spatial animation.
        assertTrue(
            "Expressive spatial spec should be a spring, was $spec",
            spec !is SnapSpec<*>,
        )
    }

    @Test
    fun expressiveScheme_effectsSpecs_haveNonZeroDuration() {
        val spec = ExpressiveMotionScheme.defaultEffectsSpec<Float>()
        assertTrue("Expressive effects spec should be a TweenSpec", spec is TweenSpec<*>)
        val tween = spec as TweenSpec<*>
        assertTrue(
            "Expressive effects duration should be > 0, was ${tween.durationMillis}",
            tween.durationMillis > 0,
        )
    }

    @Test
    fun reducedScheme_spatialSpec_isSnap() {
        val spec = ReducedMotionScheme.defaultSpatialSpec<Float>()
        assertTrue(
            "Reduced spatial spec should be snap(), was $spec",
            spec is SnapSpec<*>,
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
