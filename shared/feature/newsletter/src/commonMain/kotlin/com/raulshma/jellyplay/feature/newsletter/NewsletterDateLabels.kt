package com.raulshma.jellyplay.feature.newsletter

import kotlinx.datetime.LocalDate

/**
 * Platform-format seam for the newsletter (calendar's CalendarDateLabels.kt /
 * requests' RequestTime.kt template — the module-internal twin of core:ui's
 * PlatformTime.kt): the reads that have no multiplatform twin — java.time
 * pattern formatting ("MMMM d, yyyy" header line, "MMM d" digest entries) and
 * the JVM-only `"%.1f".format` — moved behind expect/actual so commonMain
 * stays wasm-clean. JVM output is locale-dependent (DateTimeFormatter uses
 * Locale.getDefault()), so byte-parity demands the verbatim java.time bodies
 * stay platform-side:
 *  - jvmShared actual: the verbatim java.time/String.format bodies (android +
 *    desktop behavior unchanged; jvmTest pins via reflection).
 *  - wasmJs actual: fixed-English formatting through hand-rolled month arrays
 *    and integer-math one-decimal rendering — documented locale degrade, same
 *    as the calendar seam.
 */
internal expect fun newsletterHeaderDateLabel(date: LocalDate): String

/**
 * The digest entry's relative label for an ISO-8601 timestamp ("Today" /
 * "Yesterday" / "MMM d" in the system zone), or [dateStr] itself when it does
 * not parse — the exact contract the private `formatRelativeDate` pinned.
 */
internal expect fun newsletterRelativeDateLabel(dateStr: String): String

/**
 * One-decimal fixed notation for the star-rating badges ("%.1f" contract:
 * HALF_UP at the first decimal). JVM actual keeps `"%.1f".format` verbatim
 * (decimal comma under e.g. de-DE hosts); wasm renders a dot, like core:ui's
 * PlatformTime seam.
 */
internal expect fun formatOneDecimal(value: Double): String
