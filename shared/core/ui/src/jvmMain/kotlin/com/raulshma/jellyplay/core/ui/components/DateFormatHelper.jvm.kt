package com.raulshma.jellyplay.core.ui.components

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

internal actual fun bestDateTimePattern(locale: Locale): String =
    (DateFormat.getDateInstance(DateFormat.SHORT, locale) as SimpleDateFormat).toLocalizedPattern()
