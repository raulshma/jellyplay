package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.model.wallNowMillis
import kotlinx.coroutines.delay

private fun jsWallHours(): Int = js("new Date().getHours()")
private fun jsWallMinutes(): Int = js("new Date().getMinutes()")

/**
 * Browser-local clock text ("HH:mm" / "h:mm a"). Meridiem letters are fixed
 * English — the JVM half uses `Locale.getDefault()` symbols, which web lacks.
 */
private fun wallClockText(is24Hour: Boolean): String {
    val hours = jsWallHours()
    val mm = jsWallMinutes().toString().padStart(2, '0')
    if (is24Hour) return "${hours.toString().padStart(2, '0')}:$mm"
    val hour12 = run { val r = hours % 12; if (r == 0) 12 else r }
    val meridiem = if (hours < 12) "AM" else "PM"
    return "$hour12:$mm $meridiem"
}

@Composable
actual fun rememberWallClockTimeString(): String {
    val is24Hour = rememberIs24HourFormat()
    var time by remember(is24Hour) { mutableStateOf(wallClockText(is24Hour)) }
    LaunchedEffect(is24Hour) {
        // Same minute-boundary realignment cadence as the JVM actual.
        val initialDelay = 60_000 - (wallNowMillis() % 60_000)
        kotlinx.coroutines.delay(initialDelay)
        time = wallClockText(is24Hour)
        while (true) {
            kotlinx.coroutines.delay(60_000)
            time = wallClockText(is24Hour)
        }
    }
    return time
}
