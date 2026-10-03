package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.data.testutil.FakeNetworkMonitor
import com.raulshma.jellyplay.core.data.testutil.FakeTimeSource
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class ServerHealthMonitorTest {

    private lateinit var apiClient: AuthApiClient
    private lateinit var monitor: ServerHealthMonitor

    private val networkMonitor = FakeNetworkMonitor()

    @BeforeTest
    fun setUp() {
        apiClient = mockk(relaxed = true)
        monitor = ServerHealthMonitor(apiClient, FakeTimeSource(), networkMonitor)
    }

    private fun stubHealthyServer() {
        coEvery { apiClient.getServerInfo(any()) } returns Result.success(
            mockk {
                every { id } returns "server-1"
                every { name } returns "Test Server"
                every { address } returns "http://test-server:8096"
            }
        )
    }

    @Test
    fun `initial state is Unknown`() {
        assertEquals(ServerHealth.Unknown, monitor.serverHealth.value)
    }

    @Test
    fun `checkHealth sets Unreachable when server info fails`() = runTest {
        coEvery { apiClient.getServerInfo(any()) } returns Result.failure(Exception("Connection refused"))

        monitor.checkHealth("http://test-server:8096")

        assertEquals(ServerHealth.Unreachable, monitor.serverHealth.value)
    }

    @Test
    fun `checkHealth sets Healthy when server info succeeds`() = runTest {
        coEvery { apiClient.getServerInfo(any()) } returns Result.success(
            mockk {
                every { id } returns "server-1"
                every { name } returns "Test Server"
                every { address } returns "http://test-server:8096"
            }
        )

        monitor.checkHealth("http://test-server:8096")

        val health = monitor.serverHealth.value
        assertTrue(health is ServerHealth.Healthy)
        assertTrue((health as ServerHealth.Healthy).latencyMs >= 0)
    }

    @Test
    fun `checkHealth sets Unknown when server address is null`() = runTest {
        monitor.checkHealth(null)

        assertEquals(ServerHealth.Unknown, monitor.serverHealth.value)
    }

    @Test
    fun `stopMonitoring resets state to Unknown`() = runTest {
        coEvery { apiClient.getServerInfo(any()) } returns Result.success(
            mockk {
                every { id } returns "server-1"
                every { name } returns "Test Server"
                every { address } returns "http://test-server:8096"
            }
        )

        monitor.checkHealth("http://test-server:8096")
        assertTrue(monitor.serverHealth.value is ServerHealth.Healthy)

        monitor.stopMonitoring()
        assertEquals(ServerHealth.Unknown, monitor.serverHealth.value)
    }

    @Test
    fun `startMonitoring calls checkHealth with server address`() = runTest {
        // Share a single scheduler between runTest and the monitor's loop so the
        // loop advances on the virtual clock. The default Dispatchers.IO would
        // race runTest's scheduler and make this assertion flaky.
        val dispatcher = StandardTestDispatcher(testScheduler)
        monitor.useDispatcherForTest(dispatcher)
        coEvery { apiClient.getServerInfo(any()) } returns Result.success(
            mockk {
                every { id } returns "server-1"
                every { name } returns "Test Server"
                every { address } returns "http://test-server:8096"
            }
        )

        monitor.startMonitoring("http://test-server:8096")
        try {
            // Execute only the loop's currently-scheduled first iteration
            // (checkHealth runs before the first delay). runCurrent — NOT
            // advanceUntilIdle, which would loop forever against the monitor's
            // while(true) health-check cadence and exhaust memory.
            runCurrent()

            // Exactly one: the network-trigger collector drops the StateFlow's
            // startup echo, so only the loop's first iteration probes.
            coVerify(exactly = 1) { apiClient.getServerInfo("http://test-server:8096") }
        } finally {
            // Cancel the infinite monitor loop before runTest tears down, or its
            // pending delay would keep the shared scheduler busy forever.
            monitor.stopMonitoring()
        }
    }

    @Test
    fun `a network transition to a usable state re-probes immediately`() = runTest {
        monitor.useDispatcherForTest(StandardTestDispatcher(testScheduler))
        stubHealthyServer()

        monitor.startMonitoring("http://test-server:8096")
        try {
            runCurrent()
            coVerify(exactly = 1) { apiClient.getServerInfo(any()) }

            // Wi-Fi flips to an unvalidated LAN: the 5-minute tick would
            // answer the header too late, so the transition re-probes now.
            networkMonitor.networkStatus.value = NetworkStatus.Local
            runCurrent()
            coVerify(exactly = 2) { apiClient.getServerInfo(any()) }
        } finally {
            monitor.stopMonitoring()
        }
    }

    @Test
    fun `an Offline transition does not re-probe`() = runTest {
        monitor.useDispatcherForTest(StandardTestDispatcher(testScheduler))
        stubHealthyServer()

        monitor.startMonitoring("http://test-server:8096")
        try {
            runCurrent()
            coVerify(exactly = 1) { apiClient.getServerInfo(any()) }

            // No network: the probe could only fail into Unreachable, and the
            // header already prioritizes the Offline state over health.
            networkMonitor.networkStatus.value = NetworkStatus.Offline
            runCurrent()
            coVerify(exactly = 1) { apiClient.getServerInfo(any()) }
        } finally {
            monitor.stopMonitoring()
        }
    }

    @Test
    fun `a re-probe holds the previous verdict instead of republishing Checking`() = runTest {
        monitor.useDispatcherForTest(StandardTestDispatcher(testScheduler))
        // Park each probe mid-flight so the intermediate published value is
        // observable across scheduler ticks.
        val probeGates = mutableListOf<CompletableDeferred<Unit>>()
        val healthy = Result.success(
            mockk<ServerInfo> {
                every { id } returns "server-1"
                every { name } returns "Test Server"
                every { address } returns "http://test-server:8096"
            }
        )
        coEvery { apiClient.getServerInfo(any()) } coAnswers {
            val gate = CompletableDeferred<Unit>()
            probeGates += gate
            gate.await()
            healthy
        }

        monitor.startMonitoring("http://test-server:8096")
        try {
            // First probe: no verdict yet, so Checking is the honest value
            // while the probe is parked.
            runCurrent()
            assertEquals(ServerHealth.Checking, monitor.serverHealth.value)

            probeGates.removeAt(0).complete(Unit)
            runCurrent()
            assertTrue(monitor.serverHealth.value is ServerHealth.Healthy)

            // A network transition re-probes: the parked probe must NOT flip
            // the just-published verdict back to Checking — the header has no
            // Checking branch, so it would render a Local frame between two
            // ServerUnreachable frames.
            networkMonitor.networkStatus.value = NetworkStatus.Local
            runCurrent()
            assertTrue(monitor.serverHealth.value is ServerHealth.Healthy)

            probeGates.removeAt(0).complete(Unit)
            runCurrent()
            assertTrue(monitor.serverHealth.value is ServerHealth.Healthy)
        } finally {
            monitor.stopMonitoring()
        }
    }

    @Test
    fun `startMonitoring with null address stops monitoring`() = runTest {
        monitor.startMonitoring("http://test-server:8096")
        monitor.startMonitoring(null)

        assertEquals(ServerHealth.Unknown, monitor.serverHealth.value)
    }
}
