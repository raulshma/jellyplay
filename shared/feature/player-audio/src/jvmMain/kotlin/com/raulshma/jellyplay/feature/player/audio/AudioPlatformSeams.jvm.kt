package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal actual fun rememberIs24HourFormat(): Boolean =
    remember {
        // JDK heuristic: format 13:00 with the default SHORT time pattern —
        // a 24-hour locale renders "13:…" while 12-hour locales render "1:… PM".
        val formatted = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
            .format(Date(13L * 60L * 60L * 1000L))
        !formatted.contains("PM") && !formatted.contains("AM")
    }
