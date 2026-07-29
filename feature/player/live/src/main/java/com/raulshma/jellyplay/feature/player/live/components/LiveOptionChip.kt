package com.raulshma.jellyplay.feature.player.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

/**
 * Visual style for a [LiveOptionChip].
 *  - [OVERLAY] — white-on-video pill used on the full-screen error banner,
 *    where text must read against the dimmed player surface.
 *  - [SURFACE] — Material3 [FilterChip] styling used inside the bottom sheet,
 *    where it sits on the theme surface.
 */
enum class LiveOptionChipStyle { OVERLAY, SURFACE }

/**
 * A single delivery-method selection pill for [com.raulshma.jellyplay.core.model.LiveStreamOption].
 * Renders [text] with a bold-when-[selected] weight; visual treatment follows [style].
 *
 * Extracted so the error banner and the bottom sheet share one chip definition
 * instead of a hand-rolled `DeliveryChip` and a parallel `FilterChip` call.
 */
@Composable
fun LiveOptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    style: LiveOptionChipStyle,
    modifier: Modifier = Modifier,
) {
    when (style) {
        LiveOptionChipStyle.OVERLAY -> Row(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else Color.White.copy(alpha = 0.15f)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                color = Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LiveOptionChipStyle.SURFACE -> FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    text,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            },
            shape = ShapeCache.smoothPill,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = Color.Transparent,
                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                enabled = true,
                selected = selected,
            ),
            modifier = modifier,
        )
    }
}
