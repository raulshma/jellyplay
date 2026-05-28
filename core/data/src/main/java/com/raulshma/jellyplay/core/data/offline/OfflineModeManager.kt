package com.raulshma.jellyplay.core.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _offlineMode = MutableStateFlow(OfflineMode.ONLINE)
    val offlineMode: StateFlow<OfflineMode> = _offlineMode.asStateFlow()

    val isOffline: Boolean get() = _offlineMode.value != OfflineMode.ONLINE

    init {
        scope.launch {
            networkMonitor.networkStatus.collect { status ->
                when {
                    status == NetworkStatus.Offline -> {
                        if (_offlineMode.value == OfflineMode.ONLINE) {
                            _offlineMode.value = OfflineMode.OFFLINE_AUTO
                        }
                    }
                    status == NetworkStatus.Online || status == NetworkStatus.Local -> {
                        if (_offlineMode.value == OfflineMode.OFFLINE_AUTO) {
                            _offlineMode.value = OfflineMode.ONLINE
                        }
                    }
                }
            }
        }
    }

    fun toggleManualOffline() {
        _offlineMode.value = when (_offlineMode.value) {
            OfflineMode.ONLINE -> OfflineMode.OFFLINE_MANUAL
            OfflineMode.OFFLINE_MANUAL -> OfflineMode.ONLINE
            OfflineMode.OFFLINE_AUTO -> OfflineMode.ONLINE
        }
    }

    fun checkNetworkAndAutoDetect() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let {
            connectivityManager.getNetworkCapabilities(it)
        }

        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        if (activeNetwork == null || !hasInternet) {
            if (_offlineMode.value == OfflineMode.ONLINE) {
                _offlineMode.value = OfflineMode.OFFLINE_AUTO
            }
        } else if (isValidated && _offlineMode.value == OfflineMode.OFFLINE_AUTO) {
            _offlineMode.value = OfflineMode.ONLINE
        }
    }
}
