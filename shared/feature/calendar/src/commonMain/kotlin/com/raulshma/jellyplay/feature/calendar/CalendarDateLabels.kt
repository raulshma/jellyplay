package com.raulshma.jellyplay.feature.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Locale seam for the calendar's human-readable date labels (wave 16A): the
 * two reads that have no multiplatform formatter in kotlinx-datetime —
 * java.text-style pattern formatting ("EEE, MMM d" / "MMMM yyyy") — moved
 * behind expect/actual so commonMain stays wasm-clean. Same template as
 * requests' RequestTime.kt seam:
 *  - jvmShared actual: the verbatim java.time DateTimeFormatter bodies with
 *    Locale.getDefault() (android + desktop behavior unchanged; jvmTest pins).
 *  - wasmJs actual: fixed-English formatting through hand-rolled arrays —
 *    documented locale degrade (see the actual's KDoc).
 */
internal expect fun calendarDayHeaderLabel(date: LocalDate): String

/** Full month + year label, e.g. "July 2026" — see [calendarDayHeaderLabel]. */
internal expect fun calendarMonthYearLabel(month: YearMonth): String
