package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Wave 21B first-run gate: pins the pure decision the desktop shell makes
 * once per authenticated session (DesktopNavScaffold) against the Android
 * JellyPlayApp branch it mirrors — gate fires only for a signed-in user that
 * never completed the wizard, never for a completer or a signed-out session.
 */
class DesktopOnboardingGateTest {

    @Test
    fun `first-run authenticated session gets the onboarding route`() {
        assertEquals(
            Route.Onboarding,
            desktopOnboardingGateRoute(isAuthenticated = true, onboardingCompleted = false),
            "signed-in first run must push the wizard",
        )
    }

    @Test
    fun `already-onboarded session passes the gate`() {
        assertNull(
            desktopOnboardingGateRoute(isAuthenticated = true, onboardingCompleted = true),
            "a completed user must never see the wizard re-pushed",
        )
    }

    @Test
    fun `signed-out session is never gated`() {
        // Android parity: the gate evaluates only inside the authenticated
        // branch — sign-in comes first, the wizard after.
        assertNull(
            desktopOnboardingGateRoute(isAuthenticated = false, onboardingCompleted = false),
            "the signed-out host must not be asked for the wizard",
        )
    }
}
