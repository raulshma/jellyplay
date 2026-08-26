package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import com.raulshma.jellyplay.feature.player.video.TrackOption
import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_reset_to_auto
import com.raulshma.jellyplay.feature.player.video.generated.resources.track_badge_default
import com.raulshma.jellyplay.feature.player.video.generated.resources.track_badge_forced
import com.raulshma.jellyplay.feature.player.video.generated.resources.track_badge_sdh




import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackPickerSheet(
    title: String,
    tracks: List<TrackOption>,
    onSelect: (TrackOption) -> Unit,
    onReset: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    footer: @Composable (() -> Unit)? = null,
) {
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv, tracks) {
        if (isTv && tracks.isNotEmpty()) {
            focusRequester.tryRequestFocus("sheet")
        }
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        TrackPickerSection(
            title = title,
            tracks = tracks,
            onSelect = onSelect,
            onReset = onReset,
            onPickDismiss = onDismiss,
            footer = footer,
            focusRequester = focusRequester,
        )
    }
}

/**
 * The body of [TrackPickerSheet] without its own sheet chrome, for embedding
 * inside the unified subtitle hub. [onPickDismiss] is invoked after a track is
 * selected so the host (sheet or hub) can close/navigate as needed; the hub
 * passes a no-op since it owns its own dismissal.
 *
 * Declared as a [ColumnScope] extension so the inner `LazyColumn.weight` (used
 * to bound the scroll height inside the sheet/hub) resolves.
 */
@Composable
internal fun androidx.compose.foundation.layout.ColumnScope.TrackPickerSection(
    title: String,
    tracks: List<TrackOption>,
    onSelect: (TrackOption) -> Unit,
    onReset: (() -> Unit)? = null,
    onPickDismiss: () -> Unit = {},
    footer: @Composable (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        val resetAction = onReset
        SheetHeader(
            title = title,
            icon = Tabler.Outline.Subtitles,
            trailing = if (resetAction != null) {
                {
                    val resetFocusState = rememberTvFocusState(focusedScale = 1.04f)
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .then(resetFocusState.focusModifier)
                            .tvFocusIndicator(resetFocusState, CircleShape)
                            .clickable {
                                resetAction()
                                onPickDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Tabler.Outline.Rotate,
                            contentDescription = stringResource(Res.string.player_reset_to_auto),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(Res.string.player_reset_to_auto),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else null,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.verticalWrapAround().weight(1f, fill = false)) {
            itemsIndexed(tracks, key = { _, track -> track.index }, contentType = { _, _ -> "track" }) { index, track ->
                val isSelected = track.isSelected
                val isFirst = index == 0
                val isTarget = isSelected || (tracks.none { it.isSelected } && isFirst)
                TrackItem(
                    track = track,
                    isLast = index == tracks.lastIndex,
                    itemCount = tracks.size,
                    onSelect = {
                        onSelect(track)
                        onPickDismiss()
                    },
                    modifier = Modifier.ifElse(isTarget, Modifier.focusRequester(focusRequester)),
                )
            }
        }
        if (footer != null) {
            Spacer(Modifier.height(8.dp))
            footer()
        }
    }
}

@Composable
private fun TrackItem(
    track: TrackOption,
    isLast: Boolean,
    itemCount: Int,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = when {
        itemCount == 1 -> ShapeCache.smooth16
        isLast -> com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(if (isLast) itemCount - 1 else 0, itemCount)
        else -> ShapeCache.smooth8
    }
    val focusState = rememberTvFocusState(focusedScale = 1.02f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(shape)
            .background(
                if (track.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
            )
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable { onSelect() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                track.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (track.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (track.isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            if (track.badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    track.badges.forEach { badge ->
                        TrackBadgeChip(badge = badge, isSelected = track.isSelected)
                    }
                }
            }
        }
        if (track.isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun TrackBadgeChip(badge: TrackBadge, isSelected: Boolean) {
    val text = when (badge) {
        TrackBadge.FORCED -> stringResource(Res.string.track_badge_forced)
        TrackBadge.SDH -> stringResource(Res.string.track_badge_sdh)
        TrackBadge.DEFAULT -> stringResource(Res.string.track_badge_default)
    }
    val container = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = contentColor,
        modifier = Modifier
            .clip(ShapeCache.smooth4)
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
