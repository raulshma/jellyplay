package com.raulshma.jellyplay.core.ui.components

import java.util.Locale

/** Android half of the SYSTEM date pattern: ICU's best pattern for the skeleton. */
internal actual fun bestDateTimePattern(locale: Locale): String =
    android.text.format.DateFormat.getBestDateTimePattern(locale, "MM/dd/yyyy")
