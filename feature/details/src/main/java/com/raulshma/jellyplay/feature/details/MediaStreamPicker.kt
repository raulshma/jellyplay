package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.BadgeHd
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Subtitles
import com.composables.icons.tabler.outline.WaveSine
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.isLanguageMatch
import com.raulshma.jellyplay.feature.details.R
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

// Per-index delay subtracted from the shared entrance progress. The driver
// (rememberDetailEntranceProgress) animates to 1f + DETAIL_MAX_STAGGER_INDEX *
// DETAIL_STAGGER_STEP so that the stagger only offsets each section's *start* —
// every section still settles at exactly alpha 1. If this max is bumped, the
// driver target must cover it too, otherwise high-index sections are left dim.
internal const val DETAIL_STAGGER_STEP = 0.045f
internal const val DETAIL_MAX_STAGGER_INDEX = 12

@Composable
internal fun StaggeredDetailSection(
    visible: Boolean,
    delayIndex: Int,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    // Stagger the entrance of each section against the single shared
    // [LocalDetailEntrance] progress so the detail body reveals top-to-bottom
    // rather than all at once. The stagger is a pure arithmetic offset (no
    // per-section coroutine / animateFloatAsState), collapsing ~13 coroutines
    // into the one driving the shared progress.
    val progress = LocalDetailEntrance.current
    val sectionProgress = (progress - delayIndex * DETAIL_STAGGER_STEP).coerceIn(0f, 1f)
    Box(modifier = Modifier.graphicsLayer {
        alpha = sectionProgress
        translationY = (1f - sectionProgress) * 24f
    }) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaInfoSection(
    mediaStreams: List<MediaStream>,
    selectedAudioIndex: Int?,
    selectedSubtitleIndex: Int?,
    onAudioSelect: (Int?) -> Unit,
    onSubtitleSelect: (Int?) -> Unit,
    preferences: DetailPreferences,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    // Single-pass extraction of (first video, all audio, all subtitle) replaces
    // three independent traversals per recomposition.
    val (videoStream, audioStreams, subtitleStreams) = remember(mediaStreams) {
        var firstVideo: MediaStream? = null
        val audio = mutableListOf<MediaStream>()
        val subtitle = mutableListOf<MediaStream>()
        mediaStreams.forEach { s ->
            when (s.type) {
                StreamType.VIDEO -> if (firstVideo == null) firstVideo = s
                StreamType.AUDIO -> audio += s
                StreamType.SUBTITLE -> subtitle += s
                else -> Unit
            }
        }
        Triple(firstVideo, audio, subtitle)
    }

    if (videoStream == null && audioStreams.isEmpty() && subtitleStreams.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val chipBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        var picker by remember { mutableStateOf<StreamPickerType?>(null) }

        val defaultAudio = audioStreams.firstOrNull { it.isDefault } ?: audioStreams.firstOrNull()
        val selectedAudio = when (selectedAudioIndex) {
            -1 -> defaultAudio
            null -> {
                val prefAudioLang = preferences.preferredAudioLanguage ?: "eng"
                audioStreams.firstOrNull { isLanguageMatch(it.language, prefAudioLang) } ?: defaultAudio
            }
            else -> audioStreams.firstOrNull { it.index == selectedAudioIndex } ?: defaultAudio
        }

        val defaultSubtitle = subtitleStreams.firstOrNull { it.isDefault }
        val selectedSubtitle = when (selectedSubtitleIndex) {
            -1 -> null // Explicitly Off
            null -> {
                val prefSubLang = preferences.preferredSubtitleLanguage ?: "eng"
                subtitleStreams.firstOrNull { isLanguageMatch(it.language, prefSubLang) }
            }
            else -> subtitleStreams.firstOrNull { it.index == selectedSubtitleIndex } ?: defaultSubtitle
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Hoist the per-string resources out of the remembered label builders
            // (stringResource is composable-only) and remember the labels so the
            // buildString allocations don't repeat per recomposition.
            val audioChannelsFormat = stringResource(R.string.detail_audio_channels_format)
            val subtitleOff = stringResource(R.string.detail_subtitle_off)

            val qualityLabel = remember(videoStream) {
                buildString {
                    val res = videoStream?.height?.let { h ->
                        when {
                            h >= 2160 -> "4K"
                            h >= 1080 -> "HD"
                            h >= 720 -> "HD"
                            else -> "SD"
                        }
                    } ?: "Auto"
                    append(res)
                    append(" ")
                    val range = videoStream?.videoDoViTitle
                        ?: videoStream?.videoRangeType
                        ?: videoStream?.videoRange
                        ?: "SDR"
                    append(range.uppercase())
                }
            }

            FadingItem(modifier = Modifier.weight(1f)) {
                QuickInfoPill(
                    icon = Tabler.Outline.BadgeHd,
                    text = qualityLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val audioLabel = remember(selectedAudio, audioChannelsFormat) {
                buildString {
                    append(
                        selectedAudio
                            ?.language?.uppercase()?.take(3)
                            ?: selectedAudio?.displayTitle?.take(3)?.uppercase()
                            ?: "AUTO"
                    )
                    selectedAudio?.channels?.let { channels ->
                        append(" - ")
                        append(
                            when (channels) {
                                1 -> "MONO"
                                2 -> "STEREO"
                                6 -> "5.1"
                                8 -> "7.1"
                                else -> audioChannelsFormat.format(channels)
                            }
                        )
                    }
                }
            }

            FadingItem(modifier = Modifier.weight(1f)) {
                QuickInfoPill(
                    icon = Tabler.Outline.WaveSine,
                    text = audioLabel,
                    showTrailingIndicator = true,
                    onClick = { if (audioStreams.isNotEmpty()) picker = StreamPickerType.AUDIO },
                    containerColor = chipBackgroundColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val subtitleLabel = remember(selectedSubtitle, subtitleOff) {
                selectedSubtitle
                    ?.displayTitle
                    ?.takeIf { it.isNotBlank() }
                    ?.let { if (it.length > 10) it.take(10) + "…" else it }
                    ?: selectedSubtitle?.language?.uppercase()?.take(3)
                    ?: subtitleOff
            }

            FadingItem(modifier = Modifier.weight(1f)) {
                QuickInfoPill(
                    icon = Tabler.Outline.Subtitles,
                    text = subtitleLabel,
                    showTrailingIndicator = subtitleStreams.isNotEmpty(),
                    onClick = { picker = StreamPickerType.SUBTITLE },
                    containerColor = chipBackgroundColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (picker != null) {
            val activePicker = picker ?: return@Column
            // Hoist the string lookups out of the remembered builder (stringResource
            // is composable-only) and remember the option list so it isn't rebuilt
            // with fresh StreamPickerOption allocations on every recompose while the
            // picker sheet is open (audio/subtitle lists commonly 10–20 entries).
            val autoLabel = stringResource(R.string.detail_stream_auto)
            val defaultLabel = stringResource(R.string.detail_stream_default)
            val offLabel = stringResource(R.string.detail_stream_subtitle_off)
            // Fetch the raw format templates (no args → unformatted) so the
            // remembered builder can substitute the per-stream index itself.
            val audioTrackFormat = stringResource(R.string.detail_audio_track_format)
            val subtitleTrackFormat = stringResource(R.string.detail_subtitle_track_format)
            val options = remember(
                activePicker,
                audioStreams,
                subtitleStreams,
                selectedAudioIndex,
                selectedSubtitleIndex,
                autoLabel,
                defaultLabel,
                offLabel,
                audioTrackFormat,
                subtitleTrackFormat,
            ) {
                when (activePicker) {
                    StreamPickerType.AUDIO -> {
                        buildList {
                            if (selectedAudioIndex != null) {
                                add(StreamPickerOption(index = null, label = autoLabel, isDefault = false))
                            }
                            add(StreamPickerOption(index = -1, label = defaultLabel, isDefault = true))
                            addAll(
                                audioStreams.map { stream ->
                                    StreamPickerOption(
                                        index = stream.index,
                                        label = stream.displayTitle
                                            ?: stream.title
                                            ?: stream.language
                                            ?: audioTrackFormat.format(stream.index),
                                        isDefault = stream.isDefault,
                                    )
                                }
                            )
                        }
                    }
                    StreamPickerType.SUBTITLE -> {
                        buildList {
                            if (selectedSubtitleIndex != null) {
                                add(StreamPickerOption(index = null, label = autoLabel, isDefault = false))
                            }
                            add(StreamPickerOption(index = -1, label = offLabel, isDefault = true))
                            addAll(
                                subtitleStreams.map { stream ->
                                    StreamPickerOption(
                                        index = stream.index,
                                        label = stream.displayTitle
                                            ?: stream.title
                                            ?: stream.language
                                            ?: subtitleTrackFormat.format(stream.index),
                                        isDefault = stream.isDefault,
                                    )
                                }
                            )
                        }
                    }
                }
            }

            val selectedIndex = when (activePicker) {
                StreamPickerType.AUDIO -> selectedAudioIndex
                StreamPickerType.SUBTITLE -> selectedSubtitleIndex
            }

            TvSafeSheet(
                onDismissRequest = { picker = null },
                title = if (activePicker == StreamPickerType.AUDIO) stringResource(R.string.detail_select_audio) else stringResource(R.string.detail_select_subtitle),
            ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(options, key = { "${activePicker}_${it.index}_${it.label}" }, contentType = { "streamOption" }) { option ->
                            val isSelected = option.index == selectedIndex
                            val optionInteractionSource = remember { MutableInteractionSource() }
                            val isOptionPressed by optionInteractionSource.collectIsPressedAsState()
                            val optionScale by animateFloatAsState(
                                targetValue = if (isOptionPressed) 0.97f else 1f,
                                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                label = "optionScale",
                            )
                            val optionFocusState = rememberTvFocusState(focusedScale = 1.03f)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ShapeCache.smooth12)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                    .graphicsLayer {
                                        scaleX = optionScale
                                        scaleY = optionScale
                                    }
                                    .then(optionFocusState.focusModifier)
                                    .then(Modifier.tvFocusIndicator(optionFocusState, ShapeCache.smooth12))
                                    .clickable(
                                        interactionSource = optionInteractionSource,
                                        indication = null,
                                    ) {
                                        if (picker == StreamPickerType.AUDIO) {
                                            onAudioSelect(option.index)
                                        } else {
                                            onSubtitleSelect(option.index)
                                        }
                                        picker = null
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (option.isDefault && option.index != null) {
                                    Text(
                                        text = stringResource(R.string.detail_stream_default_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Tabler.Outline.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

private enum class StreamPickerType {
    AUDIO,
    SUBTITLE,
}

private data class StreamPickerOption(
    val index: Int?,
    val label: String,
    val isDefault: Boolean,
)

@Composable
internal fun QuickInfoPill(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    showTrailingIndicator: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
    onClick: (() -> Unit)? = null,
) {
    val pillFocusState = rememberTvFocusState(focusedScale = 1.05f)

    Row(
        modifier = modifier
            .clip(ShapeCache.smooth14)
            .background(containerColor)
            .then(
                if (onClick != null) {
                    Modifier
                        .then(pillFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(pillFocusState, ShapeCache.smooth14))
                        .clickable { onClick() }
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (showTrailingIndicator) {
            Icon(
                imageVector = Tabler.Outline.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
