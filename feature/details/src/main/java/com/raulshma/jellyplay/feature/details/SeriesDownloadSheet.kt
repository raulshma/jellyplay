package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.raulshma.jellyplay.core.ui.components.rememberMultiEpisodeSelectionForDownload
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Download
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.defaultContentSizeSpec
import com.raulshma.jellyplay.core.designsystem.theme.defaultSpatialSpring
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.feature.details.R

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
    val selection = rememberMultiEpisodeSelectionForDownload(seasons, episodes, downloadedEpisodeIds)
    var expandedSeasonId by remember { mutableStateOf<String?>(null) }

    val totalSelectedCount = selection.totalSelectedCount
    val allSelected = selection.allSelected
    val allSelectableIds = selection.allSelectableIds
    val isLoading = loadingSeasons.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Cap the sheet body so the Cancel/Download actions are always
            // visible without scrolling. The bottom sheet sizes itself to its
            // content; an unbounded column here would let the LazyColumn grow
            // past the sheet and push the action buttons off screen.
            .heightIn(max = 560.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.detail_download_series_title),
            subtitle = stringResource(R.string.detail_download_series_subtitle),
            icon = Tabler.Outline.Download,
            onClose = onDismiss,
        )

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
                onClick = { selection.toggleSelectAll() },
                enabled = !isLoading,
                shape = ShapeCache.smoothPill,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (allSelected) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (allSelected) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.weight(1f).focusIndicator(),
            ) {
                Text(if (allSelected) stringResource(R.string.detail_deselect_all) else stringResource(R.string.detail_select_all))
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
                text = stringResource(R.string.detail_episodes_selected_format, totalSelectedCount, allSelectableIds.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Loading indicator shown while seasons' episodes are still being
        // eager-loaded on open. The select-all button stays disabled until this
        // clears so toggleSelectAll() always sees the complete episode set.
        if (loadingSeasons.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                JellyPlayLoadingIndicator(modifier = Modifier.size(28.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(seasons, key = { it.id }, contentType = { "season" }) { season ->
                val isExpanded = expandedSeasonId == season.id
                val seasonEpisodes = episodes[season.id].orEmpty()
                val isLoadingThis = season.id in loadingSeasons

                val downloadedInSeason = remember(seasonEpisodes, downloadedEpisodeIds) {
                    seasonEpisodes.count { it.id in downloadedEpisodeIds }
                }
                val selectedInSeason = selection.selectedForSeason(season.id)
                val triState = selection.triStateForSeason(season.id)

                fun onSeasonCheckboxToggle() {
                    selection.toggleSeason(season.id)
                }

                val seasonBgColor by animateColorAsState(
                    targetValue = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceContainer,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
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
                            onClick = { onSeasonCheckboxToggle() },
                            enabled = !isLoading,
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
                                val episodesLabel = pluralStringResource(R.plurals.detail_episode_count, count, count)
                                val downloadedLabel = stringResource(R.string.detail_downloaded_count_format, downloadedInSeason)
                                Text(
                                    text = buildString {
                                        append(episodesLabel)
                                        if (downloadedInSeason > 0) {
                                            append(" $downloadedLabel")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            imageVector = Tabler.Outline.ChevronDown,
                            contentDescription = if (isExpanded) stringResource(R.string.detail_cd_collapse) else stringResource(R.string.detail_cd_expand),
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
                                text = stringResource(R.string.detail_no_episodes_available),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
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
                                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                        label = "epBg",
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shape)
                                            .background(episodeBgColor)
                                            .clickable(enabled = !isDownloaded) {
                                                selection.toggleEpisode(season.id, episode.id)
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
                                                text = stringResource(R.string.detail_downloaded_status),
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
                Text(stringResource(R.string.detail_cancel))
            }
            Button(
                onClick = { onDownload(selection.toSelectedMap()) },
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
                    if (isDownloading) stringResource(R.string.detail_queuing)
                    else "$totalSelectedCount",
                )
            }
        }
    }
}
