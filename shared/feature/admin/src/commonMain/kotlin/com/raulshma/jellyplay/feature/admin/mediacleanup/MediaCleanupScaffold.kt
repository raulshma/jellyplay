package com.raulshma.jellyplay.feature.admin.mediacleanup

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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Shield
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.components.AuditHistoryTab
import com.raulshma.jellyplay.feature.admin.filterSelectedForDeletion
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_audit_history_tab
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cancel
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_configuration_tab
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_confirm_deletion
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_confirm_deletion_body
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_delete
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_delete_from_library
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_deleting
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_n_items
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_n_selected
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_delete_permission
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scan_results_tab
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_scanning
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_select_all
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_sort_date
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_sort_default
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_sort_largest
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_sort_name_asc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_sort_name_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_sort_smallest
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_sort_type
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The one cleanup-scan screen chassis the admin twins render through: the
 * three-tab scaffold (Results / Configuration / Audit), the results tab
 * (error card, scanning card, permission banner, select-all row, "n selected"
 * chip, delete button, sort dropdown, results list, empty state), and the
 * delete-confirmation sheet. Everything that was byte-identical between
 * `StaleMediaScreen` and `WatchedMediaCleanupScreen` lives here once.
 *
 * Per-feature slots and labels:
 *  - [itemCard] — the feature's item row renderer;
 *  - [configTab] — the feature's configuration form (threshold sliders,
 *    toggles and type chips genuinely differ);
 *  - [scanProgressLabel] / [emptyStateIcon] / [noResultsTitle] / [runScanTitle]
 *    — the stale-vs-watched wording;
 *  - [sheetItemSubtitle] — the confirmation-sheet row's second line;
 *  - [tvFocusTag] / [itemContentType] — per-feature focus/LazyColumn keys.
 *
 * Declared delta (visual unification, screens): the two former delete buttons
 * had drifted — stale had a press-scale animation with a smooth16 TV focus
 * ring, watched had a focusedScale=1.05f ring on smooth12. The chassis keeps
 * ONE treatment (the stale one: press-scale + smooth16 ring, matching both
 * config tabs' scan-button press-scale language); the watched variant is gone
 * as copy drift, not a product decision.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCleanupScreenScaffold(
    title: String,
    onBack: () -> Unit,
    state: MediaCleanupScanState,
    tvFocusTag: String,
    scanProgressLabel: @Composable (scanned: Int, itemsFound: Int) -> String,
    emptyStateIcon: ImageVector,
    noResultsTitle: String,
    runScanTitle: String,
    itemContentType: String,
    sheetItemSubtitle: (MediaItemStub) -> String,
    onToggleItem: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onSortChange: (MediaSortOption) -> Unit,
    onConfigChange: (MediaCleanupConfig) -> Unit,
    onScan: () -> Unit,
    itemCard: @Composable (item: MediaItemStub, isSelected: Boolean, onToggle: () -> Unit) -> Unit,
    configTab: @Composable (
        config: MediaCleanupConfig,
        onConfigChange: (MediaCleanupConfig) -> Unit,
        onScan: () -> Unit,
        isScanning: Boolean,
    ) -> Unit,
) {
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
        tag = tvFocusTag,
    )

    JellyPlayScreenScaffold(
        title = title,
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
                    text = { Text(stringResource(Res.string.admin_scan_results_tab)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(Res.string.admin_configuration_tab)) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(Res.string.admin_audit_history_tab)) },
                )
            }

            when (selectedTab) {
                0 -> ScanResultsTab(
                    state = state,
                    scanProgressLabel = scanProgressLabel,
                    emptyStateIcon = emptyStateIcon,
                    noResultsTitle = noResultsTitle,
                    runScanTitle = runScanTitle,
                    itemContentType = itemContentType,
                    sheetItemSubtitle = sheetItemSubtitle,
                    onSelectAll = onSelectAll,
                    onToggleItem = onToggleItem,
                    onDeleteClick = onDeleteClick,
                    onConfirmDelete = onConfirmDelete,
                    onDismissDelete = onDismissDelete,
                    onSortChange = onSortChange,
                    bottomPadding = adaptiveInfo.bottomPadding(),
                    listFocusRequester = listFocusRequester,
                    itemCard = itemCard,
                )
                1 -> configTab(
                    state.config,
                    onConfigChange,
                    onScan,
                    state.scanProgress.phase == ScanPhase.SCANNING,
                )
                2 -> AuditHistoryTab(entries = state.auditEntries)
            }
        }
    }
}

