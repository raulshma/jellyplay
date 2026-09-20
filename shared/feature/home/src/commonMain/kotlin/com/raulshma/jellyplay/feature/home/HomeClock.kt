package com.raulshma.jellyplay.feature.home

import kotlinx.datetime.LocalDate

/**
 * Common seam over core:data's jvmShared [com.raulshma.jellyplay.core.data.util.TimeSource]
 * — the two clock reads the home refresher makes (epoch-millis for the
 * throttle/TTL math, the calendar-window "today" for the *arr refresh and
 * the discover fetch). The jvmShared TimeSource carries a java.time
 * `today(ZoneId): LocalDate` surface, so the
 * refresher narrows to this feature-local seam: the JVM actual delegates to
 * the process-wide TimeSource single verbatim (desktop/android keep the
 * `LocalDate.now(system zone)` semantics and the injected-clock test
 * behavior).
 */
interface HomeClock {

    /** Current wall-clock time in epoch milliseconds (throttle/TTL math). */
    fun nowEpochMillis(): Long

    /** Today's date in the platform's system zone (calendar windows, discover). */
    fun today(): LocalDate
}
