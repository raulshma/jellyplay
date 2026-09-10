package com.raulshma.jellyplay.core.data.offline

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the [DesktopOfflineModeManager] state machine — the seam every
 * online/offline sync decision reads (`isOffline` gates the played-state
 * fan-out, the outbox stage-or-send, and the PlaybackSyncWorker itself).
 *
 * Desktop policy under test: offline mode NEVER auto-engages. The manual
 * pref is the only input — a lost network (or a flapping probe) must not
 * flip the mode, because the drain/reconcile machinery would then skip
 * work the user expects. The collector derivation and the synchronous
 * [DesktopOfflineModeManager.checkNetworkAndAutoDetect] re-derivation are
 * covered separately: both must agree.
 *
 * The manager derives on `Dispatchers.IO` from a real scope, so assertions
 * await the derived state with a real-time timeout instead of virtual time;
 * "never transitions" cases settle briefly and then assert the mode held.
 */
class DesktopOfflineModeManagerTest {

    private val sliceFlow = MutableStateFlow(NetworkOfflineSlice())
    private val statusFlow = MutableStateFlow(NetworkStatus.Online)

    private val store: NetworkOfflineStore = mockk {
        every { networkOffline } returns sliceFlow
    }

    private val networkMonitor = object : NetworkMonitor {
        override val networkStatus: MutableStateFlow<NetworkStatus> = statusFlow
        override val isMetered = MutableStateFlow(false)
    }

    private fun manager() = DesktopOfflineModeManager(networkMonitor, store)

    private suspend fun awaitMode(manager: DesktopOfflineModeManager, expected: OfflineMode) {
        withTimeout(5_000) { manager.offlineMode.first { it == expected } }
    }

    // ── collector derivation ────────────────────────────────────────────

