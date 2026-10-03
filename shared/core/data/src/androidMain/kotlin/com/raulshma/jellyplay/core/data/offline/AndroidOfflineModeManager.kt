package com.raulshma.jellyplay.core.data.offline

import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.network.activeNetworkStatus
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.NetworkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android actual of the [OfflineModeManager] seam (C4 part 2): a thin
 * constructor over the shared jvmShared body ([OfflineModeManagerBody] — the
 * I2 fold of the former hand-twins) plus the two Android-only pieces — the
 * ConnectivityManager reachability probe and the ProcessLifecycleOwner
 * foreground re-derivation. Full derivation semantics (the OFFLINE_AUTO
 * ladder) live in [OfflineModeDerivation]; this file owns only wiring.
 *
 * Body moved verbatim from the legacy `:core:data` `OfflineModeManager` —
 * the class was renamed and the Hilt annotations stripped (Koin's
 * [com.raulshma.jellyplay.core.data.di.androidDataModule] constructs it;
 * consumers resolve the same single straight from Koin).
 */
class AndroidOfflineModeManager(
    context: Context,
    networkMonitor: NetworkMonitor,
    networkOfflineStore: NetworkOfflineStore,
) : OfflineModeManagerBody(
    networkMonitor = networkMonitor,
    networkOfflineStore = networkOfflineStore,
    // The full ladder: the auto-offline pref + a lost network engages
    // OFFLINE_AUTO (desktop's delta is its allowAuto=false).
    allowAuto = true,
    probeReachable = { context.probeConnectivityReachable() },
), DefaultLifecycleObserver {

    init {
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this@AndroidOfflineModeManager)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        checkNetworkAndAutoDetect()
    }
}

/**
 * The foreground probe verdict behind [OfflineModeDerivation.fromProbe]: the
 * active network mapped through the same ladder the monitor publishes
 * ([activeNetworkStatus] → [NetworkStatus]) — Online and Local count as
 * reachable; only [NetworkStatus.Offline] (no active network, or no INTERNET
 * capability) does not. Validation is deliberately not required — an
 * unvalidated network (captive portal, Wi-Fi whose uplink is down) is exactly
 * the shape of a LAN connection whose Jellyfin server may still be reachable,
 * so demanding VALIDATED engages auto-offline — the downloaded library — over
 * a usable online session. Reading the shared ladder (not a hand-mirrored
 * capability check) is what keeps this sync verdict and the monitor's
 * published status from drifting.
 *
 * A top-level function (not a member) so the super-constructor lambda can
 * reference it from the [Context] parameter alone — no `this` capture before
 * the body's constructor has run.
 */
private fun Context.probeConnectivityReachable(): Boolean =
    activeNetworkStatus(
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
    ) != NetworkStatus.Offline
