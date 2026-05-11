package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Overscan-safe margin for Android TV (leanback guideline: 48dp / ~5% of 1080p).
 */
object TvOverscan {
    val horizontal = 48.dp
    val vertical = 48.dp
}

/**
 * Whether the app is running in TV mode (from CompositionLocal).
 */
val LocalTvMode = compositionLocalOf { false }

/**
 * A TV-optimized scaffold that handles overscan margins, focus management,
 * and proper D-pad navigation structure.
 *
 * On TV devices, wraps content in overscan-safe padding (48dp).
 * On non-TV devices, renders content with no extra padding.
 */
@Composable
fun TvScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isTv = isTvDevice()

    CompositionLocalProvider(LocalTvMode provides isTv) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(
                    if (isTv) Modifier.padding(
                        horizontal = TvOverscan.horizontal,
                        vertical = TvOverscan.vertical,
                    ) else Modifier
                ),
        ) {
            if (topBar != null) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                    topBar()
                    Box(modifier = Modifier.weight(1f)) {
                        content()
                    }
                }
            } else {
                content()
            }
        }
    }
}
