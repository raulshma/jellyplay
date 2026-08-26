package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
actual fun rememberWallClockTimeString(): String {
    val is24Hour = rememberIs24HourFormat()
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    val formatter = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    var time by remember { mutableStateOf(formatter.format(Date())) }
    LaunchedEffect(Unit) {
        val initialDelay = 60_000 - (System.currentTimeMillis() % 60_000)
        kotlinx.coroutines.delay(initialDelay)
        time = formatter.format(Date())
        while (true) {
            kotlinx.coroutines.delay(60_000)
            time = formatter.format(Date())
        }
    }
    return time
}
