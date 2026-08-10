package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.SheetSection
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Adjustments
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Refresh
import kotlin.math.roundToLong

/**
 * The A/V Sync sheet. Audio-delay-only since the subtitle offset moved to a
 * transparent video overlay (reached from the subtitle hub's Delay tab) so it
 * can be dialed in while watching the live subtitles. A "Reset audio" chip
 * zeroes the audio delay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AVSyncSheet(
    currentAudioDelayMs: Long,
    onAudioDelayChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    audioDelaySupported: Boolean = true,
) {
    var audioDelayMs by remember { mutableLongStateOf(currentAudioDelayMs) }
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            focusRequester.tryRequestFocus("av-sync")
        }
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = stringResource(R.string.player_video_av_sync),
                icon = Tabler.Outline.Adjustments,
            )

            if (audioDelaySupported) {
                SheetSection(modifier = Modifier.padding(horizontal = 16.dp)) {
                    DelayRow(
                        label = stringResource(R.string.player_video_audio_delay),
                        delayMs = audioDelayMs,
                        focusRequester = focusRequester,
                        isTv = isTv,
                        helperText = stringResource(R.string.player_video_av_sync_audio_helper),
                        onValueChange = { audioDelayMs = it; onAudioDelayChange(it) },
                    )
                }

                Spacer(Modifier.height(20.dp))

                val resetFocus = rememberTvFocusState()
                val anyOffset = audioDelayMs != 0L
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .then(resetFocus.focusModifier)
                            .tvFocusIndicator(resetFocus, CircleShape)
                            .clickable(enabled = anyOffset) {
                                audioDelayMs = 0L
                                onAudioDelayChange(0L)
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Tabler.Outline.Refresh,
                                contentDescription = null,
                                tint = if (anyOffset) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.player_video_reset_audio),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (anyOffset) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats a delay/offset in ms as a signed seconds label ("0.0s", "+1.5s",
 * "-0.5s"). Shared by the delay rows and the subtitle-delay overlay so both
 * show the same label for the same value.
 */
internal fun formatDelayLabel(delayMs: Long): String {
    val delaySec = delayMs / 1000.0
    return when {
        delayMs == 0L -> "0.0s"
        delayMs > 0 -> "+${"%.1f".format(delaySec)}s"
        else -> "${"%.1f".format(delaySec)}s"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelayRow(
    label: String,
    delayMs: Long,
    focusRequester: FocusRequester?,
    isTv: Boolean,
    helperText: String,
    onValueChange: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(8.dp))

        val valueLabel = formatDelayLabel(delayMs)
        Text(
            valueLabel,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isTv) {
                DelayStepper(icon = Tabler.Outline.Minus, description = stringResource(R.string.player_video_av_sync_decrease, label)) {
                    val newDelay = (delayMs - 100L).coerceIn(-30000L, 30000L)
                    onValueChange(newDelay)
                }
            }

            TvOrTouchSlider(
                value = delayMs.toFloat(),
                onValueChange = { onValueChange((it / 50f).roundToLong() * 50) },
                valueRange = -30000f..30000f,
                modifier = Modifier.weight(1f),
                isTv = isTv,
                steps = 1199,
                dpadStep = 100f,
                focusRequester = focusRequester,
            )

            if (isTv) {
                DelayStepper(icon = Tabler.Outline.Plus, description = stringResource(R.string.player_video_av_sync_increase, label)) {
                    val newDelay = (delayMs + 100L).coerceIn(-30000L, 30000L)
                    onValueChange(newDelay)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DelayStepper(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val focus = rememberTvFocusState()
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .then(focus.focusModifier)
            .tvFocusIndicator(focus, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
    }
}
