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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.ExpressiveToolbarIconButton
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.components.rememberStableCallback
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.composables.icons.tabler.Tabler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.livetv.generated.resources.Res
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_no_channels_available
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_tab_channels
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_watch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    onChannelClick: (String, String) -> Unit,
    onPlayChannel: (String, String) -> Unit,
    viewModel: ChannelsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current
        .collectAsStateWithLifecycle()
    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = uiState.isLoading,
        hasError = uiState.error != null,
        networkStatus = networkStatus,
    )

    val backgroundColor = rememberScreenBackgroundColor()

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val bottomPad = adaptiveInfo.bottomPadding(isTv)

    val focusRequester = remember { FocusRequester() }
    val channelsNotEmpty = uiState.channels.isNotEmpty()
    val nowPlayingChannelId by viewModel.nowPlayingChannelId.collectAsStateWithLifecycle()
    val favoriteChannelIds by viewModel.favoriteChannelIds.collectAsStateWithLifecycle()
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = uiState.channels.size,
        tag = "channels_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.livetv_tab_channels),
        backgroundColor = backgroundColor,
        topBarStyle = com.raulshma.jellyplay.core.ui.components.TopBarStyle.None,
        actions = {
            com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(start = 12.dp),
            )
        },
    ) {
        if (uiState.error != null && uiState.channels.isEmpty()) {
            ErrorScreen(
                message = uiState.error!!,
                onRetry = { viewModel.loadChannels() },
            )
        } else if (uiState.channels.isEmpty() && !uiState.isLoading) {
            ScreenEmptyState(
                icon = Tabler.Outline.DeviceTv,
                title = stringResource(Res.string.livetv_no_channels_available),
            )
        } else {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.loadChannels() },
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusRestorer()
                        .focusRequester(focusRequester),
                    contentPadding = PaddingValues(
                        start = contentPad,
                        end = contentPad,
                        top = 8.dp,
                        bottom = bottomPad,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    items(
                        items = uiState.channels,
                        key = { it.id },
                        contentType = { "channel" },
                    ) { channel ->
                        // Memoized per-item derivations + click lambdas keep
                        // ChannelCard skippable across uiState emissions
                        // (favorite toggle, refresh) — the LibraryScreen grid
                        // pattern.
                        val imageUrl = remember(channel.id, channel.imageTag) {
                            viewModel.getImageUrl(channel.id, channel.imageTag)
                        }
                        val memoizedClick = rememberStableCallback {
                            onChannelClick(channel.id, channel.name)
                        }
                        val memoizedPlay = rememberStableCallback {
                            onPlayChannel(channel.id, channel.name)
                        }
                        val memoizedFavoriteToggle = rememberStableCallback {
                            viewModel.toggleFavorite(channel.id)
                        }
                        ChannelCard(
                            channel = channel,
                            imageUrl = imageUrl,
                            isNowPlaying = channel.id == nowPlayingChannelId,
                            isFavorite = channel.id in favoriteChannelIds,
                            onClick = memoizedClick,
                            onPlay = memoizedPlay,
                            onFavoriteToggle = memoizedFavoriteToggle,
                        )
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
    isNowPlaying: Boolean = false,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onFavoriteToggle: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(
                if (isNowPlaying) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                }
            )
            .focusIndicator()
            .clickable(onClick = onClick)
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // ── Channel info ──
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isNowPlaying) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            val currentProgram = channel.currentProgram
            if (currentProgram != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = currentProgram.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // ── Favorite toggle ──
        IconButton(
            onClick = onFavoriteToggle,
            modifier = Modifier
                .focusIndicator(CircleShape),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
        ) {
            Icon(
                imageVector = if (isFavorite) Tabler.Filled.Star else Tabler.Outline.Star,
                contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                modifier = Modifier.size(22.dp),
            )
        }

        // ── Play button (glass style) ──
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                .focusIndicator(CircleShape)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = stringResource(Res.string.livetv_watch),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

