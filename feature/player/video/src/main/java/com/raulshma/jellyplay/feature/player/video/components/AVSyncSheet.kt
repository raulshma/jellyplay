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
    activeSubtitleCues: List<com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue>? = null,
    subtitlePreviewSource: com.raulshma.jellyplay.feature.player.video.SubtitlePreviewSource = com.raulshma.jellyplay.feature.player.video.SubtitlePreviewSource.NONE,
    playbackPositionMs: () -> Long = { 0L },
    audioDelaySupported: Boolean = true,
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
                .verticalScroll(rememberScrollState())
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

            if (audioDelaySupported) {
                DelayRow(
                    label = stringResource(R.string.player_video_audio_delay),
                    delayMs = audioDelayMs,
                    focusRequester = focusRequester,
                    isTv = isTv,
                    helperText = stringResource(R.string.player_video_av_sync_audio_helper),
                    onValueChange = { audioDelayMs = it; onAudioDelayChange(it) },
                )

                Spacer(Modifier.height(24.dp))
            }

            // Cue-preview subtitle-offset sync helper. Shows the subtitle line
            // active at a chosen timestamp with its previous/next neighbours,
            // and a live offset slider that recomputes the active cue as it
            // drags. This is the sole subtitle-offset control — when no cues can
            // be parsed (embedded/image subs) the cue stack degrades to an
            // "unavailable" message but the offset slider still renders.
            SubtitleSyncPreview(
                positionMs = playbackPositionMs,
                currentOffsetMs = subtitleDelayMs,
                cues = activeSubtitleCues,
                source = subtitlePreviewSource,
                isTv = isTv,
                focusRequester = if (audioDelaySupported) null else focusRequester,
                onOffsetChange = { subtitleDelayMs = it; onSubtitleDelayChange(it) },
                onReset = {
                    subtitleDelayMs = 0L
                    onSubtitleDelayChange(0L)
                },
            )

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
internal fun DelayStepper(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

/**
 * Subtitle-delay-only section for the unified subtitle hub's "Delay" tab.
 *
 * This is the subtitle half of [AVSyncSheet]: the cue-preview sync helper plus
 * its reset affordance. Audio delay is intentionally NOT included — it stays in
 * [AVSyncSheet] (reached via the overflow "A/V Sync" item), since the hub is
 * subtitle-scoped. Reuses [SubtitleSyncPreview] verbatim.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun SubtitleDelaySection(
    currentSubtitleDelayMs: Long,
    onSubtitleDelayChange: (Long) -> Unit,
    activeSubtitleCues: List<com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue>?,
    subtitlePreviewSource: com.raulshma.jellyplay.feature.player.video.SubtitlePreviewSource =
        com.raulshma.jellyplay.feature.player.video.SubtitlePreviewSource.NONE,
    playbackPositionMs: () -> Long = { 0L },
    isTv: Boolean = false,
) {
    var subtitleDelayMs by remember(currentSubtitleDelayMs) { mutableLongStateOf(currentSubtitleDelayMs) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) focusRequester.tryRequestFocus("subtitle-delay")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
    ) {
        SubtitleSyncPreview(
            positionMs = playbackPositionMs,
            currentOffsetMs = subtitleDelayMs,
            cues = activeSubtitleCues,
            source = subtitlePreviewSource,
            isTv = isTv,
            focusRequester = focusRequester,
            onOffsetChange = { subtitleDelayMs = it; onSubtitleDelayChange(it) },
            onReset = {
                subtitleDelayMs = 0L
                onSubtitleDelayChange(0L)
            },
        )
    }
}
