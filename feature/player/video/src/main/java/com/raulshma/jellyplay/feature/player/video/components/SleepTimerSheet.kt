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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.SheetSection
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import kotlinx.coroutines.delay
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

private val PRESET_DURATIONS = listOf(
    15 * 60 * 1000L,
    30 * 60 * 1000L,
    45 * 60 * 1000L,
    60 * 60 * 1000L,
    90 * 60 * 1000L,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SleepTimerSheet(
    isActive: Boolean,
    isEndOfEpisodeMode: Boolean,
    remainingMs: Long,
    lastUsedDurationMs: Long,
    onSelectDuration: (Long) -> Unit,
    onSelectEndOfEpisode: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv, isActive) {
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
            SheetHeader(
                title = stringResource(R.string.player_video_sleep_timer),
                icon = Tabler.Outline.MoonStars,
            )
            Spacer(Modifier.height(20.dp))

            if (isTv) {
                LazyColumn {
                    if (isActive) {
                        item {
                            val cancelFocusState = rememberTvFocusState(focusedScale = 1.02f)
                            val shape = ShapeCache.smooth8
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), shape)
                                    .then(cancelFocusState.focusModifier)
                                    .focusRequester(focusRequester) // Focus the cancel option by default if active
                                    .tvFocusIndicator(cancelFocusState, shape)
                                    .clickable { onCancel(); onDismiss() }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Tabler.Outline.Stopwatch,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.player_video_cancel_sleep_timer,
                                            if (isEndOfEpisodeMode) stringResource(R.string.player_video_end_of_episode) else formatTime(remainingMs),
                                        ),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    item {
                        val isEndSelected = isActive && isEndOfEpisodeMode
                        val endFocusState = rememberTvFocusState(focusedScale = 1.02f)
                        val shape = ShapeCache.smooth8
                        val requesterModifier = Modifier.ifElse(!isActive, Modifier.focusRequester(focusRequester))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clip(shape)
                                .background(
                                    if (isEndSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                )
                                .then(endFocusState.focusModifier)
                                .then(requesterModifier)
                                .tvFocusIndicator(endFocusState, shape)
                                .clickable { onSelectEndOfEpisode(); onDismiss() }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.player_video_end_of_episode),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isEndSelected) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (isEndSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (isEndSelected) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    items(PRESET_DURATIONS, key = { it }) { durationMs ->
                        val isSelected = !isEndOfEpisodeMode && isActive && remainingMs == durationMs
                        val isLastUsed = !isActive && durationMs == lastUsedDurationMs
                        val isTarget = isSelected || isLastUsed
                        val presetFocusState = rememberTvFocusState(focusedScale = 1.02f)
                        val shape = ShapeCache.smooth8
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clip(shape)
                                .background(
                                    if (isTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                )
                                .then(presetFocusState.focusModifier)
                                .tvFocusIndicator(presetFocusState, shape)
                                .clickable { onSelectDuration(durationMs); onDismiss() }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatDurationLabel(durationMs),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (isTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (isTarget) {
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
                var showCustomDialog by remember { mutableStateOf(false) }
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    if (isActive) {
                        ActiveTimerSection(
                            isEndOfEpisodeMode = isEndOfEpisodeMode,
                            remainingMs = remainingMs,
                            originalMs = lastUsedDurationMs,
                            onCancel = onCancel,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    Text(
                        stringResource(R.string.player_video_duration),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    SheetSection {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PRESET_DURATIONS.forEach { durationMs ->
                                val isSelected = !isEndOfEpisodeMode && isActive && remainingMs == durationMs
                                val isLastUsed = !isActive && durationMs == lastUsedDurationMs
                                SleepTimerChip(
                                    label = formatDurationLabel(durationMs),
                                    isSelected = isSelected || isLastUsed,
                                    onClick = { onSelectDuration(durationMs); onDismiss() },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        SleepTimerChip(
                            label = stringResource(R.string.player_video_custom_ellipsis),
                            isSelected = false,
                            onClick = { showCustomDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(12.dp))

                        val isEndSelected = isActive && isEndOfEpisodeMode
                        SleepTimerChip(
                            label = stringResource(R.string.player_video_end_of_episode),
                            isSelected = isEndSelected,
                            onClick = { onSelectEndOfEpisode(); onDismiss() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (showCustomDialog) {
                    CustomSleepDurationDialog(
                        onConfirm = { minutes ->
                            onSelectDuration(minutes * 60_000L)
                            showCustomDialog = false
                            onDismiss()
                        },
                        onDismiss = { showCustomDialog = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveTimerSection(
    isEndOfEpisodeMode: Boolean,
    remainingMs: Long,
    originalMs: Long,
    onCancel: () -> Unit,
) {
    var displayRemaining by remember(remainingMs) { mutableLongStateOf(remainingMs) }

    LaunchedEffect(remainingMs) {
        displayRemaining = remainingMs
        while (displayRemaining > 0) {
            delay(1000)
            displayRemaining = (displayRemaining - 1000).coerceAtLeast(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                ShapeCache.smoothPill,
            )
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Tabler.Outline.Stopwatch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (isEndOfEpisodeMode) stringResource(R.string.player_video_end_of_episode) else formatTime(displayRemaining),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(R.string.player_video_cancel),
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onCancel() },
            )
        }
        if (!isEndOfEpisodeMode) {
            Spacer(Modifier.height(8.dp))
            val progressFraction = if (originalMs > 0) {
                (displayRemaining.toFloat() / originalMs.toFloat()).coerceIn(0f, 1f)
            } else {
                1f
            }
            JellyPlayLinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SleepTimerChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                ShapeCache.smoothPill,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelected) {
            Icon(
                Tabler.Outline.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(4.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatDurationLabel(ms: Long): String {
    val minutes = ms / (60 * 1000)
    return when {
        minutes % 60L == 0L -> "${minutes / 60}h"
        else -> "${minutes}m"
    }
}

/**
 * Lets the user enter an arbitrary sleep-timer duration in minutes rather
 * than being limited to the 5 fixed presets.
 */
@Composable
private fun CustomSleepDurationDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutesText by remember { mutableStateOf("") }
    val parsed = minutesText.toIntOrNull()
    val isValid = parsed != null && parsed in 1..600
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_video_custom_duration)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text(stringResource(R.string.player_video_minutes)) },
                singleLine = true,
                isError = minutesText.isNotEmpty() && !isValid,
                supportingText = if (minutesText.isNotEmpty() && !isValid) {
                    { Text(stringResource(R.string.player_video_enter_1_600)) }
                } else null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = isValid,
            ) { Text(stringResource(R.string.player_video_start)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_video_cancel)) }
        },
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
