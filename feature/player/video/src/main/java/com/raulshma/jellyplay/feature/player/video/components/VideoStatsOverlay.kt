package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.extendedColors
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.core.ui.player.FormattedTranscodeReason
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VideoStatsOverlay(
    // High-frequency streams collected here so only this overlay (shown
    // only while "Stats for Nerds" is enabled) recomposes on position ticks.
    statsFlow: StateFlow<EngineVideoStats>,
    currentPositionFlow: StateFlow<Long>,
    durationMs: Long,
    playbackSpeed: Float,
    isPlaying: Boolean,
    playbackState: String,
    playMethod: String,
    streamingQuality: String,
    playerType: String,
    decoderMode: String,
    audioSessionId: Int,
    /** Server-reported transcode reasons, pre-formatted; empty when direct
     *  playing so the "why transcoding" block is hidden entirely. */
    transcodeReasons: List<FormattedTranscodeReason> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val stats by statsFlow.collectAsStateWithLifecycle()
    val currentPositionMs by currentPositionFlow.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .clip(ShapeCache.smooth12)
            .background(playerScrimColor().copy(alpha = 0.78f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.player_video_stats_for_nerds),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = playerOnScrim(),
        )

        StatsSection(stringResource(R.string.player_video_stats_playback)) {
            StatsRow(stringResource(R.string.player_video_stats_state), if (isPlaying) stringResource(R.string.player_video_stats_playing) else playbackState)
            StatsRow(stringResource(R.string.player_video_stats_position), formatDurationMsLocal(currentPositionMs))
            StatsRow(stringResource(R.string.player_video_duration), formatDurationMsLocal(durationMs))
            if (durationMs > 0) {
                val progress = (currentPositionMs.toFloat() / durationMs.toFloat() * 100f)
                StatsRow(stringResource(R.string.player_video_stats_progress), "${formatFixed(progress.toDouble(), 1)}%")
            }
            StatsRow(stringResource(R.string.player_video_speed), "${playbackSpeed}x")
            val bufferHealthMs = (stats.bufferedPositionMs - currentPositionMs).coerceAtLeast(0L)
            StatsRow(stringResource(R.string.player_video_stats_buffer_health), formatDurationMsLocal(bufferHealthMs))
            StatsRow(stringResource(R.string.player_video_stats_buffered), formatDurationMsLocal(stats.bufferedPositionMs))
            StatsRow(stringResource(R.string.player_video_stats_clock), rememberCurrentTimeString())
        }

        if (stats.videoCodec != null || stats.videoResolution != null) {
            StatsSection(stringResource(R.string.player_video_video)) {
                stats.videoCodec?.let { StatsRow(stringResource(R.string.player_video_codec), it.uppercase()) }
                stats.videoDecoder?.let { StatsRow(stringResource(R.string.player_video_decoder), it) }
                stats.videoResolution?.let { StatsRow(stringResource(R.string.player_video_resolution), it) }
                stats.videoFrameRate?.let { StatsRow(stringResource(R.string.player_video_frame_rate), "${formatFixed(it.toDouble(), 2)} fps") }
                stats.videoBitrate?.let { StatsRow(stringResource(R.string.player_video_bitrate), formatBitrate(it)) }
                stats.videoHdrType?.let { StatsRow(stringResource(R.string.player_video_stats_hdr), it) }
                stats.videoColorRange?.let { StatsRow(stringResource(R.string.player_video_stats_color_range), it) }
                stats.videoColorDepth?.let { StatsRow(stringResource(R.string.player_video_stats_color_depth), it) }
            }
        }

        if (stats.audioCodec != null || stats.audioChannels != null) {
            StatsSection(stringResource(R.string.player_video_audio)) {
                stats.audioCodec?.let { StatsRow(stringResource(R.string.player_video_codec), it.uppercase()) }
                stats.audioSampleRate?.let { StatsRow(stringResource(R.string.player_video_stats_sample_rate), "${it} Hz") }
                stats.audioChannels?.let { ch -> StatsRow(stringResource(R.string.player_video_channels), formatChannels(ch)) }
                stats.audioBitrate?.let { StatsRow(stringResource(R.string.player_video_bitrate), formatBitrate(it)) }
                if (audioSessionId != 0) {
                    StatsRow(stringResource(R.string.player_video_stats_session_id), "$audioSessionId")
                }
            }
        }

        StatsSection(stringResource(R.string.player_video_stats_network)) {
            StatsRow(stringResource(R.string.player_video_play_method), playMethod)
            StatsRow(stringResource(R.string.player_video_stats_quality), streamingQuality)
            if (stats.estimatedBandwidthBps > 0) {
                StatsRow(stringResource(R.string.player_video_stats_est_bandwidth), formatBandwidth(stats.estimatedBandwidthBps))
            }
            StatsRow(stringResource(R.string.player_video_stats_stream_bitrate), formatBitrate(
                stats.videoBitrate ?: (stats.audioBitrate ?: 0)
            ))
            if (transcodeReasons.isNotEmpty()) {
                transcodeReasons.forEach { reason ->
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = reason.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = playerOnScrim().copy(alpha = 0.9f),
                        )
                        reason.hint?.let { hint ->
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = playerOnScrim().copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }
        }

        StatsSection(stringResource(R.string.player_video_stats_performance)) {
            StatsRow(stringResource(R.string.player_video_stats_dropped_frames), "${stats.droppedFrames}")
            // Only show total rendered frames when the engine actually
            // reports them (ExoPlayer via DecoderCounters, MPV via
            // displayed-frame-count); otherwise the row is hidden rather
            // than stuck on a misleading 0.
            if (stats.totalVideoFrames > 0) {
                StatsRow(stringResource(R.string.player_video_stats_total_frames), "${stats.totalVideoFrames}")
                val dropPct = if (stats.totalVideoFrames > 0) {
                    stats.droppedFrames.toFloat() /
                        (stats.droppedFrames + stats.totalVideoFrames) * 100f
                } else 0f
                StatsRow(stringResource(R.string.player_video_stats_drop_rate), "${formatFixed(dropPct.toDouble(), 2)}%")
            }
            if (stats.bufferSizeBytes > 0) {
                StatsRow(stringResource(R.string.player_video_stats_buffer_size), formatBytes(stats.bufferSizeBytes))
            }
            // Sync / display diagnostics — null-safe: rows hidden when absent.
            stats.voFrameDropCount?.let { StatsRow(stringResource(R.string.player_video_stats_vo_dropped_frames), "$it") }
            stats.avsyncMs?.let { StatsRow(stringResource(R.string.player_video_av_sync), "${formatFixed(it.toDouble(), 1)} ms") }
            stats.voDelayedMs?.let { StatsRow(stringResource(R.string.player_video_stats_vo_delay), "${formatFixed(it.toDouble(), 1)} ms") }
            stats.displayFps?.let { StatsRow(stringResource(R.string.player_video_stats_display_rate), "${formatFixed(it.toDouble(), 2)} Hz") }
        }

        StatsSection(stringResource(R.string.player_video_stats_engine)) {
            StatsRow(stringResource(R.string.player_video_stats_player), playerType)
            StatsRow(stringResource(R.string.player_video_decoder_mode), decoderMode)
        }
    }
}

