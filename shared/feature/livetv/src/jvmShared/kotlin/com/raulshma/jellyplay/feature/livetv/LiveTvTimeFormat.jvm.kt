package com.raulshma.jellyplay.feature.livetv

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * JVM actuals for the Live-TV wall-clock/date-label renderers: format
 * through java.time with the default FORMAT locale, preserving the localized
 * AM/PM markers and month/day abbreviations the former java.time formatters
 * produced (the wasm actual pins English — kotlinx has no CLDR data there).
 * [LiveTvTimeFormatTest] pins the en-US renderings under a forced US FORMAT
 * locale.
 */
internal actual fun formatWallClockTime(local: kotlinx.datetime.LocalDateTime): String =
    LocalDateTime.of(local.year, local.monthNumber, local.dayOfMonth, local.hour, local.minute)
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault(Locale.Category.FORMAT)))

internal actual fun formatDateLabel(local: kotlinx.datetime.LocalDateTime): String =
    LocalDateTime.of(local.year, local.monthNumber, local.dayOfMonth, 0, 0)
        .format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault(Locale.Category.FORMAT)))
