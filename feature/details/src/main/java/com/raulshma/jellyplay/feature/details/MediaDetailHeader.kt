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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.BadgeHd
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Subtitles
import com.composables.icons.tabler.outline.WaveSine
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.isLanguageMatch
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Composable
internal fun StaggeredDetailSection(
    visible: Boolean,
    delayIndex: Int,
    content: @Composable () -> Unit,
) {
    if (visible) {
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
    preferences: UserPreferences,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val videoStream = mediaStreams.firstOrNull { it.type == StreamType.VIDEO }
    val audioStreams = mediaStreams.filter { it.type == StreamType.AUDIO }
    val subtitleStreams = mediaStreams.filter { it.type == StreamType.SUBTITLE }

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
            val qualityLabel = buildString {
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

            FadingItem(modifier = Modifier.weight(1f)) {
                QuickInfoPill(
                    icon = Tabler.Outline.BadgeHd,
                    text = qualityLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val audioLabel = buildString {
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
                            else -> "${channels}CH"
                        }
                    )
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

            val subtitleLabel = selectedSubtitle
                ?.displayTitle
                ?.takeIf { it.isNotBlank() }
                ?.let { if (it.length > 10) it.take(10) + "…" else it }
                ?: selectedSubtitle?.language?.uppercase()?.take(3)
                ?: "OFF"

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
            val options = when (activePicker) {
                StreamPickerType.AUDIO -> {
                    buildList {
                        if (selectedAudioIndex != null) {
                            add(StreamPickerOption(index = null, label = "Auto", isDefault = false))
                        }
                        add(StreamPickerOption(index = -1, label = "Default", isDefault = true))
                        addAll(
                            audioStreams.map { stream ->
                                StreamPickerOption(
                                    index = stream.index,
                                    label = stream.displayTitle
                                        ?: stream.title
                                        ?: stream.language
                                        ?: "Track ${stream.index}",
                                    isDefault = stream.isDefault,
                                )
                            }
                        )
                    }
                }
                StreamPickerType.SUBTITLE -> {
                    buildList {
                        if (selectedSubtitleIndex != null) {
                            add(StreamPickerOption(index = null, label = "Auto", isDefault = false))
                        }
                        add(StreamPickerOption(index = -1, label = "Off", isDefault = true))
                        addAll(
                            subtitleStreams.map { stream ->
                                StreamPickerOption(
                                    index = stream.index,
                                    label = stream.displayTitle
                                        ?: stream.title
                                        ?: stream.language
                                        ?: "Track ${stream.index}",
                                    isDefault = stream.isDefault,
                                )
                            }
                        )
                    }
                }
            }

            val selectedIndex = when (activePicker) {
                StreamPickerType.AUDIO -> selectedAudioIndex
                StreamPickerType.SUBTITLE -> selectedSubtitleIndex
            }

            TvSafeSheet(
                onDismissRequest = { picker = null },
                title = if (activePicker == StreamPickerType.AUDIO) "Select Audio" else "Select Subtitle",
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
                                        text = "DEFAULT",
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
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (showTrailingIndicator) {
            Icon(
                imageVector = Tabler.Outline.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
internal fun InfoBadge(
    text: String,
    highlight: Boolean = false,
) {
    Box(
        modifier = Modifier
            .clip(ShapeCache.smooth4)
            .background(
                if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun SubtitleChip(
    label: String,
    isSelected: Boolean,
    isDefault: Boolean = false,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "subtitleChipBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "subtitleChipContent",
    )
    val chipFocusState = rememberTvFocusState(focusedScale = 1.05f)

    Row(
        modifier = Modifier
            .clip(ShapeCache.smooth16)
            .background(bgColor)
            .then(chipFocusState.focusModifier)
            .then(Modifier.tvFocusIndicator(chipFocusState, ShapeCache.smooth16))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
        if (isDefault && !isSelected) {
            Text(
                text = "(default)",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.6f),
            )
        }
    }
}
