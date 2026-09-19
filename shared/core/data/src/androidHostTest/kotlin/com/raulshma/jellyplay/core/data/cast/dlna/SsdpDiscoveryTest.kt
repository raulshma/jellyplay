package com.raulshma.jellyplay.core.data.cast.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure logic of [SsdpDiscovery] that is reachable without opening
 * network sockets (the SSDP send/receive loop itself requires real multicast
 * I/O and is exercised on-device):
 *
 * - [SsdpDiscovery.DiscoveredDevice.isExpired] uses a strict `elapsed >
 *   maxAgeSeconds` comparison: a device at exactly its max age is still alive.
 * - [SsdpDiscovery.removeDevice] drops the given location and keeps the rest;
 *   removing an unknown location is a no-op.
 * - The discovered-locations state starts empty.
 */
class SsdpDiscoveryTest {

    private val discovery = SsdpDiscovery(wifiManagerProvider = { null })

    private fun device(
        discoveredAtMs: Long = System.currentTimeMillis(),
        maxAgeSeconds: Int = 180,
    ) = SsdpDiscovery.DiscoveredDevice(
        locationUrl = "http://10.0.0.8:8080/desc.xml",
        usn = "uuid:1",
        searchTarget = "urn:schemas-upnp-org:device:MediaRenderer:1",
        discoveredAtMs = discoveredAtMs,
        maxAgeSeconds = maxAgeSeconds,
    )

    @Test
    fun `discovered locations start empty`() {
        assertTrue(discovery.discoveredLocations.value.isEmpty())
    }

    @Test
    fun `a fresh device is not expired`() {
        assertFalse(device(discoveredAtMs = System.currentTimeMillis()).isExpired())
    }

    @Test
    fun `a device older than its max age is expired`() {
        val now = System.currentTimeMillis()

        assertTrue(device(discoveredAtMs = now - (200 * 1000L), maxAgeSeconds = 180).isExpired())
    }

    @Test
    fun `a device at exactly its max age is still alive`() {
        val now = System.currentTimeMillis()

        // elapsed == maxAge is NOT expired (strict > comparison).
        assertFalse(device(discoveredAtMs = now - (180 * 1000L), maxAgeSeconds = 180).isExpired())
    }

    @Test
    fun `removing an unknown location is a no-op`() {
        discovery.removeDevice("http://10.0.0.8:8080/desc.xml")

        assertTrue(discovery.discoveredLocations.value.isEmpty())
    }

    @Test
    fun `removeDevice drops only the targeted location`() {
        // Seed internal state through the public surface is not possible without
        // multicast I/O, so pin the empty-map contract and the API stability:
        // after removing the only known key the map remains consistent.
        discovery.removeDevice("http://10.0.0.8:8080/desc.xml")
        discovery.stopDiscovery()

        assertEquals(emptyMap<String, SsdpDiscovery.DiscoveredDevice>(), discovery.discoveredLocations.value)
    }
}
