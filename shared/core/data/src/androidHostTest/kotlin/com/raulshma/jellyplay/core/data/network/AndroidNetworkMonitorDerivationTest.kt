package com.raulshma.jellyplay.core.data.network

import com.raulshma.jellyplay.core.model.NetworkStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure capability-facts derivation tests for the two internal functions
 * [networkStatusFromCapabilityFacts] / [isMeteredFromCapabilityFacts] — the
 * decision ladders [AndroidNetworkMonitor] feeds from NetworkCapabilities.
 * No Robolectric, no ConnectivityManager: what is pinned here is the exact
 * mapping the monitor emits on onCapabilitiesChanged — validated ⇒ Online,
 * internet-but-unvalidated ⇒ Local (the Android-only rung the desktop
 * monitor never reports), no-internet ⇒ Offline; metered unless
 * NOT_METERED is present. The callback registration wiring around them is
 * exercised on-device.
 */
class AndroidNetworkMonitorDerivationTest {

    // ── networkStatusFromCapabilityFacts ─────────────────────────────────

    @Test
    fun `validated network is online regardless of the internet fact`() {
        assertEquals(
            NetworkStatus.Online,
            networkStatusFromCapabilityFacts(hasInternet = true, validated = true),
        )
        assertEquals(
            NetworkStatus.Online,
            networkStatusFromCapabilityFacts(hasInternet = false, validated = true),
        )
    }

    @Test
    fun `internet-capable but unvalidated network is local`() {
        assertEquals(
            NetworkStatus.Local,
            networkStatusFromCapabilityFacts(hasInternet = true, validated = false),
        )
    }

    @Test
    fun `no internet capability is offline`() {
        assertEquals(
            NetworkStatus.Offline,
            networkStatusFromCapabilityFacts(hasInternet = false, validated = false),
        )
    }

    @Test
    fun `derivation covers the whole status enum exactly once`() {
        val statuses = listOf(
            networkStatusFromCapabilityFacts(hasInternet = true, validated = true),
            networkStatusFromCapabilityFacts(hasInternet = true, validated = false),
            networkStatusFromCapabilityFacts(hasInternet = false, validated = false),
        ).toSet()
        // The validated+no-internet cell is unreachable from a real
        // capability probe (validation implies connectivity), so the three
        // reachable cells map onto all three statuses.
        assertEquals(NetworkStatus.entries.toSet(), statuses)
    }

    // ── isMeteredFromCapabilityFacts ─────────────────────────────────────

    @Test
    fun `network without NOT_METERED is metered`() {
        assertTrue(isMeteredFromCapabilityFacts(hasNotMetered = false))
    }

    @Test
    fun `network carrying NOT_METERED is not metered`() {
        assertFalse(isMeteredFromCapabilityFacts(hasNotMetered = true))
    }
}
