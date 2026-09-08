package com.raulshma.jellyplay.core.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Pins the [AndroidOfflineModeManager] derivation — the seam every
 * online/offline sync decision reads (`isOffline` gates the played-state
 * fan-out, the outbox stage-or-send, and the PlaybackSyncWorker itself).
 *
 * Two halves:
 *  1. the collector derivation — manual pref wins; a lost network with the
 *     auto pref engages OFFLINE_AUTO (and only then); manual-off while still
 *     offline falls back to the auto derivation; network restoration exits
 *     OFFLINE_AUTO; a Local (unvalidated Wi-Fi) network is NOT offline for
 *     the auto rule;
 *  2. [AndroidOfflineModeManager.checkNetworkAndAutoDetect] — the foreground
 *     re-derivation over the ConnectivityManager probe: no network, or an
 *     INTERNET-but-unvalidated network (captive portal), is offline; manual
 *     short-circuits before the probe; MANUAL is sticky against a reachable
 *     network (only the pref clears it, via the collector).
 *
 * The manager derives on Dispatchers.IO from a real scope, so assertions
 * await the derived state with a real-time timeout instead of virtual time;
 * the checkNetworkAndAutoDetect paths mutate state synchronously.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidOfflineModeManagerTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager

    private val sliceFlow = MutableStateFlow(NetworkOfflineSlice())
    private val statusFlow = MutableStateFlow(NetworkStatus.Online)

    private val store: NetworkOfflineStore = mockk {
        every { networkOffline } returns sliceFlow
    }

    private val networkMonitor = object : NetworkMonitor {
        override val networkStatus: MutableStateFlow<NetworkStatus> = statusFlow
        override val isMetered = MutableStateFlow(false)
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private fun manager() = AndroidOfflineModeManager(context, networkMonitor, store)

    private suspend fun awaitMode(manager: AndroidOfflineModeManager, expected: OfflineMode) {
        withTimeout(5_000) { manager.offlineMode.first { it == expected } }
    }

    private fun setReachableNetwork(validated: Boolean) {
        val shadow = Shadows.shadowOf(connectivityManager)
        // getActiveNetwork() in the shadow is non-null only when BOTH
        // defaultNetworkActive is set and activeNetworkInfo is non-null —
        // capabilities must land on the network instance the manager probe
        // (getActiveNetwork) actually returns.
        shadow.setDefaultNetworkActive(true)
        shadow.setActiveNetworkInfo(
            org.robolectric.shadows.ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                true,
                NetworkInfo.State.CONNECTED,
            )
        )
        val network = connectivityManager.activeNetwork
            ?: org.robolectric.shadows.ShadowNetwork.newInstance(1)
        shadow.setNetworkCapabilities(network, networkCaps(validated))
    }

    /**
     * SDK 37's mockable android.jar hides NetworkCapabilities' mutators from
     * unit-test compilation; the methods exist on Robolectric's android-all
     * runtime, so the capabilities are wired reflectively.
     */
    private fun networkCaps(validated: Boolean): NetworkCapabilities {
        val caps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        val addCapability = NetworkCapabilities::class.java
            .getMethod("addCapability", Int::class.javaPrimitiveType)
        addCapability(caps, NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) addCapability(caps, NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return caps
    }

    private fun setNoActiveNetwork() {
        val shadow = Shadows.shadowOf(connectivityManager)
        shadow.setDefaultNetworkActive(false)
        shadow.setActiveNetworkInfo(null)
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
    fun `a network loss with the auto pref engages OFFLINE_AUTO`() = runBlocking {
        val manager = manager()
        sliceFlow.value = NetworkOfflineSlice(autoOfflineEnabled = true)

        statusFlow.value = NetworkStatus.Offline
        awaitMode(manager, OfflineMode.OFFLINE_AUTO)
        assertTrue(manager.isOffline)
    }

    @Test
    fun `a network loss with the auto pref off stays ONLINE`() = runBlocking {
        val manager = manager()
        sliceFlow.value = NetworkOfflineSlice(autoOfflineEnabled = false)

        statusFlow.value = NetworkStatus.Offline
        delay(250) // let a (wrong) derivation land if one existed

        assertEquals(OfflineMode.ONLINE, manager.offlineMode.value)
    }

    @Test
    fun `network restoration exits OFFLINE_AUTO`() = runBlocking {
        val manager = manager()
        sliceFlow.value = NetworkOfflineSlice(autoOfflineEnabled = true)
        statusFlow.value = NetworkStatus.Offline
        awaitMode(manager, OfflineMode.OFFLINE_AUTO)

        statusFlow.value = NetworkStatus.Online
        awaitMode(manager, OfflineMode.ONLINE)
    }

    @Test
    fun `the manual pref wins over the auto derivation`() = runBlocking {
        val manager = manager()
        sliceFlow.value = NetworkOfflineSlice(autoOfflineEnabled = true)
        statusFlow.value = NetworkStatus.Offline
        awaitMode(manager, OfflineMode.OFFLINE_AUTO)

        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true, autoOfflineEnabled = true)
        awaitMode(manager, OfflineMode.OFFLINE_MANUAL)
    }

    @Test
    fun `disabling manual while still offline falls back to OFFLINE_AUTO`() = runBlocking {
        // The else-branch re-derives the auto state after clearing MANUAL —
        // it must not stop at ONLINE while the network is still gone.
        val manager = manager()
        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true, autoOfflineEnabled = true)
        statusFlow.value = NetworkStatus.Offline
        awaitMode(manager, OfflineMode.OFFLINE_MANUAL)

        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = false, autoOfflineEnabled = true)
        awaitMode(manager, OfflineMode.OFFLINE_AUTO)
    }

    @Test
    fun `a Local network is not offline for the auto rule`() = runBlocking {
        // Unvalidated Wi-Fi: the LAN server may still be reachable, so the
        // manager must not engage the offline library over it.
        val manager = manager()
        sliceFlow.value = NetworkOfflineSlice(autoOfflineEnabled = true)

        statusFlow.value = NetworkStatus.Local
        delay(250)

        assertEquals(OfflineMode.ONLINE, manager.offlineMode.value)
    }

    @Test
    fun `toggleManualOffline writes the inverted snapshot value`() = runBlocking {
        val written = mutableListOf<Boolean>()
        coEvery { store.setManualOffline(any()) } answers { written += firstArg<Boolean>() }

        val manager = manager()
        manager.toggleManualOffline() // snapshot: manual=false
        withTimeout(5_000) { while (written.isEmpty()) delay(25) }
        assertEquals(listOf(true), written)
    }

    // ── checkNetworkAndAutoDetect: the foreground re-derivation ────────

    @Test
    fun `no active network with the auto pref engages OFFLINE_AUTO`() {
        setNoActiveNetwork()
        sliceFlow.value = NetworkOfflineSlice(autoOfflineEnabled = true)
        val manager = manager()

        manager.checkNetworkAndAutoDetect()

        assertEquals(OfflineMode.OFFLINE_AUTO, manager.offlineMode.value)
    }

    @Test
    fun `no active network with the auto pref off stays ONLINE`() {
        setNoActiveNetwork()
        sliceFlow.value = NetworkOfflineSlice(autoOfflineEnabled = false)
        val manager = manager()

        manager.checkNetworkAndAutoDetect()

        assertEquals(OfflineMode.ONLINE, manager.offlineMode.value)
    }

    @Test
    fun `the manual pref short-circuits the connectivity probe`() {
        // Manual on + no network at all: MANUAL wins without the probe.
        setNoActiveNetwork()
        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true)
        val manager = manager()

        manager.checkNetworkAndAutoDetect()

        assertEquals(OfflineMode.OFFLINE_MANUAL, manager.offlineMode.value)
    }

    @Test
    fun `an internet-but-unvalidated network is treated as offline (captive portal)`() {
        // INTERNET capability without VALIDATION: the app cannot reach the
        // server through a captive portal — the auto rule must engage.
        setReachableNetwork(validated = false)
        sliceFlow.value = NetworkOfflineSlice(autoOfflineEnabled = true)
        val manager = manager()

        manager.checkNetworkAndAutoDetect()

        assertEquals(OfflineMode.OFFLINE_AUTO, manager.offlineMode.value)
    }

    @Test
    fun `a validated internet network exits OFFLINE_AUTO`() {
        setNoActiveNetwork()
        sliceFlow.value = NetworkOfflineSlice(autoOfflineEnabled = true)
        val manager = manager()
        manager.checkNetworkAndAutoDetect()
        assertEquals(OfflineMode.OFFLINE_AUTO, manager.offlineMode.value)

        setReachableNetwork(validated = true)
        manager.checkNetworkAndAutoDetect()

        assertEquals(OfflineMode.ONLINE, manager.offlineMode.value)
    }

    @Test
    fun `a reachable network keeps MANUAL sticky`() {
        // Only the manual pref (via the collector) clears MANUAL — a restored
        // network must not silently flip the user's explicit offline choice.
        setReachableNetwork(validated = true)
        sliceFlow.value = NetworkOfflineSlice(manualOfflineEnabled = true)
        val manager = manager()
        manager.checkNetworkAndAutoDetect()
        assertEquals(OfflineMode.OFFLINE_MANUAL, manager.offlineMode.value)

        manager.checkNetworkAndAutoDetect()

        assertEquals(OfflineMode.OFFLINE_MANUAL, manager.offlineMode.value)
    }
}
