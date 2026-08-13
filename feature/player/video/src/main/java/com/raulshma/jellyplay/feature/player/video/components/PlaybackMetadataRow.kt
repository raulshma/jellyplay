package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.HdrColors
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.TrackOption
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackMetadataSnapshot

/**
 * The play-method / codec / HDR / audio / channels badge row shown beneath the
 * title in [PlayerControls] (e.g. "Direct Play • HEVC • HDR10 • TRUEHD Atmos • 7.1").
 *
 * Extracted verbatim from `PlayerControls.kt` — decompose
 * the controls overlay into smaller, self-contained stateless composables).
 * Pure function of its arguments: all state is hoisted to the caller.
 */
@Composable
internal fun PlaybackMetadataRow(
    playMethod: String,
    isDirectPlayForced: Boolean,
    hdrType: String?,
    mediaStreams: List<MediaStream>,
    videoStats: PlaybackMetadataSnapshot,
    audioTracks: List<TrackOption>,
    isConnectionMetered: Boolean = false,
    subtitleDelayMs: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val videoStream = mediaStreams.firstOrNull { it.type == StreamType.VIDEO }
    val videoCodec = (videoStream?.codec ?: videoStats.videoCodec)?.uppercase()

    val isDolbyVision = hdrType?.lowercase() in listOf("dolbyvision", "dolby_vision", "dovi") ||
                        (videoStream?.videoDoViTitle?.isNotBlank() == true) ||
                        (videoStream?.videoRangeType?.lowercase()?.contains("dovi") == true) ||
                        (videoStream?.videoRange?.lowercase()?.contains("dovi") == true) ||
                        (videoStats.videoHdrType?.lowercase()?.contains("dolby") == true) ||
                        (videoStats.videoHdrType?.lowercase()?.contains("dovi") == true)

    val hdrLabel = when {
        isDolbyVision -> "Dolby Vision"
        hdrType != null -> when (hdrType.lowercase()) {
            "hdr10" -> "HDR10"
            "hdr10plus", "hdr10_plus" -> "HDR10+"
            "hlg" -> "HLG"
            "hdr" -> "HDR"
            else -> hdrType.uppercase()
        }
        videoStats.videoHdrType != null -> videoStats.videoHdrType
        else -> null
    }

    val activeAudioTrack = audioTracks.find { it.isSelected }
    val audioCodecFromStats = videoStats.audioCodec
    val audioStream = mediaStreams.firstOrNull { it.type == StreamType.AUDIO }
    val rawAudioCodec = (audioCodecFromStats ?: audioStream?.codec)?.uppercase()

    val matchText = ((activeAudioTrack?.label ?: "") + " " + (audioStream?.title ?: "") + " " + (audioStream?.displayTitle ?: "") + " " + (audioCodecFromStats ?: "")).lowercase()
    val isAtmos = matchText.contains("atmos")
    val isDtsX = matchText.contains("dts:x") || matchText.contains("dtsx")

    val audioLabel = buildString {
        if (rawAudioCodec != null) {
            append(rawAudioCodec)
            if (isAtmos && !rawAudioCodec.contains("ATMOS")) append(" Atmos")
            else if (isDtsX && !rawAudioCodec.contains("X")) append(" DTS:X")
        } else if (isAtmos) {
            append("Dolby Atmos")
        } else if (isDtsX) {
            append("DTS:X")
        }
    }

    val channels = videoStats.audioChannels ?: audioStream?.channels
    val channelsLabel = channels?.let { ch ->
        when (ch) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${ch}ch"
        }
    }

    val directPlayGreen = MaterialTheme.colorScheme.primary
    val transcodeOrange = MaterialTheme.colorScheme.tertiary
    val hdrGold = HdrColors.hdr10Gold
    val dolbyVisionGold = HdrColors.dolbyVisionGold

    val onScrim = playerOnScrim()
    val playMethodColor = when {
        isDirectPlayForced -> transcodeOrange
        playMethod.equals("Direct Play", ignoreCase = true) -> directPlayGreen
        playMethod.lowercase().contains("transcod") -> transcodeOrange
        else -> onScrim
    }
    val playMethodLabel = if (isDirectPlayForced) "Direct Play \u26A0" else playMethod

    val hdrColor = if (isDolbyVision) dolbyVisionGold else hdrGold
    val audioColor = if (isAtmos || isDtsX) dolbyVisionGold else onScrim

    val items = remember(playMethodLabel, videoCodec, hdrLabel, audioLabel, channelsLabel, playMethodColor, hdrColor, audioColor, isConnectionMetered, subtitleDelayMs) {
        listOfNotNull(
            MetadataItem(playMethodLabel, playMethodColor),
            videoCodec?.let { MetadataItem(it, onScrim) },
            hdrLabel?.let { MetadataItem(it, hdrColor) },
            if (audioLabel.isNotBlank()) MetadataItem(audioLabel, audioColor) else null,
            channelsLabel?.let { MetadataItem(it, onScrim.copy(alpha = 0.9f)) },
            if (subtitleDelayMs != 0L) MetadataItem("Sub Delay ${formatDelayLabel(subtitleDelayMs)}", directPlayGreen) else null,
            // Metered link explains a silent quality cap (AUTO caps at
            // MAX_BITRATE_METERED on cellular/metered Wi-Fi). Rendered as a
            // warning so the user understands why high-bitrate media transcodes.
            if (isConnectionMetered) MetadataItem("Metered", transcodeOrange) else null,
        )
    }

    if (items.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                OutlinedText(
                    text = "•",
                    textColor = onScrim.copy(alpha = 0.4f)
                )
            }
            OutlinedText(
                text = item.text,
                textColor = item.color
            )
        }
    }
}

@Composable
private fun OutlinedText(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp
    ),
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val outlineColor = playerScrimColor().copy(alpha = 0.8f)
        val offsets = listOf(
            -0.8.dp to -0.8.dp,
            0.8.dp to -0.8.dp,
            -0.8.dp to 0.8.dp,
            0.8.dp to 0.8.dp
        )
        offsets.forEach { (dx, dy) ->
            Text(
                text = text,
                color = outlineColor,
                style = style.copy(color = outlineColor),
                modifier = Modifier.offset(dx, dy)
            )
        }
        Text(
            text = text,
            color = textColor,
            style = style.copy(color = textColor)
        )
    }
}

private data class MetadataItem(
    val text: String,
    val color: Color
)
