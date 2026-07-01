package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus

/**
 * The three-dot overflow menu in [PlayerControls]: subtitle style, dialogue
 * boost, night mode, audio normalization, channel mixing, A/V sync, playback
 * mode, decoder, passthrough, subtitle download, stats, sleep timer, video
 * filters.
 *
 * Extracted verbatim from `PlayerControls.kt` (recommendation #2 — decompose
 * the 1.6 kLOC controls overlay into smaller, self-contained stateless
 * composables). All state is hoisted to the caller via explicit parameters;
 * only the per-submenu open/close state is local to this composable.
 */
@Composable
internal fun PlayerOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    supportsSubtitleStyle: Boolean,
    supportsDialogueBoost: Boolean,
    supportsNightMode: Boolean,
    supportsAudioDelay: Boolean,
    supportsAudioPassthrough: Boolean,
    supportsAudioNormalization: Boolean,
    supportsChannelMixing: Boolean,
    dialogueBoostEnabled: Boolean,
    dialogueBoostStrength: EffectStrength,
    nightModeEnabled: Boolean,
    nightModeStrength: EffectStrength,
    audioPassthrough: Boolean,
    showVideoStats: Boolean = false,
    audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    audioNormalizationEnabled: Boolean = false,
    channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    channelMixEnabled: Boolean = false,
    sleepTimerActive: Boolean = false,
    sleepTimerDisplayText: String = "",
    onSubtitleStyleClick: () -> Unit,
    onDialogueBoostClick: () -> Unit,
    onDialogueBoostStrengthChange: (EffectStrength) -> Unit,
    onNightModeClick: () -> Unit,
    onNightModeStrengthChange: (EffectStrength) -> Unit,
    onAVSyncClick: () -> Unit,
    playbackMode: PlaybackMode = PlaybackMode.AUTO,
    onPlaybackModeClick: () -> Unit = {},
    onDecoderClick: () -> Unit,
    onPassthroughClick: () -> Unit,
    onSubtitleDownloadClick: () -> Unit,
    onVideoStatsClick: () -> Unit = {},
    onAudioNormalizationClick: () -> Unit = {},
    onAudioNormalizationModeChange: (AudioNormalizationMode) -> Unit = {},
    onChannelMixClick: () -> Unit = {},
    onChannelMixModeChange: (ChannelMixMode) -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    supportsVideoFilters: Boolean = false,
    videoFiltersActive: Boolean = false,
    onVideoFilterClick: () -> Unit = {},
) {
    var showDialogueBoostSubmenu by remember { mutableStateOf(false) }
    var showNightModeSubmenu by remember { mutableStateOf(false) }
    var showAudioNormalizationSubmenu by remember { mutableStateOf(false) }
    var showChannelMixSubmenu by remember { mutableStateOf(false) }

    val isTv = LocalTvMode.current

    val dialogueBoostFocusRequester = remember { FocusRequester() }
    val nightModeFocusRequester = remember { FocusRequester() }
    val audioNormalizationFocusRequester = remember { FocusRequester() }
    val channelMixFocusRequester = remember { FocusRequester() }

    var isFirstDialogueBoostRender by remember { mutableStateOf(true) }
    var isFirstNightModeRender by remember { mutableStateOf(true) }
    var isFirstAudioNormalizationRender by remember { mutableStateOf(true) }
    var isFirstChannelMixRender by remember { mutableStateOf(true) }

    LaunchedEffect(showDialogueBoostSubmenu) {
        if (isFirstDialogueBoostRender) {
            isFirstDialogueBoostRender = false
        } else {
            if (isTv) {
                dialogueBoostFocusRequester.tryRequestFocus()
            }
        }
    }

    LaunchedEffect(showNightModeSubmenu) {
        if (isFirstNightModeRender) {
            isFirstNightModeRender = false
        } else {
            if (isTv) {
                nightModeFocusRequester.tryRequestFocus()
            }
        }
    }

    LaunchedEffect(showAudioNormalizationSubmenu) {
        if (isFirstAudioNormalizationRender) {
            isFirstAudioNormalizationRender = false
        } else {
            if (isTv) {
                audioNormalizationFocusRequester.tryRequestFocus()
            }
        }
    }

    LaunchedEffect(showChannelMixSubmenu) {
        if (isFirstChannelMixRender) {
            isFirstChannelMixRender = false
        } else {
            if (isTv) {
                channelMixFocusRequester.tryRequestFocus()
            }
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.then(if (isTv) Modifier.fillMaxWidth(fraction = 0.5f) else Modifier),
    ) {
        if (supportsSubtitleStyle) {
            OverflowMenuItem(
                icon = Tabler.Outline.Subtitles,
                label = "Subtitle Style",
                onClick = onSubtitleStyleClick,
            )
        }

        if (supportsDialogueBoost) {
            if (showDialogueBoostSubmenu) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowLeft,
                    label = "Dialogue Boost",
                    onClick = { showDialogueBoostSubmenu = false },
                    modifier = Modifier.focusRequester(dialogueBoostFocusRequester),
                )
                EffectStrength.entries.forEach { strength ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                strength.displayName,
                                color = if (dialogueBoostEnabled && dialogueBoostStrength == strength)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            if (!dialogueBoostEnabled) onDialogueBoostClick()
                            onDialogueBoostStrengthChange(strength)
                            onDismiss()
                        },
                        leadingIcon = {
                            if (dialogueBoostEnabled && dialogueBoostStrength == strength) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
            } else {
                OverflowMenuItem(
                    icon = Tabler.Outline.Music,
                    label = if (dialogueBoostEnabled) "Dialogue Boost \u00B7 ${dialogueBoostStrength.displayName}" else "Dialogue Boost",
                    onClick = { showDialogueBoostSubmenu = true },
                    tint = if (dialogueBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.focusRequester(dialogueBoostFocusRequester),
                )
            }
        }
        if (supportsNightMode) {
            if (showNightModeSubmenu) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowLeft,
                    label = "Night Mode",
                    onClick = { showNightModeSubmenu = false },
                    modifier = Modifier.focusRequester(nightModeFocusRequester),
                )
                EffectStrength.entries.forEach { strength ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                strength.displayName,
                                color = if (nightModeEnabled && nightModeStrength == strength)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            if (!nightModeEnabled) onNightModeClick()
                            onNightModeStrengthChange(strength)
                            onDismiss()
                        },
                        leadingIcon = {
                            if (nightModeEnabled && nightModeStrength == strength) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
            } else {
                OverflowMenuItem(
                    icon = Tabler.Outline.DotsVertical,
                    label = if (nightModeEnabled) "Night Mode \u00B7 ${nightModeStrength.displayName}" else "Night Mode",
                    onClick = { showNightModeSubmenu = true },
                    tint = if (nightModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.focusRequester(nightModeFocusRequester),
                )
            }
        }
        if (supportsAudioNormalization) {
            if (showAudioNormalizationSubmenu) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowLeft,
                    label = "Audio Normalization",
                    onClick = { showAudioNormalizationSubmenu = false },
                    modifier = Modifier.focusRequester(audioNormalizationFocusRequester),
                )
                AudioNormalizationMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                mode.displayName,
                                color = if (audioNormalizationEnabled && audioNormalizationMode == mode)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            onAudioNormalizationModeChange(mode)
                            onDismiss()
                        },
                        leadingIcon = {
                            if (audioNormalizationEnabled && audioNormalizationMode == mode) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
            } else {
                OverflowMenuItem(
                    icon = Tabler.Outline.Volume,
                    label = if (audioNormalizationEnabled) "Normalization \u00B7 ${audioNormalizationMode.displayName}" else "Audio Normalization",
                    onClick = { showAudioNormalizationSubmenu = true },
                    tint = if (audioNormalizationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.focusRequester(audioNormalizationFocusRequester),
                )
            }
        }
        if (supportsChannelMixing) {
            if (showChannelMixSubmenu) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowLeft,
                    label = "Channel Mixing",
                    onClick = { showChannelMixSubmenu = false },
                    modifier = Modifier.focusRequester(channelMixFocusRequester),
                )
                ChannelMixMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                mode.displayName,
                                color = if (channelMixEnabled && channelMixMode == mode)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            onChannelMixModeChange(mode)
                            onDismiss()
                        },
                        leadingIcon = {
                            if (channelMixEnabled && channelMixMode == mode) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
            } else {
                OverflowMenuItem(
                    icon = Tabler.Outline.Music,
                    label = if (channelMixEnabled) "Channel Mix \u00B7 ${channelMixMode.displayName}" else "Channel Mixing",
                    onClick = { showChannelMixSubmenu = true },
                    tint = if (channelMixEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.focusRequester(channelMixFocusRequester),
                )
            }
        }
        if (supportsAudioDelay) {
            OverflowMenuItem(
                icon = Tabler.Outline.Adjustments,
                label = "A/V Sync",
                onClick = onAVSyncClick,
            )
        }
        OverflowMenuItem(
            icon = Tabler.Outline.Bolt,
            label = if (playbackMode == PlaybackMode.AUTO) "Playback Mode"
                else "${playbackMode.displayName} \u00B7 On",
            onClick = onPlaybackModeClick,
            tint = if (playbackMode != PlaybackMode.AUTO) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
        )
        OverflowMenuItem(
            icon = Tabler.Outline.InfoCircle,
            label = "Decoder",
            onClick = onDecoderClick,
        )
        if (supportsAudioPassthrough) {
            OverflowMenuItem(
                icon = Tabler.Outline.Volume,
                label = "Passthrough",
                onClick = onPassthroughClick,
                tint = if (audioPassthrough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        OverflowMenuItem(
            icon = Tabler.Outline.DotsVertical,
            label = "Download Subs",
            onClick = onSubtitleDownloadClick,
        )
        OverflowMenuItem(
            icon = Tabler.Outline.InfoCircle,
            label = if (showVideoStats) "Stats for Nerds \u00B7 On" else "Stats for Nerds",
            onClick = onVideoStatsClick,
            tint = if (showVideoStats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        OverflowMenuItem(
            icon = Tabler.Outline.Stopwatch,
            label = if (sleepTimerActive) "Sleep Timer \u00B7 $sleepTimerDisplayText" else "Sleep Timer",
            onClick = onSleepTimerClick,
            tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (supportsVideoFilters) {
            OverflowMenuItem(
                icon = Tabler.Outline.ColorSwatch,
                label = if (videoFiltersActive) "Video Filters \u00B7 On" else "Video Filters",
                onClick = onVideoFilterClick,
                tint = if (videoFiltersActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun OverflowMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val effectiveTint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (enabled) effectiveTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) effectiveTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = modifier,
    )
}
