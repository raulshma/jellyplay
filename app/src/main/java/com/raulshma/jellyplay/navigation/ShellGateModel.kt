package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlinx.coroutines.flow.StateFlow

/**
 * The slice of the activity-scoped MainViewModel the shell session gate
 * ([com.raulshma.jellyplay.navigation.JellyPlayApp]) reads: the merged
 * preference pipeline (the onboarding gate's completion flag rides it), the
 * TV auto-complete command for that gate, the one-shot
 * surprise-on-launch signal + its consume ack, and the two shell-overlay
 * commands — logout (dispatched through the shared ShellSessionController
 * fork) and the What's New sheet's pending-route navigation. Extends
 * [MainShellModel], so one ViewModel implements both views of the
 * composition: this interface is the root gate's, [MainShellModel] the
 * layout subtree's ([MainContent]) — the concrete ViewModel type stays in
 * MainActivity's composition root.
 */
internal interface ShellGateModel : MainShellModel {

    /**
     * The shell preferences (MainActivity + JellyPlayApp read): the merged
     * projection pipeline with the runtime-only lockout and onboarding
     * fields combined in. The gate branch reads `onboardingCompleted` off
     * it; MainActivity's locale/theme/lock collectors read the rest.
     */
    val preferences: StateFlow<MainPreferences>

    /** Persists onboarding completion (the TV build auto-completes at the gate). */
    fun markOnboardingCompleted()

    /**
     * Whether the fresh-launch "Surprise Me" flow is armed (the launcher
     * shortcut set it). [consumeSurpriseOnLaunch] acks it exactly once.
     */
    val surpriseOnLaunch: StateFlow<Boolean>

    /** Clears the surprise-on-launch signal after it is consumed. */
    fun consumeSurpriseOnLaunch()

    /**
     * Ends the session: `revoke = true` also revokes the server session
     * token, `false` signs out locally only.
     */
    fun logout(revoke: Boolean)

    /**
     * Publishes a shell-overlay navigation request (the What's New sheet's
     * "take me there" deep links) onto the pending-route channel.
     */
    fun navigateFromShell(route: Route)
}
