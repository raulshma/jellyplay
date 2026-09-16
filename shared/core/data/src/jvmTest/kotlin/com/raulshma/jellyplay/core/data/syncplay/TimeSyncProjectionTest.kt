package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.model.UtcTimeResponse
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the [TimeSyncManager]-owned clock projection surface — the single home
 * the SyncPlay cores and the player bridge delegate to:
 *
 *  - the projection math itself (pure [TimeSyncManager.projectCurrentTicks]
 *    core, plus the offset-crossing property of the instance member against a
 *    controlled server offset, faked the same way [TimeSyncManagerTest] fakes
 *    the api client with bracket bounds instead of fixed tolerances);
 *  - the ticks↔ms conversions — exact scaling, toward-zero truncation, and a
 *    lossless round trip on whole milliseconds;
 *  - the shared ISO parse ladder ([TimeSyncManager.parseIsoTimestamp]) in both
 *    accepted shapes, plus the null → caller-side fallback split
 *    ([SyncPlayEventHandler.parseTimestamp] still degrades to 0, never to
 *    "now").
 */
class TimeSyncProjectionTest {

    private lateinit var apiClient: JellyfinApiClient
    private lateinit var manager: TimeSyncManager
    private val handler = SyncPlayEventHandler()

    @BeforeTest
    fun setUp() {
        apiClient = mockk()
        manager = TimeSyncManager(apiClient)
    }

    // ── projection math ─────────────────────────────────────────────────

    @Test
    fun `projectCurrentTicks advances position by elapsed ms at 10k ticks per ms`() {
        assertEquals(123L + 1_500L * 10_000L, TimeSyncManager.projectCurrentTicks(123L, elapsedMs = 1_500L))
    }

    @Test
    fun `projectCurrentTicks runs the clock backwards for negative elapsed`() {
        assertEquals(5_000_000L - 200L * 10_000L, TimeSyncManager.projectCurrentTicks(5_000_000L, elapsedMs = -200L))
    }

    @Test
    fun `estimateCurrentTicks crosses the synced offset for a locally stamped whenMs`() = runBlocking {
        // Server clock runs +60s ahead: fixed epoch stamps captured around the
        // client brackets sync() samples (TimeSyncManagerTest's bracket style —
        // the offset is asserted against measured bounds, never constants).
        val before = System.currentTimeMillis()
        coEvery { apiClient.getServerTime() } returns Result.success(
            UtcTimeResponse(
                requestReceptionTime = Instant.ofEpochMilli(before + 60_000).toString(),
                responseTransmissionTime = Instant.ofEpochMilli(before + 60_000).toString(),
            ),
        )
        manager.sync()
        val afterSync = System.currentTimeMillis()

        // whenMs is a LOCAL stamp: the projection must cross into remote time
        // through the synced offset (~+60s) — the reason SyncPlay projects
        // against remoteNow rather than the local clock.
        val projected = manager.estimateCurrentTicks(positionTicks = 0L, whenMs = before)
        val afterCall = System.currentTimeMillis()

        // offset ∈ [60_000 − (afterSync − before), 60_000] and remoteNow is
        // sampled inside the call ⇒ elapsed = localNow + offset − before ∈
        // [60_000, 60_000 + (afterCall − before)], modulo tiny slack for a
        // non-monotonic wall clock.
        val elapsedMs = TimeSyncManager.ticksToMs(projected)
        assertTrue(elapsedMs >= 60_000L - 100L, "elapsed ${elapsedMs}ms must cross the +60s offset")
        assertTrue(
            elapsedMs <= 60_000L + (afterCall - before) + 100L,
            "elapsed ${elapsedMs}ms must stay within the measured wall-clock bracket",
        )
    }

    @Test
    fun `estimateCurrentTicks keeps a remote-stamped whenMs in the same clock`() {
        // A whenMs sampled from remoteNow itself: the offset cancels, so only
        // the wall time between the two reads advances the clock — small and
        // non-negative under normal monotonic-ish reads.
        val whenMs = manager.remoteNow()
        val projected = manager.estimateCurrentTicks(positionTicks = 42L, whenMs = whenMs)

        assertTrue(projected >= 42L - 100_000L, "projected $projected drifted implausibly below the base ticks")
        assertTrue(projected < 42L + 10_000L * 10_000L, "projected $projected drifted implausibly above the base ticks")
    }

    // ── ticks ↔ ms conversions ──────────────────────────────────────────

    @Test
    fun `msToTicks scales by ten thousand exactly`() {
        assertEquals(12_345L * 10_000L, TimeSyncManager.msToTicks(12_345L))
        assertEquals(0L, TimeSyncManager.msToTicks(0L))
        assertEquals(-5L * 10_000L, TimeSyncManager.msToTicks(-5L))
    }

    @Test
    fun `ticksToMs truncates sub-millisecond ticks toward zero`() {
        assertEquals(1L, TimeSyncManager.ticksToMs(19_999L))
        assertEquals(1L, TimeSyncManager.ticksToMs(10_000L))
        assertEquals(0L, TimeSyncManager.ticksToMs(9_999L))
        assertEquals(-1L, TimeSyncManager.ticksToMs(-19_999L)) // toward zero, not floor
    }

    @Test
    fun `ticks and ms round-trip losslessly on whole milliseconds`() {
        for (ms in listOf(0L, 1L, 999L, 60_000L, 3_600_000L, -250L)) {
            assertEquals(ms, TimeSyncManager.ticksToMs(TimeSyncManager.msToTicks(ms)))
        }
    }

    // ── ISO parse ladder ────────────────────────────────────────────────

    @Test
    fun `parseIsoTimestamp parses the Instant form`() {
        assertEquals(
            Instant.parse("2026-01-02T03:04:05.123Z").toEpochMilli(),
            TimeSyncManager.parseIsoTimestamp("2026-01-02T03:04:05.123Z"),
        )
    }

    @Test
    fun `parseIsoTimestamp falls back to OffsetDateTime for offset-only stamps`() {
        assertEquals(
            OffsetDateTime.parse("2026-01-02T03:04:05+01:00").toInstant().toEpochMilli(),
            TimeSyncManager.parseIsoTimestamp("2026-01-02T03:04:05+01:00"),
        )
    }

    @Test
    fun `parseIsoTimestamp returns null for blank or garbage input`() {
        assertNull(TimeSyncManager.parseIsoTimestamp(""))
        assertNull(TimeSyncManager.parseIsoTimestamp("   "))
        assertNull(TimeSyncManager.parseIsoTimestamp("not-a-date"))
    }

    @Test
    fun `parseTimestamp delegates to the shared ladder and keeps its zero fallback`() {
        // Offset-only stamp reaches the shared ladder through the event
        // handler's delegation.
        assertEquals(
            OffsetDateTime.parse("2026-01-02T03:04:05+01:00").toInstant().toEpochMilli(),
            handler.parseTimestamp(JSONObject("""{"When": "2026-01-02T03:04:05+01:00"}"""), listOf("When")),
        )
        // Unparseable → falls through the keys → 0 (the documented fallback
        // divergence: "now" is TimeSyncManager's own fallback, never the
        // handler's).
        assertEquals(0L, handler.parseTimestamp(JSONObject("""{"When": "???"}"""), listOf("When")))
    }
}
