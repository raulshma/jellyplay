package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.shell.onboardingGateRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 *  first-run gate: pins the pure decision the desktop shell makes
 * once per authenticated session (DesktopNavScaffold) against the Android
 * JellyPlayApp branch it mirrors — gate fires only for a signed-in user that
 * never completed the wizard, never for a completer or a signed-out session.
 *
 * The decision itself moved to the shared
 * [onboardingGateRoute] (:shared:feature:shell — the Android shell runs the
 * same fn with the TV delta); these pins stay on the desktop wrapper, whose
 * job is now exactly the `isTv = false` specialization (the desktop build is
 * never the TV build). [runDesktopOnboardingGateOnce] — the extracted
 * one-shot read + dispatch the scaffold's LaunchedEffect delegates to — is
 * pinned here too: the persisted flag is read EXACTLY once and the wizard is
 * pushed only for a not-yet-onboarded session.
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

    @Test
    fun `wrapper is exactly the isTv=false row of the shared gate matrix`() {
        for (authenticated in listOf(false, true)) {
            for (onboardingCompleted in listOf(false, true)) {
                assertEquals(
                    onboardingGateRoute(
                        authenticated = authenticated,
                        onboardingCompleted = onboardingCompleted,
                        isTv = false,
                    ),
                    desktopOnboardingGateRoute(
                        isAuthenticated = authenticated,
                        onboardingCompleted = onboardingCompleted,
                    ),
                    "desktop wrapper must stay the isTv=false specialization",
                )
            }
        }
    }

    // ── runDesktopOnboardingGateOnce ────────────────────────────────────

    @Test
    fun `the one-shot gate pushes the wizard for a first-run session after exactly one read`() = runTest {
        var reads = 0
        var pushed: Route.Onboarding? = null
        runDesktopOnboardingGateOnce(
            readOnboardingCompleted = { reads += 1; false },
            navigate = { pushed = it },
        )
        assertEquals(Route.Onboarding, pushed, "a not-yet-onboarded session gets the wizard pushed")
        assertEquals(1, reads, "the persisted flag is read exactly once")
    }

    @Test
    fun `an onboarded session reads once and pushes nothing`() = runTest {
        var reads = 0
        var pushed = false
        runDesktopOnboardingGateOnce(
            readOnboardingCompleted = { reads += 1; true },
            navigate = { pushed = true },
        )
        assertFalse(pushed, "a completer must never see the wizard re-pushed")
        assertEquals(1, reads)
    }

    @Test
    fun `the gate never dispatches when the decision is null`() = runTest {
        // Belt-and-suspenders shape pin: a passing gate performs NO navigate
        // call at all (not a navigate-with-null), so the scaffold's navigator
        // is untouched for already-onboarded sessions.
        var navigateCalls = 0
        runDesktopOnboardingGateOnce(
            readOnboardingCompleted = { true },
            navigate = { navigateCalls += 1 },
        )
        assertTrue(navigateCalls == 0, "no navigation side effect for an onboarded session")
    }
}
