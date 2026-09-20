package com.raulshma.jellyplay.core.network.failover

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the common failover decision tables the probe transports share
 * (the jvmShared OkHttp router; the lane this
 * suite actually runs on is jvmTest). Each
 * test names the transport behavior it pins; the JVM's declared divergences
 * (latency capture, concurrent fan-out, all-down-keeps-current-active) live
 * on the router and are pinned by ServerAddressRouterTest, not here.
 *
 * Cancellation is deliberately untested: passthrough is a TRANSPORT-side
 * contract (the injected prober never gets its exceptions caught by this
 * core) — there is nothing to assert without re-testing a transport.
 */
class FailoverPolicyTest {

    /** Identity-carrying answer (a healthy /System/Info/Public body). */
    private fun identityAnswer(address: String? = null) = ProbeOutcome(
        reachable = true,
        serverId = "server-1",
        serverName = "Test",
        resolvedAddress = address,
    )

    // ── reachable-on-any-status ───────────────────────────────────────────

    @Test
    fun `any http answer is reachable regardless of identity or error payload`() {
        // The transport collapses wire status into `reachable` — a 404 body
        // (no id, no name), an empty 200, and a full identity answer are all
        // "reachable" to the decision core.
        val bare404 = ProbeOutcome(reachable = true)
        assertTrue(bare404.reachable)
        assertTrue(identityAnswer().reachable)
        // …but only the identity answer clears the adoption bar.
        assertTrue(answersWithIdentity(identityAnswer()))
        assertFalse(answersWithIdentity(bare404))
    }

    @Test
    fun `identity-less answers count reachable for selection but are never adopted`() = runTest {
        // A /emby address answering 404 (reachable, no identity) still wins
        // selection — "something answering HTTP is there".
        val chosen = selectPreferredAddress(
            primary = "https://a.example.com",
            alternates = emptyList(),
            probe = { ProbeOutcome(reachable = true) },
        )
        assertEquals("https://a.example.com", chosen)
        assertFalse(answersWithIdentity(ProbeOutcome(reachable = true)))
    }

    // ── strip-retry trigger + resolvedAddress adoption ────────────────────

    @Test
    fun `strip-retry fires on a reachable identity-less legacy address and adopts the stripped form`() = runTest {
        val calls = mutableListOf<String>()
        val result = probeResolvedAddress("https://home.example.com/emby/") { address ->
            calls.add(address)
            when (address) {
                "https://home.example.com/emby" -> ProbeOutcome(reachable = true) // 404 body
                else -> identityAnswer() // bare address answers with identity
            }
        }

        assertEquals(listOf("https://home.example.com/emby", "https://home.example.com"), calls)
        assertTrue(answersWithIdentity(result))
        assertEquals("https://home.example.com", result.resolvedAddress)
    }

    @Test
    fun `strip-retry fires even when the first probe is transport-dead`() = runTest {
        // The trigger is "no identity", not "unreachable" — both transports
        // retry the bare address once before giving up.
        val calls = mutableListOf<String>()
        val result = probeResolvedAddress("https://legacy.example.com/emby") { address ->
            calls.add(address)
            if (address == "https://legacy.example.com") identityAnswer() else ProbeOutcome(reachable = false)
        }

        assertEquals(listOf("https://legacy.example.com/emby", "https://legacy.example.com"), calls)
        assertEquals("https://legacy.example.com", result.resolvedAddress)
        assertTrue(result.reachable)
    }

    @Test
    fun `original outcome stands when the stripped address is dead too`() = runTest {
        val dead = ProbeOutcome(reachable = false, error = RuntimeException("down"))
        var calls = 0
        val result = probeResolvedAddress("https://legacy.example.com/emby") { _ ->
            calls++
            dead
        }

        assertEquals(2, calls)
        assertSame(dead, result)
        assertNull(result.resolvedAddress)
    }

    @Test
    fun `no strip when the probed address answers directly`() = runTest {
        var calls = 0
        val result = probeResolvedAddress("https://home.example.com/emby") { address ->
            calls++
            identityAnswer() // reverse proxy consumes the prefix — identity on the ORIGINAL address
        }

        assertEquals(1, calls)
        assertNull(result.resolvedAddress)
        assertEquals("server-1", result.serverId)
    }

    @Test
    fun `no strip when there is no legacy route prefix`() = runTest {
        var calls = 0
        // Identity-less reachable answer on a bare address: nothing to strip,
        // the outcome stands as-is (the caller decides what a 404 means).
        val result = probeResolvedAddress("https://home.example.com") { _ ->
            calls++
            ProbeOutcome(reachable = true)
        }

        assertEquals(1, calls)
        assertTrue(result.reachable)
        assertNull(result.serverId)
        assertNull(result.resolvedAddress)
    }

    @Test
    fun `blank address short-circuits without probing`() = runTest {
        var calls = 0
        val result = probeResolvedAddress("   /") { _ ->
            calls++
            identityAnswer()
        }

        assertEquals(0, calls)
        assertFalse(result.reachable)
        assertTrue(result.error is IllegalArgumentException)
    }

    // ── address selection order ───────────────────────────────────────────

    @Test
    fun `selection keeps the primary when it is healthy`() = runTest {
        val calls = mutableListOf<String>()
        val chosen = selectPreferredAddress(
            primary = "https://primary.example.com",
            alternates = listOf("https://alt.example.com"),
            probe = { address ->
                calls.add(address)
                ProbeOutcome(reachable = true)
            },
        )

        assertEquals("https://primary.example.com", chosen)
        assertEquals(listOf("https://primary.example.com"), calls) // alternates never probed
    }

    @Test
    fun `selection moves to the first reachable alternate when the primary is down`() = runTest {
        val chosen = selectPreferredAddress(
            primary = "https://primary.example.com",
            alternates = listOf("https://alt1.example.com", "https://alt2.example.com"),
            probe = { address ->
                ProbeOutcome(reachable = address == "https://alt2.example.com")
            },
        )

        assertEquals("https://alt2.example.com", chosen)
    }

    @Test
    fun `selection falls back to the primary when nothing answers`() = runTest {
        val chosen = selectPreferredAddress(
            primary = "https://primary.example.com",
            alternates = listOf("https://alt1.example.com", "https://alt2.example.com"),
            probe = { ProbeOutcome(reachable = false) },
        )

        assertEquals("https://primary.example.com", chosen)
    }

    @Test
    fun `precomputed selection matches the sequential order`() {
        val primary = "https://primary.example.com"
        val alternates = listOf("https://alt1.example.com", "https://alt2.example.com")

        fun resultsOf(vararg reachable: String) =
            (listOf(primary) + alternates).associateWith { ProbeOutcome(reachable = it in reachable) }

        assertEquals(primary, selectPreferredAddress(primary, alternates, resultsOf(primary)))
        assertEquals("https://alt1.example.com", selectPreferredAddress(primary, alternates, resultsOf("https://alt1.example.com", "https://alt2.example.com")))
        assertEquals("https://alt2.example.com", selectPreferredAddress(primary, alternates, resultsOf("https://alt2.example.com")))
        assertEquals(primary, selectPreferredAddress(primary, alternates, resultsOf()))
    }
}
