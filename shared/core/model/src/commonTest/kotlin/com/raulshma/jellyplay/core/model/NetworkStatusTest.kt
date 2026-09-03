package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the invariants of [NetworkStatus] — the three-way connectivity state
 * the offline-mode and retry surfaces branch on:
 *
 *  - Exactly one of [NetworkStatus.isOnline]/[isLocal]/[isOffline] is true for
 *    every entry (the states are mutually exclusive and exhaustive).
 *  - [NetworkStatus.hasNetwork] is true for everything except
 *    [NetworkStatus.Offline] — the "may we attempt a request?" predicate.
 */
class NetworkStatusTest {

    @Test
    fun `Online is online and has network`() {
        assertTrue(NetworkStatus.Online.isOnline)
        assertFalse(NetworkStatus.Online.isLocal)
        assertFalse(NetworkStatus.Online.isOffline)
        assertTrue(NetworkStatus.Online.hasNetwork)
    }

    @Test
    fun `Local is local, not online, and still has network`() {
        assertTrue(NetworkStatus.Local.isLocal)
        assertFalse(NetworkStatus.Local.isOnline)
        assertFalse(NetworkStatus.Local.isOffline)
        assertTrue(NetworkStatus.Local.hasNetwork)
    }

    @Test
    fun `Offline has no network at all`() {
        assertTrue(NetworkStatus.Offline.isOffline)
        assertFalse(NetworkStatus.Offline.isOnline)
        assertFalse(NetworkStatus.Offline.isLocal)
        assertFalse(NetworkStatus.Offline.hasNetwork)
    }

    @Test
    fun `predicates partition every entry`() {
        for (status in NetworkStatus.entries) {
            assertEquals(1, listOf(status.isOnline, status.isLocal, status.isOffline).count { it }, status.name)
            assertEquals(status != NetworkStatus.Offline, status.hasNetwork, status.name)
        }
    }
}
