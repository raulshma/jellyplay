package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_buffering
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ignore_wait
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ignore_wait_hint
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_leave_group
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_participants
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_pause
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_play
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_repeat_all
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_repeat_none
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_repeat_one
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_shuffle
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_sorted
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stop
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_synced
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_syncplay















import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.SyncStatusColors
import com.raulshma.jellyplay.core.designsystem.theme.isLightColor
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.SheetSection
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPlayPlayerSheet(
    groupName: String,
    participantCount: Int,
    isSynced: Boolean,
    isPlaying: Boolean,
    ignoreWait: Boolean,
    repeatMode: SyncPlayRepeatMode,
    shuffleMode: SyncPlayShuffleMode,
    onRepeatModeChange: (SyncPlayRepeatMode) -> Unit,
    onShuffleModeChange: (SyncPlayShuffleMode) -> Unit,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onLeave: () -> Unit,
    onIgnoreWaitChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            focusRequester.tryRequestFocus("sheet")
        }
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.player_video_syncplay),
                icon = Tabler.Outline.Users,
            )

            val statusColor = if (isSynced) SyncStatusColors.synced else SyncStatusColors.else_
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp),
                shape = ShapeCache.smooth16,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.2f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusColor,
                        modifier = Modifier.size(12.dp),
                    ) {}
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                            Text(
                                groupName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Tabler.Outline.Users,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                pluralStringResource(Res.plurals.player_video_participants, participantCount, participantCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // The status color (green/amber) is a fixed light value, so using it for
                    // both the faint container tint and the label puts light text on a near-light
                    // background — low contrast in dark mode and failing in light mode. Derive the
                    // label color from the effective composited background instead.
                    val pillBackground = statusColor.copy(alpha = 0.15f)
                        .compositeOver(MaterialTheme.colorScheme.surface)
                    val statusTextColor = remember(pillBackground) {
                        if (isLightColor(pillBackground)) Color.Black else Color.White
                    }
                    Surface(
                        shape = ShapeCache.smoothPill,
                        color = pillBackground,
                    ) {
                        Text(
                            if (isSynced) stringResource(Res.string.player_video_synced) else stringResource(Res.string.player_video_buffering),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = statusTextColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            SheetSection(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val toggleFocus = rememberTvFocusState()
                    FilledTonalButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .weight(1f)
                            .ifElse(isTv, Modifier.focusRequester(focusRequester))
                            .then(toggleFocus.focusModifier)
                            .tvFocusIndicator(toggleFocus, ShapeCache.smoothPill),
                        shape = ShapeCache.smoothPill,
                    ) {
                        Icon(
                            if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPlaying) stringResource(Res.string.player_video_pause) else stringResource(Res.string.player_video_play))
                    }
                    val stopFocus = rememberTvFocusState()
                    FilledTonalButton(
                        onClick = onStop,
                        modifier = Modifier
                            .then(stopFocus.focusModifier)
                            .tvFocusIndicator(stopFocus, ShapeCache.smoothPill),
                        shape = ShapeCache.smoothPill,
                    ) {
                        Icon(Tabler.Outline.PlayerStop, contentDescription = stringResource(Res.string.player_video_stop), modifier = Modifier.size(18.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    var repeatExpanded by remember { mutableStateOf(false) }
                    val repeatFocus = rememberTvFocusState()
                    Box(modifier = Modifier.weight(1f)) {
                        FilledTonalButton(
                            onClick = {
                                if (isTv) {
                                    val nextMode = when (repeatMode) {
                                        SyncPlayRepeatMode.REPEAT_NONE -> SyncPlayRepeatMode.REPEAT_ONE
                                        SyncPlayRepeatMode.REPEAT_ONE -> SyncPlayRepeatMode.REPEAT_ALL
                                        SyncPlayRepeatMode.REPEAT_ALL -> SyncPlayRepeatMode.REPEAT_NONE
                                    }
                                    onRepeatModeChange(nextMode)
                                } else {
                                    repeatExpanded = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(repeatFocus.focusModifier)
                                .tvFocusIndicator(repeatFocus, ShapeCache.smoothPill),
                            shape = ShapeCache.smoothPill,
                        ) {
                            Icon(Tabler.Outline.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            val label = when (repeatMode) {
                                SyncPlayRepeatMode.REPEAT_ONE -> stringResource(Res.string.player_video_repeat_one)
                                SyncPlayRepeatMode.REPEAT_ALL -> stringResource(Res.string.player_video_repeat_all)
                                SyncPlayRepeatMode.REPEAT_NONE -> stringResource(Res.string.player_video_repeat_none)
                            }
                            Text(label)
                        }
                        if (!isTv) {
                            DropdownMenu(
                                expanded = repeatExpanded,
                                onDismissRequest = { repeatExpanded = false },
                            ) {
                                SyncPlayRepeatMode.entries.forEach { mode ->
                                    val label = when (mode) {
                                        SyncPlayRepeatMode.REPEAT_ONE -> stringResource(Res.string.player_video_repeat_one)
                                        SyncPlayRepeatMode.REPEAT_ALL -> stringResource(Res.string.player_video_repeat_all)
                                        SyncPlayRepeatMode.REPEAT_NONE -> stringResource(Res.string.player_video_repeat_none)
                                    }
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onRepeatModeChange(mode)
                                            repeatExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    var shuffleExpanded by remember { mutableStateOf(false) }
                    val shuffleFocus = rememberTvFocusState()
                    Box(modifier = Modifier.weight(1f)) {
                        FilledTonalButton(
                            onClick = {
                                if (isTv) {
                                    val nextMode = when (shuffleMode) {
                                        SyncPlayShuffleMode.SORTED -> SyncPlayShuffleMode.SHUFFLE
                                        SyncPlayShuffleMode.SHUFFLE -> SyncPlayShuffleMode.SORTED
                                    }
                                    onShuffleModeChange(nextMode)
                                } else {
                                    shuffleExpanded = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(shuffleFocus.focusModifier)
                                .tvFocusIndicator(shuffleFocus, ShapeCache.smoothPill),
                            shape = ShapeCache.smoothPill,
                        ) {
                            Icon(Tabler.Outline.ArrowsShuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            val label = when (shuffleMode) {
                                SyncPlayShuffleMode.SHUFFLE -> stringResource(Res.string.player_video_shuffle)
                                SyncPlayShuffleMode.SORTED -> stringResource(Res.string.player_video_sorted)
                            }
                            Text(label)
                        }
                        if (!isTv) {
                            DropdownMenu(
                                expanded = shuffleExpanded,
                                onDismissRequest = { shuffleExpanded = false },
                            ) {
                                SyncPlayShuffleMode.entries.forEach { mode ->
                                    val label = when (mode) {
                                        SyncPlayShuffleMode.SHUFFLE -> stringResource(Res.string.player_video_shuffle)
                                        SyncPlayShuffleMode.SORTED -> stringResource(Res.string.player_video_sorted)
                                    }
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onShuffleModeChange(mode)
                                            shuffleExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val ignoreWaitFocus = rememberTvFocusState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(ShapeCache.smooth12)
                    .then(ignoreWaitFocus.focusModifier)
                    .tvFocusIndicator(ignoreWaitFocus, ShapeCache.smooth12)
                    .clickable { onIgnoreWaitChange(!ignoreWait) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        stringResource(Res.string.player_video_ignore_wait),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(Res.string.player_video_ignore_wait_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ignoreWait,
                    onCheckedChange = null,
                )
            }

            val leaveFocus = rememberTvFocusState()
            FilledTonalButton(
                onClick = onLeave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .then(leaveFocus.focusModifier)
                    .tvFocusIndicator(leaveFocus, ShapeCache.smoothPill),
                shape = ShapeCache.smoothPill,
            ) {
                Icon(Tabler.Outline.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.player_video_leave_group))
            }
        }
    }
}
