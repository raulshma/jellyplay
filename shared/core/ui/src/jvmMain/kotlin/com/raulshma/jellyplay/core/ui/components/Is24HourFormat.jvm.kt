package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat

@Composable
internal actual fun rememberIs24HourFormat(): Boolean = remember {
    // A 24-hour locale formats the short time pattern with 'H'; 12-hour uses 'h'.
    val pattern = (SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT) as SimpleDateFormat).toLocalizedPattern()
    pattern.contains('H')
}
