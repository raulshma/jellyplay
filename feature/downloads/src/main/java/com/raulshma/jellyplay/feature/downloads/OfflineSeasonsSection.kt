package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Shared seasons + episodes block for offline series, mirroring the online
 * SeasonsSection: a "Seasons" label, a row of animated season tabs (shown only
 * when there's more than one season), then a fade-transitioning horizontal row
 * of [OfflineEpisodeCard]s for the selected season.
 *
 * Used by both [OfflineSeriesScreen] (the whole-series view) and
 * [OfflineDetailScreen] (when an episode is open) so the two stay visually
 * consistent.
 *
 * @param currentItemId id of the episode currently open (when used from the
 *   episode detail screen) so the matching card is highlighted; null on the
 *   series screen.
 * @param currentSeasonId id of the season to auto-select initially; null picks
 *   the first season.
 */
@Composable
internal fun OfflineSeasonsSection(
    seasons: List<OfflineMediaItem>,
    episodes: Map<String, List<OfflineMediaItem>>,
    contentPad: Dp,
    currentItemId: String? = null,
    currentSeasonId: String? = null,
    onEpisodePlay: (OfflineMediaItem) -> Unit,
    onEpisodeDetail: (OfflineMediaItem) -> Unit,
    onEpisodeDelete: (OfflineMediaItem) -> Unit,
) {
    val initialSeasonIndex = currentSeasonId?.let { id ->
        seasons.indexOfFirst { it.id == id }.coerceAtLeast(0)
    } ?: 0
    var selectedSeasonIndex by remember { mutableIntStateOf(initialSeasonIndex) }

    // Pending per-episode delete confirmation. The per-episode trash badge is a
    // small touch target overlapping the play affordance on a 16:9 thumbnail, so
    // confirm before removing the file — matching the per-series confirmation.
    var pendingDelete by remember { mutableStateOf<OfflineMediaItem?>(null) }

    // Re-select the matching season if the highlighted episode's season changes
    // (e.g. after the detail screen loads its seasons).
    LaunchedEffect(currentSeasonId, seasons) {
        if (currentSeasonId != null) {
            val idx = seasons.indexOfFirst { it.id == currentSeasonId }
            if (idx in seasons.indices && idx != selectedSeasonIndex) {
                selectedSeasonIndex = idx
            }
        }
    }

    Column {
        if (seasons.size > 1) {
            Text(
                text = "Seasons",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = contentPad),
            )
            Spacer(Modifier.height(16.dp))
            TvFocusableItemRow(
                items = seasons,
                key = { it.id },
                contentPadding = PaddingValues(horizontal = contentPad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { index, season, focusModifier ->
                OfflineSeasonTab(
                    name = season.name.takeIf { it.isNotBlank() }
                        ?: "Season ${season.seasonNumber ?: index + 1}",
                    selected = selectedSeasonIndex == index,
                    onClick = { selectedSeasonIndex = index },
                    modifier = focusModifier,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        AnimatedContent(
            targetState = selectedSeasonIndex to (seasons.getOrNull(selectedSeasonIndex)?.let { episodes[it.id]?.size } ?: 0),
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "offlineSeasonEpisodes",
        ) { (seasonIdx, _) ->
            val season = seasons.getOrNull(seasonIdx)
            val seasonEpisodes = season?.let { episodes[it.id] } ?: emptyList()
            if (seasonEpisodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ScreenEmptyState(
                        icon = Tabler.Outline.DeviceFloppy,
                        title = "No episodes downloaded for this season",
                    )
                }
            } else {
                TvFocusableItemRow(
                    items = seasonEpisodes,
                    key = { "episode_${it.id}" },
                    contentPadding = PaddingValues(horizontal = contentPad),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) { _, episode, focusModifier ->
                    OfflineEpisodeCard(
                        episode = episode,
                        isCurrentEpisode = episode.id == currentItemId,
                        onPlayClick = { onEpisodePlay(episode) },
                        onDetailClick = { onEpisodeDetail(episode) },
                        onDelete = { pendingDelete = episode },
                        modifier = focusModifier,
                    )
                }
            }
        }
    }

    pendingDelete?.let { episode ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Tabler.Outline.Trash, contentDescription = null) },
            title = { Text("Delete episode") },
            text = { Text("Remove \"${episode.name}\" from your device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onEpisodeDelete(episode)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

/** Animated season selector tab, matching the offline series screen's styling. */
@Composable
private fun OfflineSeasonTab(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetColor = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val targetContentColor = if (selected) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.onSurface
    val surfaceColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "offlineSeasonColor",
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "offlineSeasonContentColor",
    )
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    Surface(
        modifier = modifier
            .clip(ShapeCache.smooth16)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth16)
            .clickable(onClick = onClick),
        color = surfaceColor,
        contentColor = contentColor,
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
