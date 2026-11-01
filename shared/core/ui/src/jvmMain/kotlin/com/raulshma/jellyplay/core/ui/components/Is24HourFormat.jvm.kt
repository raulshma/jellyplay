package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat

@Composable
public actual fun rememberIs24HourFormat(): Boolean {
    // The shell's `time_use_24h` override wins; null follows the system.
    LocalTimeFormat24hOverride.current?.let { return it }
    return remember {
        // A 24-hour locale formats the short time pattern with 'H'; 12-hour uses 'h'.
        val pattern = (SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT) as SimpleDateFormat).toLocalizedPattern()
        pattern.contains('H')
    }
}
