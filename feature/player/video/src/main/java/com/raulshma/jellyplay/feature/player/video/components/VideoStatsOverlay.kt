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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
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
            StatsRow("Position", formatDurationMs(currentPositionMs))
            StatsRow("Duration", formatDurationMs(durationMs))
            if (durationMs > 0) {
                val progress = (currentPositionMs.toFloat() / durationMs.toFloat() * 100f)
                StatsRow("Progress", String.format("%.1f%%", progress))
            }
            StatsRow("Speed", "${playbackSpeed}x")
            val bufferHealthMs = (stats.bufferedPositionMs - currentPositionMs).coerceAtLeast(0L)
            StatsRow("Buffer Health", formatDurationMs(bufferHealthMs))
            StatsRow("Buffered", formatDurationMs(stats.bufferedPositionMs))
            StatsRow("Clock", currentTimeString())
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

        if (stats.droppedFrames > 0) {
            StatsSection("Performance") {
                StatsRow("Dropped Frames", "${stats.droppedFrames}")
            }
        } else {
            StatsSection("Performance") {
                StatsRow("Dropped Frames", "0")
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
            color = Color(0xFF8AB4F8),
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
            color = Color.White.copy(alpha = 0.6f),
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

private fun formatDurationMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}

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

private fun formatChannels(ch: Int): String = when (ch) {
    1 -> "Mono (1)"
    2 -> "Stereo (2)"
    6 -> "5.1 (6)"
    8 -> "7.1 (8)"
    else -> "$ch ch"
}

private fun currentTimeString(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
