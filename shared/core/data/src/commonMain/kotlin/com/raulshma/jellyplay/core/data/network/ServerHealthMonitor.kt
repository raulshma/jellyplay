package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.concurrency.SingleFlight
import com.raulshma.jellyplay.core.concurrency.TaskBundle
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.EpochMillisSource
import com.raulshma.jellyplay.core.data.util.ioDispatcher
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Periodically checks the health of the connected Jellyfin server by pinging
 * the `/System/Info/Public` endpoint. Exposes a [StateFlow] of [ServerHealth]
 * that can be consumed by the UI.
 *
 * The monitor starts checking when a server is connected and stops when
 * disconnected. The check interval is [HEALTH_CHECK_INTERVAL_MS]; in between
 * ticks, every network-status transition to a usable network (Online or
 * Local) re-probes immediately, so the published verdict reflects the
 * network the device is actually on rather than the one the last periodic
 * tick saw — the header indicator reads this flow, and a stale
 * Healthy/Unreachable right after a Wi-Fi change is exactly the wrong
 * answer. While a re-probe is in flight the previous verdict stays
 * published: [ServerHealth.Checking] means only "no verdict yet" (before
 * the first probe completes, or after [stopMonitoring]), never "a verdict
 * is being re-derived" — the header ladder has no Checking branch, and
 * republishing it mid-session would flicker the indicator on every probe.
 *
 * promotion from jvmShared: the clock edge narrowed to the common
 * [EpochMillisSource] seam (JVM [TimeSource] fakes in jvmTest still satisfy
 * it through the supertype), the loop dispatcher moved to the module's
 * [ioDispatcher] expect/actual (`Dispatchers.IO` on android/desktop — the
 * identical production dispatcher), and `@Volatile` became the common
 * kotlin.concurrent annotation. [AuthApiClient] is a commonMain
 * interface, so the class crosses; its Koin single stays in dataJvmModule.
 */
private const val MONITOR_LOOP = "ServerHealthMonitor.loop"

/** The network-transition re-probe collector, cancelled with the loop. */
private const val NETWORK_TRIGGER = "ServerHealthMonitor.networkTrigger"

/** The single probe-guard key — loop ticks and transition re-probes queue on it. */
private const val PROBE = "ServerHealthMonitor.probe"

