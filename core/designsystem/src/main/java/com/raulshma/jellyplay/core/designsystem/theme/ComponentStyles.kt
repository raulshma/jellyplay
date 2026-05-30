package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.foundation.style.pressed
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationStyleApi::class)
object ComponentStyles {

    private val StyleScope.colors: ColorScheme
        get() = LocalJellyPlayColorScheme.currentValue

    private val StyleScope.typography: Typography
        get() = LocalJellyPlayTypography.currentValue

    private val StyleScope.shapes: Shapes
        get() = LocalJellyPlayShapes.currentValue

    val pinKeyStyle = Style {
        background(colors.surfaceVariant)
        shape(shapes.extraLarge)
        minWidth(72.dp)
        minHeight(72.dp)
        textStyle(typography.headlineMedium)
        
        pressed {
            background(colors.primaryContainer)
            scale(0.95f)
        }
    }

    val pinDotStyle = Style {
        shape(shapes.extraLarge)
        background(colors.outlineVariant)
        minWidth(16.dp)
        minHeight(16.dp)

        // Custom state logic for "filled" will be handled dynamically in the component since 
        // Style doesn't have a built-in `filled` state yet, or we can use custom state 
        // interaction, but using multiple styles is simpler. 
        // Alternatively, we define two styles.
    }
    
    val pinDotFilledStyle = Style {
        shape(shapes.extraLarge)
        background(colors.primary)
        minWidth(20.dp)
        minHeight(20.dp)
    }
}
