package com.raulshma.jellyplay.feature.requests

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.raulshma.jellyplay.core.ui.components.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Inbox
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.SelectionActionBar
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.requests.generated.resources.Res
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_action_approve
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_action_decline
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_empty_subtitle
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_empty_title
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_error_unknown
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_pagination_label
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_pagination_next
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_pagination_prev
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_select_all
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_selected_count
import com.raulshma.jellyplay.feature.requests.generated.resources.requests_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    viewModel: RequestsViewModel = koinViewModel(),
) {
    val state by viewModel.state
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    // The hot enrichment maps are NOT collected here: each row collects its
    // own slice through the VM's per-request flows (the
    // photoFolderChildUrlsFor pattern), so an enrichment merge — which
    // produces a new Map reference — invalidates only the one row whose
    // info changed, not the whole screen body.

    var selectedRequest by remember { mutableStateOf<SeerrRequestItem?>(null) }

    // TV focus-on-launch: focus the first request row once data arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (state.isLoading && state.requests.isEmpty()) 0 else state.requests.size,
        tag = "requests_init",
    )

    // The repository's shared background poll (badge + current user) is bound
    // to screen visibility: it runs only while Requests is foregrounded, not
    // for the VM's whole lifetime in the back stack (the refresh cycle
    // module's binding discipline; the loop itself lives behind the
    // repository's refcounted start/stop).
    LifecycleStartEffect(Unit) {
        viewModel.startBackgroundPolling()
        onStopOrDispose { viewModel.stopBackgroundPolling() }
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.requests_title),
        onBack = onBack,
    ) { paddingValues ->
        val bottomPadding = paddingValues.calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                RequestsFilterBar(
                    filters = state.filters,
                    isAdmin = isAdmin,
                    onFilterChange = { viewModel.setFilter(it) },
                    onMediaTypeChange = { viewModel.setMediaType(it) },
                    onSortChange = { viewModel.setSort(it) },
                    onSortDirectionToggle = { viewModel.toggleSortDirection() },
                    onMyRequestsToggle = { viewModel.toggleMyRequestsOnly() },
                    onSearchChange = { viewModel.setSearchQuery(it) },
                )

                PullToRefreshBox(
                    isRefreshing = state.isLoading && state.requests.isNotEmpty(),
                    onRefresh = { viewModel.loadRequests(refresh = true) },
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isLoading && state.requests.isEmpty()) {
                        ScreenLoadingState()
                    } else if (state.error != null && state.requests.isEmpty()) {
                        ErrorScreen(
                            message = state.error ?: stringResource(Res.string.requests_error_unknown),
                            onRetry = { viewModel.loadRequests(refresh = true) },
                        )
                    } else if (state.requests.isEmpty()) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Inbox,
                            title = stringResource(Res.string.requests_empty_title),
                            description = stringResource(Res.string.requests_empty_subtitle),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .tvFocusRestorer()
                                .focusRequester(listFocusRequester),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 8.dp,
                                end = 16.dp,
                                bottom = 8.dp + bottomPadding,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.requests, key = { it.id }) { request ->
                                // Per-row slice collection: the row subscribes
                                // only to its own media-info slice (seeded from
                                // the map's current value so first composition
                                // renders synchronously, exactly as the old
                                // root-map read did). An enrichment merge now
                                // recomposes only the one row whose info changed.
                                val requestMediaInfo by remember(request.media.tmdbId) {
                                    viewModel.mediaInfoFor(request.media.tmdbId)
                                }.collectAsStateWithLifecycle(
                                    viewModel.mediaInfo.value[request.media.tmdbId],
                                )
                                // Action lambdas remembered so unchanged rows skip:
                                // SeerrRequestItem is @Immutable and every other
                                // parameter is a stable value, so fresh lambdas were
                                // the only thing forcing each visible row to
                                // recompose on screen-level state churn (selection
                                // toggles, action-in-progress flips).
                                val onApprove = remember(request) { { viewModel.approveRequest(request.id) } }
                                val onDecline = remember(request) { { viewModel.declineRequest(request.id) } }
                                val onRetry = remember(request) { { viewModel.retryRequest(request.id) } }
                                val onDelete = remember(request) { { viewModel.deleteRequest(request.id) } }
                                val selectionMode = state.selectionMode
                                val onClick = remember(request, selectionMode) {
                                    {
                                        if (selectionMode) viewModel.toggleSelection(request)
                                        else selectedRequest = request
                                    }
                                }
                                val onLongClick = remember(request) { { viewModel.toggleSelection(request) } }
                                RequestListItem(
                                    request = request,
                                    mediaInfo = requestMediaInfo,
                                    isAdmin = isAdmin,
                                    actionInProgress = state.actionInProgress,
                                    selectionMode = selectionMode,
                                    isSelected = request.id in state.selectedRequestIds,
                                    onApprove = onApprove,
                                    onDecline = onDecline,
                                    onRetry = onRetry,
                                    onDelete = onDelete,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                )
                            }
                            item {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = state.actionError != null,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    state.actionError?.let { error ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                text = error,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.totalPages > 1) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { viewModel.prevPage() },
                                            enabled = state.currentPage > 1,
                                            shape = ShapeCache.smooth12,
                                            modifier = Modifier.focusIndicator(),
                                        ) {
                                            Icon(
                                                Tabler.Outline.ChevronLeft,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(Res.string.requests_pagination_prev))
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Text(
                                            stringResource(Res.string.requests_pagination_label, state.currentPage, state.totalPages),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { viewModel.nextPage() },
                                            enabled = state.currentPage < state.totalPages,
                                            shape = ShapeCache.smooth12,
                                            modifier = Modifier.focusIndicator(),
                                        ) {
                                            Text(stringResource(Res.string.requests_pagination_next))
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                Tabler.Outline.ChevronRight,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            selectedRequest?.let { request ->
                // Same per-slice discipline as the rows: the open sheet
                // subscribes only to the selected request's slices (seeded
                // from the maps' current values, so opening is synchronous).
                val sheetMediaInfo by remember(request.media.tmdbId) {
                    viewModel.mediaInfoFor(request.media.tmdbId)
                }.collectAsStateWithLifecycle(
                    viewModel.mediaInfo.value[request.media.tmdbId],
                )
                val sheetDownloadProgress by remember(request.media.tmdbId) {
                    viewModel.downloadProgressFor(request.media.tmdbId)
                }.collectAsStateWithLifecycle(
                    viewModel.downloadProgress.value[request.media.tmdbId],
                )
                RequestDetailBottomSheet(
                    request = request,
                    mediaInfo = sheetMediaInfo,
                    isAdmin = isAdmin,
                    downloadProgress = sheetDownloadProgress,
                    onRemoveFromQueue = { b, s ->
                        viewModel.removeQueueItem(request.media.tmdbId, b, s)
                    },
                    onDismiss = { selectedRequest = null },
                    onApprove = {
                        viewModel.approveRequest(request.id)
                        selectedRequest = null
                    },
                    onDecline = {
                        viewModel.declineRequest(request.id)
                        selectedRequest = null
                    },
                    onRetry = {
                        viewModel.retryRequest(request.id)
                        selectedRequest = null
                    },
                    onDelete = {
                        viewModel.deleteRequest(request.id)
                        selectedRequest = null
                    },
                    onRemoveFromService = {
                        viewModel.removeFromService(request.media.id, request.is4k)
                        selectedRequest = null
                    },
                    onNavigateToDetail = { tmdbId, mediaType ->
                        onNavigateToDetail(tmdbId, mediaType)
                        selectedRequest = null
                    },
                )
            }

            // Bulk-selection action bar.
            if (state.selectionMode) {
                SelectionActionBar(
                    countLabel = stringResource(Res.string.requests_selected_count, state.selectedRequestIds.size),
                    selectedCount = state.selectedRequestIds.size,
                    selectAllLabel = stringResource(Res.string.requests_select_all),
                    clearLabel = stringResource(CoreUiRes.string.core_cancel),
                    onSelectAll = { viewModel.selectAll() },
                    onClear = { viewModel.clearSelection() },
                    actionInProgress = state.actionInProgress,
                    actions = { actionsEnabled ->
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { viewModel.approveSelected() },
                            enabled = actionsEnabled,
                        ) {
                            Text(stringResource(Res.string.requests_action_approve))
                        }
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { viewModel.declineSelected() },
                            enabled = actionsEnabled,
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Text(stringResource(Res.string.requests_action_decline))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}
