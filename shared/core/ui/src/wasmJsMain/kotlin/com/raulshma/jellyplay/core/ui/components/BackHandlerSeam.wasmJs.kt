package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Shell-provided back dispatcher for the web runtime (wave 12C): the browser
 * shell owns ONE pop path — its nav stack is mirrored into browser history,
 * so every enabled back request must route through the shell's pop/history
 * bookkeeping rather than firing a raw `window.history.back()` per screen.
 *
 * The shell (apps/web's WebAppRoot) provides a [WebBackDispatcher] through
 * [LocalWebBackDispatcher] and — since wave 21C — consults it FIRST in its
 * pop path (`requestPop`): a registered, enabled handler consumes the back
 * press and the shell pops nothing; only when [dispatchBack] returns false
 * does the shell's own root-refusing, history-mirroring pop run. Outside
 * such a provider the value is `null` and [JellyPlayBackHandler] stays an
 * inert no-op, so future pages composed outside the web shell simply never
 * intercept anything.
 */
val LocalWebBackDispatcher: ProvidableCompositionLocal<WebBackDispatcher?> =
    staticCompositionLocalOf { null }

/**
 * Registration point the web shell hands to [JellyPlayBackHandler]
 * registrants. Handlers form a plain LIFO list: the most recently registered
 * active handler wins dispatch, which mirrors Android's on-back-callback
 * semantics for the nested-composition order Compose produces (parents compose
 * before their children, so children register later and win). The shell's
 * own pop path routes THROUGH this class since wave 21C — requestPop
 * dispatches here first and only falls back to its guarded history-mirroring
 * pop when nothing is registered.
 *
 * NOT thread-safe by design: registration happens during composition and
 * dispatch on the single UI thread/event loop of the browser page.
 */
class WebBackDispatcher {

    private val handlers = mutableListOf<() -> Unit>()

    /** Registers [handler]; the handler must be removed again via [unregister]. */
    fun register(handler: () -> Unit) {
        handlers += handler
    }

    /** Removes a previously registered [handler]; unknown handlers are ignored. */
    fun unregister(handler: () -> Unit) {
        handlers.remove(handler)
    }

    /**
     * Dispatches the most recently registered active handler. Returns whether
     * anything was armed at all — the shell's requestPop consumes exactly
     * this as its dispatch-first decision (true: a registrant handled the
     * press, the shell pops nothing; false: the shell's root-refusing,
     * history-mirroring pop runs).
     */
    fun dispatchBack(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }
}

/**
 * Wasm back-navigation seam (wave 12C): when the surrounding shell provides a
 * [WebBackDispatcher] via [LocalWebBackDispatcher], an ENABLED registration is
 * added to it while this call is in the composition (and removed on leave, or
 * whenever [enabled] flips off), with the latest [onBack] lambda captured via
 * [rememberUpdatedState] so recompositions do not churn registrations.
 * With no provider in scope (or [enabled] = false) nothing is registered —
 * the inert v1 behavior that predates the shell integration.
 */
@Composable
actual fun JellyPlayBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val dispatcher = LocalWebBackDispatcher.current
    // rememberUpdatedState BEFORE the conditional return keeps the slot table
    // consistent across enabled flips; the current lambda is what fires even
    // if it changed since registration.
    val currentOnBack by rememberUpdatedState(onBack)
    if (dispatcher == null || !enabled) return

    DisposableEffect(dispatcher) {
        val handler: () -> Unit = { currentOnBack() }
        dispatcher.register(handler)
        onDispose { dispatcher.unregister(handler) }
    }
}
