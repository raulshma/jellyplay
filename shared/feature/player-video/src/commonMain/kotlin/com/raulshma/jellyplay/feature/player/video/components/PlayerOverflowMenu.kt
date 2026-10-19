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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.SpatialAudioState
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_audio_only
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_audio_only_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ab_repeat
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ab_repeat_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_audio_normalization
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_av_sync
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_capture_frame
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_channel_mix_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_channel_mixing
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_gestures
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_gestures_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_queue_open
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_clear_ab_repeat
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_deinterlace
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_decoder
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_rendering
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_dialogue_boost
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_dialogue_boost_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_mark_unwatched_and_exit
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_mark_watched_and_exit
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_mark_watched_and_skip
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_night_mode
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_night_mode_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_normalization_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_passthrough
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_mode
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_mode_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_previous_episode
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_set_a_point
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_set_a_point_at
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_set_b_point
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_set_b_point_at
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_sleep_timer
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_sleep_timer_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_spatial_audio
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_spatial_audio_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_spatial_audio_unavailable
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_for_nerds
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_for_nerds_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitles
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_version
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_video_filters
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_video_filters_on

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.player.video.AbRepeatState
import com.raulshma.jellyplay.feature.player.video.formatDuration

/** Stand-in for callers with no sleep timer; hoisted so the default doesn't allocate per invocation. */
private val NoSleepTimerRemainingFlow: StateFlow<Long> = MutableStateFlow(0L)

/**
 * One tap-to-flip row of the overflow menu's input-binding quick toggles
 * (issue #171's mid-playback use case: kill the brightness swipe without
 * leaving the player). [bindingId] is the stable row id from the persisted
 * [com.raulshma.jellyplay.core.model.PlayerInputMap].
 */
internal data class PlayerOverflowMenuInputToggle(
    val bindingId: String,
    /** Pre-resolved label (core:ui's input-label seam resolves the resource). */
    val labelText: String,
    val enabled: Boolean,
)

/**
 * The overflow menu's action bundle (the [PlayerEffectsControls] idiom): every
 * `onClick`/`onChange` lambda the menu's rows dispatch rides one `@Immutable`
 * carrier, so the composable's parameter wall is capabilities + values + this
 * one bundle instead of thirty-one loose lambdas interleaved with them. The
 * per-row gating decisions (which capability hides which row, when a click
 * also dismisses the panel) stay code inside the menu — the bundle is the
 * verbs only.
 */
@Immutable
internal data class PlayerOverflowMenuActions(
    val onSubtitleHubClick: () -> Unit = {},
    val onDialogueBoostClick: () -> Unit = {},
    val onDialogueBoostStrengthChange: (EffectStrength) -> Unit = {},
    val onNightModeClick: () -> Unit = {},
    val onNightModeStrengthChange: (EffectStrength) -> Unit = {},
    val onAVSyncClick: () -> Unit = {},
    val onPlaybackModeClick: () -> Unit = {},
    val onVersionClick: () -> Unit = {},
    val onDecoderClick: () -> Unit = {},
    val onPassthroughClick: () -> Unit = {},
    val onSpatialAudioClick: () -> Unit = {},
    val onVideoStatsClick: () -> Unit = {},
    val onAudioNormalizationClick: () -> Unit = {},
    val onAudioNormalizationModeChange: (AudioNormalizationMode) -> Unit = {},
    val onChannelMixClick: () -> Unit = {},
    val onChannelMixModeChange: (ChannelMixMode) -> Unit = {},
    val onSleepTimerClick: () -> Unit = {},
    val onVideoFilterClick: () -> Unit = {},
    val onScreenshotClick: () -> Unit = {},
    val onAbRepeatToggle: () -> Unit = {},
    val onAbRepeatSetA: () -> Unit = {},
    val onAbRepeatSetB: () -> Unit = {},
    val onAbRepeatClear: () -> Unit = {},
    val onToggleAudioOnly: () -> Unit = {},
    val onPreviousEpisode: () -> Unit = {},
    val onQueueClick: () -> Unit = {},
    val onMarkWatchedAndSkip: () -> Unit = {},
    val onMarkUnwatchedAndQuit: () -> Unit = {},
    val onRenderClick: () -> Unit = {},
    val onDeinterlaceCycle: () -> Unit = {},
    val onInputQuickToggle: (bindingId: String, enabled: Boolean) -> Unit = { _, _ -> },
)

