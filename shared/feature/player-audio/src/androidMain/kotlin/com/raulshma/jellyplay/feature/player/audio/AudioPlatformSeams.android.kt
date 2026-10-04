package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberIs24HourFormat(): Boolean {
    val context = LocalContext.current
    return remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
}
