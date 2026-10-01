package com.raulshma.jellyplay.core.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
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
 * The foreground probe verdict behind [OfflineModeDerivation.fromProbe]: an
 * active network carrying both INTERNET and VALIDATED capabilities. Treats
 * an unvalidated network (captive portal, Wi-Fi with no upstream) as
 * unreachable too — a network can report INTERNET capability yet fail
 * validation, leaving the app unable to reach the server; auto-offline then
 * surfaces the downloaded library instead of erroring.
 *
 * A top-level function (not a member) so the super-constructor lambda can
 * reference it from the [Context] parameter alone — no `this` capture before
 * the body's constructor has run.
 */
private fun Context.probeConnectivityReachable(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork
    val capabilities = activeNetwork?.let {
        connectivityManager.getNetworkCapabilities(it)
    }
    val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    return activeNetwork != null && hasInternet && isValidated
}
