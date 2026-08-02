package com.raulshma.jellyplay.core.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val networkOfflineStore: com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore,
) : DefaultLifecycleObserver {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _offlineMode = MutableStateFlow(OfflineMode.ONLINE)
    val offlineMode: StateFlow<OfflineMode> = _offlineMode.asStateFlow()

    val isOffline: Boolean get() = _offlineMode.value != OfflineMode.ONLINE

    val networkStatus: StateFlow<NetworkStatus> = networkMonitor.networkStatus

    init {
        scope.launch {
            // Combine the manual/auto offline slice fields instead of the
            // full ~150-field `preferences` StateFlow. A settings-screen round
            // trip elsewhere in the app previously re-awakened this collector
            // and re-derived offline mode even though neither offline flag
            // changed.
            combine(
                networkOfflineStore.networkOffline.map { it.manualOfflineEnabled },
                networkOfflineStore.networkOffline.map { it.autoOfflineEnabled },
                networkMonitor.networkStatus,
            ) { manualOffline, autoOffline, status ->
                Triple(manualOffline, autoOffline, status)
            }.collect { (manualOffline, autoOffline, status) ->
                if (manualOffline) {
                    _offlineMode.value = OfflineMode.OFFLINE_MANUAL
                } else {
                    val current = _offlineMode.value
                    if (current == OfflineMode.OFFLINE_MANUAL) {
                        _offlineMode.value = OfflineMode.ONLINE
                    }

                    val nowOffline = status == NetworkStatus.Offline
                    if (nowOffline) {
                        if (autoOffline) {
                            _offlineMode.value = OfflineMode.OFFLINE_AUTO
                        } else {
                            _offlineMode.value = OfflineMode.ONLINE
                        }
                    } else {
                        if (_offlineMode.value == OfflineMode.OFFLINE_AUTO) {
                            _offlineMode.value = OfflineMode.ONLINE
                        }
                    }
                }
            }
        }

        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this@OfflineModeManager)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        checkNetworkAndAutoDetect()
    }

    fun toggleManualOffline() {
        val currentManual = networkOfflineStore.networkOffline.value.manualOfflineEnabled
        scope.launch {
            networkOfflineStore.setManualOffline(!currentManual)
        }
    }

    fun checkNetworkAndAutoDetect() {
        val prefs = networkOfflineStore.networkOffline.value
        if (prefs.manualOfflineEnabled) {
            _offlineMode.value = OfflineMode.OFFLINE_MANUAL
            return
        }

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let {
            connectivityManager.getNetworkCapabilities(it)
        }

        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        // Treat an unvalidated network (captive portal, Wi-Fi with no upstream) as
        // offline too: a network can report INTERNET capability yet fail validation,
        // leaving the app unable to reach the server. Auto-offline then surfaces the
        // downloaded library instead of erroring.
        val isReachable = hasInternet && isValidated

        if (activeNetwork == null || !isReachable) {
            if (prefs.autoOfflineEnabled && _offlineMode.value == OfflineMode.ONLINE) {
                _offlineMode.value = OfflineMode.OFFLINE_AUTO
            }
        } else if (isReachable && _offlineMode.value == OfflineMode.OFFLINE_AUTO) {
            _offlineMode.value = OfflineMode.ONLINE
        }
    }
}
