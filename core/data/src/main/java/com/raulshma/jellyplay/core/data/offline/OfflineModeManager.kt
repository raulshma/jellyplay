package com.raulshma.jellyplay.core.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
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
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val userPreferencesStore: UserPreferencesStore,
) : DefaultLifecycleObserver {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _offlineMode = MutableStateFlow(OfflineMode.ONLINE)
    val offlineMode: StateFlow<OfflineMode> = _offlineMode.asStateFlow()

    val isOffline: Boolean get() = _offlineMode.value != OfflineMode.ONLINE

    init {
        scope.launch {
            combine(
                userPreferencesStore.preferences,
                networkMonitor.networkStatus
            ) { prefs, status ->
                Pair(prefs, status)
            }.collect { (prefs, status) ->
                if (prefs.manualOfflineEnabled) {
                    _offlineMode.value = OfflineMode.OFFLINE_MANUAL
                } else {
                    val current = _offlineMode.value
                    if (current == OfflineMode.OFFLINE_MANUAL) {
                        _offlineMode.value = OfflineMode.ONLINE
                    }

                    val nowOffline = status == NetworkStatus.Offline
                    if (nowOffline) {
                        if (prefs.autoOfflineEnabled) {
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
        val currentManual = userPreferencesStore.preferences.value.manualOfflineEnabled
        scope.launch {
            userPreferencesStore.setManualOffline(!currentManual)
        }
    }

    fun checkNetworkAndAutoDetect() {
        val prefs = userPreferencesStore.preferences.value
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

        if (activeNetwork == null || !hasInternet) {
            if (prefs.autoOfflineEnabled && _offlineMode.value == OfflineMode.ONLINE) {
                _offlineMode.value = OfflineMode.OFFLINE_AUTO
            }
        } else if (isValidated && _offlineMode.value == OfflineMode.OFFLINE_AUTO) {
            _offlineMode.value = OfflineMode.ONLINE
        }
    }
}
