package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.DateFormatPreference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ISO_DATE_PATTERN = "yyyy-MM-dd"

/**
 * Cache of per-thread [SimpleDateFormat] instances, keyed by pattern+locale.
 *
 * `SimpleDateFormat` construction parses the pattern and builds a
 * `DateFormatSymbols` graph — one of the more expensive `java.text`
 * allocations — and `formatDate`/`formatDateIso` are called from list-item
 * composables and viewmodels. Caching avoids rebuilding that graph per call.
 *
 * `SimpleDateFormat` is **not** thread-safe, so a [ThreadLocal] is required
 * (rather than a single shared instance). Each thread that calls these helpers
 * pays the construction cost exactly once per distinct pattern+locale; later
 * calls reuse the cached instance.
 */
private val dateFormatCache = ThreadLocal<MutableMap<String, SimpleDateFormat>>()

private fun obtainFormatter(pattern: String, locale: Locale): SimpleDateFormat {
    val cache = dateFormatCache.get() ?: mutableMapOf<String, SimpleDateFormat>().also { dateFormatCache.set(it) }
    val key = "$pattern@${locale.toLanguageTag()}"
    return cache.getOrPut(key) { SimpleDateFormat(pattern, locale) }
}

fun getDateFormat(preference: DateFormatPreference): SimpleDateFormat = when (preference) {
    DateFormatPreference.SYSTEM -> {
        val locale = Locale.getDefault()
        obtainFormatter(bestDateTimePattern(locale), locale)
    }
    DateFormatPreference.US -> obtainFormatter("MM/dd/yyyy", Locale.US)
    DateFormatPreference.ISO -> obtainFormatter(ISO_DATE_PATTERN, Locale.US)
    DateFormatPreference.EU -> obtainFormatter("dd/MM/yyyy", Locale.UK)
    DateFormatPreference.LONG -> obtainFormatter("MMMM d, yyyy", Locale.getDefault())
    DateFormatPreference.SHORT -> obtainFormatter("M/d/yy", Locale.US)
}

fun formatDate(
    timestamp: Long,
    preference: DateFormatPreference = DateFormatPreference.SYSTEM,
): String {
    val formatter = getDateFormat(preference)
    return formatter.format(Date(timestamp))
}

fun formatDateIso(timestamp: Long): String =
    obtainFormatter(ISO_DATE_PATTERN, Locale.US).format(Date(timestamp))

/**
 * Locale-aware date skeleton pattern. Android uses ICU's
 * `getBestDateTimePattern`; desktop derives it from the locale's SHORT date
 * format (both yield the locale's numeric date convention).
 */
internal expect fun bestDateTimePattern(locale: java.util.Locale): String
