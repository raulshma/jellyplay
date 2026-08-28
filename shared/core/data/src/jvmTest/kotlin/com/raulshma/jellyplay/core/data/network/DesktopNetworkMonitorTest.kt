package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.model.NetworkStatus
import java.net.NetworkInterface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Wave 17C: the testable half of DesktopNetworkMonitor — the pure
 * up/loopback/address decision ([networkStatusFromInterfaces]) over
 * interface-table snapshots, plus the synchronous-seed contract (a cold
 * `.value` read sees a real probe, not a hardcoded constant). The polling
 * plumbing itself is compile/seed-tested only; the NetworkInterface
 * statics are the host's truth, not ours to fake.
 */
class DesktopNetworkMonitorTest {

    @Test
    fun `empty snapshot is Offline`() {
        assertEquals(NetworkStatus.Offline, networkStatusFromInterfaces(emptyList()))
    }

    @Test
    fun `loopback-only snapshot is Offline`() {
        assertEquals(
            NetworkStatus.Offline,
            networkStatusFromInterfaces(
                listOf(NetworkInterfaceSnapshot(isUp = true, isLoopback = true, hasAddress = true)),
            ),
        )
    }

    @Test
    fun `up non-loopback interface without addresses is Offline`() {
        assertEquals(
            NetworkStatus.Offline,
            networkStatusFromInterfaces(
                listOf(NetworkInterfaceSnapshot(isUp = true, isLoopback = false, hasAddress = false)),
            ),
        )
    }

    @Test
    fun `down non-loopback interface with stale addresses is Offline`() {
        assertEquals(
            NetworkStatus.Offline,
            networkStatusFromInterfaces(
                listOf(NetworkInterfaceSnapshot(isUp = false, isLoopback = false, hasAddress = true)),
            ),
        )
    }

    @Test
    fun `up non-loopback interface with an address is Online`() {
        assertEquals(
            NetworkStatus.Online,
            networkStatusFromInterfaces(
                listOf(NetworkInterfaceSnapshot(isUp = true, isLoopback = false, hasAddress = true)),
            ),
        )
    }

    @Test
    fun `any qualifying interface wins among unqualified ones`() {
        assertEquals(
            NetworkStatus.Online,
            networkStatusFromInterfaces(
                listOf(
                    NetworkInterfaceSnapshot(isUp = true, isLoopback = true, hasAddress = true),
                    NetworkInterfaceSnapshot(isUp = true, isLoopback = false, hasAddress = false),
                    NetworkInterfaceSnapshot(isUp = false, isLoopback = false, hasAddress = true),
                    NetworkInterfaceSnapshot(isUp = true, isLoopback = false, hasAddress = true),
                ),
            ),
        )
    }

    @Test
    fun `desktop never reports Local from interface presence`() {
        // Local is gateway-validated LAN-only knowledge on Android; the
        // desktop probe is binary by design, so no snapshot shape may
        // produce it — the two return paths are pinned by the tests above;
        // this pins the isMetered constant alongside the Local exclusion.
        val allShapes = listOf(
            emptyList(),
            listOf(NetworkInterfaceSnapshot(isUp = true, isLoopback = true, hasAddress = true)),
            listOf(NetworkInterfaceSnapshot(isUp = true, isLoopback = false, hasAddress = true)),
            listOf(NetworkInterfaceSnapshot(isUp = false, isLoopback = false, hasAddress = false)),
        )
        allShapes.forEach { snapshot ->
            val status = networkStatusFromInterfaces(snapshot)
            assertFalse(status == NetworkStatus.Local)
        }
    }

    @Test
    fun `isMetered is constantly false on desktop`() {
        assertFalse(DesktopNetworkMonitor().isMetered.value)
    }

    @Test
    fun `networkStatus seed reflects a real synchronous probe`() {
        // Cold `.value` (no collector → WhileSubscribed upstream idle) must
        // equal the pure decision over the host's own interface table —
        // taken immediately after construction to keep the race window
        // negligible on machines whose interfaces flap (VPN up/down).
        val monitor = DesktopNetworkMonitor()
        val hostSnapshot = NetworkInterface.getNetworkInterfaces()
            ?.toList().orEmpty()
            .map { nif ->
                NetworkInterfaceSnapshot(
                    isUp = nif.isUp,
                    isLoopback = nif.isLoopback,
                    hasAddress = nif.inetAddresses.hasMoreElements(),
                )
            }
        assertEquals(networkStatusFromInterfaces(hostSnapshot), monitor.networkStatus.value)
    }
}
