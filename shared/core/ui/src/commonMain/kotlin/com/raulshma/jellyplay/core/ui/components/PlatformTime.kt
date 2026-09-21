package com.raulshma.jellyplay.core.ui.components

/**
 * Platform seam for the handful of wall-clock/format primitives that used to
 * be JVM-API calls inside commonMain UI code (`String.format`, `Calendar`,
 * `java.time`). Android/desktop behavior is pinned by the jvmShared actuals
 * (which carry the original bodies verbatim). [formatIntPattern] is the one
 * public member: the settings and editor features (whose PlatformFormats
 * expect/actual twins it replaces) consume it directly.
 */

/**
 * Renders a localized resource pattern with one integer slot (`%1$d`) — the
 * single renderer for every feature's "N days / N minutes / N downloads"
 * translation patterns. Formerly duplicated as module-internal expect/actual
 * pairs in the settings and editor features (byte-identical actuals); the
 * jvmShared actual is the verbatim `String.format` body both shipped, host
 * locale included.
 */
expect fun formatIntPattern(pattern: String, value: Int): String

/**
 * One-decimal fixed notation ("%.1f" formatting contract: HALF_UP rounding at
 * the first decimal, sign rendered symmetrically for stray negatives). The
 * decimal separator follows the JVM's %.1f behavior — the JVM default
 * locale (decimal comma under e.g. de-DE hosts) on android/desktop. Every
 * former `"%.1f".format(x)` site in this module routes through here.
 */
internal expect fun formatOneDecimal(value: Double): String

/** Current calendar year in the system timezone (was `Calendar.YEAR`). */
internal expect fun currentYear(): Int

/**
 * Hour-of-day of [epochMillis] in the system timezone; when [epochMillis] is
 * null or non-positive, "now" (was `Calendar.getInstance().apply { if (tick >
 * 0) timeInMillis = tick }`).
 */
internal expect fun hourOfDayAt(epochMillis: Long?): Int

/**
 * Whether an ISO local date string (`yyyy-MM-dd`, as Seerr publishes it)
 * lies strictly after today in the system timezone. Unparseable input is
 * false (the pre-seam try/catch contract).
 */
internal expect fun isoDateIsAfterToday(dateStr: String): Boolean

/**
 * Parses an ISO-8601 timestamp with or without offset (Z / ±HH:mm /
 * bare-local, the three forms Jellyfin stamps) into epoch millis; null when
 * blank or malformed. The bare-local form resolves in the system timezone,
 * mirroring the original java.time path.
 */
internal expect fun parseIsoTimestampToEpochMillis(value: String?): Long?
