package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.player.SpeedSlider
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check

private val SPEED_OPTIONS = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeedPickerSheet(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
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
        ) {
            Text(
                "Playback Speed",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(20.dp))

            if (isTv) {
                LazyColumn(modifier = Modifier.verticalWrapAround()) {
                    items(
                        count = SPEED_OPTIONS.size,
                        key = { SPEED_OPTIONS[it] },
                        contentType = { "speed" },
                    ) { i ->
                        val speed = SPEED_OPTIONS[i]
                        val isSelected = speed == currentSpeed
                        val isFirstOrSelected = isSelected || (SPEED_OPTIONS.none { it == currentSpeed } && speed == 1.0f)
                        val speedFocusState = rememberTvFocusState(focusedScale = 1.02f)
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
                                .then(speedFocusState.focusModifier)
                                .ifElse(isFirstOrSelected, Modifier.focusRequester(focusRequester))
                                .tvFocusIndicator(speedFocusState, shape)
                                .clickable { onSelect(speed); onDismiss() }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (speed == 1.0f) "Normal (1x)" else "${speed}x",
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
                Spacer(Modifier.height(16.dp))
                SpeedSlider(
                    currentSpeed = currentSpeed,
                    onSelect = onSelect,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SPEED_OPTIONS.forEach { speed ->
                        val isSelected = speed == currentSpeed
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelect(speed); onDismiss() },
                            label = {
                                Text(
                                    if (speed == 1.0f) "1x" else "${speed}x",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
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
                Spacer(Modifier.height(20.dp))
                SpeedSlider(
                    currentSpeed = currentSpeed,
                    onSelect = onSelect,
                )
            }
        }
    }
}
