package com.raulshma.jellyplay.feature.newsletter

import com.raulshma.jellyplay.core.ui.components.longMonthDayYear
import com.raulshma.jellyplay.core.ui.components.oneDecimal
import com.raulshma.jellyplay.core.ui.components.relativeInstantDateLabel
import kotlinx.datetime.LocalDate

/**
 * Thin façades over the core/ui date-label seam — the module-internal names
 * the newsletter's call sites use, kept so churn stays at the seams' edges.
 * The formatting bodies (java.time DateTimeFormatter on android/desktop,
 * fixed-English tables on wasm), the Today/Yesterday ladder, and the
 * one-decimal renderer live ONLY in core:ui's DateLabels; the documented
 * fixed-English locale degrade is stated once there.
 */

/** The header line's date label, e.g. "January 5, 2026" ("MMMM d, yyyy"). */
internal fun newsletterHeaderDateLabel(date: LocalDate): String = longMonthDayYear(date)

/**
 * The digest entry's relative label for an ISO-8601 timestamp ("Today" /
 * "Yesterday" / "MMM d" in the system zone), or [dateStr] itself when it does
 * not parse — the exact contract the private `formatRelativeDate` pinned.
 */
internal fun newsletterRelativeDateLabel(dateStr: String): String = relativeInstantDateLabel(dateStr)

/**
 * One-decimal fixed notation for the star-rating badges ("%.1f" contract:
 * HALF_UP at the first decimal; JVM keeps the host-locale separator, wasm
 * renders a dot) — see the core/ui seam.
 */
internal fun formatOneDecimal(value: Double): String = oneDecimal(value)