@Composable
private fun StatsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            ),
            color = MaterialTheme.extendedColors.statsOverlayText,
        )
        content()
    }
}

@Composable
private fun StatsRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            ),
            color = playerOnScrim().copy(alpha = 0.85f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
            ),
            color = playerOnScrim(),
        )
    }
}

private fun formatDurationMsLocal(ms: Long): String = com.raulshma.jellyplay.core.ui.components.formatDurationMs(ms)

private fun formatBitrate(bps: Int): String = when {
    bps >= 1_000_000 -> "${formatFixed(bps / 1_000_000.0, 1)} Mbps"
    bps >= 1_000 -> "${Math.round(bps / 1_000.0)} kbps"
    else -> "$bps bps"
}

private fun formatBandwidth(bps: Long): String = when {
    bps >= 1_000_000 -> "${formatFixed(bps / 1_000_000.0, 1)} Mbps"
    bps >= 1_000 -> "${Math.round(bps / 1_000.0)} kbps"
    else -> "$bps bps"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "${formatFixed(bytes / 1_048_576.0, 1)} MB"
    bytes >= 1_024 -> "${Math.round(bytes / 1_024.0)} KB"
    else -> "$bytes B"
}

private fun formatChannels(ch: Int): String = when (ch) {
    1 -> "Mono (1)"
    2 -> "Stereo (2)"
    6 -> "5.1 (6)"
    8 -> "7.1 (8)"
    else -> "$ch ch"
}

@Composable
private fun rememberCurrentTimeString(): String {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    // Reuse a single Date instance and mutate its underlying time field each
    // tick instead of allocating a fresh Date() every second.
    val date = remember { Date() }
    var time by remember { mutableStateOf(formatter.format(date)) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            date.time = System.currentTimeMillis()
            time = formatter.format(date)
        }
    }
    return time
}
