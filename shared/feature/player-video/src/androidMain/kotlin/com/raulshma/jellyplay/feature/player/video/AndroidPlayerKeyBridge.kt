package com.raulshma.jellyplay.feature.player.video

import androidx.compose.ui.input.key.KeyEvent

/**
 * Android deterministic player-key bridge — the Android twin of
 * [DesktopPlayerKeyBridge]. The TV focus grab
 * ([PlayerTvFocusGrabEffect]) recovers focus per loss, but issue #174 proved
 * recovery can still leave windows — races at the hide edge, overlay
 * unmounts, View-level focus sitting outside Compose — in which a D-pad press
 * finds no Compose focus target — and on Android Compose's null-focus
 * fallback has NO topmost key-input node above the player to catch it
 * (PlayerActivity had no key handling at all), so every remote key died
 * while Back survived (OnBackPressedDispatcher needs no focus).
 *
 * The deterministic path: the commonMain [VideoPlayerScreen] installs its
 * TV D-pad arm set here while the screen composes (the `isTv`-gated seam —
 * the same [installPlayerKeySink] seam family the desktop shell consumes),
 * and `PlayerActivity.dispatchKeyEvent` calls [deliver] with every key the
 * normal dispatch chain did NOT consume — exactly the null-focus case. One
 * arm set, one interpretation, whichever path the key arrived by.
 *
 * Public because PlayerActivity (app module) and the live player's screen
 * (player-live module) consume it — mirrors DesktopPlayerKeyBridge's
 * module-crossing role.
 */
object AndroidPlayerKeyBridge {

    @Volatile
    private var sink: ((KeyEvent) -> Boolean)? = null

    @Volatile
    private var deliveryCount: Int = 0

    /** Whether a player screen's handler is installed right now. */
    val isArmed: Boolean get() = sink != null

    /** Total sink invocations since process start (reach probe). */
    fun deliveryCount(): Int = deliveryCount

    /**
     * Install/remove the composing player screen's handler. Screens reach it
     * through the module's [installPlayerKeySink]/[uninstallPlayerKeySink]
     * seam actuals ([PlayerTvKeySinkEffect] — the VOD and live screens both
     * publish their arm sets there); every other consumer must treat this as
     * the screens' seam plumbing, not an app-level API.
     */
    internal fun install(sink: ((KeyEvent) -> Boolean)?) {
        this.sink = sink
    }

    /**
     * Offer one unconsumed key event to the installed player handler. False
     * when no screen is composed (activity hosting browse, sheet open, route
     * not up) or when the handler declines — the Activity then keeps the
     * system's default handling for the key (which for D-pad keys means
     * nothing, exactly the pre-fix behavior).
     */
    fun deliver(event: KeyEvent): Boolean {
        val current = sink ?: return false
        val handled = runCatching { current(event) }.getOrDefault(false)
        if (handled) deliveryCount += 1
        return handled
    }

    /**
     * [install]'s dispose twin, identity-guarded: the sink is cleared only
     * when it is still [expected] — a stacked player installed a newer sink
     * that the popped screen must not disarm (PlayerActivity's singleTask
     * onNewIntent swaps VOD ↔ live screens without a pop). Internal like
     * [install]; see its KDoc.
     */
    internal fun uninstall(expected: ((KeyEvent) -> Boolean)?) {
        if (sink === expected) sink = null
    }
}
