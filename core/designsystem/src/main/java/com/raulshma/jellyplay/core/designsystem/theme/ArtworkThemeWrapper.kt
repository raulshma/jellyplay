package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf

import com.raulshma.jellyplay.core.model.ColorStyle

val LocalArtworkColors = compositionLocalOf<ArtworkColors?> { null }

@Composable
fun ArtworkThemeWrapper(
    imageUrl: String?,
    dynamicTheming: Boolean = true,
    darkTheme: Boolean = false,
    oledMode: Boolean = false,
    colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    content: @Composable () -> Unit,
) {
    if (!dynamicTheming || imageUrl.isNullOrBlank()) {
        CompositionLocalProvider(LocalArtworkColors provides null) {
            content()
        }
        return
    }

    val colors = rememberArtworkColors(imageUrl)
    val colorScheme: androidx.compose.material3.ColorScheme? = remember(colors, darkTheme, oledMode, colorStyle) {
        colors?.let { ArtworkColorExtractor.generateColorScheme(it, darkTheme, oledMode, colorStyle) }
    }

    CompositionLocalProvider(LocalArtworkColors provides colors) {
        if (colorScheme != null) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = MaterialTheme.typography,
                content = content,
            )
        } else {
            content()
        }
    }
}
