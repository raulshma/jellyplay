package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Download
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.detail_cancel
import com.raulshma.jellyplay.core.ui.generated.resources.detail_deselect_all
import com.raulshma.jellyplay.core.ui.generated.resources.detail_download_series_subtitle
import com.raulshma.jellyplay.core.ui.generated.resources.detail_download_series_title
import com.raulshma.jellyplay.core.ui.generated.resources.detail_downloaded_count_format
import com.raulshma.jellyplay.core.ui.generated.resources.detail_downloaded_status
import com.raulshma.jellyplay.core.ui.generated.resources.detail_episode_count
import com.raulshma.jellyplay.core.ui.generated.resources.detail_episodes_selected
import com.raulshma.jellyplay.core.ui.generated.resources.detail_no_episodes_available
import com.raulshma.jellyplay.core.ui.generated.resources.detail_queuing
import com.raulshma.jellyplay.core.ui.generated.resources.detail_select_all

/**
 * Series download sheet: per-season episode picker for enqueueing downloads.
 * Shared by the media detail screen (its Download button / deep link) and the
 * home quick-action sheet (long-press Download on a series card) — both hosts
 * supply seasons/episodes from their own data seams and execute the batch
 * through their own download intake. Purely presentational; selection state
 * lives in [rememberMultiEpisodeSelectionForDownload].
 *
 * Already-downloaded episodes ([downloadedEpisodeIds]) render as locked rows
 * (tertiary tint, non-selectable) so a partially downloaded series shows what
 * remains.
 */
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
            title = stringResource(Res.string.detail_download_series_title),
            subtitle = stringResource(Res.string.detail_download_series_subtitle),
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
                Text(if (allSelected) stringResource(Res.string.detail_deselect_all) else stringResource(Res.string.detail_select_all))
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
                text = stringResource(Res.string.detail_episodes_selected, totalSelectedCount, allSelectableIds.size),
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

        // The season header and each expanded season's episode rows are
        // individual keyed items so an expanded 100+ episode season composes
        // only its visible rows instead of one giant unvirtualized item.
        // Episode keys are season-scoped ("season-{id}-ep-…") to stay unique.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            seasons.forEach { season ->
                val isExpanded = expandedSeasonId == season.id
                val seasonEpisodes = episodes[season.id].orEmpty()
                val isLoadingThis = season.id in loadingSeasons

                item(key = "season-${season.id}", contentType = "season") {
                    val downloadedInSeason = remember(seasonEpisodes, downloadedEpisodeIds) {
                        seasonEpisodes.count { it.id in downloadedEpisodeIds }
                    }
                    val triState = selection.triStateForSeason(season.id)

                    val epCount = season.childCount ?: seasonEpisodes.size
                    val subtitle = if (epCount > 0 || seasonEpisodes.isNotEmpty()) {
                        val count = if (seasonEpisodes.isNotEmpty()) seasonEpisodes.size else epCount
                        val episodesLabel = pluralStringResource(Res.plurals.detail_episode_count, count, count)
                        val downloadedLabel = stringResource(Res.string.detail_downloaded_count_format, downloadedInSeason)
                        buildString {
                            append(episodesLabel)
                            if (downloadedInSeason > 0) {
                                append(" $downloadedLabel")
                            }
                        }
                    } else null

                    SeasonHeaderRow(
                        title = season.name,
                        subtitle = subtitle,
                        triState = triState,
                        isExpanded = isExpanded,
                        expandedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        onHeaderClick = {
                            val newExpanded = if (isExpanded) null else season.id
                            expandedSeasonId = newExpanded
                            if (newExpanded != null && season.id !in episodes.keys) {
                                onLoadEpisodes(season.id)
                            }
                        },
                        onCheckboxClick = { selection.toggleSeason(season.id) },
                        checkboxEnabled = !isLoading,
                    )
                }

                if (isExpanded) {
                    if (isLoadingThis) {
                        item(key = "season-${season.id}-loading", contentType = "loading") {
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
                        }
                    } else if (seasonEpisodes.isEmpty() && season.id in episodes.keys) {
                        item(key = "season-${season.id}-empty", contentType = "empty") {
                            Text(
                                text = stringResource(Res.string.detail_no_episodes_available),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
                            )
                        }
                    } else {
                        val selectedInSeason = selection.selectedForSeason(season.id)
                        itemsIndexed(
                            seasonEpisodes,
                            key = { _, episode -> "season-${season.id}-ep-${episode.id}" },
                            contentType = { _, _ -> "episode" },
                        ) { idx, episode ->
                            // The horizontal inset replaces the padding the Column
                            // around the episode list used to apply; the
                            // LazyColumn's 4 dp item spacing supplies the rest.
                            Column(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 4.dp),
                            ) {
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
                                    SeasonEpisodeMetaLabels(
                                        episode = episode,
                                        nameColor = if (isDownloaded)
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (isDownloaded) {
                                        Text(
                                            text = stringResource(Res.string.detail_downloaded_status),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    seasonDividerItem(season.id)
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
                Text(stringResource(Res.string.detail_cancel))
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
                    if (isDownloading) stringResource(Res.string.detail_queuing) else "$totalSelectedCount",
                )
            }
        }
    }
}
