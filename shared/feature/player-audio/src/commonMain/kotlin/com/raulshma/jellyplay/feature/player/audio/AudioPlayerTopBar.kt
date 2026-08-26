package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.components.formatDurationMs
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.player.audio.generated.resources.Res
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_add_to_playlist
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_ambient_mode
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_audio_effects
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_dialogue_boost
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_dialogue_boost_with_strength
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_equalizer
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_karaoke_mode
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_karaoke_mode_on
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_night_mode
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_night_mode_with_strength
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_sleep_timer
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_sleep_timer_active
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_menu_speed
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_sleep_timer_end_of_episode
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_topbar_lyrics
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_topbar_minimize
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_topbar_more
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_topbar_now_playing
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_topbar_queue
import com.raulshma.jellyplay.feature.player.audio.components.CastButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun PixelPlayerTopBar(
    onBack: () -> Unit,
    hasLyrics: Boolean,
    lyricsVisible: Boolean,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    speed: Float,
    dialogueBoostEnabled: Boolean,
    dialogueBoostStrength: com.raulshma.jellyplay.core.model.EffectStrength,
    nightModeEnabled: Boolean,
    nightModeStrength: com.raulshma.jellyplay.core.model.EffectStrength,
    onSpeedClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onEffectsClick: () -> Unit,
    onDialogueBoostClick: () -> Unit,
    onDialogueBoostStrengthChange: (com.raulshma.jellyplay.core.model.EffectStrength) -> Unit,
    onNightModeClick: () -> Unit,
    onNightModeStrengthChange: (com.raulshma.jellyplay.core.model.EffectStrength) -> Unit,
    onAmbientClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit = {},
    sleepTimerActive: Boolean = false,
    sleepTimerEndOfEpisode: Boolean = false,
    sleepTimerRemainingFlow: StateFlow<Long> = MutableStateFlow(0L),
    onSleepTimerClick: () -> Unit = {},
    karaokeMode: Boolean = false,
    onKaraokeToggle: (Boolean) -> Unit = {},
    hasKaraokeLyrics: Boolean = false,
    castController: AudioPlayerCast? = null,
) {
    val minimizeFocusState = rememberTvFocusState()
    val lyricsFocusState = rememberTvFocusState()
    val queueFocusState = rememberTvFocusState()
    val moreFocusState = rememberTvFocusState()

    val sleepTimerRemainingMs by sleepTimerRemainingFlow.collectAsStateWithLifecycle()
    val sleepTimerDisplayText = if (sleepTimerEndOfEpisode) {
        stringResource(Res.string.audio_sleep_timer_end_of_episode)
    } else {
        formatDurationMs(sleepTimerRemainingMs)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .then(minimizeFocusState.focusModifier)
                .tvFocusIndicator(minimizeFocusState, CircleShape)
        ) {
            Icon(
                Tabler.Outline.ChevronDown, stringResource(Res.string.audio_topbar_minimize),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            stringResource(Res.string.audio_topbar_now_playing),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            letterSpacing = 1.sp,
        )
        Row {
            if (castController != null) {
                CastButton(castController = castController)
            }
            if (hasLyrics) {
                IconButton(
                    onClick = onLyricsClick,
                    modifier = Modifier
                        .then(lyricsFocusState.focusModifier)
                        .tvFocusIndicator(lyricsFocusState, CircleShape)
                ) {
                    Icon(
                        if (lyricsVisible) Tabler.Outline.Microphone2 else Tabler.Outline.Microphone,
                        stringResource(Res.string.audio_topbar_lyrics),
                        tint = if (lyricsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(
                onClick = onQueueClick,
                modifier = Modifier
                    .then(queueFocusState.focusModifier)
                    .tvFocusIndicator(queueFocusState, CircleShape)
            ) {
                Icon(Tabler.Outline.Playlist, stringResource(Res.string.audio_topbar_queue), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
            }
            Box {
                IconButton(
                    onClick = { onMenuToggle(true) },
                    modifier = Modifier
                        .then(moreFocusState.focusModifier)
                        .tvFocusIndicator(moreFocusState, CircleShape)
                ) {
                    Icon(Tabler.Outline.DotsVertical, stringResource(Res.string.audio_topbar_more), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
                }
                val itemColors = androidx.compose.material3.MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { onMenuToggle(false) }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.audio_menu_speed, if (speed == 1.0f) "1x" else "${speed}x")) },
                        onClick = onSpeedClick,
                        leadingIcon = { Icon(Tabler.Outline.Gauge, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.audio_menu_equalizer)) },
                        onClick = onEqualizerClick,
                        leadingIcon = { Icon(Tabler.Outline.Adjustments, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.audio_menu_audio_effects)) },
                        onClick = onEffectsClick,
                        leadingIcon = { Icon(Tabler.Outline.Ear, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text(if (dialogueBoostEnabled) stringResource(Res.string.audio_menu_dialogue_boost_with_strength, dialogueBoostStrength.displayName) else stringResource(Res.string.audio_menu_dialogue_boost)) },
                        onClick = { onDialogueBoostClick(); onMenuToggle(false) },
                        leadingIcon = { Icon(Tabler.Outline.Microphone2, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text(if (nightModeEnabled) stringResource(Res.string.audio_menu_night_mode_with_strength, nightModeStrength.displayName) else stringResource(Res.string.audio_menu_night_mode)) },
                        onClick = { onNightModeClick(); onMenuToggle(false) },
                        leadingIcon = { Icon(Tabler.Outline.Moon, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.audio_menu_ambient_mode)) },
                        onClick = onAmbientClick,
                        leadingIcon = { Icon(Tabler.Outline.MoonStars, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.audio_menu_add_to_playlist)) },
                        onClick = { onAddToPlaylistClick(); onMenuToggle(false) },
                        leadingIcon = { Icon(Tabler.Outline.PlaylistAdd, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text(if (sleepTimerActive) stringResource(Res.string.audio_menu_sleep_timer_active, sleepTimerDisplayText) else stringResource(Res.string.audio_menu_sleep_timer)) },
                        onClick = onSleepTimerClick,
                        leadingIcon = { Icon(Tabler.Outline.Stopwatch, null) },
                        colors = itemColors,
                    )
                    if (hasKaraokeLyrics) {
                        DropdownMenuItem(
                            text = { Text(if (karaokeMode) stringResource(Res.string.audio_menu_karaoke_mode_on) else stringResource(Res.string.audio_menu_karaoke_mode)) },
                            onClick = { onKaraokeToggle(!karaokeMode); onMenuToggle(false) },
                            leadingIcon = { Icon(Tabler.Outline.Microphone2, null) },
                            colors = itemColors,
                        )
                    }
                }
            }
        }
    }
}
