package com.raulshma.jellyplay.core.ui.components

import java.util.Locale

internal actual fun bestDateTimePattern(locale: Locale): String =
    android.text.format.DateFormat.getBestDateTimePattern(locale, "MM/dd/yyyy")
