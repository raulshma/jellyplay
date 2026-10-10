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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmState
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.components.SheetSection
import com.raulshma.jellyplay.core.ui.components.formatDurationMs
import com.raulshma.jellyplay.core.ui.components.rememberConfirmState
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.player.video.rememberIs24HourFormat
import kotlinx.coroutines.delay
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cancel
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cancel_sleep_timer
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cancel_sleep_timer_confirm_message
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cancel_sleep_timer_confirm_title
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cancel_timer
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_custom_duration
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_custom_ellipsis
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_duration
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_end_of_episode
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_enter_1_600
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_keep_sleep_timer
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_minutes
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_sleep_timer
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_sleep_timer_stops_at
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_start

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
    // cancelling a timer with more than five minutes left asks for
    // confirmation (a fat-finger cancel of a long timer is the expensive
    // accident); the deferred lambda mirrors the repo's ConfirmState idiom.
    val cancelConfirm = rememberConfirmState()
    PickerSheetScaffold(
        title = stringResource(Res.string.player_video_sleep_timer),
        icon = Tabler.Outline.MoonStars,
        onDismiss = onDismiss,
        // The timer state flips while the sheet is open (start/cancel) — the
        // focus grab re-arms on those transitions, as before.
        focusEffectKeys = listOf(isActive),
    ) { isTv, focusRequester ->
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
                                .clickable {
                                    requestCancel(cancelConfirm, remainingMs) {
                                        onCancel(); onDismiss()
                                    }
                                }
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
                                Column {
                                    Text(
                                        text = stringResource(
                                            Res.string.player_video_cancel_sleep_timer,
                                            if (isEndOfEpisodeMode) stringResource(Res.string.player_video_end_of_episode) else formatDurationMs(remainingMs),
                                        ),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    // the projected stop wall-clock time under
                                    // the countdown (end-of-episode arms nothing
                                    // to project).
                                    if (!isEndOfEpisodeMode) {
                                        val stopsAt = rememberStopsAtTime(remainingMs)
                                        if (stopsAt != null) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = stringResource(Res.string.player_video_sleep_timer_stops_at, stopsAt),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                item {
                    val isEndSelected = isActive && isEndOfEpisodeMode
                    TvFocusableOptionRow(
                        selected = isEndSelected,
                        onClick = { onSelectEndOfEpisode(); onDismiss() },
                        focusRequesterModifier = Modifier.ifElse(!isActive, Modifier.focusRequester(focusRequester)),
                        trailingCheck = true,
                    ) {
                        // The sleep-timer rows emphasize with Bold, not the
                        // chassis label's SemiBold default.
                        TvOptionRowLabel(
                            text = stringResource(Res.string.player_video_end_of_episode),
                            selected = isEndSelected,
                            selectedFontWeight = FontWeight.Bold,
                        )
                    }
                }

                items(PRESET_DURATIONS, key = { it }) { durationMs ->
                    val isSelected = !isEndOfEpisodeMode && isActive && remainingMs == durationMs
                    val isLastUsed = !isActive && durationMs == lastUsedDurationMs
                    val isTarget = isSelected || isLastUsed
                    TvFocusableOptionRow(
                        selected = isTarget,
                        onClick = { onSelectDuration(durationMs); onDismiss() },
                        trailingCheck = true,
                    ) {
                        TvOptionRowLabel(
                            text = formatDurationLabel(durationMs),
                            selected = isTarget,
                            selectedFontWeight = FontWeight.Bold,
                        )
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
                        cancelConfirm = cancelConfirm,
                        onCancel = onCancel,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    stringResource(Res.string.player_video_duration),
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
                        label = stringResource(Res.string.player_video_custom_ellipsis),
                        isSelected = false,
                        onClick = { showCustomDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))

                    val isEndSelected = isActive && isEndOfEpisodeMode
                    SleepTimerChip(
                        label = stringResource(Res.string.player_video_end_of_episode),
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

    // the cancel-confirmation dialog — cancel keeps the timer running,
    // confirm tears it down. Each click site keeps its own dismiss behavior
    // (TV dismisses the sheet on cancel, touch keeps it open), so the
    // confirmed lambda is the very lambda the immediate path ran.
    cancelConfirm.ConfirmDialog(
        title = stringResource(Res.string.player_video_cancel_sleep_timer_confirm_title),
        message = stringResource(Res.string.player_video_cancel_sleep_timer_confirm_message, formatDurationMs(remainingMs)),
        confirmText = stringResource(Res.string.player_video_cancel_timer),
        dismissText = stringResource(Res.string.player_video_keep_sleep_timer),
        icon = Tabler.Outline.MoonStars,
        tone = ConfirmTone.WARNING,
    )
}

/**
 * Run [onCancel] immediately when the remaining time is at/below the
 * confirm threshold (or nothing is left — the end-of-episode arm), else raise
 * the confirmation dialog behind [confirm].
 */
private fun requestCancel(
    confirm: ConfirmState,
    remainingMs: Long,
    onCancel: () -> Unit,
) {
    if (SleepTimerSheetPolicy.requiresCancelConfirmation(remainingMs)) {
        confirm.request(onCancel)
    } else {
        onCancel()
    }
}

/**
 * The projected stop wall-clock label ("23:45" / "11:45 PM"), or null when
 * there is nothing to project (idle / end-of-episode). Re-anchored on every
 * authoritative [remainingMs] emission (the same wall-clock re-sync model the
 * countdown itself rides), deduped to minute boundaries so the label holds
 * steady between them.
 */
@Composable
private fun rememberStopsAtTime(remainingMs: Long): String? {
    if (remainingMs <= 0L) return null
    val is24Hour = rememberIs24HourFormat()
    val stopEpochMs = remember(remainingMs) {
        SleepTimerSheetPolicy.projectedStopEpochMs(System.currentTimeMillis(), remainingMs)
    }
    return remember(stopEpochMs / 60_000L) {
        SleepTimerSheetPolicy.formatStopTime(stopEpochMs, is24Hour)
    }
}

@Composable
private fun ActiveTimerSection(
    isEndOfEpisodeMode: Boolean,
    remainingMs: Long,
    originalMs: Long,
    cancelConfirm: ConfirmState,
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
                Column {
                    Text(
                        text = if (isEndOfEpisodeMode) stringResource(Res.string.player_video_end_of_episode) else formatDurationMs(displayRemaining),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // the projected stop wall-clock time under the
                    // countdown (end-of-episode arms nothing to project).
                    if (!isEndOfEpisodeMode) {
                        val stopsAt = rememberStopsAtTime(displayRemaining)
                        if (stopsAt != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(Res.string.player_video_sleep_timer_stops_at, stopsAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(Res.string.player_video_cancel),
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    requestCancel(cancelConfirm, displayRemaining) { onCancel() }
                },
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

/**
 * The sleep-timer chip label: whole-hour multiples render as bare hours
 * (60m → `1h`, 120m → `2h`), every other duration stays in plain minutes
 * (90m stays `90m`). Deliberately NOT core/ui's
 * [formatDurationFromMinutes] — that renders `1h 30m` for the 90-minute
 * preset, which the compact chip row was never sized for; do not unify
 * blindly.
 */
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
    ImeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.player_video_custom_duration)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text(stringResource(Res.string.player_video_minutes)) },
                singleLine = true,
                isError = minutesText.isNotEmpty() && !isValid,
                supportingText = if (minutesText.isNotEmpty() && !isValid) {
                    { Text(stringResource(Res.string.player_video_enter_1_600)) }
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
            ) { Text(stringResource(Res.string.player_video_start)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(Res.string.player_video_cancel)) }
        },
    )
}
