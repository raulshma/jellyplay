package com.raulshma.jellyplay.feature.home

import kotlinx.datetime.LocalDate

/**
 * Web seam over core:data's jvmShared [com.raulshma.jellyplay.core.data.util.TimeSource]
 * — the two clock reads the home refresher makes (epoch-millis for the
 * throttle/TTL math, the calendar-window "today" for the *arr refresh and
 * the discover fetch). The jvmShared TimeSource carries a java.time
 * `today(ZoneId): LocalDate` surface that cannot compile for wasm, so the
 * refresher narrows to this feature-local seam: the JVM actual delegates to
 * the process-wide TimeSource single verbatim (desktop/android keep the
 * `LocalDate.now(system zone)` semantics and the injected-clock test
 * behavior), and the wasm actual reads the platform wall clock through
 * kotlinx-datetime's system-zone calendar.
 *
 * LiveTvTimeFormat's locale split, clock edition: JVM keeps the java.time
 * wall read, wasm derives the same ISO date from the common
 * kotlinx-datetime calendar — both produce the current calendar day in the
 * device's system zone.
 */
interface HomeClock {

    /** Current wall-clock time in epoch milliseconds (throttle/TTL math). */
    fun nowEpochMillis(): Long

    /** Today's date in the platform's system zone (calendar windows, discover). */
    fun today(): LocalDate
}
