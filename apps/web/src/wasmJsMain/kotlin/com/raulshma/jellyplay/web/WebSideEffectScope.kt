package com.raulshma.jellyplay.web

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Page-lifetime fire-and-forget side-effect scope shared by the web shell's
 * controllers ([WebConnectController], [WebSeerrController]) — the single
 * owner of the `CoroutineScope(SupervisorJob() + Dispatchers.Default)` +
 * swallow-degrade launch shape both used to hand-roll.
 *
 * WHY IT EXISTS (SIDE-EFFECT OWNERSHIP, from WebConnectController's KDoc):
 * publishing a session / navigating away DISPOSES the pane coroutine scope
 * that made the call, killing anything still running there. Post-success work
 * with real effects (capabilities POST, DataStore/localStorage writes) must
 * run on a scope the pane does NOT own — this one lives as long as the page
 * does, like the singleton API clients, and is never cancelled explicitly.
 * SupervisorJob keeps one failed job from tearing down siblings;
 * Dispatchers.Default is fine for a DataStore write and one POST on wasm.
 *
 * [launchDegrading] is the only launch path: side-effect work degrades
 * silently (a broken store must keep the UI usable, session-only — the
 * localStorage adapters already degrade internally; this catch covers the
 * store plumbing itself) and must never let a page-lifetime job die loudly.
 * CancellationException is rethrown: the scope is never cancelled, but a
 * rethrow keeps the shape coroutine-correct if that ever changes.
 */
internal class WebSideEffectScope {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Fire-and-forget launch with silent degrade: a thrown failure is
     * contained inside the job (broken-store swallow, pane-disposal survival)
     * instead of surfacing. Deliberately NOT a general-purpose [launch] —
     * callers cannot skip the degrade guard.
     */
    fun launchDegrading(block: suspend CoroutineScope.() -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Storage unavailable/quota/corruption, job races, transport
                // throw around a Result-based call: degrade, keep the UI usable.
            }
        }
    }
}
