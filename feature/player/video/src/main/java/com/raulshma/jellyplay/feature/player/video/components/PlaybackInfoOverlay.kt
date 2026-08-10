package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.InfoCircle
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.feature.player.video.TrackOption

@Composable
fun PlaybackInfoOverlay(
    mediaSource: MediaSource?,
    mediaStreams: List<MediaStream>,
    playMethod: String,
    isConnectionMetered: Boolean = false,
    hdrType: String? = null,
    playerType: String = "Unknown",
    decoderMode: String = "Unknown",
    aspectRatio: String = "Auto",
    nightModeEnabled: Boolean = false,
    nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    dialogueBoostEnabled: Boolean = false,
    dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    audioPassthrough: Boolean = false,
    audioTracks: List<TrackOption> = emptyList(),
    subtitleTracks: List<TrackOption> = emptyList(),
    playbackSpeed: Float = 1f,
    audioDelayMs: Long = 0L,
    subtitleDelayMs: Long = 0L,
    playerError: String? = null,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            focusRequester.tryRequestFocus("sheet")
        }
    }

    Surface(
        modifier = modifier,
        shape = ShapeCache.smooth20,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .ifElse(isTv, Modifier.focusRequester(focusRequester).focusable())
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(
                title = stringResource(R.string.player_video_playback_info),
                icon = Tabler.Outline.InfoCircle,
            )

            InfoSection(title = stringResource(R.string.player_video_media_source)) {
                mediaSource?.let { source ->
                    InfoRow(stringResource(R.string.player_video_container), source.container ?: stringResource(R.string.player_video_unknown))
                    InfoRow(stringResource(R.string.player_video_size), source.size?.let { formatFileSize(it) } ?: stringResource(R.string.player_video_unknown))
                    InfoRow(stringResource(R.string.player_video_bitrate), source.bitrate?.let { "${it / 1000} kbps" } ?: stringResource(R.string.player_video_unknown))
                    InfoRow(stringResource(R.string.player_video_play_method), playMethod)
                    InfoRow(
                        stringResource(R.string.player_video_connection),
                        if (isConnectionMetered) stringResource(R.string.player_video_metered) else stringResource(R.string.player_video_unmetered),
                    )
                }
            }

            val videoStream = mediaStreams.firstOrNull { it.type == StreamType.VIDEO }
            if (videoStream != null) {
                InfoSection(title = stringResource(R.string.player_video_video)) {
                    InfoRow(stringResource(R.string.player_video_codec), videoStream.codec?.uppercase() ?: stringResource(R.string.player_video_unknown))
                    InfoRow(
                        stringResource(R.string.player_video_resolution),
                        if (videoStream.width != null && videoStream.height != null) {
                            "${videoStream.width}x${videoStream.height}"
                        } else stringResource(R.string.player_video_unknown)
                    )
                    InfoRow(stringResource(R.string.player_video_bitrate), videoStream.bitRate?.let { "${it / 1000} kbps" } ?: stringResource(R.string.player_video_unknown))

                    val hdrTypeDetails = listOfNotNull(
                        hdrType,
                        videoStream.videoRangeType,
                        videoStream.videoDoViTitle
                    ).distinct().joinToString(" - ")

                    val videoRange = videoStream.videoRange
                    if (hdrTypeDetails.isNotEmpty()) {
                        InfoRow(stringResource(R.string.player_video_hdr_format), hdrTypeDetails)
                    } else if (videoRange != null) {
                        InfoRow(stringResource(R.string.player_video_range), videoRange)
                    }

                    videoStream.realFrameRate?.let { fps ->
                        InfoRow(stringResource(R.string.player_video_frame_rate), "${"%.2f".format(fps)} fps")
                    }
                }
            }

            val audioStreams = mediaStreams.filter { it.type == StreamType.AUDIO }
            if (audioStreams.isNotEmpty()) {
                InfoSection(title = stringResource(R.string.player_audio)) {
                    audioStreams.forEachIndexed { index, stream ->
                        val label = stream.language?.let { lang ->
                            stream.title?.let { "$lang - $it" } ?: lang
                        } ?: stream.title ?: stringResource(R.string.player_video_track_n, index + 1)

                        val codecUpper = stream.codec?.uppercase() ?: stringResource(R.string.player_video_unknown)
                        val matchText = (stream.title ?: "") + " " + (stream.displayTitle ?: "")
                        val isAtmos = matchText.contains("atmos", ignoreCase = true) || codecUpper.contains("ATMOS", ignoreCase = true)
                        val isDtsX = matchText.contains("dts:x", ignoreCase = true) || matchText.contains("dtsx", ignoreCase = true) || codecUpper.contains("DTS", ignoreCase = true) && matchText.contains("x", ignoreCase = true)

                        val audioFormat = buildString {
                            append(codecUpper)
                            if (isAtmos && !codecUpper.contains("ATMOS")) append(" (Atmos)")
                            else if (isDtsX && !codecUpper.contains("X")) append(" (DTS:X)")
                        }

                        InfoRow(label, audioFormat)
                        if (stream.channels != null) {
                            InfoRow(stringResource(R.string.player_video_channels), "${stream.channels}ch")
                        }
                    }
                }
            }

            val subtitleStreams = mediaStreams.filter { it.type == StreamType.SUBTITLE }
            if (subtitleStreams.isNotEmpty()) {
                InfoSection(title = stringResource(R.string.player_subtitles)) {
                    subtitleStreams.forEach { stream ->
                        val label = stream.language ?: stream.title ?: stringResource(R.string.player_video_unknown)
                        InfoRow(label, if (stream.isDefault) stringResource(R.string.player_track_default) else "")
                    }
                }
            }

            InfoSection(title = stringResource(R.string.player_video_active_controls)) {
                InfoRow(stringResource(R.string.player_video_player_engine), playerType)
                InfoRow(stringResource(R.string.player_video_decoder_mode), decoderMode)
                InfoRow(stringResource(R.string.player_video_speed), "${playbackSpeed}x")
                if (audioDelayMs != 0L) InfoRow(stringResource(R.string.player_video_audio_delay), "${audioDelayMs} ms")
                if (subtitleDelayMs != 0L) InfoRow(stringResource(R.string.player_video_subtitle_delay), formatDelayLabel(subtitleDelayMs))
                InfoRow(stringResource(R.string.player_video_aspect_ratio_label), aspectRatio)
                InfoRow(stringResource(R.string.player_video_night_mode_label), if (nightModeEnabled) nightModeStrength.displayName else stringResource(R.string.player_video_off))
                InfoRow(stringResource(R.string.player_video_dialogue_boost_label), if (dialogueBoostEnabled) dialogueBoostStrength.displayName else stringResource(R.string.player_video_off))
                InfoRow(stringResource(R.string.player_video_audio_passthrough), if (audioPassthrough) stringResource(R.string.player_video_on) else stringResource(R.string.player_video_off))

                val activeAudio = audioTracks.find { it.isSelected }?.label ?: stringResource(R.string.player_video_unknown)
                InfoRow(stringResource(R.string.player_video_active_audio), activeAudio)
                val activeSubtitle = subtitleTracks.find { it.isSelected }?.label ?: stringResource(R.string.player_track_none)
                InfoRow(stringResource(R.string.player_video_active_subtitle), activeSubtitle)
            }

            if (playerError != null) {
                InfoSection(title = stringResource(R.string.player_video_player_errors)) {
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
        Surface(
            shape = ShapeCache.smoothPill,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
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
            .clip(ShapeCache.smooth8)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
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
