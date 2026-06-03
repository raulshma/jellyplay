package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Download
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.defaultContentSizeSpec
import com.raulshma.jellyplay.core.designsystem.theme.defaultSpatialSpring
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.MediaItem

@Stable
private data class SeasonSelectionState(
    val allEpisodeIds: Set<String>,
    val selectableEpisodeIds: Set<String>,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeriesDownloadSheet(
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    loadingSeasons: Set<String>,
    downloadedEpisodeIds: Set<String>,
    onLoadEpisodes: (seasonId: String) -> Unit,
    isDownloading: Boolean,
    onDownload: (selectedEpisodes: Map<String, List<String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    var expandedSeasonId by remember { mutableStateOf<String?>(null) }
    var selectedEpisodeIds by remember(seasons) {
        mutableStateOf(seasons.associate { it.id to mutableSetOf<String>() })
    }

    val allSelectableIds = remember(seasons, downloadedEpisodeIds, episodes) {
        episodes.values
            .flatten()
            .map { it.id }
            .filter { it !in downloadedEpisodeIds }
            .toSet()
    }

    val totalSelectedCount = selectedEpisodeIds.values.sumOf { it.size }

    val allSelected = allSelectableIds.isNotEmpty() &&
        allSelectableIds.all { id ->
            selectedEpisodeIds.values.any { id in it }
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download Series",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Tap a season to see episodes. Select which to download.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconToggleButton(
                checked = false,
                onCheckedChange = { onDismiss() },
                modifier = Modifier.size(40.dp),
            ) {
                Text(
                    text = "\u2715",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeCache.smoothPill)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    selectedEpisodeIds = if (allSelected) {
                        seasons.associate { it.id to mutableSetOf() }
                    } else {
                        seasons.associate { seasonId ->
                            val seasonEpisodes = episodes[seasonId.id].orEmpty()
                            seasonId.id to seasonEpisodes
                                .map { it.id }
                                .filter { it !in downloadedEpisodeIds }
                                .toMutableSet()
                        }
                    }
                },
                shape = ShapeCache.smoothPill,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (allSelected) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (allSelected) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (allSelected) "Deselect All" else "Select All")
            }
        }

        if (totalSelectedCount > 0 && allSelectableIds.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    totalSelectedCount.toFloat() / allSelectableIds.size.toFloat()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(ShapeCache.smooth4),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$totalSelectedCount of ${allSelectableIds.size} episodes selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(seasons, key = { it.id }) { season ->
                val isExpanded = expandedSeasonId == season.id
                val seasonEpisodes = episodes[season.id].orEmpty()
                val isLoadingThis = season.id in loadingSeasons

                val downloadedInSeason = seasonEpisodes.count { it.id in downloadedEpisodeIds }
                val selectableInSeason = seasonEpisodes
                    .map { it.id }
                    .filter { it !in downloadedEpisodeIds }
                    .toSet()
                val selectedInSeason = selectedEpisodeIds[season.id].orEmpty()

                val triState = when {
                    selectableInSeason.isEmpty() && downloadedInSeason > 0 -> ToggleableState.On
                    selectableInSeason.isEmpty() -> ToggleableState.Off
                    selectableInSeason.all { it in selectedInSeason } -> ToggleableState.On
                    selectableInSeason.none { it in selectedInSeason } -> ToggleableState.Off
                    else -> ToggleableState.Indeterminate
                }

                val seasonBgColor by animateColorAsState(
                    targetValue = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceContainer,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "seasonBg",
                )

                val chevronRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    animationSpec = defaultSpatialSpring(),
                    label = "chevron",
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = defaultContentSizeSpec()),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCache.smooth16)
                            .background(seasonBgColor)
                            .clickable {
                                val newExpanded = if (isExpanded) null else season.id
                                expandedSeasonId = newExpanded
                                if (newExpanded != null && season.id !in episodes.keys) {
                                    onLoadEpisodes(season.id)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(
                            state = triState,
                            onClick = {
                                val current = selectedEpisodeIds.toMutableMap()
                                val seasonSet = (current[season.id] ?: mutableSetOf()).toMutableSet()
                                if (selectableInSeason.all { it in seasonSet }) {
                                    seasonSet.removeAll(selectableInSeason)
                                } else {
                                    seasonSet.addAll(selectableInSeason)
                                }
                                current[season.id] = seasonSet
                                selectedEpisodeIds = current
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = season.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val epCount = season.childCount ?: seasonEpisodes.size
                            if (epCount > 0 || seasonEpisodes.isNotEmpty()) {
                                val count = if (seasonEpisodes.isNotEmpty()) seasonEpisodes.size else epCount
                                Text(
                                    text = buildString {
                                        append("$count episodes")
                                        if (downloadedInSeason > 0) {
                                            append(" \u2022 $downloadedInSeason downloaded")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            imageVector = Tabler.Outline.ChevronDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (isExpanded) {
                        if (isLoadingThis) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                JellyPlayLoadingIndicator(
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        } else if (seasonEpisodes.isEmpty() && season.id in episodes.keys) {
                            Text(
                                text = "No episodes available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
                            ) {
                                seasonEpisodes.forEachIndexed { idx, episode ->
                                    val isDownloaded = episode.id in downloadedEpisodeIds
                                    val isEpisodeSelected = isDownloaded || episode.id in selectedInSeason
                                    val shape = expressiveListShape(
                                        index = idx,
                                        count = seasonEpisodes.size,
                                        outerRadius = 14.dp,
                                        innerRadius = 8.dp,
                                    )

                                    val episodeBgColor by animateColorAsState(
                                        targetValue = when {
                                            isDownloaded -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                            isEpisodeSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else -> Color.Transparent
                                        },
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                        label = "epBg",
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shape)
                                            .background(episodeBgColor)
                                            .clickable(enabled = !isDownloaded) {
                                                val current = selectedEpisodeIds.toMutableMap()
                                                val seasonSet = (current[season.id]
                                                    ?: mutableSetOf()).toMutableSet()
                                                if (episode.id in seasonSet) {
                                                    seasonSet.remove(episode.id)
                                                } else {
                                                    seasonSet.add(episode.id)
                                                }
                                                current[season.id] = seasonSet
                                                selectedEpisodeIds = current
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = isEpisodeSelected,
                                            onCheckedChange = null,
                                            enabled = !isDownloaded,
                                            colors = if (isDownloaded) {
                                                CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.tertiary,
                                                )
                                            } else {
                                                CheckboxDefaults.colors()
                                            },
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = buildString {
                                                    episode.episodeNumber?.let { append("E$it. ") }
                                                    append(episode.name)
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isDownloaded)
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                else
                                                    MaterialTheme.colorScheme.onSurface,
                                            )
                                            episode.runTimeTicks?.let { ticks ->
                                                val minutes = ticks / 600_000_000
                                                Text(
                                                    text = "${minutes}m",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        if (isDownloaded) {
                                            Text(
                                                text = "Downloaded",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                shape = ShapeCache.smoothPill,
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val result = selectedEpisodeIds.mapValues { (_, ids) -> ids.toList() }
                    onDownload(result)
                },
                enabled = totalSelectedCount > 0 && !isDownloading,
                shape = ShapeCache.smoothPill,
            ) {
                if (isDownloading) {
                    JellyPlayLoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Icon(
                    imageVector = Tabler.Outline.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    if (isDownloading) "Queuing..."
                    else "$totalSelectedCount"
                )
            }
        }
    }
}
