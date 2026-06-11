package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.feature.player.video.TrackOption
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
) {
    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (onReset != null) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                onReset()
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Tabler.Outline.Rotate,
                            contentDescription = "Reset to Auto",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Reset to Auto",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                itemsIndexed(tracks, key = { _, track -> track.index }, contentType = { _, _ -> "track" }) { index, track ->
                    val isLast = index == tracks.lastIndex
                    val shape = when {
                        tracks.size == 1 -> ShapeCache.smooth16
                        index == 0 -> com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(0, tracks.size)
                        isLast -> com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(index, tracks.size)
                        else -> ShapeCache.smooth8
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(shape)
                            .background(
                                if (track.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else Color.White.copy(alpha = 0.04f)
                            )
                            .tvFocusable()
                            .clickable {
                                onSelect(track)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            track.label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (track.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (track.isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
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
            }
        }
    }
}
