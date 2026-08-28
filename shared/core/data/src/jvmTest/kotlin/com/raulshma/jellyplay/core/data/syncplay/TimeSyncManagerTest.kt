package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.model.UtcTimeResponse
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
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
        // Wave 20D deflake. The historical flake (fails under full-suite
        // parallel load, passes standalone) had two wall-clock dependencies:
        // 1. The offsets were asserted against FIXED bounds (-1000..21000).
        //    offset = serverTime(mock-captured) - System.currentTimeMillis()
        //    (sampled inside sync()), so any >1s stall between stub setup and
        //    sync()'s internal clock reads — routine under parallel JVM load —
        //    pushed the first offset below -1000 and failed the range check.
        // 2. assertNotEquals(-1L, offset). Which measurement wins the
        //    min-delay selection is a coin flip between two sub-millisecond
        //    mocked calls; when the first wins, its offset is -(round-trip
        //    span) — exactly -1 whenever that span is ~2ms.
        // Fix, without touching production: (a) make the second sync's mocked
        // call deliberately SLOW (delay(500)) so the first measurement is the
        // deterministic min-delay winner — the property under test — and (b)
        // assert against brackets derived from the same wall clock sync()
        // reads, so the bounds hold under any load instead of a fixed
        // tolerance. Residual wall-clock seam (reviewer, accepted): a
        // >500ms stall STRICTLY inside the first mocked dispatch (GC pause)
        // could still flip the winner; margins below that are safe.
        val beforeFirst = System.currentTimeMillis()
        coEvery { apiClient.getServerTime() } returns Result.success(
            UtcTimeResponse(
                requestReceptionTime = Instant.ofEpochMilli(beforeFirst).toString(),
                responseTransmissionTime = Instant.ofEpochMilli(beforeFirst).toString(),
            ),
        )
        manager.sync()
        val afterFirst = System.currentTimeMillis()

        val beforeSecond = System.currentTimeMillis()
        coEvery { apiClient.getServerTime() } coAnswers {
            delay(500)
            Result.success(
                UtcTimeResponse(
                    requestReceptionTime = Instant.ofEpochMilli(beforeSecond + 20_000).toString(),
                    responseTransmissionTime = Instant.ofEpochMilli(beforeSecond + 20_000).toString(),
                ),
            )
        }
        manager.sync()
        val afterSecond = System.currentTimeMillis()

        // Bracket math (requestSent/responseReceived are sampled by sync()
        // inside the surrounding test brackets, and the mocked server times
        // are fixed epoch values):
        //   first:  offset = ((beforeFirst - requestSent) +
        //                     (beforeFirst - responseReceived)) / 2
        //           -> [beforeFirst - afterFirst, 0]
        //   second: same shape shifted +20s
        //           -> [beforeSecond - afterSecond + 20_000, 20_000]
        // The forced-slow second answer makes the first measurement the
        // min-delay winner (mock dispatch overhead is far below the 500ms
        // margin), so the stored offset must sit in the FIRST bracket — the
        // newer, slower +20s measurement must NOT displace it.
        val firstRange = (beforeFirst - afterFirst)..0L
        val secondRange = (beforeSecond - afterSecond + 20_000)..20_000L
        val offset = manager.toRemote(0L)
        assertTrue(
            offset in firstRange,
            "min-delay measurement (first sync) must be kept — offset $offset outside $firstRange " +
                "(second bracket was $secondRange)",
        )
        // Conversion stays self-consistent with whichever offset is stored.
        assertEquals(offset, manager.toRemote(5_000L) - 5_000L)
        // The kept measurement's ping is its delay / 2 — never negative.
        assertTrue(manager.getPingMs() >= 0)
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