    @Test
    fun `enabling the manual pref derives OFFLINE_MANUAL`() = runBlocking {
        val manager = manager()

        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true)
        awaitMode(manager, OfflineMode.OFFLINE_MANUAL)
        assertTrue(manager.isOffline)
    }

    @Test
    fun `clearing the manual pref returns to ONLINE`() = runBlocking {
        val manager = manager()
        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true)
        awaitMode(manager, OfflineMode.OFFLINE_MANUAL)

        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = false)
        awaitMode(manager, OfflineMode.ONLINE)
    }

    @Test
    fun `a network loss never auto-engages offline mode`() = runBlocking {
        val manager = manager()

        // Auto-offline pref ON and the network fully gone: desktop policy
        // still stays online — the manual toggle is the only path to offline.
        sliceFlow.value = NetworkOfflineSlice(autoOfflineEnabled = true)
        statusFlow.value = NetworkStatus.Offline
        delay(250) // let a (wrong) derivation land if one existed

        assertEquals(OfflineMode.ONLINE, manager.offlineMode.value)
    }

    @Test
    fun `network status transitions alone never move the mode`() = runBlocking {
        val manager = manager()

        statusFlow.value = NetworkStatus.Local
        delay(100)
        statusFlow.value = NetworkStatus.Offline
        delay(100)
        statusFlow.value = NetworkStatus.Online
        delay(100)

        assertEquals(OfflineMode.ONLINE, manager.offlineMode.value)
    }

    @Test
    fun `the manual pref wins over a simultaneously lost network`() = runBlocking {
        val manager = manager()
        statusFlow.value = NetworkStatus.Offline

        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true, autoOfflineEnabled = true)
        awaitMode(manager, OfflineMode.OFFLINE_MANUAL)
    }

    // ── toggleManualOffline ─────────────────────────────────────────────

    @Test
    fun `toggleManualOffline writes the inverted snapshot value`() = runBlocking {
        val written = mutableListOf<Boolean>()
        io.mockk.coEvery { store.setManualOffline(any()) } answers { written += firstArg<Boolean>() }

        val manager = manager()
        manager.toggleManualOffline() // snapshot: manual=false
        withTimeout(5_000) { while (written.isEmpty()) delay(25) }
        assertEquals(listOf(true), written)
    }

    @Test
    fun `toggleManualOffline re-enables online from manual offline`() = runBlocking {
        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true)
        val written = mutableListOf<Boolean>()
        io.mockk.coEvery { store.setManualOffline(any()) } answers { written += firstArg<Boolean>() }

        val manager = manager()
        awaitMode(manager, OfflineMode.OFFLINE_MANUAL)
        manager.toggleManualOffline() // snapshot: manual=true
        withTimeout(5_000) { while (written.isEmpty()) delay(25) }
        assertEquals(listOf(false), written)
    }

    // ── goingOnline flag: set-on-arm + clear-on-ONLINE ──────────────────

    @Test
    fun `going-online toggle arms the flag synchronously and the ONLINE derivation clears it`() = runBlocking {
        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true)
        // The write lands: the slice flips, the collector derives ONLINE, and
        // the flag's clear rides the same emission. The write is held behind a
        // gate so the arm assertion cannot race the clear — the whole chain
        // (write → ONLINE derivation → flag clear) must stay downstream of it.
        val writeGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        io.mockk.coEvery { store.setManualOffline(any()) } coAnswers {
            writeGate.await()
            sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = firstArg())
        }
        val manager = manager()
        awaitMode(manager, OfflineMode.OFFLINE_MANUAL)

        manager.toggleManualOffline() // snapshot: manual=true → going online
        assertTrue(manager.goingOnline.value, "the flag must arm on the toggle, before the write lands")

        writeGate.complete(Unit) // release the write; the ONLINE derivation clears the flag
        withTimeout(5_000) { manager.goingOnline.first { !it } }
        assertFalse(manager.goingOnline.value)
    }

    @Test
    fun `going-online toggle with a lost write holds the flag until the watchdog`() = runBlocking {
        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true)
        // Relaxed-free mock: the write is swallowed — the lost-write scenario.
        io.mockk.coEvery { store.setManualOffline(any()) } returns Unit
        val manager = manager()
        awaitMode(manager, OfflineMode.OFFLINE_MANUAL)

        manager.toggleManualOffline()
        assertTrue(manager.goingOnline.value)
        // The watchdog clear itself is pinned on virtual time by
        // GoingOnlineFlagTest; here just verify the flag does NOT clear
        // spuriously without an ONLINE emission (settle briefly).
        delay(250)
        assertTrue(manager.goingOnline.value)
    }

    @Test
    fun `going-offline toggle never arms the flag`() = runBlocking {
        io.mockk.coEvery { store.setManualOffline(any()) } returns Unit
        val manager = manager()

        manager.toggleManualOffline() // snapshot: manual=false → going OFFLINE

        assertFalse(manager.goingOnline.value)
    }

    // ── checkNetworkAndAutoDetect: synchronous re-derivation ───────────

    @Test
    fun `checkNetworkAndAutoDetect re-derives MANUAL synchronously from the snapshot`() {
        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true)
        val manager = manager()

        manager.checkNetworkAndAutoDetect()

        assertEquals(OfflineMode.OFFLINE_MANUAL, manager.offlineMode.value)
    }

    @Test
    fun `checkNetworkAndAutoDetect clears MANUAL when the snapshot says manual off`() {
        val manager = manager()
        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true)
        manager.checkNetworkAndAutoDetect()
        assertEquals(OfflineMode.OFFLINE_MANUAL, manager.offlineMode.value)

        // Flip the snapshot and re-derive synchronously — the network status
        // (Online here) must not matter on desktop.
        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = false)
        manager.checkNetworkAndAutoDetect()

        assertEquals(OfflineMode.ONLINE, manager.offlineMode.value)
    }

    @Test
    fun `checkNetworkAndAutoDetect stays ONLINE for a lost network`() {
        // The desktop re-derivation must mirror the collector: no auto path.
        sliceFlow.value = NetworkOfflineSlice(autoOfflineEnabled = true)
        statusFlow.value = NetworkStatus.Offline
        val manager = manager()

        manager.checkNetworkAndAutoDetect()

        assertEquals(OfflineMode.ONLINE, manager.offlineMode.value)
    }
}
