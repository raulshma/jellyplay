package com.raulshma.jellyplay.core.testfixtures

import com.raulshma.jellyplay.core.model.TimeSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * The canonical controllable [TimeSource] fake for feature-module jvmTest
 * suites.
 *
 * Implements the core:model TimeSource (the core:data FQIN is a deprecated
 * typealias).
 *
 * Semantics:
 *  - [nowEpochMillis] / [nowElapsedRealtimeMillis] both read [nowMs]
 *    (tests advance time by assigning/incrementing it directly).
 *  - [today] ignores the zone and returns the fixed [todayDate] (default
 *    2026-01-01).
 *  - With [autoAdvance] set, [nowEpochMillis] ticks [nowMs] forward 1 ms per
 *    read, so repeated saves get distinct monotone stamps (the ordering
 *    real `Thread.sleep` pauses provide) — [nowElapsedRealtimeMillis]
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
