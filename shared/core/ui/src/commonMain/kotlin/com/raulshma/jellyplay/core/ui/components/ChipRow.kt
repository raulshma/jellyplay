package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

/**
 * A horizontally-scrolling row of pill chips, used for genres, studios, and
 * similar short string lists on detail screens. Each value renders as a rounded
 * box with the [container] background.
 *
 * @param values the chips to render (deduplicated/styled by value).
 * @param container chip background color. Defaults to a subtle
 *   `onSurface.copy(alpha = 0.18f)`; pass a different color (e.g.
 *   `primaryContainer.copy(alpha = 0.5f)` for studios) to distinguish a group.
 */
@Composable
fun ChipRow(
    values: List<String>,
    container: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(values, key = { it }) { value ->
            Box(
                modifier = Modifier
                    .clip(ShapeCache.smooth16)
                    .background(container)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f))
            }
        }
    }
}
