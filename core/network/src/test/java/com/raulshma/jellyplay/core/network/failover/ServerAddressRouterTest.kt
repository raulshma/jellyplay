package com.raulshma.jellyplay.core.network.failover

import com.raulshma.jellyplay.core.model.ServerInfo
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerAddressRouterTest {

    private lateinit var router: ServerAddressRouter

    /** Addresses reported reachable by the injected prober; everything else is down. */
    private val reachable = mutableSetOf<String>()
    private val probeCalls = mutableListOf<String>()

    private val primary = "https://home.example.com"
    private val alternate = "https://outside.example.com"

    private val server = ServerInfo(
        id = "server-1",
        name = "Test Server",
        address = primary,
        alternateAddresses = listOf(alternate),
    )

    @Before
    fun setup() {
        reachable.clear()
        probeCalls.clear()
        router = ServerAddressRouter()
        router.prober = { address ->
            probeCalls.add(address)
            AddressProbeResult(reachable = address in reachable, serverId = "server-1", serverName = "Test")
        }
    }

    @Test
    fun `configure selects the primary as active`() {
        router.configure(server)

        assertEquals(primary, router.activeAddress.value)
        assertTrue(router.hasAlternates)
        assertEquals(listOf(primary, alternate), router.preferenceOrder)
    }

    @Test
    fun `configure without alternates has no failover`() {
        router.configure(server.copy(alternateAddresses = emptyList()))

        assertFalse(router.hasAlternates)
        assertEquals(listOf(primary), router.preferenceOrder)
    }

    @Test
    fun `clear resets routing`() {
        router.configure(server)
        router.clear()

        assertNull(router.activeAddress.value)
        assertFalse(router.hasAlternates)
    }

    @Test
    fun `reselect moves to alternate when primary is unreachable`() = runTest {
        router.configure(server)
        reachable += alternate

        val changed = router.reselectActiveEndpoint()

        assertTrue(changed)
        assertEquals(alternate, router.activeAddress.value)
        // Probed in preference order and stopped at the first success.
        assertEquals(listOf(primary, alternate), probeCalls)
    }

    @Test
    fun `reselect keeps primary when it is reachable`() = runTest {
        router.configure(server)
        reachable += primary
        reachable += alternate

        val changed = router.reselectActiveEndpoint()

        assertFalse(changed)
        assertEquals(primary, router.activeAddress.value)
    }

    @Test
    fun `reselect switches back to primary once it answers again`() = runTest {
        router.configure(server)
        reachable += alternate
        router.reselectActiveEndpoint()
        assertEquals(alternate, router.activeAddress.value)

        // Arriving home: the primary answers again.
        reachable += primary
        val changed = router.reselectActiveEndpoint()

        assertTrue(changed)
        assertEquals(primary, router.activeAddress.value)
    }

    @Test
    fun `reselect keeps the current address when everything is unreachable`() = runTest {
        router.configure(server)
        reachable += alternate
        router.reselectActiveEndpoint()

        // Going fully offline.
        reachable.clear()
        val changed = router.reselectActiveEndpoint()

        assertFalse(changed)
        assertEquals(alternate, router.activeAddress.value)
    }

    @Test
    fun `reselect throttles repeated calls`() = runTest {
        router.configure(server)
        reachable += primary

        router.reselectActiveEndpoint(minIntervalMs = 60_000)
        router.reselectActiveEndpoint(minIntervalMs = 60_000)

        // The second call fell inside the throttle window and never probed.
        assertEquals(1, probeCalls.size)
    }

    @Test
    fun `reselect is a no-op when no server is configured`() = runTest {
        assertFalse(router.reselectActiveEndpoint())
        assertNull(router.activeAddress.value)
    }

    @Test
    fun `configure with changed primary resets active to the new primary`() = runTest {
        router.configure(server)
        reachable += alternate
        router.reselectActiveEndpoint()
        assertEquals(alternate, router.activeAddress.value)

        // Manual primary/alternate swap in Server Management.
        router.configure(
            server.copy(
                address = alternate,
                alternateAddresses = listOf(primary),
            )
        )

        assertEquals(alternate, router.activeAddress.value)
        assertEquals(listOf(alternate, primary), router.preferenceOrder)
    }

    @Test
    fun `configure preserves active across same-endpoint republish`() = runTest {
        router.configure(server)
        reachable += alternate
        router.reselectActiveEndpoint()

        // Engine re-publishes the same server with auth state attached.
        router.configure(server.copy(userId = "user-1", isConnected = true))

        assertEquals(alternate, router.activeAddress.value)
    }

    @Test
    fun `markActive ignores unknown addresses`() {
        router.configure(server)

        router.markActive("https://evil.example.com")

        assertEquals(primary, router.activeAddress.value)
    }

    @Test
    fun `rewriteToActive retargets a primary URL onto the active alternate`() {
        val portedPrimary = "https://home.example.com:8920"
        router.configure(server.copy(address = portedPrimary))
        router.markActive(alternate)

        val url = "$portedPrimary/Sessions?api_key=x".toHttpUrl()
        val rewritten = router.rewriteToActive(url)!!

        assertEquals("outside.example.com", rewritten.host)
        assertEquals(443, rewritten.port)
        assertEquals("/Sessions", rewritten.encodedPath)
        assertEquals("x", rewritten.queryParameter("api_key"))
    }

    @Test
    fun `rewriteToActive returns null for foreign hosts`() {
        router.configure(server)

        assertNull(router.rewriteToActive("https://github.com/releases".toHttpUrl()))
    }

    @Test
    fun `rewriteToActive returns null when already targeting active`() {
        router.configure(server)

        assertNull(router.rewriteToActive("$primary/System/Info".toHttpUrl()))
    }

    @Test
    fun `failoverCandidates puts active first then primary then alternates`() {
        router.configure(server)
        router.markActive(alternate)

        val url = "$primary/Items".toHttpUrl()
        val candidates = router.failoverCandidates(url)

        assertEquals(2, candidates.size)
        assertEquals(alternate, router.addressString(candidates[0]))
        assertEquals(primary, router.addressString(candidates[1]))
        // Path and query survive the rewrite.
        assertEquals("/Items", candidates[0].encodedPath)
    }

    @Test
    fun `failoverCandidates is empty for foreign hosts`() {
        router.configure(server)

        assertTrue(router.failoverCandidates("https://api.github.com/x".toHttpUrl()).isEmpty())
    }

    @Test
    fun `addressString keeps explicit ports and drops default ports`() {
        assertEquals(
            "http://192.168.1.10:8096",
            router.addressString("http://192.168.1.10:8096/".toHttpUrl()),
        )
        assertEquals(
            "https://jelly.example.com",
            router.addressString("https://jelly.example.com:443/".toHttpUrl()),
        )
    }

    @Test
    fun `probe normalizes the address before probing`() = runTest {
        router.prober = { address ->
            assertEquals("https://outside.example.com", address)
            AddressProbeResult(reachable = true)
        }

        val result = router.probe("https://outside.example.com/")

        assertTrue(result.reachable)
    }
}
