package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Refresh
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AVSyncSheet(
    currentAudioDelayMs: Long,
    currentSubtitleDelayMs: Long,
    onAudioDelayChange: (Long) -> Unit,
    onSubtitleDelayChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    playbackSpeed: Float = 1f,
    activeSubtitleCues: List<com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue>? = null,
    playbackPositionMs: () -> Long = { 0L },
    subtitleDelaySupported: Boolean = true,
) {
    var audioDelayMs by remember { mutableLongStateOf(currentAudioDelayMs) }
    var subtitleDelayMs by remember { mutableLongStateOf(currentSubtitleDelayMs) }
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                stringResource(R.string.player_video_av_sync),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(20.dp))

            DelayRow(
                label = stringResource(R.string.player_video_audio_delay),
                delayMs = audioDelayMs,
                focusRequester = focusRequester,
                isTv = isTv,
                helperText = stringResource(R.string.player_video_av_sync_audio_helper),
                onValueChange = { audioDelayMs = it; onAudioDelayChange(it) },
            )

            Spacer(Modifier.height(24.dp))

            DelayRow(
                label = stringResource(R.string.player_video_subtitle_delay),
                delayMs = subtitleDelayMs,
                focusRequester = null,
                isTv = isTv,
                helperText = stringResource(R.string.player_video_av_sync_subtitle_helper),
                onValueChange = { subtitleDelayMs = it; onSubtitleDelayChange(it) },
            )

            // G6: press-and-hold sync measurer. Hold "Voice heard" the instant a
            // line is spoken, then "Text seen" when its subtitle appears; the gap
            // auto-computes the delay delta (modelled on mpvKt's SubtitleDelayPanel).
            // The measurer stamps wall-clock ms; at speed != 1.0 the wall-clock gap
            // differs from media time by the speed factor, so scale the delta back
            // to media-time ms before applying it to the (media-time) delay.
            SubtitleSyncMeasurer(
                onDelayComputed = { delta ->
                    val mediaDelta = if (playbackSpeed > 0f) (delta / playbackSpeed).toLong() else delta
                    val applied = com.raulshma.jellyplay.feature.player.video.SubtitleSyncCalculator
                        .applyDelta(subtitleDelayMs, mediaDelta)
                    subtitleDelayMs = applied
                    onSubtitleDelayChange(applied)
                },
            )

            // G10: timestamp + cue-preview sync helper. Shows the subtitle line
            // active at a chosen timestamp with its previous/next neighbours, and
            // a live offset slider that recomputes the active cue as it drags.
            if (subtitleDelaySupported) {
                Spacer(Modifier.height(20.dp))
                SubtitleSyncPreview(
                    positionMs = playbackPositionMs,
                    currentOffsetMs = subtitleDelayMs,
                    cues = activeSubtitleCues,
                    isTv = isTv,
                    onOffsetChange = { subtitleDelayMs = it; onSubtitleDelayChange(it) },
                )
            }

            Spacer(Modifier.height(20.dp))

            val resetFocus = rememberTvFocusState()
            val anyOffset = audioDelayMs != 0L || subtitleDelayMs != 0L
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            subtitleDelayMs = 0L
                            onAudioDelayChange(0L)
                            onSubtitleDelayChange(0L)
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
                            stringResource(R.string.player_video_reset_both),
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

/**
 * Formats a delay/offset in ms as a signed seconds label ("0.0s", "+1.5s",
 * "-0.5s"). Shared by the delay rows and the cue-preview slider so both show
 * the same label for the same value.
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
private fun DelayStepper(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val focus = rememberTvFocusState()
    Box(
        modifier = Modifier
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

/**
 * Press-and-hold subtitle-sync measurer (G6). Two buttons:
 *  - "🔊 Voice heard": press-and-hold the instant a spoken line is heard; the
 *    release time is recorded.
 *  - "💬 Text seen": press-and-hold the instant the matching subtitle appears;
 *    the release time is recorded.
 *
 * Releasing "Text seen" after "Voice heard" was released fires [onDelayComputed]
 * with the delta (voice release − text release). The caller applies it via
 * [SubtitleSyncCalculator.applyDelta]. Releasing voice after text is treated as
 * a re-measure start (no computation), matching the natural two-step flow.
 */
@Composable
private fun SubtitleSyncMeasurer(
    onDelayComputed: (Long) -> Unit,
) {
    var voiceReleaseMs by remember { mutableStateOf<Long?>(null) }
    val voiceSource = remember { MutableInteractionSource() }
    val textSource = remember { MutableInteractionSource() }
    val voicePressed by voiceSource.collectIsPressedAsState()
    val textPressed by textSource.collectIsPressedAsState()

    LaunchedEffect(voicePressed) {
        if (!voicePressed) {
            // Released — stamp the voice time.
            voiceReleaseMs = android.os.SystemClock.elapsedRealtime()
        }
    }
    LaunchedEffect(textPressed) {
        if (!textPressed) {
            // Released — if voice was stamped first, compute and fire.
            val voice = voiceReleaseMs
            if (voice != null) {
                val textRelease = android.os.SystemClock.elapsedRealtime()
                onDelayComputed(
                    com.raulshma.jellyplay.feature.player.video.SubtitleSyncCalculator
                        .computeDelayDelta(voice, textRelease)
                )
                voiceReleaseMs = null // one-shot; re-arm requires a fresh voice press
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.player_video_sync_helper),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.player_video_sync_helper_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SyncMeasurerButton(
                label = if (voicePressed) stringResource(R.string.player_video_listening) else stringResource(R.string.player_video_voice_heard),
                pressed = voicePressed,
                interactionSource = voiceSource,
                modifier = Modifier.weight(1f),
            )
            SyncMeasurerButton(
                label = if (textPressed) stringResource(R.string.player_video_watching) else stringResource(R.string.player_video_text_seen),
                pressed = textPressed,
                interactionSource = textSource,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SyncMeasurerButton(
    label: String,
    pressed: Boolean,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(
                if (pressed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (pressed) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.primary,
        )
    }
}
