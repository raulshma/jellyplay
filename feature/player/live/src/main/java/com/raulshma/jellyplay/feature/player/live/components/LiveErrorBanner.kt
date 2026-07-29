package com.raulshma.jellyplay.feature.player.live.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator

/**
 * Full-screen overlay shown while a live stream buffers or after it fails.
 *
 * On error it offers a "Retry" (same delivery method) plus explicit delivery-
 * method buttons (Auto / Direct Stream / Transcode) so the user can recover
 * from a transcode-path failure (e.g. server 500) or a direct-stream failure
 * (codec / tuner probe) without leaving the player. Mirrors the VOD
 * [com.raulshma.jellyplay.feature.player.video.components.PlaybackErrorOverlay]
 * recovery affordances, adapted to live delivery options.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveErrorBanner(
    isBuffering: Boolean,
    errorMessage: String?,
    currentOption: LiveStreamOption,
    onRetry: () -> Unit,
    onRetryWithOption: (LiveStreamOption) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            isBuffering -> JellyPlayLoadingIndicator(color = Color.White)
            errorMessage != null -> Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Playback Error",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    errorMessage,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Retry") }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Try a different delivery method",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiveStreamOption.entries.forEach { option ->
                        val selected = option == currentOption
                        DeliveryChip(
                            text = option.displayName,
                            selected = selected,
                            onClick = { onRetryWithOption(option) },
                        )
                    }
                }

                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
        }
    }
}

@Composable
private fun DeliveryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
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
}
