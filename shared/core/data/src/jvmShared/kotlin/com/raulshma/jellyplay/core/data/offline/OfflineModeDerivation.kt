package com.raulshma.jellyplay.core.data.offline

import com.raulshma.jellyplay.core.model.OfflineMode

/**
 * The ONE offline-mode derivation (I2 fold) — the pure value ladders the
 * Android and desktop [OfflineModeManager] implementations used to restate
 * as hand-twins: the store×network collector (AndroidOfflineModeManager's
 * combine ladder, mirrored on desktop minus the auto arms) and the
 * foreground re-derivation (`checkNetworkAndAutoDetect`'s ConnectivityManager
 * ladder, mirrored on desktop as a manual-only re-derive). No flows, no
 * Android imports — the platform managers own the wiring (lifecycle
 * observers, connectivity probes) and these functions own the decisions.
 *
 * The one real policy delta is explicit as [allowAuto]:
 *
 *  - `true` (Android): the full ladder — the auto-offline preference plus a
 *    lost network engages [OfflineMode.OFFLINE_AUTO], and network
 *    restoration clears it.
 *  - `false` (desktop): offline mode never auto-engages — the manual toggle
 *    is the only path to [OfflineMode.OFFLINE_MANUAL], and a flapping
 *    network probe must not flip the mode (a drain/reconcile skip the user
 *    does not expect is worse than a stale online row). The dropped
 *    `autoOfflineEnabled` arm of the desktop twins is this parameter, not a
 *    silent omission.
 */
internal object OfflineModeDerivation {

    /**
     * The store×network collector ladder: (manual pref, auto pref, network
     * offline, current mode) → next mode.
     *
     * Manual wins outright. Below it, a stale [OfflineMode.OFFLINE_MANUAL]
     * clears first (the pref just flipped off), then the network decides:
     * offline engages [OfflineMode.OFFLINE_AUTO] only when the auto pref is
     * set AND [allowAuto]; everything else converges to
     * [OfflineMode.ONLINE] — including a current [OfflineMode.OFFLINE_AUTO]
     * on a restored network.
     */
    fun fromCollector(
        manual: Boolean,
        auto: Boolean,
        offline: Boolean,
        current: OfflineMode,
        allowAuto: Boolean,
    ): OfflineMode {
        if (manual) return OfflineMode.OFFLINE_MANUAL
        var next = current
        if (next == OfflineMode.OFFLINE_MANUAL) next = OfflineMode.ONLINE
        return when {
            offline -> if (auto && allowAuto) OfflineMode.OFFLINE_AUTO else OfflineMode.ONLINE
            // Reads the post-clear value, matching the managers' in-place
            // writes: a stale MANUAL clears to ONLINE here, never AUTO.
            next == OfflineMode.OFFLINE_AUTO -> OfflineMode.ONLINE
            else -> next
        }
    }

    /**
     * The synchronous re-derivation behind `checkNetworkAndAutoDetect`:
     * (manual pref, probe reachability, auto pref, current mode) → next
     * mode. [reachable] folds the managers' probe verdicts (Android: an
     * active network with INTERNET + VALIDATED — an unvalidated captive
     * portal counts as unreachable; desktop: never consulted, see below).
     *
     * With [allowAuto] the ladder is manual-wins, then unreachable engages
     * [OfflineMode.OFFLINE_AUTO] only from [OfflineMode.ONLINE] (an engaged
     * AUTO is sticky while still unreachable), and a reachable network
     * clears a current [OfflineMode.OFFLINE_AUTO]. With `allowAuto = false`
     * the probe is irrelevant by policy: the manual flag is the only input,
     * and a stale [OfflineMode.OFFLINE_MANUAL] still clears — desktop's
     * manual-only re-derive, verbatim.
     */
    fun fromProbe(
        manual: Boolean,
        reachable: Boolean,
        auto: Boolean,
        current: OfflineMode,
        allowAuto: Boolean,
    ): OfflineMode = when {
        manual -> OfflineMode.OFFLINE_MANUAL
        !allowAuto ->
            if (current == OfflineMode.OFFLINE_MANUAL) OfflineMode.ONLINE else current
        reachable ->
            if (current == OfflineMode.OFFLINE_AUTO) OfflineMode.ONLINE else current
        else ->
            if (auto && current == OfflineMode.ONLINE) OfflineMode.OFFLINE_AUTO else current
    }
}
