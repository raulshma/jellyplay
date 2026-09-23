package com.raulshma.jellyplay.feature.livetv.testutil

import com.raulshma.jellyplay.core.data.util.TimeSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * Controllable [TimeSource] on a fixed epoch (the HomeRefresher fake
 * idiom) — suites construct it with their boot epoch and move [nowMs]
 * to deliberately cross a boundary (request window, throttle, ticker).
 * `today(zone)` pins the calendar day to 2026-01-01.
 */
class FakeTimeSource(var nowMs: Long) : TimeSource {
    override fun nowEpochMillis(): Long = nowMs
    override fun nowElapsedRealtimeMillis(): Long = nowMs
    override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
}
