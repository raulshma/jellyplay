package com.raulshma.jellyplay.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the module's one WebSocket-reconnect backoff law
 * ([WebSocketBackoffPolicy]): 1s doubling through the 16s step, 30s ceiling,
 * `null` past the attempt budget, and jitter that stays additive-within-bounds.
 * The deterministic schedule (jitter source `{ 0 }`, the default) is the
 * ActivityLogRealtimeChannel shape; [WebSocketBackoffPolicy.uniformJitter]
 * is the JellyfinWebSocketClient shape — `(0..1000L).random()` verbatim.
 */
class WebSocketBackoffPolicyTest {

    @Test
    fun `attempt 1 delays 1s and doubles through attempt 5`() {
        val policy = WebSocketBackoffPolicy()
        assertEquals(1_000L, policy.delayMs(1))
        assertEquals(2_000L, policy.delayMs(2))
        assertEquals(4_000L, policy.delayMs(3))
        assertEquals(8_000L, policy.delayMs(4))
        assertEquals(16_000L, policy.delayMs(5))
    }

    @Test
    fun `exponent saturates at the 16s step`() {
        // The shift cap (2^4) saturates the exponential term at 16s: beyond
        // attempt 5 every wait equals 16s. At the production constants
        // (base 1000, ceiling 30s) the ceiling never binds — it guards
        // callers with a larger base (tested next).
        val policy = WebSocketBackoffPolicy(maxAttempts = 8)
        assertEquals(16_000L, policy.delayMs(5))
        assertEquals(16_000L, policy.delayMs(6))
        assertEquals(16_000L, policy.delayMs(7))
        assertEquals(16_000L, policy.delayMs(8))
    }

    @Test
    fun `delays cap at maxDelayMs once the exponential term crosses it`() {
        val policy = WebSocketBackoffPolicy(maxAttempts = 8, maxDelayMs = 5_000L)
        assertEquals(1_000L, policy.delayMs(1))
        assertEquals(2_000L, policy.delayMs(2))
        assertEquals(4_000L, policy.delayMs(3))
        assertEquals(5_000L, policy.delayMs(4))
        assertEquals(5_000L, policy.delayMs(8))
        // Jitter is applied before the ceiling — the total is capped.
        val jittered = WebSocketBackoffPolicy(maxAttempts = 1, maxDelayMs = 1_200L, jitterMs = { 500L })
        assertEquals(1_200L, jittered.delayMs(1))
    }

    @Test
    fun `returns null past max attempts and below attempt 1`() {
        val policy = WebSocketBackoffPolicy()
        assertEquals(5, policy.maxAttempts)
        assertEquals(16_000L, policy.delayMs(5))
        assertNull(policy.delayMs(6))
        assertNull(policy.delayMs(50))
        assertNull(policy.delayMs(0))
    }

    @Test
    fun `uniform jitter stays within 0 to 1000ms of the exponential term and never crosses the ceiling`() {
        // Wide budget so every swept attempt (including across the 2^4
        // saturation plateau) yields a delay.
        val policy = WebSocketBackoffPolicy(maxAttempts = 500, jitterMs = WebSocketBackoffPolicy.uniformJitter())
        for (attempt in 1..500) {
            val exponential = 1_000L * (1L shl (attempt - 1).coerceAtMost(4))
            val delay = policy.delayMs(attempt)!!
            assertTrue(delay >= exponential, "attempt $attempt: $delay below base $exponential")
            assertTrue(
                delay <= (exponential + 1_000L).coerceAtMost(30_000L),
                "attempt $attempt: $delay above jittered cap ${(exponential + 1_000L).coerceAtMost(30_000L)}",
            )
        }
    }

    @Test
    fun `fake jitter source is applied verbatim before the cap`() {
        val policy = WebSocketBackoffPolicy(jitterMs = { 7L })
        assertEquals(1_007L, policy.delayMs(1))
        assertEquals(2_007L, policy.delayMs(2))
        assertEquals(16_007L, policy.delayMs(5))
    }

    @Test
    fun `default policy is deterministic — no jitter`() {
        val policy = WebSocketBackoffPolicy()
        repeat(100) {
            assertEquals(1_000L, policy.delayMs(1))
        }
    }
}
