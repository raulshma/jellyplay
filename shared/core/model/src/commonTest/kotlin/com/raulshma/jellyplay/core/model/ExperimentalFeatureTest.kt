package com.raulshma.jellyplay.core.model

import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the invariants of the `isExperimentalEnabled` helpers — the readable
 * membership check shared by the three preference surfaces:
 *
 *  - The extension over [UserPreferences], [ExperimentalPreferences], and
 *    [MainPreferences] all answer the SAME question: "is this feature in the
 *    enabled set?" — so a toggle observed through any projection agrees.
 *  - The default state is all-off on every surface.
 */
class ExperimentalFeatureTest {

    @Test
    fun `user preferences helper reflects the enabled set`() {
        val off = UserPreferences()
        assertFalse(off.isExperimentalEnabled(ExperimentalFeature.MEDIA_CARD_PEEK))

        val on = UserPreferences(enabledExperimentalFeatures = setOf(ExperimentalFeature.MEDIA_CARD_PEEK))
        assertTrue(on.isExperimentalEnabled(ExperimentalFeature.MEDIA_CARD_PEEK))
        assertFalse(on.isExperimentalEnabled(ExperimentalFeature.HOME_CARD_CLIPPING))
    }

    @Test
    fun `experimental slice helper mirrors the full preferences helper`() {
        val enabled = setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION)
        val fromUserPrefs = UserPreferences(enabledExperimentalFeatures = enabled)
        val fromSlice = ExperimentalPreferences(enabledExperimentalFeatures = enabled)

        for (feature in ExperimentalFeature.entries) {
            assertEquals(
                fromUserPrefs.isExperimentalEnabled(feature),
                fromSlice.isExperimentalEnabled(feature),
                feature.name,
            )
        }
    }

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
