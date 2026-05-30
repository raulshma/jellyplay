package com.raulshma.jellyplay.feature.livetv.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun ChannelsScreen(
    onChannelClick: (String, String) -> Unit,
    onGuideClick: () -> Unit,
    onDvrClick: () -> Unit = {},
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current
        .collectAsStateWithLifecycle()
    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = viewModel.isLoading,
        hasError = viewModel.error != null,
        networkStatus = networkStatus,
    )

    val backgroundColor = rememberScreenBackgroundColor()

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val bottomPad = adaptiveInfo.bottomPadding(isTv)

    JellyPlayScreenScaffold(
        title = "Live TV",
        backgroundColor = backgroundColor,
        actions = {
            com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(start = 12.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExpressiveToolbarIconButton(
                    onClick = onDvrClick,
                    icon = Tabler.Outline.Circle,
                    contentDescription = "Recordings",
                )
                ExpressiveToolbarIconButton(
                    onClick = onGuideClick,
                    icon = Tabler.Outline.Calendar,
                    contentDescription = "Program Guide",
                )
            }
        },
    ) {
        if (viewModel.error != null && viewModel.channels.isEmpty()) {
            ErrorScreen(
                message = viewModel.error!!,
                onRetry = { viewModel.loadChannels() },
            )
        } else if (viewModel.channels.isEmpty() && !viewModel.isLoading) {
            ScreenEmptyState(
                icon = Tabler.Outline.DeviceTv,
                title = "No channels available",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = contentPad,
                    end = contentPad,
                    top = 8.dp,
                    bottom = bottomPad,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                items(
                    items = viewModel.channels,
                    key = { it.id },
                    contentType = { "channel" },
                ) { channel ->
                    ChannelCard(
                        channel = channel,
                        imageUrl = viewModel.getImageUrl(channel.id, channel.imageTag),
                        onClick = { onChannelClick(channel.id, channel.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: LiveTvChannel,
    imageUrl: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .tvFocusable().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Channel icon ──
        Box(
            modifier = Modifier
                .size(64.dp, 48.dp)
                .clip(ShapeCache.smooth8)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl.isNotBlank()) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = channel.name,
                    blurHash = channel.primaryBlurHash,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Tabler.Outline.DeviceTv,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // ── Channel info ──
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val currentProgram = channel.currentProgram
            if (currentProgram != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = currentProgram.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val number = channel.number
            if (number != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Ch. $number",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ── Play button (glass style) ──
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                .tvFocusable().clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = "Watch",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ── Subcomponents (matching LibraryScreen / MediaDetailScreen design language)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveToolbarIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    highlighted: Boolean = false,
) {
    val tint = if (highlighted) MaterialTheme.colorScheme.primary
               else MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (highlighted) 0.18f else 0.08f
            ),
        ),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
    }
}
