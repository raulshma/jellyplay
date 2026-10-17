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
    accentColorSwatch: String = "dynamic",
    content: @Composable () -> Unit,
) {
    if (!dynamicTheming || imageUrl.isNullOrBlank()) {
        CompositionLocalProvider(LocalArtworkColors provides null) {
            content()
        }
        return
    }

    val colors = rememberArtworkColors(imageUrl)
    // Palette arrival and scheme regeneration land in ONE recomposition pass:
    // `rememberArtworkColors` returns state, and when the async extraction
    // writes it the wrapper recomposes once, re-running this synchronous
    // remember — so the CompositionLocal update and the MaterialTheme remount
    // below always apply together in the same frame, not as separate waves.
    val colorScheme: androidx.compose.material3.ColorScheme? = remember(
        colors, darkTheme, oledMode, colorStyle, accentColorSwatch,
    ) {
        colors?.let {
            ArtworkColorExtractor.generateColorScheme(
                artworkColors = it,
                darkTheme = darkTheme,
                oledMode = oledMode,
                colorStyle = colorStyle,
                accentColorSwatch = accentColorSwatch,
            )
        }
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
