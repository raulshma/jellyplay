package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the pause-details overlay's gate (#13 — the additive
 * [shouldShowPauseDetails] fold in PlayerScreenPolicies.kt): pref on, paused,
 * displayable details present, and the #11 screensaver NOT active — the
 * details yield to the screensaver when both conditions hold.
 */
class PauseDetailsPolicyTest {

    @Test
    fun showsOnPauseWithDetails() {
        assertTrue(
            shouldShowPauseDetails(
                enabled = true,
                isPaused = true,
                hasDetails = true,
                screensaverActive = false,
            ),
        )
    }

    @Test
    fun everyMissingInputSuppressesThePanel() {
        fun show(
            enabled: Boolean = true,
            isPaused: Boolean = true,
            hasDetails: Boolean = true,
            screensaverActive: Boolean = false,
        ) = shouldShowPauseDetails(enabled, isPaused, hasDetails, screensaverActive)

        assertFalse(show(enabled = false), "the pref gates the whole feature")
        assertFalse(show(isPaused = false), "playing playback never shows it")
        assertFalse(show(hasDetails = false), "an empty session (no title, no synopsis) has nothing to render")
    }

    @Test
    fun theScreensaverWinsWhenBothAreActive() {
        assertFalse(
            shouldShowPauseDetails(
                enabled = true,
                isPaused = true,
                hasDetails = true,
                screensaverActive = true,
            ),
            "the #11 screensaver covers the whole surface — the details panel must yield",
        )
    }

    @Test
    fun theDecisionIsExactlyTheConjunction() {
        // Exhaustive 16-row matrix: true iff (enabled ∧ paused ∧ details ∧ ¬screensaver).
        for (enabled in listOf(false, true)) {
            for (isPaused in listOf(false, true)) {
                for (hasDetails in listOf(false, true)) {
                    for (screensaverActive in listOf(false, true)) {
                        val expected = enabled && isPaused && hasDetails && !screensaverActive
                        assertEquals(
                            expected,
                            shouldShowPauseDetails(enabled, isPaused, hasDetails, screensaverActive),
                            "matrix row(enabled=$enabled, paused=$isPaused, details=$hasDetails, screensaver=$screensaverActive)",
                        )
                    }
                }
            }
        }
    }
}
