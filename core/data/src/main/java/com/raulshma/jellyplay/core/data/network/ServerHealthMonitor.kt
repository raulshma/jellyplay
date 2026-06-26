package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Periodically checks the health of the connected Jellyfin server by pinging
 * the `/System/Info/Public` endpoint. Exposes a [StateFlow] of [ServerHealth]
 * that can be consumed by the UI.
 *
 * The monitor starts checking when a server is connected and stops when
 * disconnected. The check interval is [HEALTH_CHECK_INTERVAL_MS].
 */
@Singleton
class ServerHealthMonitor @Inject constructor(
    private val apiClient: JellyfinApiClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _serverHealth = MutableStateFlow<ServerHealth>(ServerHealth.Unknown)
    val serverHealth: StateFlow<ServerHealth> = _serverHealth.asStateFlow()

    @Volatile
    private var currentServerAddress: String? = null
    private var monitorJob: Job? = null

    /**
     * Starts monitoring the server health. Safe to call multiple times;
     * switching to a different address restarts the loop, while calling with
     * the same address is a no-op.
     */
    fun startMonitoring(serverAddress: String?) {
        if (serverAddress == null) {
            stopMonitoring()
            return
        }
        if (currentServerAddress == serverAddress && monitorJob?.isActive == true) return

        // Cancel any in-flight loop before starting a new one to avoid races
        // where two coroutines ping concurrently after an address switch.
        monitorJob?.cancel()
        currentServerAddress = serverAddress

        monitorJob = scope.launch {
            while (true) {
                checkHealth(serverAddress)
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Stops the health monitoring loop and clears the published status.
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        currentServerAddress = null
        _serverHealth.value = ServerHealth.Unknown
    }

    /**
     * Performs a single health check against the given server address
     * (or the currently monitored address when null).
     */
    suspend fun checkHealth(serverAddress: String? = currentServerAddress) {
        if (serverAddress == null) {
            _serverHealth.value = ServerHealth.Unknown
            return
        }

        _serverHealth.value = ServerHealth.Checking

        val startTime = System.currentTimeMillis()
        val result = apiClient.getServerInfo(serverAddress)
        val latency = System.currentTimeMillis() - startTime

        _serverHealth.value = if (result.isSuccess) {
            ServerHealth.Healthy(latencyMs = latency)
        } else {
            ServerHealth.Unreachable
        }
    }

    companion object {
        /** How often to ping the server (5 minutes). */
        private const val HEALTH_CHECK_INTERVAL_MS = 5 * 60 * 1000L
    }
}
