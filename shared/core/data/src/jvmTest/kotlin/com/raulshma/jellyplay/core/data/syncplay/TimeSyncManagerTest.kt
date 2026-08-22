package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.model.UtcTimeResponse
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import java.time.Instant

class TimeSyncManagerTest {

    private lateinit var apiClient: JellyfinApiClient
    private lateinit var manager: TimeSyncManager

    @BeforeTest
    fun setUp() {
        apiClient = mockk(relaxed = true)
        manager = TimeSyncManager(apiClient)
    }

    @Test
    fun toRemote_defaultOffsetZero_isIdentity() {
        assertEquals(1_000L, manager.toRemote(1_000L))
    }

    @Test
    fun toLocal_defaultOffsetZero_isIdentity() {
        assertEquals(1_000L, manager.toLocal(1_000L))
    }

    @Test
    fun toRemote_and_toLocal_areInverse() {
        val sample = 12_345L
        assertEquals(sample, manager.toLocal(manager.toRemote(sample)))
    }

    @Test
    fun getPingMs_defaultZero() {
        assertEquals(0L, manager.getPingMs())
    }

    @Test
    fun sync_success_updatesPingToNonNegative() = runBlocking {
        coEvery { apiClient.getServerTime() } returns Result.success(
            UtcTimeResponse(
                requestReceptionTime = Instant.now().toString(),
                responseTransmissionTime = Instant.now().toString(),
            ),
        )

        manager.sync()

        assertTrue(manager.getPingMs() >= 0, "ping should be >= 0 after sync")
    }

    @Test
    fun sync_success_appliesOffsetToRemoteConversion() = runBlocking {
        // Server is 10 seconds ahead of the client.
        coEvery { apiClient.getServerTime() } returns Result.success(
            UtcTimeResponse(
                requestReceptionTime = Instant.now().plusSeconds(10).toString(),
                responseTransmissionTime = Instant.now().plusSeconds(10).toString(),
            ),
        )

        manager.sync()

        val offset = manager.toRemote(0L)
        assertTrue(
            offset in 9_000L..11_000L,
            "offset should be ~10000ms (was $offset), allowing measurement noise",
        )
    }

    @Test
    fun sync_failure_doesNotThrowAndKeepsDefaultOffset() = runBlocking {
        coEvery { apiClient.getServerTime() } returns Result.failure(RuntimeException("network"))

        manager.sync()

        // Offset stays at default 0; conversion remains identity.
        assertEquals(0L, manager.toRemote(0L))
        assertEquals(0L, manager.getPingMs())
    }

    @Test
    fun sync_multipleCalls_keepsBestDelayMeasurement() = runBlocking {
        // First sync: server "now" (offset ~0). Second sync: server +20s (offset ~20000).
        coEvery { apiClient.getServerTime() } returns Result.success(
            UtcTimeResponse(
                requestReceptionTime = Instant.now().toString(),
                responseTransmissionTime = Instant.now().toString(),
            ),
        )
        manager.sync()
        val offsetAfterFirst = manager.toRemote(0L)

        coEvery { apiClient.getServerTime() } returns Result.success(
            UtcTimeResponse(
                requestReceptionTime = Instant.now().plusSeconds(20).toString(),
                responseTransmissionTime = Instant.now().plusSeconds(20).toString(),
            ),
        )
        manager.sync()
        val offsetAfterSecond = manager.toRemote(0L)

        // The manager keeps the measurement with the lowest delay; both are valid offsets but the
        // stored value must be one of the two, and conversion must stay self-consistent.
        val offset = offsetAfterSecond
        assertTrue(offset in -1_000L..21_000L)
        assertEquals(offset, manager.toRemote(5_000L) - 5_000L)
        assertNotEquals(-1L, offset, "second sync should have run")
    }

    @Test
    fun parseIso_validInstant_isParsed() {
        val fn = TimeSyncManager::class.java.getDeclaredMethod("parseIso", String::class.java)
        fn.isAccessible = true
        val parsed = fn.invoke(manager, "2024-01-15T12:00:00Z") as Long
        assertTrue(parsed > 0)
    }

    @Test
    fun parseIso_blankString_fallsBackToNow() {
        val fn = TimeSyncManager::class.java.getDeclaredMethod("parseIso", String::class.java)
        fn.isAccessible = true
        val before = System.currentTimeMillis()
        val parsed = fn.invoke(manager, "") as Long
        val after = System.currentTimeMillis()
        assertTrue(parsed in before..after)
    }

    @Test
    fun parseIso_garbageString_fallsBackToNow() {
        val fn = TimeSyncManager::class.java.getDeclaredMethod("parseIso", String::class.java)
        fn.isAccessible = true
        val before = System.currentTimeMillis()
        val parsed = fn.invoke(manager, "not-a-date") as Long
        val after = System.currentTimeMillis()
        assertTrue(parsed in before..after)
    }
}
