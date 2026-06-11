package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

val LocalFloatingNavOffset = compositionLocalOf<Float> { 0f }
val LocalFloatingNavVisibility = compositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(true)
}

