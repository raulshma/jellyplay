package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A transparent top app bar that floats over a backdrop, matching the online
 * detail screen. The [containerColor] and title ([titleAlpha]) fade in as the
 * user scrolls past the backdrop (driven by [scrollCollapsed]); over the
 * backdrop itself both are fully transparent so the image shows through.
 *
 * The back button ([CircleBgBackButton]) always renders with its translucent
 * circle so it stays legible over the image.
 *
 * @param scrollCollapsed 0f over the backdrop → 1f once collapsed.
 */
@Composable
fun TransparentTopBar(
    title: String,
    onBack: () -> Unit,
    containerColor: Color,
    titleAlpha: Float,
    scrollCollapsed: Float,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .drawBehind { drawRect(containerColor) },
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            CircleBgBackButton(
                onClick = onBack,
                scrollCollapsed = scrollCollapsed,
            )
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = titleAlpha),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
    }
}
