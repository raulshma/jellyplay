package com.raulshma.jellyplay.feature.downloads

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.defaultContentSizeSpec
import com.raulshma.jellyplay.core.designsystem.theme.defaultSpatialSpring
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.rememberMultiEpisodeSelectionForDelete

/**
 * Multi-select delete sheet for an offline series. Mirrors the online
 * [com.raulshma.jellyplay.feature.details.SeriesDownloadSheet] layout — a
 * header, a select-all pill, a per-season LazyColumn with tri-state season
 * checkboxes and expandable episode rows, and a footer action row — but every
 * selection is destructive and styled with the error color scheme.
 *
 * Users can mix-and-match: whole series (Select All), whole seasons (season
 * checkbox), or individual episodes across seasons (episode checkbox). The
 * footer button reports the selected count and the bytes that will be freed,
 * and calls back with the selected episode ids.
 *
 * Episodes are already pre-loaded by [OfflineSeriesViewModel.episodes], so
 * there is no lazy-load machinery here: every season is immediately selectable.
 *
 * @param seasons season rows (for names + ordering); each must exist as a key in
 *   [episodes] (possibly mapped to an empty list).
 * @param episodes episodes keyed by season id.
 * @param onDelete invoked with the flat set of episode ids to remove.
 * @param onDismiss closes the sheet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeleteSeriesSheet(
    seasons: List<OfflineMediaItem>,
    episodes: Map<String, List<OfflineMediaItem>>,
    onDelete: (episodeIds: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selection = rememberMultiEpisodeSelectionForDelete(episodes)
    var expandedSeasonIds by remember { mutableStateOf(emptySet<String>()) }

    val totalSelectedCount = selection.totalSelectedCount
    // Delete-specific: bytes freed by the current selection. The base class
    // owns selection algebra; the byte total is a pure lookup over the selected
    // ids against the episode map this sheet already holds.
    val episodeById = remember(episodes) { episodes.values.flatten().associateBy { it.id } }
    val totalSelectedBytes = selection.selectedEpisodeIds.values.flatten().sumOf { id ->
        episodeById[id]?.totalSizeBytes ?: 0L
    }
    val allSelected = selection.allSelected
    val allSelectableIds = selection.allSelectableIds

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Cap the sheet body so the Cancel/Delete actions stay visible
            // without scrolling. Matches the download sheet.
            .heightIn(max = 560.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // ── Header ──
        SheetHeader(
            title = stringResource(R.string.downloads_delete_downloads_title),
            subtitle = stringResource(R.string.downloads_select_episodes_to_remove),
            icon = Tabler.Outline.Trash,
            onClose = onDismiss,
        )

        Spacer(Modifier.height(12.dp))

        // ── Select-all pill ──
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
                shape = ShapeCache.smoothPill,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (allSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (allSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.weight(1f),
            ) {
                val selectAllLabel = stringResource(R.string.downloads_select_all)
                val deselectAllLabel = stringResource(R.string.downloads_deselect_all)
                Text(if (allSelected) deselectAllLabel else selectAllLabel)
            }
        }

        // ── Selection progress (count + bytes freed) ──
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
                color = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(4.dp))
            val freedBytesStr = if (totalSelectedBytes > 0) totalSelectedBytes.formatBytes() else null
            Text(
                text = if (freedBytesStr != null)
                    stringResource(R.string.downloads_episodes_selected_with_freed, totalSelectedCount, allSelectableIds.size, freedBytesStr)
                else
                    stringResource(R.string.downloads_episodes_selected, totalSelectedCount, allSelectableIds.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Seasons + episodes ──
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(seasons, key = { it.id }, contentType = { "season" }) { season ->
                val isExpanded = season.id in expandedSeasonIds
                val seasonEpisodes = episodes[season.id].orEmpty()
                val selectedInSeason = selection.selectedForSeason(season.id)
                val triState = selection.triStateForSeason(season.id)

                val seasonBgColor by animateColorAsState(
                    targetValue = if (isExpanded) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
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
                                expandedSeasonIds = if (isExpanded) {
                                    expandedSeasonIds - season.id
                                } else {
                                    expandedSeasonIds + season.id
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(
                            state = triState,
                            onClick = { selection.toggleSeason(season.id) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = season.name.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.downloads_season_default, season.seasonNumber ?: 1),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (seasonEpisodes.isNotEmpty()) {
                                val seasonSize = seasonEpisodes.sumOf { it.totalSizeBytes }
                                Text(
                                    text = if (seasonSize > 0)
                                        stringResource(R.string.downloads_episodes_count_with_size, seasonEpisodes.size, seasonSize.formatBytes())
                                    else
                                        pluralStringResource(R.plurals.downloads_episodes_count, seasonEpisodes.size, seasonEpisodes.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            imageVector = Tabler.Outline.ChevronDown,
                            contentDescription = stringResource(if (isExpanded) R.string.downloads_collapse else R.string.downloads_expand),
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
                        ) {
                            seasonEpisodes.forEachIndexed { idx, episode ->
                                val isEpisodeSelected = episode.id in selectedInSeason
                                val shape = expressiveListShape(
                                    index = idx,
                                    count = seasonEpisodes.size,
                                    outerRadius = 14.dp,
                                    innerRadius = 8.dp,
                                )

                                val episodeBgColor by animateColorAsState(
                                    targetValue = if (isEpisodeSelected) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    } else {
                                        androidx.compose.ui.graphics.Color.Transparent
                                    },
                                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                    label = "epBg",
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(shape)
                                        .background(episodeBgColor)
                                        .clickable {
                                            selection.toggleEpisode(season.id, episode.id)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = isEpisodeSelected,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.error,
                                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = buildString {
                                                episode.episodeNumber?.let { append("E$it. ") }
                                                append(episode.name)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        val sizeText = episode.totalSizeBytes.takeIf { it > 0 }?.formatBytes()
                                        val minutes = episode.runTimeTicks?.let { it / 600_000_000 }
                                        val secondary = buildList {
                                            if (sizeText != null) add(sizeText)
                                            if (minutes != null) add("${minutes}m")
                                        }.joinToString(" · ")
                                        if (secondary.isNotEmpty()) {
                                            Text(
                                                text = secondary,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // ── Footer actions ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                shape = ShapeCache.smoothPill,
            ) {
                Text(stringResource(R.string.downloads_cancel))
            }
            Button(
                onClick = { onDelete(selection.toSelectedIds()) },
                enabled = totalSelectedCount > 0,
                shape = ShapeCache.smoothPill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.38f),
                ),
            ) {
                Icon(
                    imageVector = Tabler.Outline.Trash,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                val freedBytesStr = if (totalSelectedBytes > 0) totalSelectedBytes.formatBytes() else null
                Text(
                    if (freedBytesStr != null)
                        stringResource(R.string.downloads_delete_count_with_freed, totalSelectedCount, freedBytesStr)
                    else
                        stringResource(R.string.downloads_delete_count, totalSelectedCount),
                )
            }
        }
    }
}
