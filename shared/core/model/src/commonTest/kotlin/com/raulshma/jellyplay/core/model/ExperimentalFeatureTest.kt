package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the invariants of the `isExperimentalEnabled` helpers — the readable
 * membership check shared by the preference surfaces:
 *
 *  - The extensions over [ExperimentalPreferences] and [MainPreferences]
 *    answer the SAME question: "is this feature in the enabled set?" — so a
 *    toggle observed through any projection agrees.
 *  - The default state is all-off on every surface.
 */
class ExperimentalFeatureTest {

    @Test
    fun `main preferences helper reflects the enabled set`() {
        val off = MainPreferences()
        assertFalse(off.isExperimentalEnabled(ExperimentalFeature.HOME_CARD_CLIPPING))

        val on = MainPreferences(enabledExperimentalFeatures = ExperimentalFeature.entries.toSet())
        for (feature in ExperimentalFeature.entries) {
            assertTrue(on.isExperimentalEnabled(feature), feature.name)
        }
    }

    @Test
    fun `empty slice means everything is off`() {
        val prefs = ExperimentalPreferences()
        for (feature in ExperimentalFeature.entries) {
            assertFalse(prefs.isExperimentalEnabled(feature), feature.name)
        }
    }
}
