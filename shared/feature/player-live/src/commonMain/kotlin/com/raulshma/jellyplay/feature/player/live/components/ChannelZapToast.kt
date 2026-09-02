package com.raulshma.jellyplay.feature.player.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_channel_number
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_logo_cd

/**
 * Transient toast shown for ~3s after a channel change. Renders the channel
 * logo, "Ch. <n>", name and the current program subtitle. Animated by the
 * caller via [androidx.compose.animation.AnimatedVisibility].
 */
@Composable
fun ChannelZapToast(
    channel: LiveTvChannel,
    currentProgram: LiveTvProgram?,
    logoUrl: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!logoUrl.isNullOrBlank()) {
            MediaImage(
                url = logoUrl,
                contentDescription = stringResource(Res.string.live_logo_cd, channel.name),
                modifier = Modifier.size(48.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                channel.number?.let {
                    Text(
                        text = stringResource(Res.string.live_channel_number, it),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            currentProgram?.let {
                Text(
                    text = it.name + (it.episodeTitle?.let { e -> " — $e" } ?: ""),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
