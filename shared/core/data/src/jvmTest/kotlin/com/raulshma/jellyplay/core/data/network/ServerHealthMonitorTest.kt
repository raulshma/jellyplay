package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.raulshma.jellyplay.core.data.util.TimeSource
import java.time.LocalDate
import java.time.ZoneId

class ServerHealthMonitorTest {

    private lateinit var apiClient: JellyfinApiClient
    private lateinit var monitor: ServerHealthMonitor

    @BeforeTest
    fun setUp() {
        apiClient = mockk(relaxed = true)
        monitor = ServerHealthMonitor(apiClient, FakeTimeSource())
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

            coVerify { apiClient.getServerInfo("http://test-server:8096") }
        } finally {
            // Cancel the infinite monitor loop before runTest tears down, or its
            // pending delay would keep the shared scheduler busy forever.
            monitor.stopMonitoring()
        }
    }

    @Test
    fun `startMonitoring with null address stops monitoring`() = runTest {
        monitor.startMonitoring("http://test-server:8096")
        monitor.startMonitoring(null)

        assertEquals(ServerHealth.Unknown, monitor.serverHealth.value)
    }

    /**
     * Controllable [TimeSource] for the latency measurement — same shape as
     * the fake in LyricsRepositoryImplTest (core:data deliberately hosts no
     * shared test fakes; see TimeSource's KDoc).
     */
    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
