package com.raulshma.jellyplay.feature.livetv.channels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.image.MediaImage

@OptIn(ExperimentalMaterial3Api::class)
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

    val backgroundColor = lerp(
        MaterialTheme.colorScheme.background,
        Color.Black,
        0.70f,
    )

    var headerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { headerVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        if (viewModel.error != null && viewModel.channels.isEmpty()) {
            ErrorScreen(
                message = viewModel.error!!,
                onRetry = { viewModel.loadChannels() },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // ═══════════════════════════════════════════════════════════════
                // ── Header Section (cinematic dark, white-on-dark text)
                // ═══════════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    backgroundColor.copy(alpha = 0.95f),
                                    backgroundColor,
                                ),
                            )
                        )
                        .statusBarsPadding()
                        .padding(top = 16.dp),
                ) {
                    // ── Title + action row ──
                    AnimatedVisibility(
                        visible = headerVisible,
                        enter = fadeIn(tween(500)) + slideInVertically(
                            tween(500),
                            initialOffsetY = { -40 },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Live TV",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = Color.White,
                                )
                                com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                                    status = headerStatus,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GlassIconButton(
                                    onClick = onDvrClick,
                                    icon = Icons.Default.FiberManualRecord,
                                    contentDescription = "Recordings",
                                )
                                GlassIconButton(
                                    onClick = onGuideClick,
                                    icon = Icons.Default.CalendarViewWeek,
                                    contentDescription = "Program Guide",
                                )
                            }
                        }
                    }

                    // ── Channel count ──
                    AnimatedVisibility(
                        visible = headerVisible && viewModel.channels.isNotEmpty(),
                        enter = fadeIn(tween(400, delayMillis = 200)),
                    ) {
                        Text(
                            text = "${viewModel.channels.size} channels",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(
                                horizontal = 24.dp,
                                vertical = 8.dp,
                            ),
                        )
                    }
                }

                // ═══════════════════════════════════════════════════════════════
                // ── Channel List
                // ═══════════════════════════════════════════════════════════════
                if (viewModel.channels.isEmpty() && !viewModel.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.LiveTv,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White.copy(alpha = 0.3f),
                            )
                            Text(
                                text = "No channels available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 100.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Channel icon ──
        Box(
            modifier = Modifier
                .size(64.dp, 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
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
                    Icons.Default.LiveTv,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White.copy(alpha = 0.5f),
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
                color = Color.White,
            )
            val currentProgram = channel.currentProgram
            if (currentProgram != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = currentProgram.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
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
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Watch",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ── Subcomponents (matching LibraryScreen / MediaDetailScreen design language)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    highlighted: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = if (highlighted) 0.18f else 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = if (highlighted) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
        )
    }
}
