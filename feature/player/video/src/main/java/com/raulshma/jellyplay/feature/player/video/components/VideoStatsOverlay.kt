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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.extendedColors
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VideoStatsOverlay(
    stats: EngineVideoStats,
    currentPositionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    isPlaying: Boolean,
    playbackState: String,
    playMethod: String,
    streamingQuality: String,
    playerType: String,
    decoderMode: String,
    audioSessionId: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(ShapeCache.smooth12)
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Stats for Nerds",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = Color.White,
        )

        StatsSection("Playback") {
            StatsRow("State", if (isPlaying) "Playing" else playbackState)
            StatsRow("Position", formatDurationMsLocal(currentPositionMs))
            StatsRow("Duration", formatDurationMsLocal(durationMs))
            if (durationMs > 0) {
                val progress = (currentPositionMs.toFloat() / durationMs.toFloat() * 100f)
                StatsRow("Progress", String.format("%.1f%%", progress))
            }
            StatsRow("Speed", "${playbackSpeed}x")
            val bufferHealthMs = (stats.bufferedPositionMs - currentPositionMs).coerceAtLeast(0L)
            StatsRow("Buffer Health", formatDurationMsLocal(bufferHealthMs))
            StatsRow("Buffered", formatDurationMsLocal(stats.bufferedPositionMs))
            StatsRow("Clock", rememberCurrentTimeString())
        }

        if (stats.videoCodec != null || stats.videoResolution != null) {
            StatsSection("Video") {
                stats.videoCodec?.let { StatsRow("Codec", it.uppercase()) }
                stats.videoDecoder?.let { StatsRow("Decoder", it) }
                stats.videoResolution?.let { StatsRow("Resolution", it) }
                stats.videoFrameRate?.let { StatsRow("Frame Rate", String.format("%.2f fps", it)) }
                stats.videoBitrate?.let { StatsRow("Bitrate", formatBitrate(it)) }
                stats.videoHdrType?.let { StatsRow("HDR", it) }
                stats.videoColorRange?.let { StatsRow("Color Range", it) }
                stats.videoColorDepth?.let { StatsRow("Color Depth", it) }
            }
        }

        if (stats.audioCodec != null || stats.audioChannels != null) {
            StatsSection("Audio") {
                stats.audioCodec?.let { StatsRow("Codec", it.uppercase()) }
                stats.audioSampleRate?.let { StatsRow("Sample Rate", "${it} Hz") }
                stats.audioChannels?.let { ch -> StatsRow("Channels", formatChannels(ch)) }
                stats.audioBitrate?.let { StatsRow("Bitrate", formatBitrate(it)) }
                if (audioSessionId != 0) {
                    StatsRow("Session ID", "$audioSessionId")
                }
            }
        }

        StatsSection("Network") {
            StatsRow("Play Method", playMethod)
            StatsRow("Quality", streamingQuality)
            if (stats.estimatedBandwidthBps > 0) {
                StatsRow("Est. Bandwidth", formatBandwidth(stats.estimatedBandwidthBps))
            }
            StatsRow("Stream Bitrate", formatBitrate(
                stats.videoBitrate ?: (stats.audioBitrate ?: 0)
            ))
        }

        StatsSection("Performance") {
            StatsRow("Dropped Frames", "${stats.droppedFrames}")
            // Only show total rendered frames when the engine actually
            // reports them (ExoPlayer via DecoderCounters, MPV via
            // displayed-frame-count); otherwise the row is hidden rather
            // than stuck on a misleading 0.
            if (stats.totalVideoFrames > 0) {
                StatsRow("Total Frames", "${stats.totalVideoFrames}")
                val dropPct = if (stats.totalVideoFrames > 0) {
                    stats.droppedFrames.toFloat() /
                        (stats.droppedFrames + stats.totalVideoFrames) * 100f
                } else 0f
                StatsRow("Drop Rate", String.format("%.2f%%", dropPct))
            }
            if (stats.bufferSizeBytes > 0) {
                StatsRow("Buffer Size", formatBytes(stats.bufferSizeBytes))
            }
        }

        StatsSection("Engine") {
            StatsRow("Player", playerType)
            StatsRow("Decoder Mode", decoderMode)
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
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
            ),
            color = Color.White,
        )
    }
}

private fun formatDurationMsLocal(ms: Long): String = com.raulshma.jellyplay.core.ui.components.formatDurationMs(ms)

private fun formatBitrate(bps: Int): String = when {
    bps >= 1_000_000 -> String.format("%.1f Mbps", bps / 1_000_000.0)
    bps >= 1_000 -> String.format("%.0f kbps", bps / 1_000.0)
    else -> "$bps bps"
}

private fun formatBandwidth(bps: Long): String = when {
    bps >= 1_000_000 -> String.format("%.1f Mbps", bps / 1_000_000.0)
    bps >= 1_000 -> String.format("%.0f kbps", bps / 1_000.0)
    else -> "$bps bps"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024 -> String.format("%.0f KB", bytes / 1_024.0)
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
    var time by remember { mutableStateOf(formatter.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            time = formatter.format(Date())
        }
    }
    return time
}
