package com.raulshma.jellyplay.core.ui.components

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Desktop half of the SYSTEM date pattern, from the SHORT date format. */
internal actual fun bestDateTimePattern(locale: Locale): String =
    (DateFormat.getDateInstance(DateFormat.SHORT, locale) as SimpleDateFormat).toLocalizedPattern()
