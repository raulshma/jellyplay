package com.raulshma.jellyplay.feature.player.live.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Star
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.player.live.R

/**
 * In-player channel list. Rendered as a TvSafeSheet on TV / ModalBottomSheet
 * on phone. Lists every channel; Select tunes; the favorite star toggles
 * favorite. Initial focus lands on the currently-playing channel.
 *
 * @param currentChannelId id of the channel that's currently playing; its row
 * gets initial focus and a "now playing" indicator.
 * @param favorites set of favorite channel ids (for the star state).
 * @param logoUrlFor resolves the channel logo URL (null when the channel has
 * no PRIMARY image tag).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChannelListSheet(
    channels: List<LiveTvChannel>,
    currentChannelId: String?,
    lastChannelId: String? = null,
    favorites: Set<String>,
    logoUrlFor: (LiveTvChannel) -> String?,
    onChannelSelected: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    TvSafeSheet(
        onDismissRequest = onDismiss,
    ) {
        SheetHeader(
            title = stringResource(R.string.live_channels),
            icon = Tabler.Outline.DeviceTv,
        )
        val listState = rememberLazyListState()
        val currentRequester = remember { FocusRequester() }

        // Pin favorites first (parity with the browse channel list).
        // Stable sort keeps server order within each group.
        val sortedChannels = remember(channels, favorites) {
            channels.sortedByDescending { it.id in favorites }
        }

        // Pinned last-watched channel. Skipped when it is the currently-playing
        // channel — no point pinning what's already live.
        val lastWatchedChannel = remember(channels, lastChannelId, currentChannelId) {
            val lw = lastChannelId?.let { id -> channels.firstOrNull { it.id == id } }
            if (lw != null && lw.id != currentChannelId) lw else null
        }
        val displayChannels = remember(sortedChannels, lastWatchedChannel) {
            if (lastWatchedChannel != null) sortedChannels.filter { it.id != lastWatchedChannel.id }
            else sortedChannels
        }

        // Scroll the current channel into view and focus its row on open.
        // The pinned last-watched section (header + row) sits above the list,
        // so account for its 2-item height in the target index.
        LaunchedEffect(displayChannels, currentChannelId, lastWatchedChannel) {
            val baseIndex = displayChannels.indexOfFirst { it.id == currentChannelId }.coerceAtLeast(0)
            val headerOffset = if (lastWatchedChannel != null) 2 else 0
            listState.scrollToItem(baseIndex + headerOffset)
            currentRequester.tryRequestFocus()
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val lastWatched = lastWatchedChannel
            if (lastWatched != null) {
                item(key = "last_watched_header") {
                    Text(
                        text = stringResource(R.string.livetv_last_watched_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item(key = "last_watched_${lastWatched.id}") {
                    val rowFocusRequester = remember(lastWatched.id) { FocusRequester() }
                    val onClick = remember(lastWatched.id) {
                        { onChannelSelected(lastWatched.id); onDismiss() }
                    }
                    val onToggleFavorite = remember(lastWatched.id) { { onToggleFavorite(lastWatched.id) } }
                    ChannelRow(
                        channel = lastWatched,
                        logoUrl = logoUrlFor(lastWatched),
                        isCurrent = false,
                        isFavorite = lastWatched.id in favorites,
                        focusRequester = rowFocusRequester,
                        onClick = onClick,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
            items(items = displayChannels, key = { it.id }) { channel ->
                // FocusRequester must always be produced unconditionally so
                // positional memoization holds for the whole items() block;
                // pick the shared requester for the current channel, otherwise
                // a per-channel one remembered by id.
                val rowFocusRequester = remember(channel.id) { FocusRequester() }
                // Memoize per-channel so the click lambdas aren't rebuilt on
                // every recomposition, keeping ChannelRow skippable.
                val onClick = remember(channel.id) {
                    {
                        onChannelSelected(channel.id)
                        onDismiss()
                    }
                }
                val onToggleFavorite = remember(channel.id) { { onToggleFavorite(channel.id) } }
                ChannelRow(
                    channel = channel,
                    logoUrl = logoUrlFor(channel),
                    isCurrent = channel.id == currentChannelId,
                    isFavorite = channel.id in favorites,
                    focusRequester = if (channel.id == currentChannelId) currentRequester
                    else rowFocusRequester,
                    onClick = onClick,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: LiveTvChannel,
    logoUrl: String?,
    isCurrent: Boolean,
    isFavorite: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        channel.number?.let {
            Text(
                text = it,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.size(width = 56.dp, height = 24.dp),
            )
        }
        // Channel logo.
        if (!logoUrl.isNullOrBlank()) {
            MediaImage(
                url = logoUrl,
                contentDescription = stringResource(R.string.live_logo_cd, channel.name),
                blurHash = channel.primaryBlurHash,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            channel.currentProgram?.let { prog ->
                // Program name (+ episode title for series) and an "airing now"
                // rating suffix when available.
                val ratingSuffix = prog.officialRating?.let { " · $it" }.orEmpty()
                Text(
                    text = prog.name + (prog.episodeTitle?.let { e -> " — $e" }.orEmpty()) + ratingSuffix,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = Tabler.Outline.Star,
                contentDescription = if (isFavorite) stringResource(R.string.live_unfavorite)
                else stringResource(R.string.live_favorite),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isCurrent) {
            Text(
                text = stringResource(R.string.live_now_playing_short),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
