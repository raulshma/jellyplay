package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.Wifi
import com.raulshma.jellyplay.core.designsystem.theme.HdrColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.player.video.TrackOption
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackMetadataSnapshot

/**
 * The play-method / resolution / codec / HDR / audio / channels chip row shown
 * above the seekbar in [PlayerControls] (e.g. pill chips for "Direct Play", "4K",
 * "HEVC", "Dolby Vision", "TrueHD Atmos", "7.1").
 *
 * Each metadata item is rendered as a refined glassmorphic pill chip with a
 * category-tuned translucent backdrop, delicate hairline border, and crisp
 * typography. The subtitle delay chip is clickable and triggers the subtitle
 * delay overlay.
 *
 * Pure function of its arguments: all state is hoisted to the caller.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    onSubtitleDelayClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val videoStream = mediaStreams.firstOrNull { it.type == StreamType.VIDEO }
    val resolutionLabel = deriveResolutionLabel(videoStream, videoStats.videoResolution)
    val videoCodec = formatVideoCodec(videoStream?.codec ?: videoStats.videoCodec)

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
    val rawAudioCodec = formatAudioCodec(audioCodecFromStats ?: audioStream?.codec)

    val matchText = ((activeAudioTrack?.label ?: "") + " " + (audioStream?.title ?: "") + " " + (audioStream?.displayTitle ?: "") + " " + (audioCodecFromStats ?: "")).lowercase()
    val isAtmos = matchText.contains("atmos")
    val isDtsX = matchText.contains("dts:x") || matchText.contains("dtsx")

    val audioLabel = buildString {
        if (rawAudioCodec != null) {
            append(rawAudioCodec)
            if (isAtmos && !rawAudioCodec.contains("ATMOS", ignoreCase = true)) append(" Atmos")
            else if (isDtsX && !rawAudioCodec.contains("X", ignoreCase = true)) append(" DTS:X")
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val warningColor = MaterialTheme.colorScheme.tertiary
    val hdrGold = HdrColors.hdr10Gold
    val dolbyVisionGold = HdrColors.dolbyVisionGold
    val onScrim = playerOnScrim()
    val defaultBackground = playerScrimColor().copy(alpha = 0.55f)
    val defaultBorder = onScrim.copy(alpha = 0.16f)

    val isDirectPlay = !isDirectPlayForced && playMethod.equals("Direct Play", ignoreCase = true)
    val isTranscode = isDirectPlayForced || playMethod.lowercase().contains("transcod")

    val (playMethodTextColor, playMethodContainerColor, playMethodBorderColor, playMethodHasDot) = when {
        isDirectPlay -> Quadruple(
            primaryColor,
            primaryColor.copy(alpha = 0.14f),
            primaryColor.copy(alpha = 0.32f),
            true,
        )
        isTranscode -> Quadruple(
            warningColor,
            warningColor.copy(alpha = 0.14f),
            warningColor.copy(alpha = 0.35f),
            true,
        )
        else -> Quadruple(
            onScrim,
            defaultBackground,
            defaultBorder,
            false,
        )
    }
    val playMethodLabel = if (isDirectPlayForced) "Direct Play \u26A0" else playMethod

    val items = remember(
        playMethodLabel,
        resolutionLabel,
        videoCodec,
        hdrLabel,
        audioLabel,
        channelsLabel,
        playMethodTextColor,
        playMethodContainerColor,
        playMethodBorderColor,
        playMethodHasDot,
        isConnectionMetered,
        subtitleDelayMs,
    ) {
        listOfNotNull(
            MetadataItem(
                text = playMethodLabel,
                textColor = playMethodTextColor,
                containerColor = playMethodContainerColor,
                borderColor = playMethodBorderColor,
                hasIndicatorDot = playMethodHasDot,
                dotColor = playMethodTextColor,
            ),
            resolutionLabel?.let {
                MetadataItem(
                    text = it,
                    textColor = onScrim,
                    containerColor = defaultBackground,
                    borderColor = defaultBorder,
                )
            },
            videoCodec?.let {
                MetadataItem(
                    text = it,
                    textColor = onScrim,
                    containerColor = defaultBackground,
                    borderColor = defaultBorder,
                )
            },
            hdrLabel?.let {
                if (isDolbyVision) {
                    MetadataItem(
                        text = it,
                        textColor = dolbyVisionGold,
                        containerColor = dolbyVisionGold.copy(alpha = 0.12f),
                        borderColor = hdrGold.copy(alpha = 0.50f),
                    )
                } else {
                    MetadataItem(
                        text = it,
                        textColor = hdrGold,
                        containerColor = hdrGold.copy(alpha = 0.12f),
                        borderColor = hdrGold.copy(alpha = 0.40f),
                    )
                }
            },
            if (audioLabel.isNotBlank()) {
                if (isAtmos || isDtsX) {
                    MetadataItem(
                        text = audioLabel,
                        textColor = dolbyVisionGold,
                        containerColor = dolbyVisionGold.copy(alpha = 0.10f),
                        borderColor = hdrGold.copy(alpha = 0.38f),
                    )
                } else {
                    MetadataItem(
                        text = audioLabel,
                        textColor = onScrim.copy(alpha = 0.95f),
                        containerColor = defaultBackground,
                        borderColor = defaultBorder,
                    )
                }
            } else null,
            channelsLabel?.let {
                MetadataItem(
                    text = it,
                    textColor = onScrim.copy(alpha = 0.90f),
                    containerColor = defaultBackground,
                    borderColor = defaultBorder,
                )
            },
            if (subtitleDelayMs != 0L) {
                MetadataItem(
                    text = "Sub Delay ${formatDelayLabel(subtitleDelayMs)}",
                    textColor = primaryColor,
                    containerColor = primaryColor.copy(alpha = 0.16f),
                    borderColor = primaryColor.copy(alpha = 0.40f),
                    icon = Tabler.Outline.Clock,
                    isClickable = true,
                )
            } else null,
            // Metered link explains a silent quality cap (AUTO caps at
            // MAX_BITRATE_METERED on cellular/metered Wi-Fi). Rendered as a
            // warning badge so the user understands why high-bitrate media transcodes.
            if (isConnectionMetered) {
                MetadataItem(
                    text = "Metered",
                    textColor = warningColor,
                    containerColor = warningColor.copy(alpha = 0.14f),
                    borderColor = warningColor.copy(alpha = 0.35f),
                    icon = Tabler.Outline.Wifi,
                )
            } else null,
        )
    }

    if (items.isEmpty()) return

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items.forEach { item ->
            MetadataChip(
                item = item,
                onClick = if (item.isClickable) onSubtitleDelayClick else null,
            )
        }
    }
}

@Composable
private fun MetadataChip(
    item: MetadataItem,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState(focusedScale = 1.05f)

    Box(
        modifier = modifier
            .clip(ShapeCache.smoothPill)
            .background(item.containerColor)
            .border(1.dp, item.borderColor, ShapeCache.smoothPill)
            .then(
                if (onClick != null) {
                    Modifier
                        .then(if (isTv) tvFocusState.focusModifier else Modifier)
                        .then(if (isTv) Modifier.tvFocusIndicator(tvFocusState, ShapeCache.smoothPill) else Modifier)
                        .clickable(role = Role.Button, onClick = onClick)
                        .semantics { role = Role.Button }
                } else Modifier
            )
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.5.dp),
        ) {
            if (item.hasIndicatorDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(item.dotColor),
                )
            }
            if (item.icon != null) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.textColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = item.text,
                color = item.textColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

private fun deriveResolutionLabel(
    videoStream: MediaStream?,
    videoResolutionFromStats: String?,
): String? {
    val height = videoStream?.height
    val width = videoStream?.width
    if (height != null || width != null) {
        return resolutionLabelFor(width, height)
    }
    if (!videoResolutionFromStats.isNullOrBlank()) {
        val parts = videoResolutionFromStats.lowercase().split("x")
        if (parts.size == 2) {
            val statWidth = parts[0].trim().toIntOrNull()
            val statHeight = parts[1].trim().toIntOrNull()
            if (statHeight != null || statWidth != null) {
                return resolutionLabelFor(statWidth, statHeight)
            }
        }
    }
    return null
}

private fun resolutionLabelFor(width: Int?, height: Int?): String? = when {
    (width != null && width >= 3800) || (height != null && height >= 2000) -> "4K"
    (width != null && width >= 2500) || (height != null && height >= 1400) -> "1440p"
    (width != null && width >= 1800) || (height != null && height >= 900) -> "1080p"
    (width != null && width >= 1200) || (height != null && height >= 700) -> "720p"
    (width != null && width >= 700) || (height != null && height >= 460) -> "480p"
    height != null && height > 0 -> "${height}p"
    else -> null
}

private fun formatVideoCodec(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val upper = raw.trim().uppercase()
    return when {
        upper == "H264" || upper == "AVC" -> "H.264"
        upper == "H265" || upper == "HEVC" -> "HEVC"
        upper == "AV1" -> "AV1"
        upper == "VP9" -> "VP9"
        upper == "VP8" -> "VP8"
        upper.contains("MPEG4") || upper.contains("MP4V") -> "MPEG-4"
        upper.contains("MPEG2") -> "MPEG-2"
        else -> upper
    }
}

private fun formatAudioCodec(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val upper = raw.trim().uppercase()
    return when {
        upper == "EAC3" || upper == "E-AC3" || upper == "EC-3" -> "E-AC-3"
        upper == "AC3" || upper == "AC-3" -> "AC-3"
        upper == "TRUEHD" -> "TrueHD"
        upper == "DTSHD" || upper == "DTS-HD" -> "DTS-HD"
        upper == "DTSHD_MA" || upper == "DTS-HD MA" -> "DTS-HD MA"
        upper == "DCA" || upper == "DTS" -> "DTS"
        upper == "FLAC" -> "FLAC"
        upper == "AAC" -> "AAC"
        upper == "OPUS" -> "Opus"
        upper == "VORBIS" -> "Vorbis"
        upper == "MP3" -> "MP3"
        upper == "ALAC" -> "ALAC"
        upper == "PCM" || upper.contains("PCM") -> "PCM"
        else -> upper
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private data class MetadataItem(
    val text: String,
    val textColor: Color,
    val containerColor: Color,
    val borderColor: Color,
    val icon: ImageVector? = null,
    val hasIndicatorDot: Boolean = false,
    val dotColor: Color = Color.Unspecified,
    val isClickable: Boolean = false,
)