/**
 * The three-dot overflow menu in [PlayerControls]: subtitle style, dialogue
 * boost, night mode, audio normalization, channel mixing, A/V sync, playback
 * mode, decoder, passthrough, subtitle download, stats, sleep timer, video
 * filters.
 *
 * Rendered **in-window** rather than via a Material3
 * [androidx.compose.material3.DropdownMenu]. A DropdownMenu opens a separate Popup
 * window that does not inherit the player's immersive mode, so the status/navigation
 * bars would flash on every open — the same problem solved for the bottom sheets
 * (see [com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet]).
 * Anchoring the panel inside the controls overlay keeps it in the immersive window so
 * the system bars never appear.
 *
 * Must be hosted inside a full-size [Box]: it emits a transparent full-size interceptor
 * (taps outside dismiss) and the panel itself, anchored to [Alignment.TopEnd].
 *
 * Extracted verbatim from `PlayerControls.kt` — decompose
 * the 1.6 kLOC controls overlay into smaller, self-contained stateless
 * composables). The per-engine row gates ride the `@Immutable`
 * [EngineCapabilities] data class whole (the [PlayerControls] call-wall
 * idiom — never twelve `supportsX` booleans flattened at the signature) and
 * the action lambdas ride [PlayerOverflowMenuActions]; the remaining
 * parameters are the session values the rows render. All state is hoisted to
 * the caller via those parameters; only the per-submenu open/close state is
 * local to this composable.
 */

