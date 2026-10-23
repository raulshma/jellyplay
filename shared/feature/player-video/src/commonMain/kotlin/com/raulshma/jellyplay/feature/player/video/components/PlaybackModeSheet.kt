package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Server
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_mode_auto_desc
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_mode_force_direct_desc
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_mode_force_transcode_desc
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_options
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_options_delivery_section
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_options_desc
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_options_engine_section
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_playback_options_quality_section

/**
 * The combined playback-options panel: engine, delivery method and quality in
 * ONE scrollable sheet that stays open across picks (each select updates in
 * place; only the scaffold's dismiss closes it). The engine section hides
 * while casting / in SyncPlay ([availablePlayers] empty); the quality section
 * renders its unavailable help text when the session has no quality to pick.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaybackModeSheet(
    currentMode: PlaybackMode,
    currentPlayMethod: String,
    onSelect: (PlaybackMode) -> Unit,
    onDismiss: () -> Unit,
    currentQuality: com.raulshma.jellyplay.core.model.StreamingQuality,
    adaptiveBitrateEnabled: Boolean,
    onQualitySelect: (com.raulshma.jellyplay.core.model.StreamingQuality) -> Unit,
    onToggleAdaptiveBitrate: (Boolean) -> Unit,
    qualityAvailable: Boolean,
    currentEngineName: String?,
    availablePlayers: List<PlayerType>,
    onPlayerSelect: (PlayerType) -> Unit,
) {
    PickerSheetScaffold(
        title = stringResource(Res.string.player_video_playback_options),
        icon = Tabler.Outline.Server,
        onDismiss = onDismiss,
        preContent = {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.player_video_playback_options_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        },
    ) { isTv, focusRequester ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            if (availablePlayers.isNotEmpty()) {
                SectionLabel(stringResource(Res.string.player_video_playback_options_engine_section))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availablePlayers.forEach { player ->
                        val isSelected = currentEngineName != null && player.displayName == currentEngineName
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPlayerSelect(player) },
                            label = {
                                Text(
                                    player.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            shape = ShapeCache.smoothPill,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.Transparent,
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                enabled = true,
                                selected = isSelected,
                            ),
                        )
                    }
                }
                SectionSpacer()
            }

            SectionLabel(stringResource(Res.string.player_video_playback_options_delivery_section))
            Text(
                text = currentPlayMethod,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (isTv) {
                LazyColumn(modifier = Modifier.verticalWrapAround()) {
                    items(PlaybackMode.entries, key = { it.name }) { mode ->
                        val isSelected = mode == currentMode
                        val isFirstOrSelected = isSelected ||
                            (currentMode !in PlaybackMode.entries && mode == PlaybackMode.AUTO)
                        TvFocusableOptionRow(
                            selected = isSelected,
                            onClick = { onSelect(mode) },
                            focusRequesterModifier = Modifier.ifElse(isFirstOrSelected, Modifier.focusRequester(focusRequester)),
                            trailingCheck = true,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                TvOptionRowLabel(text = mode.displayName, selected = isSelected)
                                Text(
                                    text = modeDescription(mode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlaybackMode.entries.forEach { mode ->
                        val isSelected = mode == currentMode
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelect(mode) },
                            label = {
                                Text(
                                    mode.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            shape = ShapeCache.smoothPill,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.Transparent,
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                enabled = true,
                                selected = isSelected,
                            ),
                        )
                    }
                }
            }
            SectionSpacer()

            SectionLabel(stringResource(Res.string.player_video_playback_options_quality_section))
            val qualityFocusRequester = remember { FocusRequester() }
            QualityPickerOptions(
                currentQuality = currentQuality,
                adaptiveBitrateEnabled = adaptiveBitrateEnabled,
                onToggleAdaptiveBitrate = onToggleAdaptiveBitrate,
                onSelect = onQualitySelect,
                onDismiss = onDismiss,
                isTv = isTv,
                // The combined sheet's quality section is never the TV focus
                // landing zone (the delivery rows own the requester); the
                // embedded options only attach this one.
                tvFocusRequester = qualityFocusRequester,
                enabled = qualityAvailable,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun SectionSpacer() {
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun modeDescription(mode: PlaybackMode): String = when (mode) {
    PlaybackMode.AUTO -> stringResource(Res.string.player_video_playback_mode_auto_desc)
    PlaybackMode.FORCE_DIRECT_PLAY -> stringResource(Res.string.player_video_playback_mode_force_direct_desc)
    PlaybackMode.FORCE_TRANSCODE -> stringResource(Res.string.player_video_playback_mode_force_transcode_desc)
}
