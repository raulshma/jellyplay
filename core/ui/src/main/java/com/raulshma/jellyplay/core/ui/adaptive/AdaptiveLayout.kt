package com.raulshma.jellyplay.core.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
fun AdaptiveLayout(
    content: @Composable (AdaptiveInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val adaptiveInfo = classifyWindow(widthDp, heightDp)

    CompositionLocalProvider(LocalAdaptiveInfo provides adaptiveInfo) {
        content(adaptiveInfo)
    }
}

@Composable
fun AdaptiveTwoPane(
    modifier: Modifier = Modifier,
    listPane: @Composable () -> Unit,
    detailPane: @Composable (() -> Unit)?,
    showDetail: Boolean,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current

    if (adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded ||
        (adaptiveInfo.windowSizeClass == WindowSizeClass.Medium && adaptiveInfo.isLandscape)
    ) {
        androidx.compose.foundation.layout.Row(modifier = modifier) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded)
                            Modifier.weight(0.45f)
                        else Modifier.weight(0.5f)
                    )
            ) {
                listPane()
            }
            if (detailPane != null && showDetail) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded)
                                Modifier.weight(0.55f)
                            else Modifier.weight(0.5f)
                        )
                ) {
                    detailPane()
                }
            }
        }
    } else {
        BoxWithConstraints(modifier = modifier) {
            listPane()
        }
    }
}
