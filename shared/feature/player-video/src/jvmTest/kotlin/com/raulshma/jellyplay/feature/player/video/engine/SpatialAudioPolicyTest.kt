package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The [resolveEffectiveSpatialState] truth table — the pure heart of the
 * spatial-audio feature (the Android [android.media.Spatializer] surface is
 * query-only, so this decision IS the feature; the platform facts feed in as
 * plain inputs). Also pins [spatialSupersedesVirtualizer] (the mutual-
 * exclusion report).
 */
class SpatialAudioPolicyTest {

    /** The all-qualifying baseline: flipping ONE input per case isolates it. */
    private fun resolve(
        userEnabled: Boolean = true,
        onlyHeadphones: Boolean = true,
        outputIsHeadphoneClass: Boolean = true,
        offloadActive: Boolean = false,
        systemSpatialEnabled: Boolean = true,
        canBeSpatialized: Boolean = true,
    ): SpatialAudioState = resolveEffectiveSpatialState(
        userEnabled = userEnabled,
        onlyHeadphones = onlyHeadphones,
        outputIsHeadphoneClass = outputIsHeadphoneClass,
        offloadActive = offloadActive,
        systemSpatialEnabled = systemSpatialEnabled,
        canBeSpatialized = canBeSpatialized,
    )

    @Test
    fun `baseline — every gate open is ACTIVE`() {
        assertEquals(SpatialAudioState.ACTIVE, resolve())
    }

    @Test
    fun `no canBeSpatialized is UNAVAILABLE — before any other input`() {
        // Covers pre-API-32 devices, devices without a spatializer, and
        // non-qualifying formats/routes: the helper reports false there.
        assertEquals(SpatialAudioState.UNAVAILABLE, resolve(canBeSpatialized = false))
        // Capability dominates even when everything else would say ACTIVE.
        assertEquals(
            SpatialAudioState.UNAVAILABLE,
            resolve(
                canBeSpatialized = false,
                offloadActive = true,
                outputIsHeadphoneClass = false,
                userEnabled = false,
                systemSpatialEnabled = false,
            ),
        )
    }

    @Test
    fun `audio offload forces OFF — the platform spatializer is bypassed there`() {
        assertEquals(SpatialAudioState.OFF, resolve(offloadActive = true))
        // Even with the toggle off and system spatial on: OFF, not AVAILABLE —
        // the engine's configuration holds spatial out.
        assertEquals(
            SpatialAudioState.OFF,
            resolve(offloadActive = true, userEnabled = false, systemSpatialEnabled = false),
        )
    }

    @Test
    fun `headphones-only gate — non-headphone output is OFF, opt-out continues`() {
        assertEquals(SpatialAudioState.OFF, resolve(outputIsHeadphoneClass = false))
        // The gate is the user's qualification: off means speakers qualify too.
        assertEquals(
            SpatialAudioState.ACTIVE,
            resolve(onlyHeadphones = false, outputIsHeadphoneClass = false),
        )
    }

    @Test
    fun `user switches — either off is AVAILABLE, both on is ACTIVE`() {
        assertEquals(SpatialAudioState.AVAILABLE, resolve(userEnabled = false))
        assertEquals(SpatialAudioState.AVAILABLE, resolve(systemSpatialEnabled = false))
        assertEquals(SpatialAudioState.ACTIVE, resolve())
    }

    @Test
    fun `virtualizer never gates — spatial supersedes it when both are requested`() {
        // The resolver takes no virtualizer input at all: a requested
        // virtualizer cannot downgrade the spatial state. The engine reads
        // the config it already holds for the supersession report below.
        assertEquals(SpatialAudioState.ACTIVE, resolve())
        assertTrue(spatialSupersedesVirtualizer(SpatialAudioState.ACTIVE, virtualizerActive = true))
        // The report is only about ACTIVE winning over a requested virtualizer.
        assertFalse(spatialSupersedesVirtualizer(SpatialAudioState.AVAILABLE, virtualizerActive = true))
        assertFalse(spatialSupersedesVirtualizer(SpatialAudioState.ACTIVE, virtualizerActive = false))
    }
}
