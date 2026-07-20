package com.raulshma.jellyplay.core.data.util

import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only seam over the system clock, so time-aware logic (TTL gates,
 * refresh jitter, calendar windows) can be unit-tested by injecting a fake.
 *
 * The system implementation delegates to the JDK. There is no test fake here
 * — fakes live next to the tests that need them (`feature/.../src/test`).
 */
interface TimeSource {
    /** Current wall-clock time in epoch milliseconds. */
    fun nowEpochMillis(): Long

    /** Today's date in the given [zone]. */
    fun today(zone: ZoneId): LocalDate
}

@Singleton
class SystemTimeSource @Inject constructor() : TimeSource {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
    override fun today(zone: ZoneId): LocalDate = LocalDate.now(zone)
}
