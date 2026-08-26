package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.wallNowMillis
import kotlin.math.abs
import kotlin.math.round

/** `new Date(ms)` local-time field probes (browser timezone stands in for the JVM system zone). */
private fun jsFullYear(ms: Long): Int = js("new Date(ms).getFullYear()")
private fun jsMonth(ms: Long): Int = js("new Date(ms).getMonth()")
private fun jsDayOfMonth(ms: Long): Int = js("new Date(ms).getDate()")
private fun jsHours(ms: Long): Int = js("new Date(ms).getHours()")
private fun jsMinutes(ms: Long): Int = js("new Date(ms).getMinutes()")
private fun jsLocale(): String = js("(Intl.DateTimeFormat().resolvedOptions().locale || '')")
private fun jsParseMillis(iso: String): Double = js("Date.parse(iso)")

/** Gregorian month length with the leap-year rule (java.time parity). */
private fun maxDayInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
}

internal actual fun formatOneDecimal(value: Double): String {
    // HALF_UP at the first decimal through integer math ("%.1f" replacement,
    // mission note in spike w-10C class A), sign applied symmetrically so a
    // stray negative matches "%.1f"'s away-from-zero output instead of the
    // "-1.-5" an unguarded path would render. Ties inside binary-representation
    // noise can differ by one ulp from the JVM Formatter — invisible at UI
    // stat precision.
    val magnitude = round(abs(value) * 10).toLong()
    val rendered = "${magnitude / 10}.${magnitude % 10}"
    return if (value < 0) "-$rendered" else rendered
}

internal actual fun currentYear(): Int = jsFullYear(wallNowMillis())

internal actual fun hourOfDayAt(epochMillis: Long?): Int =
    jsHours(if (epochMillis != null && epochMillis > 0) epochMillis else wallNowMillis())

/**
 * Date-order regions per ICU short-date convention (what
 * `getBestDateTimePattern`/SHORT-date derivation produce on the JVM halves):
 * month-first locales, day-first everywhere else, year-first East Asian +
 * Hungarian. Unmapped/undetermined locales default to month-first.
 */
private const val MDY_REGIONS = "US|PH|CA|KE|GH|FM|PW|PG|BZ|MT"
private const val YMD_REGIONS = "JP|KR|KP|CN|TW|HU|MN|LT"

internal actual fun isoDateIsAfterToday(dateStr: String): Boolean {
    // Strictly-shaped yyyy-MM-dd only (the Seerr wire form); anything else
    // fails like the old try/catch around LocalDate.parse did. The field-range
    // check rejects impossible dates the shape regex alone would admit
    // ("2099-02-31") — java.time accepted no such input either.
    if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(dateStr)) return false
    val (year, month, day) = dateStr.split('-').map { it.toInt() }
    if (month !in 1..12 || day !in 1..maxDayInMonth(year, month)) return false
    val nowMs = wallNowMillis()
    val nowY = jsFullYear(nowMs)
    val nowM = jsMonth(nowMs) + 1 // JS months are zero-based
    val nowD = jsDayOfMonth(nowMs)
    // Lexicographic date compare (common stdlib has no Comparable Triple).
    return when {
        year != nowY -> year > nowY
        month != nowM -> month > nowM
        else -> day > nowD
    }
}

internal actual fun parseIsoTimestampToEpochMillis(value: String?): Long? {
    value ?: return null
    // Shape-guard first so legacy non-ISO strings reach a clean null instead
    // of browser lenient-parsing garbage.
    val isoShape = Regex(
        """^-?\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$""",
    )
    if (!isoShape.matches(value)) return null
    // Date.parse resolves Z / ±HH:mm offsets natively and treats the bare
    // form as browser-local time — the analog of LocalDateTime.atZone(systemDefault).
    val parsed = jsParseMillis(value)
    return if (parsed.isNaN()) null else parsed.toLong()
}

/**
 * Local date layout family for [com.raulshma.jellyplay.core.ui.components]
 * date formatting: M/D/Y, D/M/Y or Y/M/D resolution for the SYSTEM
 * [DateFormatPreference] path (see DateFormatHelper.wasmJs.kt).
 */
internal enum class WasmDateOrder { MDY, DMY, YMD }

internal fun resolveWasmDateOrder(): WasmDateOrder {
    val locale = jsLocale()
    val region = locale.split('-').getOrNull(1)?.takeIf { it.length == 2 }?.uppercase()
    return when {
        region == null -> WasmDateOrder.MDY
        region in YMD_REGIONS.split('|') -> WasmDateOrder.YMD
        region in MDY_REGIONS.split('|') -> WasmDateOrder.MDY
        else -> WasmDateOrder.DMY
    }
}

/** Shared local-date field bundle for the wasm date formatter. */
internal class WasmLocalDate(millis: Long) {
    val year: Int = jsFullYear(millis)
    val month: Int = jsMonth(millis) + 1
    val day: Int = jsDayOfMonth(millis)
}
