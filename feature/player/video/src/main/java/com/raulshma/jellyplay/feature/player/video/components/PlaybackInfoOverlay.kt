package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType

import com.raulshma.jellyplay.feature.player.video.TrackOption

@Composable
fun PlaybackInfoOverlay(
    mediaSource: MediaSource?,
    mediaStreams: List<MediaStream>,
    playMethod: String,
    hdrType: String? = null,
    playerType: String = "Unknown",
    decoderMode: String = "Unknown",
    aspectRatio: String = "Auto",
    nightModeEnabled: Boolean = false,
    dialogueBoostEnabled: Boolean = false,
    audioPassthrough: Boolean = false,
    audioTracks: List<TrackOption> = emptyList(),
    subtitleTracks: List<TrackOption> = emptyList(),
    playbackSpeed: Float = 1f,
    audioDelayMs: Long = 0L,
    playerError: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.1f),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Playback Info",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )

            InfoSection(title = "Media Source") {
                mediaSource?.let { source ->
                    InfoRow("Container", source.container ?: "Unknown")
                    InfoRow("Size", source.size?.let { formatFileSize(it) } ?: "Unknown")
                    InfoRow("Bitrate", source.bitrate?.let { "${it / 1000} kbps" } ?: "Unknown")
                    InfoRow("Play Method", playMethod)
                }
            }

            val videoStream = mediaStreams.firstOrNull { it.type == StreamType.VIDEO }
            if (videoStream != null) {
                InfoSection(title = "Video") {
                    InfoRow("Codec", videoStream.codec?.uppercase() ?: "Unknown")
                    InfoRow(
                        "Resolution",
                        if (videoStream.width != null && videoStream.height != null) {
                            "${videoStream.width}x${videoStream.height}"
                        } else "Unknown"
                    )
                    InfoRow("Bitrate", videoStream.bitRate?.let { "${it / 1000} kbps" } ?: "Unknown")
                    if (hdrType != null) {
                        InfoRow("HDR", hdrType)
                    }
                    videoStream.realFrameRate?.let { fps ->
                        InfoRow("Frame Rate", "${"%.2f".format(fps)} fps")
                    }
                }
            }

            val audioStreams = mediaStreams.filter { it.type == StreamType.AUDIO }
            if (audioStreams.isNotEmpty()) {
                InfoSection(title = "Audio") {
                    audioStreams.forEachIndexed { index, stream ->
                        val label = stream.language?.let { lang ->
                            stream.title?.let { "$lang - $it" } ?: lang
                        } ?: stream.title ?: "Track ${index + 1}"
                        InfoRow(label, stream.codec?.uppercase() ?: "Unknown")
                        if (stream.channels != null) {
                            InfoRow("Channels", "${stream.channels}ch")
                        }
                    }
                }
            }

            val subtitleStreams = mediaStreams.filter { it.type == StreamType.SUBTITLE }
            if (subtitleStreams.isNotEmpty()) {
                InfoSection(title = "Subtitles") {
                    subtitleStreams.forEach { stream ->
                        val label = stream.language ?: stream.title ?: "Unknown"
                        InfoRow(label, if (stream.isDefault) "Default" else "")
                    }
                }
            }

            InfoSection(title = "Active Controls") {
                InfoRow("Player Engine", playerType)
                InfoRow("Decoder Mode", decoderMode)
                InfoRow("Speed", "${playbackSpeed}x")
                if (audioDelayMs != 0L) InfoRow("Audio Delay", "${audioDelayMs} ms")
                InfoRow("Aspect Ratio", aspectRatio)
                InfoRow("Night Mode", if (nightModeEnabled) "On" else "Off")
                InfoRow("Dialogue Boost", if (dialogueBoostEnabled) "On" else "Off")
                InfoRow("Audio Passthrough", if (audioPassthrough) "On" else "Off")
                
                val activeAudio = audioTracks.find { it.isSelected }?.label ?: "Unknown"
                InfoRow("Active Audio", activeAudio)
                val activeSubtitle = subtitleTracks.find { it.isSelected }?.label ?: "None"
                InfoRow("Active Subtitle", activeSubtitle)
            }

            if (playerError != null) {
                InfoSection(title = "Player Errors") {
                    Text(
                        text = playerError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format("%.0f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}
