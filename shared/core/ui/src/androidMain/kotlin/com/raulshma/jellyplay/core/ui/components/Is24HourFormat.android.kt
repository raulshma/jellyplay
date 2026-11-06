package com.raulshma.jellyplay.core.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
public actual fun rememberIs24HourFormat(): Boolean {
    // The shell's `time_use_24h` override wins; null follows the system.
    LocalTimeFormat24hOverride.current?.let { return it }
    val context = LocalContext.current
    return remember(context) { DateFormat.is24HourFormat(context) }
}
