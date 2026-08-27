package com.raulshma.jellyplay.feature.requests

/**
 * Wave 15B wasmJs purification of the two java.time consumers this module
 * had ([RequestListItem]'s relative time, [RequestDetailBottomSheet]'s
 * requested-date row). The JVM/android behavior is byte-preserved by the
 * jvmShared actuals (the literal pre-15B java.time bodies); the wasmJs
 * actuals re-implement the same parsing with strict regexes + integer math.
 *
 * SEMANTIC EQUIVALENCE (both platforms):
 *  - Relative time compares two ABSOLUTE instants (the stamp's offset vs
 *    now), so the result is time-zone independent everywhere.
 *  - The requested-date row parses the stamp's LOCAL fields and discards any
 *    offset/bracket-zone suffix — which is exactly what
 *    `LocalDateTime.parse(..., ISO_DATE_TIME)` did on the JVM.
 *
 * DOCUMENTED BROWSER-TZ / LOCALE DEGRADES (web only, wave-11A §8 template):
 *  - The requested-date row's month abbreviation is FIXED ENGLISH on web
 *    ("MMM d, yyyy" through `DateTimeFormatter.ofPattern` was host-locale
 *    driven on the JVM) — same cut core/ui's DateFormatHelper documents.
 *  - Web parsing accepts exactly the strict extended ISO shapes listed in
 *    each actual's KDoc; exotic ISO variants java tolerates (basic format,
 *    offsets without a colon) fail parsing and hit the same null fallbacks
 *    the JVM's DateTimeParseException path used.
 */

/**
 * Whole minutes between the ISO-8601 offset timestamp [dateStr] and now —
 * negative when the stamp is in the future, `null` when it does not parse.
 * JVM actual: the verbatim `OffsetDateTime.parse` + `Duration.between` pair
 * (negative durations truncate toward zero there; every negative already
 * lands in the "just now" bucket before any division can diverge).
 */
internal expect fun requestAgeMinutes(dateStr: String): Long?

/**
 * Formats the ISO-8601 local date-time stamp [dateStr] as the requested-date
 * row's label ("MMM d, yyyy"), or `null` when it does not parse (call sites
 * fall back to the raw stamp's first 10 chars, exactly as before).
 */
internal expect fun formatRequestedDate(dateStr: String): String?

/**
 * Substitutes the resource templates' count placeholder (`%1$d`, or plain
 * `%d` in some translations) with [value]. Wave 15B: replaces the old
 * `String.format(template, n)` calls — kotlin.text.format has no wasmJs
 * actual, and `String.format("%1\$dm", 5L)` produces exactly the same string
 * as the substitution for these plain `%d` placeholders (no flags/width).
 */
internal fun formatCount(template: String, value: Long): String =
    template.replace("%1\$d", value.toString()).replace("%d", value.toString())
