package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.DateFormatPreference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ISO_DATE_PATTERN = "yyyy-MM-dd"

fun getDateFormat(preference: DateFormatPreference): SimpleDateFormat = when (preference) {
    DateFormatPreference.SYSTEM -> {
        val locale = Locale.getDefault()
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "MM/dd/yyyy"), locale)
    }
    DateFormatPreference.US -> SimpleDateFormat("MM/dd/yyyy", Locale.US)
    DateFormatPreference.ISO -> SimpleDateFormat(ISO_DATE_PATTERN, Locale.US)
    DateFormatPreference.EU -> SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    DateFormatPreference.LONG -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    DateFormatPreference.SHORT -> SimpleDateFormat("M/d/yy", Locale.US)
}

fun formatDate(
    timestamp: Long,
    preference: DateFormatPreference = DateFormatPreference.SYSTEM,
): String {
    val formatter = getDateFormat(preference)
    return formatter.format(Date(timestamp))
}

fun formatDateIso(timestamp: Long): String {
    return SimpleDateFormat(ISO_DATE_PATTERN, Locale.US).format(Date(timestamp))
}
