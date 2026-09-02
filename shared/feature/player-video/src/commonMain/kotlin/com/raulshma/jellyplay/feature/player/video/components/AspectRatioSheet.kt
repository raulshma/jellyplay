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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_aspect_auto
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_aspect_detected
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_aspect_ratio



import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioSheet(
    currentRatio: AspectRatio,
    detectedRatio: AspectRatio?,
    onSelect: (AspectRatio) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            focusRequester.tryRequestFocus("sheet")
        }
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.player_video_aspect_ratio),
                icon = Tabler.Outline.AspectRatio,
            )
            if (detectedRatio != null && detectedRatio != AspectRatio.FIT) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.player_video_aspect_detected, detectedRatio.displayName),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            if (isTv) {
                LazyColumn(modifier = Modifier.verticalWrapAround()) {
                    items(AspectRatio.entries, key = { it.name }) { ratio ->
                        val isSelected = ratio == currentRatio
                        val isFirstOrSelected = isSelected || (currentRatio !in AspectRatio.entries && ratio == AspectRatio.AUTO)
                        val ratioFocusState = rememberTvFocusState(focusedScale = 1.02f)
                        val shape = ShapeCache.smooth8
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clip(shape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                )
                                .then(ratioFocusState.focusModifier)
                                .ifElse(isFirstOrSelected, Modifier.focusRequester(focusRequester))
                                .tvFocusIndicator(ratioFocusState, shape)
                                .clickable { onSelect(ratio); onDismiss() }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val displayText = if (ratio == AspectRatio.AUTO && detectedRatio != null) {
                                stringResource(Res.string.player_video_aspect_auto, detectedRatio.displayName)
                            } else {
                                ratio.displayName
                            }
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (isSelected) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AspectRatio.entries.forEach { ratio ->
                        val isSelected = ratio == currentRatio
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onSelect(ratio)
                                onDismiss()
                            },
                            label = {
                                if (ratio == AspectRatio.AUTO && detectedRatio != null) {
                                    Text(stringResource(Res.string.player_video_aspect_auto, detectedRatio.displayName))
                                } else {
                                    Text(
                                        ratio.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = ShapeCache.smoothPill,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.Transparent,
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                enabled = true,
                                selected = isSelected,
                            ),
                        )
                    }
                }
            }
        }
    }
}
