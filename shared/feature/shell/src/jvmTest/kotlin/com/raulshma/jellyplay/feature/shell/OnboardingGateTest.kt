package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Full boolean matrix for the shared first-run onboarding gate — the one pure
 * decision both shells run (Android JellyPlayApp's compose gate and desktop's
 * DesktopNavScaffold push). Pins the three inputs' contributions and the TV
 * delta: the TV build auto-completes onboarding at its call site and must
 * never gate, so `isTv = true` is a blanket deny.
 */
class OnboardingGateTest {

    @Test
    fun `first-run authenticated non-TV session gets the onboarding route`() {
        assertEquals(
            Route.Onboarding,
            onboardingGateRoute(authenticated = true, onboardingCompleted = false, isTv = false),
            "signed-in first run must push the wizard",
        )
    }

    @Test
    fun `already-onboarded session passes the gate`() {
        assertNull(
            onboardingGateRoute(authenticated = true, onboardingCompleted = true, isTv = false),
            "a completed user must never see the wizard re-pushed",
        )
    }

    @Test
    fun `signed-out session is never gated`() {
        // Android parity: the gate evaluates only inside the authenticated
        // branch — sign-in comes first, the wizard after.
        assertNull(
            onboardingGateRoute(authenticated = false, onboardingCompleted = false, isTv = false),
            "the signed-out host must not be asked for the wizard",
        )
    }

    @Test
    fun `TV delta — the auto-completing build is never gated`() {
        assertNull(
            onboardingGateRoute(authenticated = true, onboardingCompleted = false, isTv = true),
            "TV auto-completes onboarding at its call site; the gate must not fire",
        )
    }

    @Test
    fun `exhaustive boolean matrix`() {
        for (authenticated in listOf(false, true)) {
            for (onboardingCompleted in listOf(false, true)) {
                for (isTv in listOf(false, true)) {
                    val expected = if (authenticated && !onboardingCompleted && !isTv) {
                        Route.Onboarding
                    } else {
                        null
                    }
                    assertEquals(
                        expected,
                        onboardingGateRoute(authenticated, onboardingCompleted, isTv),
                        "gate($authenticated, $onboardingCompleted, isTv=$isTv)",
                    )
                }
            }
        }
    }
}
