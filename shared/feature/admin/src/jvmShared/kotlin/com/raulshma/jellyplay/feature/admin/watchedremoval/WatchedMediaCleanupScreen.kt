package com.raulshma.jellyplay.feature.admin.watchedremoval

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.Search
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.feature.admin.mediacleanup.MediaCleanupScreenScaffold
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cleanup_configuration
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_dry_run
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_include_partially_watched
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_keep_favorites
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_media_types
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_minimum_days_watched
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_minimum_time
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_watched_found
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_run_scan_watched
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scan_now
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scan_progress_watched
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scanning
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_type_episode
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_type_movie
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_type_music_video
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_watched_cleanup_title
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WatchedMediaCleanupScreen(
    onBack: () -> Unit,
    viewModel: WatchedMediaCleanupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MediaCleanupScreenScaffold(
        title = stringResource(Res.string.admin_watched_cleanup_title),
        onBack = onBack,
        state = state,
        tvFocusTag = "watched_cleanup_init",
        scanProgressLabel = { scanned, itemsFound ->
            stringResource(Res.string.admin_scan_progress_watched, scanned, itemsFound)
        },
        emptyStateIcon = Tabler.Outline.EyeOff,
        noResultsTitle = stringResource(Res.string.admin_no_watched_found),
        runScanTitle = stringResource(Res.string.admin_run_scan_watched),
        itemContentType = "watchedItem",
        sheetItemSubtitle = { item ->
            "${item.type}${if (item.detail.isNotBlank()) " · ${item.detail}" else ""}"
        },
        onToggleItem = viewModel::toggleItemSelection,
        onSelectAll = viewModel::selectAll,
        onDeleteClick = viewModel::showDeleteConfirmation,
        onConfirmDelete = viewModel::deleteSelected,
        onDismissDelete = viewModel::dismissDeleteConfirmation,
        onSortChange = viewModel::updateSort,
        onConfigChange = viewModel::updateConfig,
        onScan = viewModel::startScan,
        itemCard = { item, isSelected, onToggle ->
            WatchedItemCard(item = item, isSelected = isSelected, onToggle = onToggle)
        },
        configTab = { config, onConfigChange, onScan, isScanning ->
            WatchedConfigurationTab(
                config = config,
                onConfigChange = onConfigChange,
                onScan = onScan,
                isScanning = isScanning,
            )
        },
    )
}

@Composable
private fun WatchedItemCard(
    item: MediaItemStub,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        shape = ShapeCache.smooth12,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeCache.smooth12)
                .focusIndicator()
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(44.dp).clip(ShapeCache.smooth8).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.type.take(1),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier.clip(ShapeCache.smoothPill).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(item.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.detail.isNotBlank()) {
                        Text(item.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (item.seriesName != null || item.dateText != null) {
                    Spacer(Modifier.height(1.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (item.seriesName != null) {
                            val epLabel = buildString {
                                append(item.seriesName)
                                if (item.seasonNumber != null || item.episodeNumber != null) {
                                    append(" ")
                                    if (item.seasonNumber != null) append("S${item.seasonNumber}")
                                    if (item.episodeNumber != null) append("E${item.episodeNumber}")
                                }
                            }
                            Text(epLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (item.dateText != null) {
                            Text(item.dateText!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (item.sizeText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(ShapeCache.smooth8)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(item.sizeText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WatchedConfigurationTab(
    config: MediaCleanupConfig,
    onConfigChange: (MediaCleanupConfig) -> Unit,
    onScan: () -> Unit,
    isScanning: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                shape = ShapeCache.smooth20,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(Res.string.admin_cleanup_configuration), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(20.dp))

                    Text(stringResource(Res.string.admin_media_types), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    val allTypes = listOf(
                        "Movie" to Res.string.admin_type_movie,
                        "Episode" to Res.string.admin_type_episode,
                        "MusicVideo" to Res.string.admin_type_music_video,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allTypes.forEach { (type, typeRes) ->
                            FilterChip(
                                selected = config.includeItemTypes.contains(type),
                                onClick = {
                                    val newTypes = if (config.includeItemTypes.contains(type)) config.includeItemTypes - type else config.includeItemTypes + type
                                    if (newTypes.isNotEmpty()) onConfigChange(config.copy(includeItemTypes = newTypes))
                                },
                                label = { Text(stringResource(typeRes)) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (config.minDaysSinceWatched > 0) {
                        Text(stringResource(Res.string.admin_minimum_days_watched, config.minDaysSinceWatched), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(stringResource(Res.string.admin_no_minimum_time), style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = config.minDaysSinceWatched.toFloat(),
                        onValueChange = { onConfigChange(config.copy(minDaysSinceWatched = it.toInt())) },
                        valueRange = 0f..180f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Tabler.Outline.Heart, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.admin_keep_favorites), style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = config.keepFavorites, onCheckedChange = { onConfigChange(config.copy(keepFavorites = it)) })
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.admin_include_partially_watched), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = config.includePartiallyWatched, onCheckedChange = { onConfigChange(config.copy(includePartiallyWatched = it)) })
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.admin_dry_run), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = config.dryRun, onCheckedChange = { onConfigChange(config.copy(dryRun = it)) })
                    }
                }
            }
        }

        item {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.93f else 1f,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "scanBtnScale",
            )
            FilledTonalButton(
                onClick = onScan,
                shape = ShapeCache.smooth16,
                modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
                enabled = !isScanning,
                interactionSource = interactionSource,
            ) {
                Icon(Tabler.Outline.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isScanning) stringResource(Res.string.admin_scanning) else stringResource(Res.string.admin_scan_now))
            }
        }
    }
}
