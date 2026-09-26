package com.raulshma.jellyplay.core.testfixtures

import com.raulshma.jellyplay.core.model.TimeSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * The canonical controllable [TimeSource] fake for feature-module jvmTest
 * suites — the richest of the per-module copies that used to drift (livetv's
 * former private one was a strict subset of this shape).
 * :core:data keeps its own same-shaped local copy for its 14 consumer files;
 * per-touch adoption means those stay put until a touch migrates them — keep
 * the two in shape sync when editing either.
 *
 * D3: implements the core:model TimeSource (the seam moved there; the
 * old core:data FQIN is a deprecated typealias).
 *
 * Semantics are the shape all the copies shared:
 *  - [nowEpochMillis] / [nowElapsedRealtimeMillis] both read [nowMs]
 *    (tests advance time by assigning/incrementing it directly).
 *  - [today] ignores the zone and returns the fixed [todayDate] (whose
 *    default, 2026-01-01, is the date the old per-module hardcodes pinned).
 *  - With [autoAdvance] set, [nowEpochMillis] ticks [nowMs] forward 1 ms per
 *    read, so repeated saves get distinct monotone stamps (the ordering the
 *    real `Thread.sleep` pauses used to buy) — [nowElapsedRealtimeMillis]
 *    does not tick.
 */
class FakeTimeSource(
    var nowMs: Long = 1_000L,
    val todayDate: LocalDate = LocalDate.of(2026, 1, 1),
    private val autoAdvance: Boolean = false,
) : TimeSource {
    override fun nowEpochMillis(): Long = if (autoAdvance) ++nowMs else nowMs
    override fun nowElapsedRealtimeMillis(): Long = nowMs
    override fun today(zone: ZoneId): LocalDate = todayDate
}
