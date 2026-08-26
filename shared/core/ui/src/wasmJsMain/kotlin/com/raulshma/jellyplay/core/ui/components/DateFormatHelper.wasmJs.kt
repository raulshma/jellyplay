package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.DateFormatPreference

/**
 * Wasm half of [formatDate]. The fixed preferences reproduce the exact
 * patterns the JVM formatters emit (`MM/dd/yyyy`, `yyyy-MM-dd`, `dd/MM/yyyy`,
 * `M/d/yy`, `MMMM d, yyyy`); SYSTEM resolves its slash order through the
 * browser's ICU region instead of a host locale database — see
 * [resolveWasmDateOrder]. LONG month names are always English on web (the
 * device-localized month spellings need CLDR data this target does not ship).
 */
private val ENGLISH_MONTHS =
    arrayOf("January", "February", "March", "April", "May", "June", "July",
        "August", "September", "October", "November", "December")

private fun pad2(value: Int): String = value.toString().padStart(2, '0')

actual fun formatDate(timestamp: Long, preference: DateFormatPreference): String {
    val date = WasmLocalDate(timestamp)
    val mm = pad2(date.month)
    val dd = pad2(date.day)
    return when (preference) {
        DateFormatPreference.SYSTEM -> when (resolveWasmDateOrder()) {
            WasmDateOrder.YMD -> "${date.year}/$mm/$dd"
            WasmDateOrder.DMY -> "$dd/$mm/${date.year}"
            WasmDateOrder.MDY -> "$mm/$dd/${date.year}"
        }
        DateFormatPreference.US -> "$mm/$dd/${date.year}"
        DateFormatPreference.ISO -> "${date.year}-$mm-$dd"
        DateFormatPreference.EU -> "$dd/$mm/${date.year}"
        DateFormatPreference.LONG -> "${ENGLISH_MONTHS[date.month - 1]} ${date.day}, ${date.year}"
        DateFormatPreference.SHORT -> "${date.month}/${date.day}/${pad2(date.year % 100)}"
    }
}