class ServerHealthMonitor(
    private val apiClient: AuthApiClient,
    /** Clock seam for the per-check latency measurement (start/delta pair). */
    private val timeSource: EpochMillisSource,
    /**
     * The network seam behind the transition re-probe: every emission after
     * the collector's initial echo that is still usable (`NetworkStatus.hasNetwork`)
     * triggers an immediate [checkHealth]. Offline is skipped — with no
     * network the probe can only fail, and the Offline verdict itself is
     * already the header's top-priority state.
     */
    private val networkMonitor: NetworkMonitor,
) {
    // The monitor loop runs on [ioDispatcher] in production. Unit tests swap
    // this for their virtual-time test dispatcher (see [useDispatcherForTest])
    // so the loop advances on the test's clock — otherwise it races runTest's
    // scheduler and the "startMonitoring calls checkHealth" assertion flakes.
    private var loopDispatcher: CoroutineDispatcher = ioDispatcher
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + loopDispatcher)

    /**
     * Test-only: run the monitoring loop on [dispatcher] (typically runTest's
     * [kotlinx.coroutines.test.StandardTestDispatcher]) so the loop advances on
     * the test's virtual clock instead of a real IO thread. Production code
     * constructs this via Koin (dataJvmModule) and keeps [ioDispatcher].
     * Must be called before
     * [startMonitoring].
     */
    fun useDispatcherForTest(dispatcher: CoroutineDispatcher) {
        // Tear down any loop started on the default IO scope before swapping.
        monitorTasks.cancel(MONITOR_LOOP)
        scope.cancel()
        loopDispatcher = dispatcher
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        monitorTasks = TaskBundle(scope)
    }

    private val _serverHealth = MutableStateFlow<ServerHealth>(ServerHealth.Unknown)
    val serverHealth: StateFlow<ServerHealth> = _serverHealth.asStateFlow()

    /**
     * Serializes probes: the periodic loop and the network-transition
     * collector can both want to check at once, and without the mutual
     * exclusion their verdict writes can interleave — a slow probe's verdict
     * landing over a fresh one's. Inside the guard each probe publishes
     * atomically; a queued second probe simply waits its turn.
     *
     * A [SingleFlight] with one key rather than a bare Mutex field — the
     * same init-order structural fix as EPG's guide mutex (99e665a35): the
     * lock table is born, empty and complete, inside this val, so there is
     * no lock declaration left to race a coroutine reaching it ahead of its
     * initialization. One key — the point here is the born-initialized
     * table, not fan-out.
     */
    private val probes = SingleFlight()

    @Volatile
    private var currentServerAddress: String? = null

    // The single monitor-loop slot: cancel-and-replace on address switch.
    // Recreated with the scope in [useDispatcherForTest]. Confined to the
    // caller's thread, like the plain var it replaces.
    private var monitorTasks = TaskBundle(scope)

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
        // Both slots must be alive to call this a no-op — they start and stop
        // together, and a dead trigger would otherwise survive as a zombie
        // behind the loop's guard.
        if (currentServerAddress == serverAddress && monitorTasks[MONITOR_LOOP]?.isActive == true &&
            monitorTasks[NETWORK_TRIGGER]?.isActive == true
        ) {
            return
        }

        // Cancel any in-flight loop before starting a new one to avoid races
        // where two coroutines ping concurrently after an address switch.
        currentServerAddress = serverAddress

        monitorTasks.replace(MONITOR_LOOP) {
            scope.launch {
            while (true) {
                // Re-run address selection first: this both fails over to an
                // alternate when the active endpoint died and switches back to
                // the primary once it answers again (primary is always probed
                // first). Health is then reported for the endpoint actually
                // in use.
                runCatchingRethrowingCancellation { apiClient.selectReachableAddress() }
                val activeAddress = apiClient.getServerUrl()?.takeIf { it.isNotBlank() } ?: serverAddress
                probeNow(activeAddress)
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
            }
        }
        monitorTasks.replace(NETWORK_TRIGGER) {
            scope.launch {
                // drop(1) skips the StateFlow's startup echo — the loop's
                // first iteration already checks, so the echo would only
                // double the initial probe. Every later emission is a real
                // network transition.
                networkMonitor.networkStatus.drop(1).collect { status ->
                    if (status.hasNetwork) {
                        probeNow(currentServerAddress)
                    }
                }
            }
        }
    }

    /**
     * Stops the health monitoring loop and clears the published status.
     */
    fun stopMonitoring() {
        monitorTasks.cancel(MONITOR_LOOP)
        monitorTasks.cancel(NETWORK_TRIGGER)
        currentServerAddress = null
        _serverHealth.value = ServerHealth.Unknown
    }

    /**
     * One probe behind the loop/collector's shared guard: [checkHealth] with
     * cancellation rethrown — both callers sit in infinite loops and
     * collectors where a swallowed cancellation would leak a zombie task.
     */
    private suspend fun probeNow(serverAddress: String?) {
        runCatchingRethrowingCancellation { checkHealth(serverAddress) }
    }

    /**
     * Performs a single health check against the given server address
     * (or the currently monitored address when null). Serialized by
     * [probes] so concurrent loop/transition probes publish their
     * verdicts atomically instead of interleaving.
     */
    suspend fun checkHealth(serverAddress: String? = currentServerAddress) {
        if (serverAddress == null) {
            _serverHealth.value = ServerHealth.Unknown
            return
        }

        probes.inFlight(PROBE) {
            // Checking only until the first verdict lands; later probes hold
            // the previous verdict visible while re-probing. The header has
            // no Checking branch, so republishing it mid-session rendered a
            // calm Local frame between two ServerUnreachable frames — a
            // flicker on every transition re-probe and every periodic tick.
            if (_serverHealth.value == ServerHealth.Unknown) {
                _serverHealth.value = ServerHealth.Checking
            }

            val startTime = timeSource.nowEpochMillis()
            val result = apiClient.getServerInfo(serverAddress)
            val latency = timeSource.nowEpochMillis() - startTime

            _serverHealth.value = if (result.isSuccess) {
                ServerHealth.Healthy(latencyMs = latency)
            } else {
                ServerHealth.Unreachable
            }
        }
    }

    companion object {
        /** How often to ping the server (5 minutes). */
        private const val HEALTH_CHECK_INTERVAL_MS = 5 * 60 * 1000L
    }
}
