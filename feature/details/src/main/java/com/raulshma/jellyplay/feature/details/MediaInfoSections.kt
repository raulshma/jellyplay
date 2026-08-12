package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.BadgeHd
import com.composables.icons.tabler.outline.Subtitles
import com.composables.icons.tabler.outline.Volume
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.ui.util.formatFileSize

/**
 * Shared media-info rendering extracted (verbatim) from [MediaInfoScreen] so the
 * technical-info screen and the download-details bottom sheet render a
 * [MediaSource] + its streams identically without duplicating the row-building
 * logic. Pure presentation — consumes only [MaterialTheme], [ShapeCache],
 * [MediaInfoFormat], and the existing `detail_media_info_*` strings.
 *
 *  - [MediaSourceInfoSection] — one source: container/size/bitrate/duration grid
 *    + partitioned video/audio/subtitle streams.
 *  - [StreamSection]          — a titled stream group with per-stream inner cards.
 *  - [InfoGrid]               — the label/value monospace-value row list.
 */
@Composable
internal fun MediaSourceInfoSection(
    source: MediaSource,
    sourceIndex: Int,
    totalSources: Int,
) {
    // Constant target — animateColorAsState was pure overhead (target never changes at runtime).
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(ShapeCache.smooth20)
            .background(containerColor)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (totalSources > 1) {
            Text(
                text = if (source.name.isNotBlank()) source.name else "Source ${sourceIndex + 1}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        InfoGrid(entries = buildList {
            source.container?.let { add("Container" to it.uppercase()) }
            source.size?.let { add("File Size" to formatFileSize(it)) }
            source.bitrate?.let { add("Overall Bitrate" to MediaInfoFormat.formatBitrate(it)) }
            source.runTimeTicks?.let { add("Duration" to MediaInfoFormat.formatTicks(it)) }
            source.path?.let {
                val fileName = it.substringAfterLast('/').substringAfterLast('\\')
                add("File" to fileName)
            }
            add("Direct Play" to if (source.supportsDirectPlay) "Supported" else "No")
            add("Direct Stream" to if (source.supportsDirectStream) "Supported" else "No")
            add("Transcode" to if (source.supportsTranscoding) "Supported" else "No")
        })

        // Single-pass partition of mediaStreams replaces 3 independent filter
        // allocations per source per recomposition.
        val (videoStreams, audioStreams, subtitleStreams) = remember(source.mediaStreams) {
            val video = mutableListOf<MediaStream>()
            val audio = mutableListOf<MediaStream>()
            val subtitle = mutableListOf<MediaStream>()
            source.mediaStreams.forEach { s ->
                when (s.type) {
                    StreamType.VIDEO -> video += s
                    StreamType.AUDIO -> audio += s
                    StreamType.SUBTITLE -> subtitle += s
                    // EMBEDDED_IMAGE and any future types are not displayed here.
                    else -> Unit
                }
            }
            Triple(video, audio, subtitle)
        }

        if (videoStreams.isNotEmpty()) {
            StreamSection(
                title = stringResource(R.string.detail_media_info_title_video),
                icon = Tabler.Outline.BadgeHd,
                streams = videoStreams,
            ) { stream ->
                StreamInfoRows(entries = buildList {
                    stream.codec?.let { add("Codec" to it.uppercase()) }
                    if (stream.width != null && stream.height != null) {
                        add("Resolution" to "${stream.width}x${stream.height}")
                        add("Quality" to MediaInfoFormat.resolutionLabel(stream.height))
                    }
                    stream.realFrameRate?.let { add("Frame Rate" to "${it} fps") }
                    stream.bitRate?.let { add("Bitrate" to MediaInfoFormat.formatBitrate(it)) }
                    stream.videoRange?.let { add("Video Range" to it) }
                    stream.videoRangeType?.let { add("Range Type" to it) }
                    stream.videoDoViTitle?.let { add("Dolby Vision" to it) }
                    stream.isDefault.let { if (it) add("Default" to "Yes") }
                    stream.title?.let { add("Title" to it) }
                })
            }
        }

        if (audioStreams.isNotEmpty()) {
            StreamSection(
                title = stringResource(R.string.detail_media_info_title_audio),
                icon = Tabler.Outline.Volume,
                streams = audioStreams,
            ) { stream ->
                StreamInfoRows(entries = buildList {
                    stream.codec?.let { add("Codec" to it.uppercase()) }
                    stream.channels?.let { add("Channels" to MediaInfoFormat.channelLabel(it)) }
                    stream.sampleRate?.let { add("Sample Rate" to "${it / 1000.0} kHz") }
                    stream.bitRate?.let { add("Bitrate" to MediaInfoFormat.formatBitrate(it)) }
                    stream.audioSampleRate?.let { add("Sample Rate" to "${it} Hz") }
                    stream.language?.let { add("Language" to it) }
                    stream.isDefault.let { if (it) add("Default" to "Yes") }
                    stream.isForced.let { if (it) add("Forced" to "Yes") }
                    stream.title?.let { add("Title" to it) }
                })
            }
        }

        if (subtitleStreams.isNotEmpty()) {
            StreamSection(
                title = stringResource(R.string.detail_media_info_title_subtitles),
                icon = Tabler.Outline.Subtitles,
                streams = subtitleStreams,
            ) { stream ->
                StreamInfoRows(entries = buildList {
                    stream.codec?.let { add("Codec" to it.uppercase()) }
                    stream.language?.let { add("Language" to it) }
                    stream.isDefault.let { if (it) add("Default" to "Yes") }
                    stream.isForced.let { if (it) add("Forced" to "Yes") }
                    stream.isExternal.let { if (it) add("External" to "Yes") }
                    stream.title?.let { add("Title" to it) }
                    stream.displayTitle?.let { add("Display" to it) }
                })
            }
        }
    }
}

@Composable
private fun StreamSection(
    title: String,
    icon: ImageVector,
    streams: List<MediaStream>,
    streamContent: @Composable (MediaStream) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$title (${streams.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        streams.forEachIndexed { index, stream ->
            // Constant target — animateColorAsState was pure overhead (target never changes).
            val streamColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCache.smooth14)
                    .background(streamColor)
                    .padding(14.dp),
            ) {
                if (streams.size > 1) {
                    Text(
                        text = stream.displayTitle ?: stream.title ?: "Stream ${stream.index}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                streamContent(stream)
            }
        }
    }
}

@Composable
private fun StreamInfoRows(entries: List<Pair<String, String>>) {
    if (entries.isEmpty()) return
    InfoGrid(entries = entries)
}

@Composable
private fun InfoGrid(entries: List<Pair<String, String>>) {
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
