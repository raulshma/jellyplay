package com.raulshma.jellyplay.core.data.testutil

import com.raulshma.jellyplay.core.model.TimeSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single controllable [TimeSource] fake for the core:data jvmTest suites —
 * replaces the ~14 hand copies that each test class used to declare (the
 * drift those invited: one copy had renamed its field, another a different
 * default, so single-homing them here).
 *
 * The canonical cross-module twin of this fake lives in :shared:core:test-fixtures
 * (com.raulshma.jellyplay.core.testfixtures.FakeTimeSource) for the feature
 * modules' jvmTests; this local copy stays until a touch migrates core:data's
 * 14 consumers (per-touch adoption). Keep the two in shape sync.
 *
 * Semantics are the common shape all the copies shared:
 *  - [nowEpochMillis] / [nowElapsedRealtimeMillis] both read [nowMs]
 *    (tests advance time by assigning/incrementing it directly).
 *  - [today] ignores the zone and returns the fixed [todayDate].
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
