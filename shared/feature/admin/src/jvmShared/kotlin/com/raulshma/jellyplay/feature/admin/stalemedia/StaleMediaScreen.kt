package com.raulshma.jellyplay.feature.admin.stalemedia

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.composables.icons.tabler.outline.Search
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.feature.admin.mediacleanup.MediaCleanupScreenScaffold
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_dry_run
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_include_never_played
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_media_types
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_stale_found
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_run_scan_stale
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scan_configuration
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scan_now
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scan_progress_stale
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scanning
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_stale_media_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_stale_threshold
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_type_audio
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_type_book
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_type_episode
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_type_movie
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_type_music_video
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_type_series
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_use_date_added
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StaleMediaScreen(
    onBack: () -> Unit,
    viewModel: StaleMediaViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MediaCleanupScreenScaffold(
        title = stringResource(Res.string.admin_stale_media_title),
        onBack = onBack,
        state = state,
        tvFocusTag = "stale_media_init",
        scanProgressLabel = { scanned, itemsFound ->
            stringResource(Res.string.admin_scan_progress_stale, scanned, itemsFound)
        },
        emptyStateIcon = Tabler.Outline.Search,
        noResultsTitle = stringResource(Res.string.admin_no_stale_found),
        runScanTitle = stringResource(Res.string.admin_run_scan_stale),
        itemContentType = "staleItem",
        sheetItemSubtitle = { item ->
            "${item.type}${if (item.sizeText.isNotBlank()) " · ${item.sizeText}" else ""}"
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
            StaleItemCard(item = item, isSelected = isSelected, onToggle = onToggle)
        },
        configTab = { config, onConfigChange, onScan, isScanning ->
            ConfigurationTab(
                config = config,
                onConfigChange = onConfigChange,
                onScan = onScan,
                isScanning = isScanning,
            )
        },
    )
}

@Composable
private fun StaleItemCard(
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
                modifier = Modifier
                    .size(44.dp)
                    .clip(ShapeCache.smooth8)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
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
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            item.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.detail.isNotBlank()) {
                        Text(
                            item.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            Text(
                                epLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (item.dateText != null) {
                            Text(
                                item.dateText!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                    Text(
                        item.sizeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigurationTab(
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
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(Res.string.admin_scan_configuration),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(20.dp))

                    Text(stringResource(Res.string.admin_stale_threshold, config.daysThreshold), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = config.daysThreshold.toFloat(),
                        onValueChange = { onConfigChange(config.copy(daysThreshold = it.toInt())) },
                        valueRange = 30f..365f,
                        steps = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Res.string.admin_use_date_added), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = config.useDateAdded,
                            onCheckedChange = { onConfigChange(config.copy(useDateAdded = it)) },
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Res.string.admin_include_never_played), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = config.includeNeverPlayed,
                            onCheckedChange = { onConfigChange(config.copy(includeNeverPlayed = it)) },
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(stringResource(Res.string.admin_media_types), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    val allTypes = listOf(
                        "Movie" to Res.string.admin_type_movie,
                        "Series" to Res.string.admin_type_series,
                        "Episode" to Res.string.admin_type_episode,
                        "Audio" to Res.string.admin_type_audio,
                        "MusicVideo" to Res.string.admin_type_music_video,
                        "Book" to Res.string.admin_type_book,
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        allTypes.forEach { (type, typeRes) ->
                            FilterChip(
                                selected = config.includeItemTypes.contains(type),
                                onClick = {
                                    val newTypes = if (config.includeItemTypes.contains(type)) {
                                        config.includeItemTypes - type
                                    } else {
                                        config.includeItemTypes + type
                                    }
                                    if (newTypes.isNotEmpty()) onConfigChange(config.copy(includeItemTypes = newTypes))
                                },
                                label = { Text(stringResource(typeRes)) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Res.string.admin_dry_run), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = config.dryRun,
                            onCheckedChange = { onConfigChange(config.copy(dryRun = it)) },
                        )
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
            val scanFocusState = rememberTvFocusState()
            FilledTonalButton(
                onClick = onScan,
                shape = ShapeCache.smooth16,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .then(scanFocusState.focusModifier)
                    .tvFocusIndicator(scanFocusState, ShapeCache.smooth16),
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
