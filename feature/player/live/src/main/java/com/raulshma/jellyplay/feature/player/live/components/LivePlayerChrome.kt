package com.raulshma.jellyplay.feature.player.live.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronUp
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerSkipBack
import com.composables.icons.tabler.outline.PlayerTrackNext
import com.composables.icons.tabler.outline.PlayerTrackPrev
import com.composables.icons.tabler.outline.Volume
import com.composables.icons.tabler.outline.VolumeOff
import com.composables.icons.tabler.outline.Video
import com.composables.icons.tabler.outline.Menu2
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.ui.components.rememberWallClockTimeString
import com.raulshma.jellyplay.feature.player.live.R
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.player.PlayerIconButton
import com.raulshma.jellyplay.core.ui.player.playerBottomScrim
import com.raulshma.jellyplay.core.ui.player.playerTopScrim

/**
 * Top bar of the live player chrome: back, channel logo, "Ch. <n> — <name>",
 * LIVE pill, wall clock, mute. Does not include now/next program text —
 * that lives in the transient [ChannelZapToast].
 */
@Composable
fun LivePlayerTopBar(
    channel: LiveTvChannel,
    logoUrl: String?,
    isMuted: Boolean,
    playMethod: LivePlayMethod?,
    isRecording: Boolean,
    canRecord: Boolean,
    onBack: () -> Unit,
    onMute: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .playerTopScrim()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIconButton(
            icon = Tabler.Outline.ArrowLeft,
            contentDescription = stringResource(R.string.live_back),
            onClick = onBack,
        )
        Spacer(Modifier.width(8.dp))
        if (!logoUrl.isNullOrBlank()) {
            MediaImage(
                url = logoUrl,
                contentDescription = stringResource(R.string.live_logo_cd, channel.name),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.number?.let {
                    stringResource(R.string.live_channel_title_with_number, it, channel.name)
                } ?: channel.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LiveBadge()
        if (playMethod != null) {
            Spacer(Modifier.width(4.dp))
            LivePlayMethodBadge(method = playMethod)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = rememberWallClockTimeString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(end = 12.dp),
        )
        if (canRecord) {
            // Record affordance. Gated on a current program —
            // pure-live channels with no EPG have nothing to record. Tinted red
            // while a timer is already set so the active-recording state reads
            // at a glance. Opens the record sheet (Record / Record Series /
            // Cancel), which mirrors the browse-tab RecordSplitButton.
            PlayerIconButton(
                icon = Tabler.Outline.Video,
                contentDescription = stringResource(R.string.live_record),
                onClick = onRecord,
                tint = if (isRecording) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
            )
        }
        PlayerIconButton(
            icon = if (isMuted) Tabler.Outline.VolumeOff else Tabler.Outline.Volume,
            contentDescription = if (isMuted) stringResource(R.string.live_unmute) else stringResource(R.string.live_mute),
            onClick = onMute,
        )
    }
}

/**
 * Bottom bar of the live player chrome: seek bar (or LIVE label), transport
 * row (seek back, play/pause, seek forward, channel up/down, more).
 *
 * Seek-back/forward buttons are disabled when [canSeek] is false (pure-live
 * or at the live edge).
 */
@Composable
fun LivePlayerBottomBar(
    isPlaying: Boolean,
    canSeek: Boolean,
    isAtLiveEdge: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPlayFromStart: () -> Unit,
    onChannelUp: () -> Unit,
    onChannelDown: () -> Unit,
    onMore: () -> Unit,
    onChannels: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekToLiveEdge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .playerBottomScrim()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (durationMs > 0L) {
            LiveSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                isAtLiveEdge = isAtLiveEdge,
                onSeek = onSeek,
                onSeekToLiveEdge = onSeekToLiveEdge,
            )
        } else if (!isAtLiveEdge) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                androidx.compose.material3.Button(onClick = onSeekToLiveEdge) {
                    Text(stringResource(R.string.live_go_to_live))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                LiveBadge()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play from start: one-tap restart of the in-progress
            // program. Only enabled when a DVR window exists (`canSeek`); pure-live
            // streams have no seekable start, so the action is hidden-by-disable
            // rather than removed (keeps the transport layout stable).
            PlayerIconButton(
                icon = Tabler.Outline.PlayerSkipBack,
                contentDescription = stringResource(R.string.live_play_from_start),
                onClick = onPlayFromStart,
                enabled = canSeek,
            )
            PlayerIconButton(
                icon = Tabler.Outline.PlayerTrackPrev,
                contentDescription = stringResource(R.string.live_seek_back),
                onClick = onSeekBack,
                enabled = canSeek,
            )
            PlayerIconButton(
                icon = if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                contentDescription = if (isPlaying) stringResource(R.string.live_pause)
                else stringResource(R.string.live_play),
                onClick = onPlayPause,
            )
            PlayerIconButton(
                icon = Tabler.Outline.PlayerTrackNext,
                contentDescription = stringResource(R.string.live_seek_forward),
                onClick = onSeekForward,
                enabled = canSeek,
            )
            Spacer(Modifier.width(16.dp))
            PlayerIconButton(
                icon = Tabler.Outline.ChevronUp,
                contentDescription = stringResource(R.string.live_channel_up),
                onClick = onChannelUp,
            )
            PlayerIconButton(
                icon = Tabler.Outline.ChevronDown,
                contentDescription = stringResource(R.string.live_channel_down),
                onClick = onChannelDown,
            )
            Spacer(Modifier.width(16.dp))
            PlayerIconButton(
                icon = Tabler.Outline.Menu2,
                contentDescription = stringResource(R.string.live_channels),
                onClick = onChannels,
            )
            PlayerIconButton(
                icon = Tabler.Outline.DotsVertical,
                contentDescription = stringResource(R.string.live_stream_options),
                onClick = onMore,
            )
        }
    }
}
