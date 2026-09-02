package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowDown
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.quickActions
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.MediaQuickActionHost
import com.raulshma.jellyplay.core.ui.components.OfflineMediaCard
import com.raulshma.jellyplay.core.ui.components.QuickAction
import com.raulshma.jellyplay.core.ui.components.RemoveDownloadConfirmHost
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.rememberMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.rememberRemoveDownloadState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_clear_cd
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_item_count
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_library_title
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_no_downloaded_content
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_no_items_in_category
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_no_matches
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_on_device
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_search_cd
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_search_downloads_placeholder
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_sort_cd

@Composable
fun OfflineLibraryScreen(
    onSeriesClick: (seriesId: String) -> Unit,
    onItemClick: (itemId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: OfflineLibraryViewModel = koinViewModel(),
) {
    val items by viewModel.offlineLibrary.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoading = viewModel.isLoading
    val storageSummary by viewModel.storageSummary.collectAsStateWithLifecycle(
        initialValue = StorageSummary(0L, 0),
    )
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    var searchOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    // Removal goes through the shared confirm dialog, like every other host.
    val removeDownloadState = rememberRemoveDownloadState()

    // Long-press quick actions. Everything in this grid is downloaded, so the
    // sheet offers mark-watched / favorite / delete / view-details and routing
    // always lands on the offline detail screens (never the online page).
    val quickActionController = rememberMediaQuickActionController(
        resolveActions = remember {
            { item: MediaItem ->
                item.quickActions(
                    MediaQuickActionScope.LIBRARY,
                    includeDownload = false,
                    includeAddToPlaylist = false,
                    includeRemoveDownload = true,
                    includeFavorite = true,
                )
            }
        },
        executeAction = remember(viewModel, onItemClick, onSeriesClick, removeDownloadState) {
            { item: MediaItem, action: QuickAction ->
                when (action) {
                    // PLAY and DETAILS both open the offline detail/series screen,
                    // which owns the Play button and full offline metadata.
                    QuickAction.PLAY, QuickAction.DETAILS -> {
                        if (item.mediaType == com.raulshma.jellyplay.core.model.MediaType.SERIES) {
                            onSeriesClick(item.id)
                        } else {
                            onItemClick(item.id)
                        }
                    }
                    QuickAction.MARK_WATCHED -> viewModel.markItemPlayed(item, played = true)
                    QuickAction.MARK_UNWATCHED -> viewModel.markItemPlayed(item, played = false)
                    QuickAction.FAVORITE, QuickAction.UNFAVORITE -> viewModel.toggleFavorite(item)
                    QuickAction.REMOVE_DOWNLOAD -> removeDownloadState.request(item)
                    else -> Unit
                }
            }
        },
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.downloads_library_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                searchOpen = !searchOpen
                if (searchOpen) searchFocus.requestFocus()
            }) {
                Icon(if (searchOpen) Tabler.Outline.X else Tabler.Outline.Search, contentDescription = stringResource(Res.string.downloads_search_cd))
            }
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(Tabler.Outline.ArrowDown, contentDescription = stringResource(Res.string.downloads_sort_cd))
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    OfflineLibrarySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(option.labelRes),
                                    fontWeight = if (option == sort) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                viewModel.setSort(option)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }
        },
    ) {
        if (isLoading) {
            ScreenLoadingState()
        } else if (items.isEmpty() && query.isBlank()) {
            ScreenEmptyState(
                icon = Tabler.Outline.Download,
                title = stringResource(Res.string.downloads_no_downloaded_content),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                // Storage summary.
                if (storageSummary.itemCount > 0) {
                    StorageHeader(
                        totalBytes = storageSummary.totalBytes,
                        itemCount = storageSummary.itemCount,
                        modifier = Modifier.padding(horizontal = contentPad, vertical = 8.dp),
                    )
                }

                // Expandable search field.
                AnimatedVisibility(
                    visible = searchOpen,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(Res.string.downloads_search_downloads_placeholder)) },
                        leadingIcon = { Icon(Tabler.Outline.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Tabler.Outline.X, contentDescription = stringResource(Res.string.downloads_clear_cd))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = contentPad, vertical = 4.dp)
                            .focusRequester(searchFocus),
                    )
                }

                // Filter tabs.
                TabRow(
                    selectedTabIndex = filter.ordinal,
                    modifier = Modifier.padding(horizontal = contentPad),
                ) {
                    OfflineLibraryFilter.entries.forEach { option ->
                        Tab(
                            selected = filter == option,
                            onClick = { viewModel.setFilter(option) },
                            text = { Text(stringResource(option.labelRes)) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Search,
                            title = if (query.isNotBlank()) stringResource(Res.string.downloads_no_matches) else stringResource(Res.string.downloads_no_items_in_category),
                        )
                    }
                } else {
                    TvFocusableGrid(
                        items = items,
                        key = { it.id },
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(
                            start = contentPad,
                            end = contentPad,
                            top = 8.dp,
                            bottom = adaptiveInfo.bottomPadding(isTv),
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentType = { "offlineItem" },
                        modifier = Modifier.fillMaxSize(),
                    ) { _, item, itemModifier ->
                        CompositionLocalProvider(LocalMediaQuickActionController provides quickActionController) {
                            OfflineMediaCard(
                                item = item,
                                onClick = {
                                    if (item.mediaType == com.raulshma.jellyplay.core.model.MediaType.SERIES) {
                                        onSeriesClick(item.id)
                                    } else {
                                        onItemClick(item.id)
                                    }
                                },
                                modifier = itemModifier,
                            )
                        }
                    }
                }
            }
        }
    }
    MediaQuickActionHost(quickActionController)
    RemoveDownloadConfirmHost(
        state = removeDownloadState,
        onConfirmRemove = { viewModel.delete(it) },
    )
}

@Composable
private fun StorageHeader(
    totalBytes: Long,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            Tabler.Outline.DeviceFloppy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = pluralStringResource(Res.plurals.downloads_item_count, itemCount, itemCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        if (totalBytes > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(Res.string.downloads_on_device, totalBytes.formatBytes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
