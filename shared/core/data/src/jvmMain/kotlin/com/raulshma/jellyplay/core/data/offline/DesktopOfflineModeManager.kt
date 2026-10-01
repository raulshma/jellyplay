package com.raulshma.jellyplay.core.data.offline

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore

/**
 * Desktop actual of the [OfflineModeManager] seam: a bare constructor over
 * the shared jvmShared body ([OfflineModeManagerBody] — the I2 fold of the
 * former line-for-line twins) with `allowAuto = false` — offline mode never
 * auto-engages on desktop. The manual toggle is the only path to
 * [OfflineMode.OFFLINE_MANUAL]; post-17C the [DesktopNetworkMonitor] probe
 * feeds the collector as a re-derive trigger only, never a value, and the
 * auto-offline arms the Android ladder carries are — visibly now, via the
 * policy parameter — desktop-dropped. `checkNetworkAndAutoDetect`'s
 * reachability probe is correspondingly never consulted (the body skips it
 * when `allowAuto` is false): the real probe flipping the reported network
 * status must not flip the mode either.
 *
 * The dropped ProcessLifecycleOwner foreground check has no desktop
 * equivalent; nothing else remains platform-specific here.
 */
class DesktopOfflineModeManager(
    networkMonitor: NetworkMonitor,
    networkOfflineStore: NetworkOfflineStore,
) : OfflineModeManagerBody(
    networkMonitor = networkMonitor,
    networkOfflineStore = networkOfflineStore,
    allowAuto = false,
    probeReachable = { false }, // never invoked while allowAuto = false
)
