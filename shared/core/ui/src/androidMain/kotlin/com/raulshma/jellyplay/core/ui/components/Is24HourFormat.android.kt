package com.raulshma.jellyplay.core.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberIs24HourFormat(): Boolean {
    val context = LocalContext.current
    return remember(context) { DateFormat.is24HourFormat(context) }
}
