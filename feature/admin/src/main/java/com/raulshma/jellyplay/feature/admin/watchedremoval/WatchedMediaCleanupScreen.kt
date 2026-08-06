package com.raulshma.jellyplay.feature.admin.watchedremoval

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Shield
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.feature.admin.filterSelectedForDeletion
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.R
import com.raulshma.jellyplay.feature.admin.components.AuditHistoryTab
import com.raulshma.jellyplay.feature.admin.stalemedia.MediaSortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchedMediaCleanupScreen(
    onBack: () -> Unit,
    viewModel: WatchedMediaCleanupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // TV focus-on-launch: focus the first result/scan-button once content arrives so D-pad input
    // lands on content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    val focusableItemCount = when (selectedTab) {
        0 -> if (state.isLoading) 0 else state.scanResults.size.coerceAtLeast(1)
        else -> 1
    }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = focusableItemCount,
        tag = "watched_cleanup_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(R.string.admin_watched_cleanup_title),
        onBack = onBack,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.admin_scan_results_tab)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.admin_configuration_tab)) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.admin_audit_history_tab)) },
                )
            }

            when (selectedTab) {
                0 -> WatchedScanResultsTab(
                    state = state,
                    onSelectAll = { viewModel.selectAll() },
                    onToggleItem = { viewModel.toggleItemSelection(it) },
                    onDeleteClick = { viewModel.showDeleteConfirmation() },
                    onConfirmDelete = { viewModel.deleteSelected() },
                    onDismissDelete = { viewModel.dismissDeleteConfirmation() },
                    onSortChange = { viewModel.updateSort(it) },
                    bottomPadding = adaptiveInfo.bottomPadding(),
                    listFocusRequester = listFocusRequester,
                )
                1 -> WatchedConfigurationTab(
                    config = state.config,
                    onConfigChange = { viewModel.updateConfig(it) },
                    onScan = { viewModel.startScan() },
                    isScanning = state.scanProgress.phase == ScanPhase.SCANNING,
                )
                2 -> AuditHistoryTab(entries = state.auditEntries)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchedSortDropdown(
    currentSort: MediaSortOption,
    onSortChange: (MediaSortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = currentSort.label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            textStyle = MaterialTheme.typography.labelMedium,
            singleLine = true,
            shape = ShapeCache.smooth12,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MediaSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (option == currentSort) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSortChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun WatchedScanResultsTab(
    state: WatchedMediaState,
    onSelectAll: () -> Unit,
    onToggleItem: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onSortChange: (MediaSortOption) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    listFocusRequester: FocusRequester,
) {
    val deleteFocusState = rememberTvFocusState(focusedScale = 1.05f)
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.error != null) {
            Card(
                shape = ShapeCache.smooth12,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        if (state.scanProgress.phase == ScanPhase.SCANNING) {
            Card(
                shape = ShapeCache.smooth16,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.admin_scanning), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    JellyPlayLinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(ShapeCache.smooth4),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.admin_scan_progress_watched, state.scanProgress.scanned, state.scanProgress.itemsFound),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.scanResults.isNotEmpty()) {
            if (!state.canDeleteContent) {
                Card(
                    shape = ShapeCache.smooth12,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Tabler.Outline.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Your account does not have permission to delete content. Contact your server administrator.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(ShapeCache.smooth12)
                        .focusIndicator()
                        .clickable(onClick = onSelectAll)
                ) {
                    Checkbox(
                        checked = state.selectedItems.size == state.scanResults.size && state.scanResults.isNotEmpty(),
                        onCheckedChange = { onSelectAll() },
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.admin_select_all), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                if (state.selectedItems.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.admin_n_selected, state.selectedItems.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onDeleteClick,
                        enabled = state.canDeleteContent,
                        modifier = Modifier
                            .then(deleteFocusState.focusModifier)
                            .tvFocusIndicator(deleteFocusState, ShapeCache.smooth12),
                        shape = ShapeCache.smooth16,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(Tabler.Outline.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.admin_delete))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.admin_n_items, state.scanResults.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                WatchedSortDropdown(
                    currentSort = state.sortOption,
                    onSortChange = onSortChange,
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        if (state.scanResults.isEmpty() && state.scanProgress.phase != ScanPhase.SCANNING && !state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScreenEmptyState(
                    icon = Tabler.Outline.EyeOff,
                    title = stringResource(if (state.scanProgress.phase == ScanPhase.COMPLETED) R.string.admin_no_watched_found else R.string.admin_run_scan_watched),
                )
            }
        } else if (state.scanResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRestorer()
                    .focusRequester(listFocusRequester),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.scanResults, key = { it.itemId }, contentType = { "watchedItem" }) { item ->
                    WatchedItemCard(
                        item = item,
                        isSelected = state.selectedItems.contains(item.itemId),
                        onToggle = { onToggleItem(item.itemId) },
                    )
                }
            }
        }
    }

    // Memoize the selected-for-deletion list so we don't re-filter — and re-run
    // the `scanResults` computed-sort getter — on every recomposition while the
    // sheet is open. Recomputes only when the underlying data/sort/selection moves.
    val itemsToDelete = remember(state.rawScanResults, state.sortOption, state.selectedItems) {
        state.scanResults.filterSelectedForDeletion(state.selectedItems)
    }
    if (state.showDeleteConfirmation) {
        WatchedDeleteConfirmationSheet(
            items = itemsToDelete,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
            isDeleting = state.isDeleting,
        )
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchedDeleteConfirmationSheet(
    items: List<MediaItemStub>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDeleting: Boolean,
) {
    val isTv = LocalTvMode.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val content: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.admin_confirm_deletion), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(stringResource(R.string.admin_confirm_deletion_body, items.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.height(300.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(items, key = { it.itemId }, contentType = { "watchedItem" }) { item ->
                    Card(shape = ShapeCache.smooth12, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text("${item.type}${if (item.detail.isNotBlank()) " · ${item.detail}" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, shape = ShapeCache.smooth16, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.admin_cancel)) }
                Button(
                    onClick = onConfirm,
                    shape = ShapeCache.smooth16,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                    enabled = !isDeleting,
                ) { Text(if (isDeleting) stringResource(R.string.admin_deleting) else stringResource(R.string.admin_delete_from_library)) }
            }
        }
    }
    if (isTv) {
        TvSafeSheet(onDismissRequest = onDismiss, content = content)
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = ShapeCache.smoothTop28,
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
            content = content,
        )
    }
}

@Composable
private fun WatchedConfigurationTab(
    config: com.raulshma.jellyplay.core.model.MediaCleanupConfig,
    onConfigChange: (com.raulshma.jellyplay.core.model.MediaCleanupConfig) -> Unit,
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
                    Text(stringResource(R.string.admin_cleanup_configuration), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(20.dp))

                    Text(stringResource(R.string.admin_media_types), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    val allTypes = listOf(
                        "Movie" to R.string.admin_type_movie,
                        "Episode" to R.string.admin_type_episode,
                        "MusicVideo" to R.string.admin_type_music_video,
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
                        Text(stringResource(R.string.admin_minimum_days_watched, config.minDaysSinceWatched), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(stringResource(R.string.admin_no_minimum_time), style = MaterialTheme.typography.bodyMedium)
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
                            Text(stringResource(R.string.admin_keep_favorites), style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = config.keepFavorites, onCheckedChange = { onConfigChange(config.copy(keepFavorites = it)) })
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.admin_include_partially_watched), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = config.includePartiallyWatched, onCheckedChange = { onConfigChange(config.copy(includePartiallyWatched = it)) })
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.admin_dry_run), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
                Text(if (isScanning) stringResource(R.string.admin_scanning) else stringResource(R.string.admin_scan_now))
            }
        }
    }
}
