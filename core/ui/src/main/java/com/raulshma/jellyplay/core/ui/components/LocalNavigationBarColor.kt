package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalNavigationBarColor = staticCompositionLocalOf<MutableState<Color?>> {
    mutableStateOf(null)
}
