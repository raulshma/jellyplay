package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalArtworkColors = staticCompositionLocalOf<ArtworkColors?> { null }

@Composable
fun ArtworkThemeWrapper(
    imageUrl: String?,
    dynamicTheming: Boolean = true,
    darkTheme: Boolean = false,
    oledMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!dynamicTheming || imageUrl.isNullOrBlank()) {
        CompositionLocalProvider(LocalArtworkColors provides null) {
            content()
        }
        return
    }

    val colors = rememberArtworkColors(imageUrl)
    val colorScheme: androidx.compose.material3.ColorScheme? = remember(colors, darkTheme, oledMode) {
        colors?.let { ArtworkColorExtractor.generateColorScheme(it, darkTheme, oledMode) }
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
