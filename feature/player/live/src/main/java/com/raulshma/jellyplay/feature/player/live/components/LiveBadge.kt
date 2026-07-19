package com.raulshma.jellyplay.feature.player.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Red "LIVE" pill badge shown in the live player's top bar to indicate the
 * stream is a live broadcast.
 */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = "LIVE",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Red)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
