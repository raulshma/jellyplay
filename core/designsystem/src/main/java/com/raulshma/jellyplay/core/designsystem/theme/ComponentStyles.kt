package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.foundation.style.pressed
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationStyleApi::class)
object ComponentStyles {

    private val StyleScope.colors: ColorScheme
        get() = LocalJellyPlayColorScheme.currentValue

    private val StyleScope.typography: Typography
        get() = LocalJellyPlayTypography.currentValue

    private val StyleScope.shapes: Shapes
        get() = LocalJellyPlayShapes.currentValue

    fun pinKeyStyle(keySize: Dp = 72.dp) = Style {
        background(colors.surfaceVariant)
        shape(shapes.extraLarge)
        minWidth(keySize)
        minHeight(keySize)
        textStyle(typography.headlineMedium)

        pressed {
            background(colors.primaryContainer)
            // NOTE: a `scale(0.95f)` pressed-state transform previously lived here
            // but was dropped — Compose Foundation 1.12.0-beta01's Styles API no
            // longer resolves `scale` inside the `pressed { }` lambda (it now
            // routes through `state(StyleStateKey)`). Re-add once the API stabilizes.
        }
    }

    fun pinDotStyle(dotSize: Dp = 16.dp) = Style {
        shape(shapes.extraLarge)
        background(colors.outlineVariant)
        minWidth(dotSize)
        minHeight(dotSize)
    }
    
    fun pinDotFilledStyle(dotSize: Dp = 20.dp) = Style {
        shape(shapes.extraLarge)
        background(colors.primary)
        minWidth(dotSize)
        minHeight(dotSize)
    }
}
