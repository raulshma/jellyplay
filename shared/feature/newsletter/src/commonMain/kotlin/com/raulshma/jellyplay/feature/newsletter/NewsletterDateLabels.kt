package com.raulshma.jellyplay.feature.newsletter

import com.raulshma.jellyplay.core.ui.components.longMonthDayYear
import com.raulshma.jellyplay.core.ui.components.relativeInstantDateLabel
import kotlinx.datetime.LocalDate

/**
 * Thin façades over the core/ui date-label seam — the module-internal names
 * the newsletter's call sites use, kept so churn stays at the seams' edges.
 * The formatting bodies (java.time DateTimeFormatter on android/desktop) and
 * the Today/Yesterday ladder live ONLY in core:ui's DateLabels. (The
 * one-decimal renderer that used to be façaded here too is gone: core/ui's
 * public [com.raulshma.jellyplay.core.ui.components.formatOneDecimal] —
 * DurationFormatter.kt — is imported by the star-rating call sites directly.)
 */

/** The header line's date label, e.g. "January 5, 2026" ("MMMM d, yyyy"). */
internal fun newsletterHeaderDateLabel(date: LocalDate): String = longMonthDayYear(date)

/**
 * The digest entry's relative label for an ISO-8601 timestamp ("Today" /
 * "Yesterday" / "MMM d" in the system zone), or [dateStr] itself when it does
 * not parse — the exact contract the private `formatRelativeDate` pinned.
 */
internal fun newsletterRelativeDateLabel(dateStr: String): String = relativeInstantDateLabel(dateStr)
