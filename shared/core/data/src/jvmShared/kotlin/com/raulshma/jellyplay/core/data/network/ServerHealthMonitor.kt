package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CoroutineDispatcher
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

/**
 * Periodically checks the health of the connected Jellyfin server by pinging
 * the `/System/Info/Public` endpoint. Exposes a [StateFlow] of [ServerHealth]
 * that can be consumed by the UI.
 *
 * The monitor starts checking when a server is connected and stops when
 * disconnected. The check interval is [HEALTH_CHECK_INTERVAL_MS].
 */
class ServerHealthMonitor(
    private val apiClient: JellyfinApiClient,
) {
    // The monitor loop runs on [Dispatchers.IO] in production. Unit tests swap
    // this for their virtual-time test dispatcher (see [useDispatcherForTest])
    // so the loop advances on the test's clock — otherwise it races runTest's
    // scheduler and the "startMonitoring calls checkHealth" assertion flakes.
    private var loopDispatcher: CoroutineDispatcher = Dispatchers.IO
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + loopDispatcher)

    /**
     * Test-only: run the monitoring loop on [dispatcher] (typically runTest's
     * [kotlinx.coroutines.test.StandardTestDispatcher]) so the loop advances on
     * the test's virtual clock instead of a real IO thread. Production code
     * constructs this via Koin (dataJvmModule) and keeps [Dispatchers.IO].
     * Must be called before
     * [startMonitoring].
     */
    fun useDispatcherForTest(dispatcher: CoroutineDispatcher) {
        // Tear down any loop started on the default IO scope before swapping.
        monitorJob?.cancel()
        monitorJob = null
        scope.cancel()
        loopDispatcher = dispatcher
        scope = CoroutineScope(SupervisorJob() + dispatcher)
    }

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
                // Re-run address selection first: this both fails over to an
                // alternate when the active endpoint died and switches back to
                // the primary once it answers again (primary is always probed
                // first). Health is then reported for the endpoint actually
                // in use.
                runCatching { apiClient.selectReachableAddress() }
                val activeAddress = apiClient.getServerUrl()?.takeIf { it.isNotBlank() } ?: serverAddress
                runCatching { checkHealth(activeAddress) }
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
