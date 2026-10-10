package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_aspect_auto
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_aspect_detected
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_aspect_ratio

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioSheet(
    currentRatio: AspectRatio,
    detectedRatio: AspectRatio?,
    onSelect: (AspectRatio) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerSheetScaffold(
        title = stringResource(Res.string.player_video_aspect_ratio),
        icon = Tabler.Outline.AspectRatio,
        onDismiss = onDismiss,
        preContent = {
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
        },
    ) { isTv, focusRequester ->
        if (isTv) {
            LazyColumn(modifier = Modifier.verticalWrapAround()) {
                items(AspectRatio.entries, key = { it.name }) { ratio ->
                    val isSelected = ratio == currentRatio
                    val isFirstOrSelected = isSelected || (currentRatio !in AspectRatio.entries && ratio == AspectRatio.AUTO)
                    TvFocusableOptionRow(
                        selected = isSelected,
                        onClick = { onSelect(ratio); onDismiss() },
                        focusRequesterModifier = Modifier.ifElse(isFirstOrSelected, Modifier.focusRequester(focusRequester)),
                        trailingCheck = true,
                    ) {
                        val displayText = if (ratio == AspectRatio.AUTO && detectedRatio != null) {
                            stringResource(Res.string.player_video_aspect_auto, detectedRatio.displayName)
                        } else {
                            ratio.displayName
                        }
                        TvOptionRowLabel(text = displayText, selected = isSelected)
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
