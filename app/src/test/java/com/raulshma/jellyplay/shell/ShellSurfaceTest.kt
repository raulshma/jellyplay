package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.feature.shell.LaunchPhase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the pure surface fold the root overlays' main-surface gate runs — the
 * branch-for-branch mirror of JellyPlayApp's session `when` (see
 * [shellSurface]'s KDoc). The deferred-render story for a gated update sheet
 * depends on this mirror being exact: a surface that disagreed with the
 * rendered branch would either leak the sheet over onboarding/auth or
 * withhold it from main. Pure values only — no Android, no coroutines.
 */
class ShellSurfaceTest {

    @Test
    fun `Restoring is OnboardingOrAuth - overlays withheld while the splash is pinned`() {
        assertEquals(
            ShellSurface.OnboardingOrAuth,
            shellSurface(LaunchPhase.Restoring, onboardingCompleted = true, isTv = false),
        )
    }

    @Test
    fun `Authenticated without completed onboarding is OnboardingOrAuth - the wizard branch`() {
        assertEquals(
            ShellSurface.OnboardingOrAuth,
            shellSurface(LaunchPhase.Authenticated, onboardingCompleted = false, isTv = false),
        )
    }

    @Test
    fun `Authenticated past the gate is Main`() {
        assertEquals(
            ShellSurface.Main,
            shellSurface(LaunchPhase.Authenticated, onboardingCompleted = true, isTv = false),
        )
    }

    @Test
    fun `TV is Main even while the onboarding pref is unset - the auto-complete build never gates`() {
        // Same frame the `when` renders MainContent: the gate returns null for
        // TV and the LaunchedEffect marks the pref, so the update sheet may
        // surface here exactly like any other main-surface content.
        assertEquals(
            ShellSurface.Main,
            shellSurface(LaunchPhase.Authenticated, onboardingCompleted = false, isTv = true),
        )
    }

    @Test
    fun `NeedsAuth is OnboardingOrAuth - the signed-out auth host branch, regardless of the pref`() {
        assertEquals(
            ShellSurface.OnboardingOrAuth,
            shellSurface(LaunchPhase.NeedsAuth, onboardingCompleted = true, isTv = false),
        )
        assertEquals(
            ShellSurface.OnboardingOrAuth,
            shellSurface(LaunchPhase.NeedsAuth, onboardingCompleted = false, isTv = false),
        )
    }
}
