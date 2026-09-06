package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.*
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.feedback.rememberConfirmHaptic
import com.raulshma.jellyplay.feature.player.audio.generated.resources.Res
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_ab_clear
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_ab_loop_active_label
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_ab_point_a_label
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_ab_set_point_a
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_ab_set_point_b
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_download
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_favorite
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_next
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_pause
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_play
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_previous
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_repeat_all
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_repeat_off
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_repeat_one
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_shuffle
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_up_next
import com.raulshma.jellyplay.feature.player.audio.components.WaveformSeekBar
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/** Waveform seek bar + timestamp row — Pixel Player style. */
@Composable
internal fun PixelProgressSection(
    currentPosition: LongState,
    duration: Long,
    isPlaying: Boolean,
    accentColor: Color,
    onSeek: (Float) -> Unit,
) {
    val positionMs = currentPosition.value
    // Position-derived labels memoized by second to cut formatDurationMs
    // allocations during the 4 Hz position tick — the elapsed label changes
    // once per second and the duration never changes. Mirrors the video
    // player's PlayerControls.
    val positionText = remember(positionMs / 1000) {
        com.raulshma.jellyplay.core.ui.components.formatDurationMs(positionMs)
    }
    val durationText = remember(duration) {
        if (duration > 0) com.raulshma.jellyplay.core.ui.components.formatDurationMs(duration) else "--:--"
    }
    WaveformSeekBar(
        progress = if (duration > 0) positionMs.toFloat() / duration else 0f,
        isPlaying = isPlaying,
        activeColor = accentColor,
        inactiveColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
        onSeek = onSeek,
        modifier = Modifier.fillMaxWidth(),
        durationMs = duration,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            positionText,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor.copy(alpha = 0.8f),
        )
        Text(
            durationText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Primary transport row in a pill container: |◁  ‖  ▷| */
@Composable
internal fun PixelTransportControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    pillSurface: Color,
    accentColor: Color,
    playFocusRequester: FocusRequester? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .clip(ShapeCache.smoothPill)
            .background(pillSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sharedTransitionScope = com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope.current
        val animatedVisibilityScope = com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope.current

        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        val sharedNextModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    rememberSharedContentState(key = "audio_player_skip_next"),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        } else Modifier

        IconButtonWithPressAnimation(
            onClick = onSkipPrevious,
            icon = {
                Icon(
                    Tabler.Outline.PlayerSkipBack, stringResource(Res.string.audio_controls_previous),
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            },
            size = 48.dp,
        )
        // Central play/pause — larger, rounded-square, light accent bg
        PixelPlayPauseButton(
            isPlaying = isPlaying,
            isLoading = isLoading,
            onClick = onTogglePlayPause,
            accentColor = accentColor,
            focusRequester = playFocusRequester,
        )
        IconButtonWithPressAnimation(
            onClick = onSkipNext,
            icon = {
                Icon(
                    Tabler.Outline.PlayerSkipForward, stringResource(Res.string.audio_controls_next),
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            },
            size = 48.dp,
            modifier = sharedNextModifier,
        )
    }
}

/** Secondary controls row: Shuffle, Repeat, Favorite — in darker pill */
@Composable
internal fun PixelSecondaryControls(
    shuffleMode: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    downloadItem: com.raulshma.jellyplay.core.model.DownloadItem?,
    abLoopStartMs: Long?,
    abLoopEndMs: Long?,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadClick: () -> Unit,
    onAbLoopClick: () -> Unit,
    pillSurfaceDark: Color,
    accentColor: Color,
) {
    val abLabelSetA = stringResource(Res.string.audio_ab_set_point_a)
    val abLabelSetB = stringResource(Res.string.audio_ab_set_point_b)
    val abLabelClear = stringResource(Res.string.audio_ab_clear)
    val abPointALabel = stringResource(Res.string.audio_ab_point_a_label)
    val abLoopActiveLabel = stringResource(Res.string.audio_ab_loop_active_label)
    val repeatOff = stringResource(Res.string.audio_controls_repeat_off)
    val repeatAll = stringResource(Res.string.audio_controls_repeat_all)
    val repeatOne = stringResource(Res.string.audio_controls_repeat_one)
    Row(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .focusGroup()
            .clip(ShapeCache.smoothPill)
            .background(pillSurfaceDark)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButtonWithPressAnimation(
            onClick = onToggleShuffle,
            tint = if (shuffleMode) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                Icon(Tabler.Outline.ArrowsShuffle, stringResource(Res.string.audio_controls_shuffle), modifier = Modifier.size(22.dp))
            },
        )
        IconButtonWithPressAnimation(
            onClick = onCycleRepeatMode,
            tint = if (repeatMode > 0) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                Icon(
                    if (repeatMode == 2) Tabler.Outline.RepeatOnce else Tabler.Outline.Repeat,
                    when (repeatMode) {
                        0 -> repeatOff; 1 -> repeatAll; else -> repeatOne
                    },
                    modifier = Modifier.size(22.dp),
                )
            },
        )
        // A→B repeat: cycles set-A → set-B → clear.
        IconButtonWithPressAnimation(
            onClick = onAbLoopClick,
            tint = if (abLoopStartMs != null) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                val label = when {
                    abLoopStartMs != null && abLoopEndMs != null -> abLabelClear
                    abLoopStartMs != null -> abLabelSetB
                    else -> abLabelSetA
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .semantics { this.contentDescription = label },
                ) {
                    Text(
                        text = if (abLoopEndMs != null) abLoopActiveLabel else abPointALabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (abLoopStartMs != null) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        IconButtonWithPressAnimation(
            onClick = onToggleFavorite,
            tint = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                Icon(
                    if (isFavorite) Tabler.Filled.Heart else Tabler.Outline.Heart,
                    stringResource(Res.string.audio_controls_favorite),
                    modifier = Modifier.size(22.dp),
                )
            },
        )
        IconButtonWithPressAnimation(
            onClick = onDownloadClick,
            tint = if (downloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                if (downloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.DOWNLOADING || downloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.PENDING) {
                    JellyPlayCircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = accentColor
                    )
                } else {
                    Icon(
                        imageVector = if (downloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) Tabler.Outline.Check else Tabler.Outline.Download,
                        contentDescription = stringResource(Res.string.audio_controls_download),
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
        )
    }
}

/**
 * "Up next" strip shown beneath the secondary controls row. Surfaces the next
 * queued track and lets the user skip over it (remove it from the upcoming
 * queue). Flat, borderless design — no card/surface container, just inline
 * artwork, text, and a skip affordance.
 */
@Composable
internal fun NextTrackBar(
    title: String,
    artist: String,
    artworkUrl: String?,
    onSkipTrack: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color,
) {
    val focusState = rememberTvFocusState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onSkipTrack)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(ShapeCache.smooth8)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                com.raulshma.jellyplay.core.ui.image.MediaImage(
                    url = artworkUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Icon(
                    Tabler.Outline.Music,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.audio_up_next),
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.8f),
                maxLines = 1,
            )
            Text(
                text = "$title · $artist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // Skip-over affordance: removes this upcoming track from the queue.
        Icon(
            Tabler.Outline.X,
            contentDescription = stringResource(Res.string.audio_controls_next),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Pixel Player play/pause: rounded-square, light accent background */
@Composable
internal fun PixelPlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    focusRequester: FocusRequester? = null,
) {
    val focusState = rememberTvFocusState()
    val confirmHaptic = rememberConfirmHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pixelPlayScale",
    )

    val sharedTransitionScope = com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope.current

    @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "audio_player_play_pause"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

    val buttonBg = MaterialTheme.colorScheme.primaryContainer
    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer

    // Light confirmation haptic on play/pause toggle (matches video player).
    // Gated by the in-app `hapticsEnabled` preference.
    val hapticOnClick: () -> Unit = remember(onClick, confirmHaptic) {
        {
            onClick()
            confirmHaptic()
        }
    }

    Box(
        modifier = Modifier
            .then(sharedModifier)
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth20)
            .clip(ShapeCache.smooth20)
            .background(buttonBg)

            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = hapticOnClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            // Show a spinner while the media item is buffering/loading.
            JellyPlayCircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = iconColor,
            )
        } else {
            Icon(
                if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                if (isPlaying) stringResource(Res.string.audio_controls_pause) else stringResource(Res.string.audio_controls_play),
                modifier = Modifier.size(36.dp),
                tint = iconColor,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconButtonWithPressAnimation(
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    icon: @Composable () -> Unit,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()
    val confirmHaptic = rememberConfirmHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "transportScale",
    )

    // Light confirmation haptic on every transport tap (consistent with the
    // video player). Respects the in-app `hapticsEnabled` preference.
    val hapticOnClick: () -> Unit = remember(onClick, confirmHaptic) {
        {
            onClick()
            confirmHaptic()
        }
    }

    IconButton(
        onClick = hapticOnClick,
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, CircleShape)
            ,
        shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
        interactionSource = interactionSource,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides tint
        ) {
            icon()
        }
    }
}