@Composable
internal fun BoxScope.PlayerOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    // The per-engine row gates, passed whole (the SubtitleStyleControls
    // capabilities precedent): which submenu/entry rows render at all.
    capabilities: EngineCapabilities,
    // Every row's action lambda (the [PlayerOverflowMenuActions] bundle).
    menu: PlayerOverflowMenuActions,
    dialogueBoostEnabled: Boolean = false,
    dialogueBoostStrength: EffectStrength = EffectStrength.NONE,
    nightModeEnabled: Boolean = false,
    nightModeStrength: EffectStrength = EffectStrength.NONE,
    audioPassthrough: Boolean = false,
    // The ADVISORY spatial-audio toggle: [spatialAudioState] is the engine's
    // live platform answer (null when none reported — unsupported device /
    // engine, or stats not yet emitted). UNAVAILABLE disables the row with the
    // reason label; otherwise the row is a plain preference toggle.
    spatialAudioEnabled: Boolean = false,
    spatialAudioState: SpatialAudioState? = null,
    showVideoStats: Boolean = false,
    audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    audioNormalizationEnabled: Boolean = false,
    channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    channelMixEnabled: Boolean = false,
    sleepTimerActive: Boolean = false,
    sleepTimerEndOfEpisode: Boolean = false,
    sleepTimerRemainingFlow: StateFlow<Long> = NoSleepTimerRemainingFlow,
    playbackMode: PlaybackMode = PlaybackMode.AUTO,
    // The Version sheet entry — multi-version items only (gated by the caller).
    hasMultipleVersions: Boolean = false,
    // the Rendering sheet (shader pack / tone mapping / quality) —
    // mpv engines only, gated by the caller (a player-type fact, not an
    // [EngineCapabilities] field).
    supportsRenderPanel: Boolean = false,
    // the session-scoped deinterlace cycle (capability-gated
    // [EngineCapabilities.supportsDeinterlace] — mpv engines only). [mode] is
    // the CURRENT session mode, surfaced in the item's label.
    deinterlaceMode: com.raulshma.jellyplay.core.model.DeinterlaceMode? = null,
    videoFiltersActive: Boolean = false,
    abRepeat: AbRepeatState = AbRepeatState(),
    audioOnly: Boolean = false,
    // The mark-and-exit pair. The watched item's label flips between
    // "…& skip" (a next episode exists) and "…& exit" (it doesn't) — one
    // action, the VM's markWatchedAndSkip owns the advance-vs-close branch.
    hasNextEpisode: Boolean = false,
    // The transport row's prev button is only enabled when a previous episode
    // exists; this menu item mirrors it as a discoverable, labelled path (the
    // row buttons are icon-only and disabled state carries no explanation).
    hasPreviousEpisode: Boolean = false,
    // The manual up-next queue entry: visible when the queue holds items and
    // no SyncPlay group session owns the order (its queue sheet replaces it).
    queueItemCount: Int = 0,
    isInSyncPlaySession: Boolean = false,
    // Incognito leaves no watch state — the unwatched variant hides.
    incognitoModeEnabled: Boolean = false,
    // The input-binding quick toggles: grouped behind one "Gestures" entry
    // (expands like the normalization submenu). The collapsed row shows the
    // enabled count; inside, "✓ <input>" = enabled, bare label = disabled.
    // Flipping emits through [PlayerOverflowMenuActions.onInputQuickToggle]
    // (persisted via the ViewModel), no dismissal.
    inputQuickToggles: List<PlayerOverflowMenuInputToggle> = emptyList(),
) {
    if (!expanded) return
    val sleepTimerRemainingMs by sleepTimerRemainingFlow.collectAsStateWithLifecycle()
    val sleepTimerDisplayText = if (sleepTimerEndOfEpisode) "End of episode" else formatDuration(sleepTimerRemainingMs)
    var showDialogueBoostSubmenu by remember { mutableStateOf(false) }
    var showNightModeSubmenu by remember { mutableStateOf(false) }
    var showAudioNormalizationSubmenu by remember { mutableStateOf(false) }
    var showChannelMixSubmenu by remember { mutableStateOf(false) }
    var showGesturesSubmenu by remember { mutableStateOf(false) }

    val isTv = LocalTvMode.current

    val dialogueBoostFocusRequester = remember { FocusRequester() }
    val nightModeFocusRequester = remember { FocusRequester() }
    val audioNormalizationFocusRequester = remember { FocusRequester() }
    val channelMixFocusRequester = remember { FocusRequester() }
    val gesturesFocusRequester = remember { FocusRequester() }

    var isFirstDialogueBoostRender by remember { mutableStateOf(true) }
    var isFirstNightModeRender by remember { mutableStateOf(true) }
    var isFirstAudioNormalizationRender by remember { mutableStateOf(true) }
    var isFirstChannelMixRender by remember { mutableStateOf(true) }
    var isFirstGesturesRender by remember { mutableStateOf(true) }

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

    LaunchedEffect(showGesturesSubmenu) {
        if (isFirstGesturesRender) {
            isFirstGesturesRender = false
        } else {
            if (isTv) {
                gesturesFocusRequester.tryRequestFocus()
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
                label = stringResource(Res.string.player_video_subtitles),
                onClick = menu.onSubtitleHubClick,
            )

            // Input-binding quick toggles (issue #171): grouped behind one
            // "Gestures" entry like the audio normalization submenu — the
            // collapsed row shows the enabled count, expanding it reveals the
            // tap-to-flip rows. Flipping stays in place (persisted), the
            // submenu stays open so several gates can be flipped in one visit.
            if (inputQuickToggles.isNotEmpty()) {
                if (showGesturesSubmenu) {
                    OverflowMenuItem(
                        icon = Tabler.Outline.ArrowLeft,
                        label = stringResource(Res.string.player_video_gestures),
                        onClick = { showGesturesSubmenu = false },
                        modifier = Modifier.focusRequester(gesturesFocusRequester),
                    )
                    inputQuickToggles.forEach { toggle ->
                        OverflowMenuItem(
                            icon = Tabler.Outline.HandMove,
                            label = toggle.labelText,
                            checked = toggle.enabled,
                            onClick = { menu.onInputQuickToggle(toggle.bindingId, !toggle.enabled) },
                        )
                    }
                } else {
                    val enabledCount = inputQuickToggles.count { it.enabled }
                    OverflowMenuItem(
                        icon = Tabler.Outline.HandMove,
                        label = if (enabledCount > 0) {
                            stringResource(Res.string.player_video_gestures_on, enabledCount, inputQuickToggles.size)
                        } else {
                            stringResource(Res.string.player_video_gestures)
                        },
                        onClick = { showGesturesSubmenu = true },
                        tint = if (enabledCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.focusRequester(gesturesFocusRequester),
                    )
                }
            }

            if (capabilities.supportsDialogueBoost) {
                if (showDialogueBoostSubmenu) {
                    OverflowMenuItem(
                        icon = Tabler.Outline.ArrowLeft,
                        label = stringResource(Res.string.player_video_dialogue_boost),
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
                                menu.onDialogueBoostStrengthChange(strength)
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
                        label = if (dialogueBoostEnabled) stringResource(Res.string.player_video_dialogue_boost_on, dialogueBoostStrength.displayName) else stringResource(Res.string.player_video_dialogue_boost),
                        onClick = { showDialogueBoostSubmenu = true },
                        tint = if (dialogueBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.focusRequester(dialogueBoostFocusRequester),
                    )
                }
            }
            if (capabilities.supportsNightMode) {
                if (showNightModeSubmenu) {
                    OverflowMenuItem(
                        icon = Tabler.Outline.ArrowLeft,
                        label = stringResource(Res.string.player_video_night_mode),
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
                                if (!nightModeEnabled) menu.onNightModeClick()
                                menu.onNightModeStrengthChange(strength)
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
                        label = if (nightModeEnabled) stringResource(Res.string.player_video_night_mode_on, nightModeStrength.displayName) else stringResource(Res.string.player_video_night_mode),
                        onClick = { showNightModeSubmenu = true },
                        tint = if (nightModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.focusRequester(nightModeFocusRequester),
                    )
                }
            }
            if (capabilities.supportsSpatialAudio) {
                // The honest ADVISORY spatial row (see
                // AudioEffectsConfig.spatialAudioEnabled in MediaEngine.kt for
                // the full rationale). UNAVAILABLE — the current output cannot
                // be spatialized — disables the row with the reason instead of
                // offering a dead switch.
                val spatialUnavailable = spatialAudioState == SpatialAudioState.UNAVAILABLE
                OverflowMenuItem(
                    icon = Tabler.Outline.Headphones,
                    label = when (spatialAudioState) {
                        SpatialAudioState.UNAVAILABLE -> stringResource(Res.string.player_video_spatial_audio_unavailable)
                        SpatialAudioState.ACTIVE -> stringResource(Res.string.player_video_spatial_audio_on)
                        else -> stringResource(Res.string.player_video_spatial_audio)
                    },
                    onClick = menu.onSpatialAudioClick,
                    tint = if (spatialAudioEnabled && !spatialUnavailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    enabled = !spatialUnavailable,
                    checked = if (spatialUnavailable) null else spatialAudioEnabled,
                )
            }
            if (capabilities.supportsAudioNormalization) {
                if (showAudioNormalizationSubmenu) {
                    OverflowMenuItem(
                        icon = Tabler.Outline.ArrowLeft,
                        label = stringResource(Res.string.player_video_audio_normalization),
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
                                menu.onAudioNormalizationModeChange(mode)
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
                        label = if (audioNormalizationEnabled) stringResource(Res.string.player_video_normalization_on, audioNormalizationMode.displayName) else stringResource(Res.string.player_video_audio_normalization),
                        onClick = { showAudioNormalizationSubmenu = true },
                        tint = if (audioNormalizationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.focusRequester(audioNormalizationFocusRequester),
                    )
                }
            }
            if (capabilities.supportsChannelMixing) {
                if (showChannelMixSubmenu) {
                    OverflowMenuItem(
                        icon = Tabler.Outline.ArrowLeft,
                        label = stringResource(Res.string.player_video_channel_mixing),
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
                                menu.onChannelMixModeChange(mode)
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
                        label = if (channelMixEnabled) stringResource(Res.string.player_video_channel_mix_on, channelMixMode.displayName) else stringResource(Res.string.player_video_channel_mixing),
                        onClick = { showChannelMixSubmenu = true },
                        tint = if (channelMixEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.focusRequester(channelMixFocusRequester),
                    )
                }
            }
            // A/V Sync now hosts audio delay only — subtitle delay moved into
            // the unified subtitle hub's "Delay" tab, so this item gates on
            // audio delay alone.
            if (capabilities.supportsAudioDelay) {
                OverflowMenuItem(
                    icon = Tabler.Outline.Adjustments,
                    label = stringResource(Res.string.player_video_av_sync),
                    onClick = menu.onAVSyncClick,
                )
            }
            OverflowMenuItem(
                icon = Tabler.Outline.Bolt,
                label = if (playbackMode == PlaybackMode.AUTO) stringResource(Res.string.player_video_playback_mode)
                    else stringResource(Res.string.player_video_playback_mode_on, playbackMode.displayName),
                onClick = menu.onPlaybackModeClick,
                tint = if (playbackMode != PlaybackMode.AUTO) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            )
            OverflowMenuItem(
                icon = Tabler.Outline.InfoCircle,
                label = stringResource(Res.string.player_video_decoder),
                onClick = menu.onDecoderClick,
            )
            // Multi-version items only (1080p + 4K, HDR variants, alternate
            // cuts): opens the Version sheet for a position-preserving swap.
            if (hasMultipleVersions) {
                OverflowMenuItem(
                    icon = Tabler.Outline.Stack2,
                    label = stringResource(Res.string.player_video_version),
                    onClick = menu.onVersionClick,
                )
            }
            if (supportsRenderPanel) {
                OverflowMenuItem(
                    icon = Tabler.Outline.Wand,
                    label = stringResource(Res.string.player_video_rendering),
                    onClick = menu.onRenderClick,
                )
            }
            if (capabilities.supportsDeinterlace && deinterlaceMode != null) {
                OverflowMenuItem(
                    icon = Tabler.Outline.Scan,
                    label = stringResource(Res.string.player_video_deinterlace, deinterlaceMode.displayName),
                    onClick = menu.onDeinterlaceCycle,
                    tint = if (deinterlaceMode != com.raulshma.jellyplay.core.model.DeinterlaceMode.AUTO) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (capabilities.supportsAudioPassthrough) {
                OverflowMenuItem(
                    icon = Tabler.Outline.Volume,
                    label = stringResource(Res.string.player_video_passthrough),
                    onClick = menu.onPassthroughClick,
                    tint = if (audioPassthrough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            OverflowMenuItem(
                icon = Tabler.Outline.InfoCircle,
                label = if (showVideoStats) stringResource(Res.string.player_video_stats_for_nerds_on) else stringResource(Res.string.player_video_stats_for_nerds),
                onClick = menu.onVideoStatsClick,
                tint = if (showVideoStats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            OverflowMenuItem(
                icon = Tabler.Outline.Stopwatch,
                label = if (sleepTimerActive) stringResource(Res.string.player_video_sleep_timer_on, sleepTimerDisplayText) else stringResource(Res.string.player_video_sleep_timer),
                onClick = menu.onSleepTimerClick,
                tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (capabilities.supportsVideoFilters) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ColorSwatch,
                    label = if (videoFiltersActive) stringResource(Res.string.player_video_video_filters_on) else stringResource(Res.string.player_video_video_filters),
                    onClick = menu.onVideoFilterClick,
                    tint = if (videoFiltersActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (capabilities.supportsScreenshot) {
                OverflowMenuItem(
                    icon = Tabler.Outline.Photo,
                    label = stringResource(Res.string.player_video_capture_frame),
                    onClick = menu.onScreenshotClick,
                )
            }
            OverflowMenuItem(
                icon = Tabler.Outline.Repeat,
                label = if (abRepeat.enabled) stringResource(Res.string.player_video_ab_repeat_on) else stringResource(Res.string.player_video_ab_repeat),
                onClick = menu.onAbRepeatToggle,
                tint = if (abRepeat.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            // The set-point items must be reachable the moment A/B repeat is
            // enabled — gating them on `isActive` (both points already set)
            // made the feature a dead end: the points could never be set.
            if (abRepeat.enabled || abRepeat.aMs != null || abRepeat.bMs != null) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowLeft,
                    label = if (abRepeat.aMs != null) stringResource(Res.string.player_video_set_a_point_at, formatDuration(abRepeat.aMs)) else stringResource(Res.string.player_video_set_a_point),
                    onClick = menu.onAbRepeatSetA,
                )
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowRight,
                    label = if (abRepeat.bMs != null) stringResource(Res.string.player_video_set_b_point_at, formatDuration(abRepeat.bMs)) else stringResource(Res.string.player_video_set_b_point),
                    onClick = menu.onAbRepeatSetB,
                )
                OverflowMenuItem(
                    icon = Tabler.Outline.X,
                    label = stringResource(Res.string.player_video_clear_ab_repeat),
                    onClick = menu.onAbRepeatClear,
                )
            }
            OverflowMenuItem(
                icon = Tabler.Outline.Volume,
                label = if (audioOnly) stringResource(Res.string.player_audio_only_on)
                    else stringResource(Res.string.player_audio_only),
                onClick = menu.onToggleAudioOnly,
                tint = if (audioOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            // Mark-and-exit actions. TV reaches them through the same
            // overflow focus path as every other item (the panel is one
            // scrollable focus column).
            OverflowMenuItem(
                icon = Tabler.Outline.PlayerSkipBack,
                label = stringResource(Res.string.player_video_previous_episode),
                onClick = menu.onPreviousEpisode,
                enabled = hasPreviousEpisode,
            )
            if (queueItemCount > 0 && !isInSyncPlaySession) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ListDetails,
                    label = stringResource(Res.string.player_video_queue_open, queueItemCount),
                    onClick = menu.onQueueClick,
                )
            }
            OverflowMenuItem(
                icon = Tabler.Outline.Checks,
                label = if (hasNextEpisode) stringResource(Res.string.player_video_mark_watched_and_skip)
                    else stringResource(Res.string.player_video_mark_watched_and_exit),
                onClick = menu.onMarkWatchedAndSkip,
            )
            if (!incognitoModeEnabled) {
                OverflowMenuItem(
                    icon = Tabler.Outline.EyeOff,
                    label = stringResource(Res.string.player_video_mark_unwatched_and_exit),
                    onClick = menu.onMarkUnwatchedAndQuit,
                )
            }
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
    checked: Boolean? = null,
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
        // A non-null [checked] renders the input-toggle state as a trailing
        // check icon (null = no state, a plain action row).
        trailingIcon = if (checked != null) {
            {
                Icon(
                    Tabler.Outline.Check,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        modifier = modifier,
    )
}
