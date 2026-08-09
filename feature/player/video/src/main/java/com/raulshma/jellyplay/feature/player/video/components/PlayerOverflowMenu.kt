package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.player.video.R
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
 * Extracted verbatim from `PlayerControls.kt` — decompose
 * the 1.6 kLOC controls overlay into smaller, self-contained stateless
 * composables). All state is hoisted to the caller via explicit parameters;
 * only the per-submenu open/close state is local to this composable.
 */
/**
 * The three-dot overflow menu, rendered **in-window** rather than via a Material3
 * [androidx.compose.material3.DropdownMenu]. A DropdownMenu opens a separate Popup
 * window that does not inherit the player's immersive mode, so the status/navigation
 * bars would flash on every open — the same problem solved for the bottom sheets
 * (see [com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet]).
 * Anchoring the panel inside the controls overlay keeps it in the immersive window so
 * the system bars never appear.
 *
 * Must be hosted inside a full-size [Box]: it emits a transparent full-size interceptor
 * (taps outside dismiss) and the panel itself, anchored to [Alignment.TopEnd].
 */
@Composable
internal fun BoxScope.PlayerOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    supportsSubtitleStyle: Boolean,
    supportsDialogueBoost: Boolean,
    supportsNightMode: Boolean,
    supportsAudioDelay: Boolean,
    supportsSubtitleDelay: Boolean,
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
    onSubtitleHubClick: () -> Unit,
    onDialogueBoostClick: () -> Unit,
    onDialogueBoostStrengthChange: (EffectStrength) -> Unit,
    onNightModeClick: () -> Unit,
    onNightModeStrengthChange: (EffectStrength) -> Unit,
    onAVSyncClick: () -> Unit,
    playbackMode: PlaybackMode = PlaybackMode.AUTO,
    onPlaybackModeClick: () -> Unit = {},
    onDecoderClick: () -> Unit,
    onPassthroughClick: () -> Unit,
    onVideoStatsClick: () -> Unit = {},
    onAudioNormalizationClick: () -> Unit = {},
    onAudioNormalizationModeChange: (AudioNormalizationMode) -> Unit = {},
    onChannelMixClick: () -> Unit = {},
    onChannelMixModeChange: (ChannelMixMode) -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    supportsVideoFilters: Boolean = false,
    videoFiltersActive: Boolean = false,
    onVideoFilterClick: () -> Unit = {},
    supportsScreenshot: Boolean = false,
    onScreenshotClick: () -> Unit = {},
    abRepeatActive: Boolean = false,
    onAbRepeatToggle: () -> Unit = {},
    onAbRepeatSetA: () -> Unit = {},
    onAbRepeatSetB: () -> Unit = {},
    onAbRepeatClear: () -> Unit = {},
    audioOnly: Boolean = false,
    onToggleAudioOnly: () -> Unit = {},
) {
    if (!expanded) return
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

    // Rendered in-window (NOT via a Material3 DropdownMenu/Popup). A DropdownMenu
    // opens a brand-new top-level window that does not inherit the player's
    // immersive mode, so the status/navigation bars would flash on every open —
    // the same problem solved for the bottom sheets (see PlayerModalBottomSheet).
    // Anchoring the panel inside the controls overlay keeps it in the immersive
    // window so the system bars never appear. The caller positions this content
    // within a full-size Box; a transparent interceptor behind the panel dismisses
    // on outside taps, matching the sheet behavior.
    // Transparent full-size interceptor: taps outside the panel dismiss.
    Box(
        Modifier
            .matchParentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 56.dp)
            .then(if (isTv) Modifier.fillMaxWidth(fraction = 0.5f) else Modifier)
            .widthIn(max = 320.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            // Single "Subtitles" entry opens the unified subtitle hub (tracks /
            // style / get / delay). Replaces the former separate "Subtitle Style"
            // and "Get Subtitles" overflow items that were buried apart.
            OverflowMenuItem(
                icon = Tabler.Outline.Subtitles,
                label = stringResource(R.string.player_video_subtitles),
                onClick = onSubtitleHubClick,
            )

            if (supportsDialogueBoost) {
                if (showDialogueBoostSubmenu) {
                    OverflowMenuItem(
                        icon = Tabler.Outline.ArrowLeft,
                        label = stringResource(R.string.player_video_dialogue_boost),
                        onClick = { showDialogueBoostSubmenu = false },
                        modifier = Modifier.focusRequester(dialogueBoostFocusRequester),
                    )
                    EffectStrength.entries.forEach { strength ->
                        val isActive = if (strength == EffectStrength.NONE) {
                            !dialogueBoostEnabled
                        } else {
                            dialogueBoostEnabled && dialogueBoostStrength == strength
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    strength.displayName,
                                    color = if (isActive)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            // Selecting a strength (including NONE/off) routes through the
                            // per-item strength setter, which clears the pref for NONE.
                            onClick = {
                                onDialogueBoostStrengthChange(strength)
                                onDismiss()
                            },
                            leadingIcon = {
                                if (isActive) {
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
                        label = if (dialogueBoostEnabled) stringResource(R.string.player_video_dialogue_boost_on, dialogueBoostStrength.displayName) else stringResource(R.string.player_video_dialogue_boost),
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
                        label = stringResource(R.string.player_video_night_mode),
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
                        label = if (nightModeEnabled) stringResource(R.string.player_video_night_mode_on, nightModeStrength.displayName) else stringResource(R.string.player_video_night_mode),
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
                        label = stringResource(R.string.player_video_audio_normalization),
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
                        label = if (audioNormalizationEnabled) stringResource(R.string.player_video_normalization_on, audioNormalizationMode.displayName) else stringResource(R.string.player_video_audio_normalization),
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
                        label = stringResource(R.string.player_video_channel_mixing),
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
                        label = if (channelMixEnabled) stringResource(R.string.player_video_channel_mix_on, channelMixMode.displayName) else stringResource(R.string.player_video_channel_mixing),
                        onClick = { showChannelMixSubmenu = true },
                        tint = if (channelMixEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.focusRequester(channelMixFocusRequester),
                    )
                }
            }
            // A/V Sync now hosts audio delay only — subtitle delay moved into
            // the unified subtitle hub's "Delay" tab, so this item gates on
            // audio delay alone.
            if (supportsAudioDelay) {
                OverflowMenuItem(
                    icon = Tabler.Outline.Adjustments,
                    label = stringResource(R.string.player_video_av_sync),
                    onClick = onAVSyncClick,
                )
            }
            OverflowMenuItem(
                icon = Tabler.Outline.Bolt,
                label = if (playbackMode == PlaybackMode.AUTO) stringResource(R.string.player_video_playback_mode)
                    else stringResource(R.string.player_video_playback_mode_on, playbackMode.displayName),
                onClick = onPlaybackModeClick,
                tint = if (playbackMode != PlaybackMode.AUTO) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            )
            OverflowMenuItem(
                icon = Tabler.Outline.InfoCircle,
                label = stringResource(R.string.player_video_decoder),
                onClick = onDecoderClick,
            )
            if (supportsAudioPassthrough) {
                OverflowMenuItem(
                    icon = Tabler.Outline.Volume,
                    label = stringResource(R.string.player_video_passthrough),
                    onClick = onPassthroughClick,
                    tint = if (audioPassthrough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            OverflowMenuItem(
                icon = Tabler.Outline.InfoCircle,
                label = if (showVideoStats) stringResource(R.string.player_video_stats_for_nerds_on) else stringResource(R.string.player_video_stats_for_nerds),
                onClick = onVideoStatsClick,
                tint = if (showVideoStats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            OverflowMenuItem(
                icon = Tabler.Outline.Stopwatch,
                label = if (sleepTimerActive) stringResource(R.string.player_video_sleep_timer_on, sleepTimerDisplayText) else stringResource(R.string.player_video_sleep_timer),
                onClick = onSleepTimerClick,
                tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (supportsVideoFilters) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ColorSwatch,
                    label = if (videoFiltersActive) stringResource(R.string.player_video_video_filters_on) else stringResource(R.string.player_video_video_filters),
                    onClick = onVideoFilterClick,
                    tint = if (videoFiltersActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (supportsScreenshot) {
                OverflowMenuItem(
                    icon = Tabler.Outline.Photo,
                    label = stringResource(R.string.player_video_capture_frame),
                    onClick = onScreenshotClick,
                )
            }
            OverflowMenuItem(
                icon = Tabler.Outline.Repeat,
                label = if (abRepeatActive) stringResource(R.string.player_video_ab_repeat_on) else stringResource(R.string.player_video_ab_repeat),
                onClick = onAbRepeatToggle,
                tint = if (abRepeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (abRepeatActive) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowLeft,
                    label = stringResource(R.string.player_video_set_a_point),
                    onClick = onAbRepeatSetA,
                )
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowRight,
                    label = stringResource(R.string.player_video_set_b_point),
                    onClick = onAbRepeatSetB,
                )
                OverflowMenuItem(
                    icon = Tabler.Outline.X,
                    label = stringResource(R.string.player_video_clear_ab_repeat),
                    onClick = onAbRepeatClear,
                )
            }
            OverflowMenuItem(
                icon = Tabler.Outline.Volume,
                label = if (audioOnly) stringResource(R.string.player_audio_only_on)
                    else stringResource(R.string.player_audio_only),
                onClick = onToggleAudioOnly,
                tint = if (audioOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
