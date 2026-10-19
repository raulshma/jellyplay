package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The resolved, screen-facing verdict for one plugin feature: is this surface
 * usable right now? [Unknown] is the pre-probe state — the surface renders its
 * loading face and must not touch the plugin routes; [Available] opens the
 * surface (probe AVAILABLE **and** the feature key exposed **and** the user's
 * toggle on); [Unavailable] is every other no. The constants deliberately
 * mirror [JellyPlayPluginStatus] but are NOT it — the verdict folds the
 * per-feature toggle in on top of the probe.
 */
enum class JellyPlayCapabilityState { Unknown, Available, Unavailable }

/**
 * The plugin capability gate (the architecture review's cross-feature term):
 * the **probe-once-while-UNKNOWN, re-arm-on-identity-reset,
 * gate-reads-the-store** discipline behind one interface. The transcodes
 * monitor, the analytics dashboard, the admin dashboard's plugin tiles and
 * the settings root's plugin section each re-wired the same three pieces per
 * ViewModel — the `if (status == UNKNOWN) refresh()` probe hook, the
 * status ⊕ availability combine, and the store's session reset re-arming the
 * next visit — until this folded them.
 *
 * The three pieces, declared:
 *
 *  - **Probe once while UNKNOWN** — [arm] is the screen-visibility hook every
 *    consumer calls on entry ([refresh]/manual too): the store is refreshed
 *    exactly when its status is still [JellyPlayPluginStatus.UNKNOWN]; an
 *    AVAILABLE/UNAVAILABLE verdict stands for the session. A stock server
 *    eats exactly one probe per visit.
 *  - **Re-arm on identity reset** — inherited from the store: its
 *    [SessionCacheRegistry] action flips the status back to UNKNOWN on every
 *    non-SignedIn identity transition, [state] re-emits [Unknown], and the
 *    next [arm] (the next visit) re-probes. Nothing here listens to identity
 *    directly — the store is the one reset seam.
 *  - **Gate reads the store** — [state] combines the store's status with the
 *    feature's availability ([JellyPlayFeatureGate.isAvailable] — probe
 *    registry AND the user's toggle — or the probe-only fallback when the
 *    toggle seam is unwired, per [isAvailableOrProbe]); it never re-probes
 *    on its own and never reads per-endpoint 404s (ADR 0010 §6: the
 *    capability registry is the ONLY gating mechanism).
 *
 * Instances are cheap views over the singleton [JellyPlayPluginStatusStore] —
 * a ViewModel holds one and derives its feature's [state] from it; the probe
 * itself is store-level, so one [arm] serves every derived gate.
 */
class JellyPlayCapabilityGate(
    private val statusStore: JellyPlayPluginStatusStore,
    private val scope: CoroutineScope,
) {

    /**
     * The probe-once-while-UNKNOWN hook: one store refresh when no verdict
     * exists yet, a no-op otherwise. Fire-and-forget on [scope] — callers
     * observe the outcome through [state], never through this.
     */
    fun arm() {
        scope.launch {
            if (statusStore.status.value == JellyPlayPluginStatus.UNKNOWN) {
                statusStore.refresh()
            }
        }
    }

    /**
     * The reactive verdict for [featureKey]: [JellyPlayCapabilityState.Available]
     * only when the availability seam is open ([featureGate]'s toggle-aware
     * gate when wired, else the probe-only combine), [Unknown] while the store
     * has no verdict yet, [Unavailable] otherwise. A toggle flip, a probe
     * landing or an identity reset re-emits.
     *
     * Each call builds its own [StateFlow] — hold the returned value (a VM
     * keeps one per feature key it gates), don't re-invoke per read.
     */
    fun state(
        featureKey: String,
        featureGate: JellyPlayFeatureGate? = null,
    ): StateFlow<JellyPlayCapabilityState> =
        combine(statusStore.status, featureGate.isAvailableOrProbe(statusStore, featureKey)) { status, open ->
            when {
                open -> JellyPlayCapabilityState.Available
                status == JellyPlayPluginStatus.UNKNOWN -> JellyPlayCapabilityState.Unknown
                else -> JellyPlayCapabilityState.Unavailable
            }
        }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, JellyPlayCapabilityState.Unknown)
}
