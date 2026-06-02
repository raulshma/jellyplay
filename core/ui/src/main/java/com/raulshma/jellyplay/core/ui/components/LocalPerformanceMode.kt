package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

val LocalPerformanceMode: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { false }
