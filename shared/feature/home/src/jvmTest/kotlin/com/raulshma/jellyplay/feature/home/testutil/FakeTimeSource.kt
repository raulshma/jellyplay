package com.raulshma.jellyplay.feature.home.testutil

import com.raulshma.jellyplay.feature.home.HomeClock

/**
 * Controllable [HomeClock] whose clock defaults to a fixed epoch so the
 * periodic-refresh and TTL gates stay on one side of their thresholds;
 * tests move [nowMs] to deliberately cross one. The epoch-millis read
 * drives the throttle/TTL math; `today()` pins the calendar day
 * (2026-01-01).
 */
class FakeTimeSource(var nowMs: Long = 1_000L) : HomeClock {
    override fun nowEpochMillis(): Long = nowMs
    override fun today(): kotlinx.datetime.LocalDate = kotlinx.datetime.LocalDate(2026, 1, 1)
}
