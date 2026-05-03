package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ArtworkThemeWrapper(
    imageUrl: String?,
    dynamicTheming: Boolean = true,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!dynamicTheming || imageUrl.isNullOrBlank()) {
        content()
        return
    }

    val colors = rememberArtworkColors(imageUrl)
    if (colors != null) {
        val colorScheme = ArtworkColorExtractor.generateColorScheme(colors, darkTheme)
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            content = content,
        )
    } else {
        content()
    }
}
