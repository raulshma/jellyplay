package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.BadgeHd
import com.composables.icons.tabler.outline.FileDescription
import com.composables.icons.tabler.outline.Subtitles
import com.composables.icons.tabler.outline.Volume
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.util.formatFileSize

@Composable
fun MediaInfoScreen(
    itemId: String,
    onBack: () -> Unit,
    viewModel: MediaInfoViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) {
        viewModel.load(itemId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backgroundColor = rememberScreenBackgroundColor()
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current

    // TV focus-on-launch: focus the first info section once content arrives so D-pad input lands
    // on content, not the navigation drawer.
    val contentFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = contentFocusRequester,
        itemCount = if (uiState is MediaInfoUiState.Success) 1 else 0,
        tag = "media_info_init",
    )

    JellyPlayScreenScaffold(
        title = "Technical Info",
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        when (val state = uiState) {
            MediaInfoUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            is MediaInfoUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is MediaInfoUiState.Success -> {
                val detail = state.detail
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusRestorer()
                        .focusGroup()
                        .focusRequester(contentFocusRequester)
                        .verticalScroll(scrollState)
                        .padding(
                            start = adaptiveInfo.contentPadding(isTv),
                            end = adaptiveInfo.contentPadding(isTv),
                            bottom = innerPadding.calculateBottomPadding() + 80.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val currentItem = detail.item
                    Text(
                        text = currentItem.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )

                    detail.mediaSources.forEachIndexed { sourceIndex, source ->
                        MediaSourceSection(
                            source = source,
                            sourceIndex = sourceIndex,
                            totalSources = detail.mediaSources.size,
                        )
                    }

                    if (detail.mediaSources.isEmpty()) {
                        EmptyMediaInfo()
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaSourceSection(
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
            source.bitrate?.let { add("Overall Bitrate" to formatBitrate(it)) }
            source.runTimeTicks?.let { add("Duration" to formatTicks(it)) }
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
                title = "Video",
                icon = Tabler.Outline.BadgeHd,
                streams = videoStreams,
            ) { stream ->
                StreamInfoRows(entries = buildList {
                    stream.codec?.let { add("Codec" to it.uppercase()) }
                    if (stream.width != null && stream.height != null) {
                        add("Resolution" to "${stream.width}x${stream.height}")
                        add("Quality" to resolutionLabel(stream.height))
                    }
                    stream.realFrameRate?.let { add("Frame Rate" to "${it} fps") }
                    stream.bitRate?.let { add("Bitrate" to formatBitrate(it)) }
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
                title = "Audio",
                icon = Tabler.Outline.Volume,
                streams = audioStreams,
            ) { stream ->
                StreamInfoRows(entries = buildList {
                    stream.codec?.let { add("Codec" to it.uppercase()) }
                    stream.channels?.let { add("Channels" to channelLabel(it)) }
                    stream.sampleRate?.let { add("Sample Rate" to "${it / 1000.0} kHz") }
                    stream.bitRate?.let { add("Bitrate" to formatBitrate(it)) }
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
                title = "Subtitles",
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@Composable
private fun EmptyMediaInfo() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        com.raulshma.jellyplay.core.ui.components.ScreenEmptyState(
            icon = Tabler.Outline.FileDescription,
            title = "No Media Info",
            description = "Technical information is not available for this item",
        )
    }
}

private fun formatBitrate(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f Mbps".format(bps / 1_000_000.0)
    bps >= 1_000 -> "%.0f Kbps".format(bps / 1_000.0)
    else -> "$bps bps"
}

private fun formatTicks(ticks: Long): String {
    val totalSeconds = ticks / 10_000_000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%dh %dm".format(hours, minutes) else "%dm %ds".format(minutes, seconds)
}

private fun resolutionLabel(height: Int?): String = when {
    height == null -> "Unknown"
    height >= 2160 -> "4K UHD"
    height >= 1440 -> "1440p QHD"
    height >= 1080 -> "1080p Full HD"
    height >= 720 -> "720p HD"
    height >= 480 -> "480p SD"
    else -> "${height}p"
}

private fun channelLabel(channels: Int): String = when (channels) {
    1 -> "1.0 (Mono)"
    2 -> "2.0 (Stereo)"
    6 -> "5.1 (Surround)"
    8 -> "7.1 (Surround)"
    else -> "$channels ch"
}
