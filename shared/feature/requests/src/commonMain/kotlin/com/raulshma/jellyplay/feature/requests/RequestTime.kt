package com.raulshma.jellyplay.feature.requests

import com.raulshma.jellyplay.core.ui.components.isoOffsetMinutesAgo
import com.raulshma.jellyplay.core.ui.components.localDateFromIsoTimestamp
import com.raulshma.jellyplay.core.ui.components.shortMonthDayYear

/**
 * Thin façades over the core/ui date-label seam for the two ISO-stamp reads
 * this module's UI uses ([RequestListItem]'s relative time,
 * [RequestDetailBottomSheet]'s requested-date row) — the module-internal
 * names the call sites use, kept so churn stays at the seams' edges.
 *
 * The parsing and formatting bodies live ONLY in core:ui's DateLabels
 * (jvmShared actual: the verbatim java.time pipelines — `OffsetDateTime.parse`
 * + `Duration.between`, `LocalDateTime.parse(ISO_DATE_TIME)` + "MMM d, yyyy").
 * SEMANTIC EQUIVALENCE (pinned by
 * RequestTimeJvmSemanticsTest through these façades):
 *  - Relative time compares two ABSOLUTE instants (the stamp's offset vs
 *    now), so the result is time-zone independent everywhere.
 *  - The requested-date row parses the stamp's LOCAL fields and discards any
 *    offset/bracket-zone suffix — exactly what
 *    `LocalDateTime.parse(..., ISO_DATE_TIME)` did on the JVM.
 *  - A stamp with NO zone offset fails the relative read (null) on both
 *    platforms; parse failures null out exactly where the old catch paths did.
 */

/**
 * Whole minutes between the ISO-8601 offset timestamp [dateStr] and now —
 * negative when the stamp is in the future, `null` when it does not parse.
 */
internal fun requestAgeMinutes(dateStr: String): Long? = isoOffsetMinutesAgo(dateStr)

/**
 * Formats the ISO-8601 local date-time stamp [dateStr] as the requested-date
 * row's label ("MMM d, yyyy"), or `null` when it does not parse (call sites
 * fall back to the raw stamp's first 10 chars, exactly as before).
 */
internal fun formatRequestedDate(dateStr: String): String? =
    localDateFromIsoTimestamp(dateStr)?.let(::shortMonthDayYear)

/**
 * Substitutes the resource templates' count placeholder (`%1$d`, or plain
 * `%d` in some translations) with [value].: replaces the old
 * `String.format(template, n)` calls — `String.format("%1\$dm", 5L)` produces
 * exactly the same string
 * as the substitution for these plain `%d` placeholders (no flags/width).
 */
internal fun formatCount(template: String, value: Long): String =
    template.replace("%1\$d", value.toString()).replace("%d", value.toString())