/** Sort labels are resources, not enum literals — the screens localize. */
private fun MediaSortOption.labelRes(): StringResource = when (this) {
    MediaSortOption.DEFAULT -> Res.string.admin_sort_default
    MediaSortOption.NAME_ASC -> Res.string.admin_sort_name_asc
    MediaSortOption.NAME_DESC -> Res.string.admin_sort_name_desc
    MediaSortOption.SIZE_DESC -> Res.string.admin_sort_largest
    MediaSortOption.SIZE_ASC -> Res.string.admin_sort_smallest
    MediaSortOption.TYPE -> Res.string.admin_sort_type
    MediaSortOption.DATE -> Res.string.admin_sort_date
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaCleanupSortDropdown(
    currentSort: MediaSortOption,
    onSortChange: (MediaSortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = stringResource(currentSort.labelRes()),
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
                            stringResource(option.labelRes()),
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
private fun ScanResultsTab(
    state: MediaCleanupScanState,
    scanProgressLabel: @Composable (scanned: Int, itemsFound: Int) -> String,
    emptyStateIcon: ImageVector,
    noResultsTitle: String,
    runScanTitle: String,
    itemContentType: String,
    sheetItemSubtitle: (MediaItemStub) -> String,
    onSelectAll: () -> Unit,
    onToggleItem: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onSortChange: (MediaSortOption) -> Unit,
    bottomPadding: Dp = 0.dp,
    listFocusRequester: FocusRequester,
    itemCard: @Composable (item: MediaItemStub, isSelected: Boolean, onToggle: () -> Unit) -> Unit,
) {
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
                    state.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        if (state.scanProgress.phase == ScanPhase.SCANNING) {
            Card(
                shape = ShapeCache.smooth16,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.admin_scanning),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    JellyPlayLinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(ShapeCache.smooth4),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        scanProgressLabel(state.scanProgress.scanned, state.scanProgress.itemsFound),
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
                            stringResource(Res.string.admin_no_delete_permission),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(ShapeCache.smooth12)
                        .focusIndicator()
                        .clickable(onClick = onSelectAll),
                ) {
                    Checkbox(
                        checked = state.selectedItems.size == state.scanResults.size && state.scanResults.isNotEmpty(),
                        onCheckedChange = { onSelectAll() },
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.admin_select_all), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                if (state.selectedItems.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(Res.string.admin_n_selected, state.selectedItems.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val deleteInteractionSource = remember { MutableInteractionSource() }
                    val isPressed by deleteInteractionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "deleteBtnScale",
                    )
                    val deleteFocusState = rememberTvFocusState()
                    FilledTonalButton(
                        onClick = onDeleteClick,
                        enabled = state.canDeleteContent,
                        shape = ShapeCache.smooth16,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .then(deleteFocusState.focusModifier)
                            .tvFocusIndicator(deleteFocusState, ShapeCache.smooth16),
                        interactionSource = deleteInteractionSource,
                    ) {
                        Icon(Tabler.Outline.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.admin_delete))
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
                    stringResource(Res.string.admin_n_items, state.scanResults.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                MediaCleanupSortDropdown(
                    currentSort = state.sortOption,
                    onSortChange = onSortChange,
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        if (state.scanResults.isEmpty() && state.scanProgress.phase != ScanPhase.SCANNING && !state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ScreenEmptyState(
                    icon = emptyStateIcon,
                    title = if (state.scanProgress.phase == ScanPhase.COMPLETED) noResultsTitle else runScanTitle,
                )
            }
        } else if (state.scanResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRestorer()
                    .focusRequester(listFocusRequester),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.scanResults, key = { it.itemId }, contentType = { itemContentType }) { item ->
                    itemCard(
                        item,
                        state.selectedItems.contains(item.itemId),
                        { onToggleItem(item.itemId) },
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
        MediaCleanupDeleteConfirmationSheet(
            items = itemsToDelete,
            sheetItemSubtitle = sheetItemSubtitle,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
            isDeleting = state.isDeleting,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaCleanupDeleteConfirmationSheet(
    items: List<MediaItemStub>,
    sheetItemSubtitle: (MediaItemStub) -> String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDeleting: Boolean,
) {
    val isTv = LocalTvMode.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val content: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.admin_confirm_deletion),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                stringResource(Res.string.admin_confirm_deletion_body, items.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.height(300.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(items, key = { it.itemId }, contentType = { "cleanupConfirmationItem" }) { item ->
                    Card(
                        shape = ShapeCache.smooth12,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    sheetItemSubtitle(item),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = ShapeCache.smooth16,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.admin_cancel)) }
                Button(
                    onClick = onConfirm,
                    shape = ShapeCache.smooth16,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.weight(1f),
                    enabled = !isDeleting,
                ) { Text(if (isDeleting) stringResource(Res.string.admin_deleting) else stringResource(Res.string.admin_delete_from_library)) }
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
