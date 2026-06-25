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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Inbox
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.state
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()

    var selectedRequest by remember { mutableStateOf<SeerrRequestItem?>(null) }

    JellyPlayScreenScaffold(
        title = "Requests",
        onBack = onBack,
    ) { paddingValues ->
        val bottomPadding = paddingValues.calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                RequestsFilterBar(
                    currentFilter = state.filter,
                    currentMediaType = state.mediaType,
                    currentSort = state.sort,
                    currentSortDirection = state.sortDirection,
                    showMyRequestsOnly = state.showMyRequestsOnly,
                    isAdmin = isAdmin,
                    onFilterChange = { viewModel.setFilter(it) },
                    onMediaTypeChange = { viewModel.setMediaType(it) },
                    onSortChange = { viewModel.setSort(it) },
                    onSortDirectionToggle = { viewModel.toggleSortDirection() },
                    onMyRequestsToggle = { viewModel.toggleMyRequestsOnly() },
                )

                PullToRefreshBox(
                    isRefreshing = state.isLoading && state.requests.isNotEmpty(),
                    onRefresh = { viewModel.loadRequests(refresh = true) },
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isLoading && state.requests.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            JellyPlayCircularProgressIndicator(modifier = Modifier.size(48.dp))
                        }
                    } else if (state.error != null && state.requests.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.error ?: "Unknown error",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(12.dp))
                                androidx.compose.material3.TextButton(onClick = { viewModel.loadRequests(refresh = true) }) {
                                    Text("Retry")
                                }
                            }
                        }
                    } else if (state.requests.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Tabler.Outline.Inbox,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "No requests found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Try adjusting your filters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 8.dp,
                                end = 16.dp,
                                bottom = 8.dp + bottomPadding,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.requests, key = { it.id }) { request ->
                                RequestListItem(
                                    request = request,
                                    mediaInfo = state.mediaInfo[request.media.tmdbId],
                                    isAdmin = isAdmin,
                                    actionInProgress = state.actionInProgress,
                                    onApprove = { viewModel.approveRequest(request.id) },
                                    onDecline = { viewModel.declineRequest(request.id) },
                                    onRetry = { viewModel.retryRequest(request.id) },
                                    onDelete = { viewModel.deleteRequest(request.id) },
                                    onClick = { selectedRequest = request },
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
                                        ) {
                                            Icon(
                                                Tabler.Outline.ChevronLeft,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Prev")
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Text(
                                            "${state.currentPage} / ${state.totalPages}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { viewModel.nextPage() },
                                            enabled = state.currentPage < state.totalPages,
                                            shape = ShapeCache.smooth12,
                                        ) {
                                            Text("Next")
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
                RequestDetailBottomSheet(
                    request = request,
                    mediaInfo = state.mediaInfo[request.media.tmdbId],
                    isAdmin = isAdmin,
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
        }
    }
}
