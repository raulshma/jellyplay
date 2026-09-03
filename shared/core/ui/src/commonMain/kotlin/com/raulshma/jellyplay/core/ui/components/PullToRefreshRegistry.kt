package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Session-scoped registry of the pull-to-refresh action belonging to the
 * screen currently on screen.
 *
 * Why it exists: a shell-level refresh affordance (the desktop title bar's
 * File→Refresh / Ctrl+R) must trigger whatever refresh action the active
 * screen owns, but the shell cannot know each screen's ViewModel event type.
 * Instead of threading a per-screen request flow through navigation (a
 * parameter per PTR screen, forever), [PullToRefreshBox] registers its
 * `onRefresh` here while it is composed and enabled, and the shell dispatches
 * into the registry. New pull-to-refresh screens participate automatically —
 * zero per-screen wiring.
 *
 * UI-thread only (Compose state discipline); instance lifetime = the shell
 * that provides it via [LocalPullToRefreshRegistry], so registrations never
 * outlive their screen's session.
 */
class PullToRefreshRegistry {

    // Registration order is composition order; at most one pull-to-refresh
    // container is composed at a time on every current screen layout, but the
    // stack keeps dispatch deterministic (topmost wins) if that ever changes.
    private val actions = mutableListOf<() -> Unit>()

    /**
     * Registers [action] as the refresh action of a composed, enabled
     * pull-to-refresh container. Returns the unregister handle — the container
     * invokes it on dispose (or when [PullToRefreshBox's enabled][PullToRefreshBox]
     * flips false, e.g. an overlaid search taking the screen over).
     */
    fun register(action: () -> Unit): () -> Unit {
        actions += action
        return { actions.remove(action) }
    }

    /**
     * Runs the active screen's refresh action. Returns false when no
     * pull-to-refresh screen is composed (or its container is disabled) —
     * shells decide what that means (the desktop menu drops it silently).
     */
    fun refreshActive(): Boolean {
        val action = actions.lastOrNull() ?: return false
        action()
        return true
    }
}

/**
 * Provided by form-factor shells that own a global refresh affordance. The
 * default instance is a throwaway: screens on platforms without a dispatcher
 * (Android, TV, web) register into it, which costs nothing and never fires.
 */
val LocalPullToRefreshRegistry = staticCompositionLocalOf { PullToRefreshRegistry() }
