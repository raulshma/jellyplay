package com.raulshma.jellyplay.feature.arrqueue

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.Ban
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.Res
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_blocklist_search
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_brand_radarr
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_brand_sonarr
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_cancel
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_clear_selection
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_delete
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_disabled_body
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_disabled_title
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_empty_body
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_empty_title
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_grab
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_grab_message
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_grab_title
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_import
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_import_message
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_import_title
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_open_settings
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_refresh
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_remove_body_bulk
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_remove_body_single
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_remove_item_title
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_remove_only
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_remove_search
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_remove_selected_title
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_retry
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_select_all
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_selected_count
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_completed
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_downloading
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_failed
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_imported
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_paused
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_queued
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_unknown
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_warning
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_title
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_unknown_error
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArrQueueScreen(
    onBack: () -> Unit,
    onOpenArrSettings: () -> Unit = {},
    viewModel: ArrQueueViewModel = koinViewModel(),
) {
    val state by viewModel.state
    val featureEnabled by viewModel.featureEnabled.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current

    // One-shot action feedback (livetv screen-forward pattern): the VM emits
    // unresolved ArrQueueMessage values; this collector resolves them with the
    // suspend compose-resources getString — the args-bearing acks (release
    // title) can't be pre-resolved in composition the way livetv's two fixed
    // strings were — and forwards through the messenger actual (Android: the
    // app-wide UserMessageBus; desktop: null, messages drop). Collector is
    // screen-scoped, so an ack emitted just before a quick-back is dropped
    // (livetv-documented accepted delta).
    val messenger = rememberArrQueueMessenger()
    LaunchedEffect(messenger) {
        viewModel.messages.collect { message ->
            when (message) {
                is ArrQueueMessage.Info -> messenger?.info(getString(message.res, *message.args.toTypedArray()))
                is ArrQueueMessage.Error -> messenger?.error(getString(message.res, *message.args.toTypedArray()))
                is ArrQueueMessage.Raw -> messenger?.error(message.text)
            }
        }
    }

    // TV focus-on-launch: focus the first queue card once data arrives so D-pad
    // input lands on content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (!featureEnabled || state.isLoading || state.error != null) 0 else state.queue.size,
        tag = "arrqueue_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.arrqueue_title),
        onBack = onBack,
        actions = {
            val focusState = rememberTvFocusState()
            IconButton(
                onClick = { viewModel.refresh() },
                enabled = !state.isLoading,
                modifier = Modifier
                    .then(focusState.focusModifier)
                    .tvFocusIndicator(focusState, CircleShape),
            ) {
                Icon(Tabler.Outline.Refresh, contentDescription = stringResource(Res.string.arrqueue_refresh))
            }
        },
    ) { paddingValues ->
        val bottomPadding = paddingValues.calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !featureEnabled -> FeatureDisabledState(
                    onOpenSettings = onOpenArrSettings,
                    modifier = Modifier.fillMaxSize(),
                )

                state.isLoading && state.queue.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    JellyPlayCircularProgressIndicator(modifier = Modifier.size(48.dp))
                }

                state.error != null && state.queue.isEmpty() -> ErrorState(
                    message = state.error ?: stringResource(Res.string.arrqueue_unknown_error),
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                )

                state.queue.isEmpty() -> EmptyQueueState(modifier = Modifier.fillMaxSize())

                else -> PullToRefreshBox(
                    isRefreshing = state.isLoading && state.queue.isNotEmpty(),
                    onRefresh = { viewModel.refresh() },
                    enabled = !isTv,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .tvFocusRestorer()
                            .focusRequester(listFocusRequester),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = if (state.selectionMode) 88.dp else (16.dp + bottomPadding),
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.queue, key = { "${it.serverKind}|${it.queueId}|${it.serverId}" }) { item ->
                            QueueRow(
                                item = item,
                                selected = item.rowKey in state.selectedIds,
                                selectionMode = state.selectionMode,
                                actionInProgress = state.actionInProgress,
                                onClick = {
                                    if (state.selectionMode) viewModel.toggleSelection(item)
                                },
                                onLongClick = { viewModel.toggleSelection(item) },
                                onDelete = { viewModel.showDeleteDialog(item) },
                                onGrab = { viewModel.showGrabDialog(item) },
                                onImport = { viewModel.showImportDialog(item) },
                            )
                        }
                    }
                }
            }

            // Selection-mode bottom action bar.
            if (state.selectionMode) {
                SelectionActionBar(
                    selectedCount = state.selectedIds.size,
                    actionInProgress = state.actionInProgress,
                    onSelectAll = { viewModel.selectAll() },
                    onClear = { viewModel.clearSelection() },
                    onBulkDelete = {
                        // Surface the same 3-option delete dialog the single-row
                        // path uses, so a bulk delete isn't forced into "remove only"
                        // without the blocklist / search-again choices.
                        viewModel.showBulkDeleteDialog()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }

    // Inline action dialogs.
    state.pendingAction?.let { action ->
        when (action) {
            is ArrQueueAction.Delete -> DeleteActionDialog(
                item = action.item,
                bulk = false,
                onDismiss = { viewModel.dismissAction() },
                onConfirm = { blocklist, searchAgain ->
                    viewModel.deleteItem(action.item, blocklist, searchAgain)
                },
            )
            ArrQueueAction.BulkDelete -> DeleteActionDialog(
                item = null,
                bulk = true,
                onDismiss = { viewModel.dismissAction() },
                onConfirm = { blocklist, searchAgain ->
                    viewModel.deleteSelected(blocklist = blocklist, searchAgain = searchAgain)
                },
            )
            is ArrQueueAction.Grab -> ConfirmDialog(
                title = stringResource(Res.string.arrqueue_grab_title),
                message = stringResource(Res.string.arrqueue_grab_message, action.item.title),
                confirmText = stringResource(Res.string.arrqueue_grab),
                onConfirm = { viewModel.grabItem(action.item) },
                onDismiss = { viewModel.dismissAction() },
                dismissText = stringResource(Res.string.arrqueue_cancel),
                tone = ConfirmTone.NEUTRAL,
            )
            is ArrQueueAction.Import -> ConfirmDialog(
                title = stringResource(Res.string.arrqueue_import_title),
                message = stringResource(Res.string.arrqueue_import_message, action.item.title, serviceName(action.item.serverKind)),
                confirmText = stringResource(Res.string.arrqueue_import),
                onConfirm = { viewModel.importItem(action.item) },
                onDismiss = { viewModel.dismissAction() },
                dismissText = stringResource(Res.string.arrqueue_cancel),
                tone = ConfirmTone.NEUTRAL,
            )
        }
    }
}

// ── Rows ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    item: ArrQueueItem,
    selected: Boolean,
    selectionMode: Boolean,
    actionInProgress: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onGrab: () -> Unit,
    onImport: () -> Unit,
) {
    val focusState = rememberTvFocusState()
    val isTv = LocalTvMode.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = ShapeCache.smooth12,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    // TV: the card row is the single focus target; the nested
                    // checkbox mirrors selection state instead of competing for
                    // D-pad focus (the row handles the interaction).
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onClick() },
                        modifier = if (isTv) Modifier.focusProperties { canFocus = false } else Modifier,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceBadge(kind = item.serverKind)
                        Spacer(Modifier.width(8.dp))
                        StatusChip(status = item.status)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Progress bar — shown for every item so the list stays visually
            // consistent. Queued items show an empty track, failed/warning
            // items show how far they got, completed/imported show full.
            LinearProgressIndicator(
                progress = { item.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = progressColor(item.status),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(6.dp))

            // Subtitle: percent · size left · time left · quality.
            val subtitle = buildString {
                if (item.percent in 1..100) append("${item.percent}%")
                item.sizeLeft?.toReadableBytes()?.let { if (isNotEmpty()) append(" · "); append(it) }
                item.timeLeft?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append(" · "); append(it) }
                item.quality?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append(" · "); append(it) }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Attention messages (stuck/import warnings).
            if (item.messages.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                item.messages.take(2).forEach { msg ->
                    Text(
                        text = msg.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.needsAttention) StatusColors.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!selectionMode) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !actionInProgress,
                        modifier = Modifier.weight(1f),
                        shape = ShapeCache.smooth12,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Tabler.Outline.Trash, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.arrqueue_delete))
                    }
                    OutlinedButton(
                        onClick = onGrab,
                        enabled = !actionInProgress,
                        modifier = Modifier.weight(1f),
                        shape = ShapeCache.smooth12,
                    ) {
                        Icon(Tabler.Outline.PlayerPlay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.arrqueue_grab))
                    }
                    OutlinedButton(
                        onClick = onImport,
                        enabled = !actionInProgress,
                        modifier = Modifier.weight(1f),
                        shape = ShapeCache.smooth12,
                    ) {
                        Icon(Tabler.Outline.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.arrqueue_import))
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceBadge(kind: ArrServiceKind) {
    val (label, color) = when (kind) {
        ArrServiceKind.RADARR -> stringResource(Res.string.arrqueue_brand_radarr) to StatusColors.requested
        ArrServiceKind.SONARR -> stringResource(Res.string.arrqueue_brand_sonarr) to StatusColors.pending
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusChip(status: ArrDownloadStatus) {
    val (label, color) = when (status) {
        ArrDownloadStatus.DOWNLOADING -> stringResource(Res.string.arrqueue_status_downloading) to StatusColors.available
        ArrDownloadStatus.QUEUED -> stringResource(Res.string.arrqueue_status_queued) to StatusColors.info
        ArrDownloadStatus.PAUSED -> stringResource(Res.string.arrqueue_status_paused) to StatusColors.pending
        ArrDownloadStatus.COMPLETED -> stringResource(Res.string.arrqueue_status_completed) to StatusColors.available
        ArrDownloadStatus.IMPORTED -> stringResource(Res.string.arrqueue_status_imported) to StatusColors.success
        ArrDownloadStatus.FAILED -> stringResource(Res.string.arrqueue_status_failed) to StatusColors.error
        ArrDownloadStatus.WARNING -> stringResource(Res.string.arrqueue_status_warning) to StatusColors.warning
        ArrDownloadStatus.UNKNOWN -> stringResource(Res.string.arrqueue_status_unknown) to StatusColors.debug
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun progressColor(status: ArrDownloadStatus): Color = when (status) {
    ArrDownloadStatus.COMPLETED -> StatusColors.available
    ArrDownloadStatus.IMPORTED -> StatusColors.success
    ArrDownloadStatus.PAUSED -> StatusColors.pending
    ArrDownloadStatus.DOWNLOADING -> StatusColors.requested
    ArrDownloadStatus.FAILED -> StatusColors.error
    ArrDownloadStatus.WARNING -> StatusColors.warning
    else -> StatusColors.info
}

// ── States ────────────────────────────────────────────────────────────────

@Composable
private fun FeatureDisabledState(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Tabler.Outline.Database,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.arrqueue_disabled_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.arrqueue_disabled_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenSettings, shape = ShapeCache.smooth12) {
                Icon(Tabler.Outline.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.arrqueue_open_settings))
            }
        }
    }
}

@Composable
private fun EmptyQueueState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Tabler.Outline.Download,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.arrqueue_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.arrqueue_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.arrqueue_retry)) }
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    actionInProgress: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onBulkDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = ShapeCache.smooth12,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.arrqueue_selected_count, selectedCount),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSelectAll, enabled = !actionInProgress) {
                Icon(Tabler.Outline.Check, contentDescription = stringResource(Res.string.arrqueue_select_all))
            }
            IconButton(onClick = onClear, enabled = !actionInProgress) {
                Icon(Tabler.Outline.X, contentDescription = stringResource(Res.string.arrqueue_clear_selection))
            }
            FilledTonalButton(
                onClick = onBulkDelete,
                enabled = !actionInProgress && selectedCount > 0,
                shape = ShapeCache.smooth12,
                colors = ButtonDefaults.filledTonalButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Tabler.Outline.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.arrqueue_delete))
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────

@Composable
private fun DeleteActionDialog(
    item: ArrQueueItem?,
    bulk: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (blocklist: Boolean, searchAgain: Boolean) -> Unit,
) {
    // Three-way choice (remove-only / remove + search / blocklist + search): this
    // is a selection dialog, not a binary confirm. The real actions live in the
    // `content` slot as full-width buttons; `confirmText` is omitted so no primary
    // confirm button renders, and Cancel lives in the dismiss slot.
    ConfirmDialog(
        title = if (bulk) {
            stringResource(Res.string.arrqueue_remove_selected_title)
        } else {
            stringResource(Res.string.arrqueue_remove_item_title, item?.title ?: "")
        },
        message = if (bulk) {
            stringResource(Res.string.arrqueue_remove_body_bulk)
        } else {
            stringResource(Res.string.arrqueue_remove_body_single)
        },
        onDismiss = onDismiss,
        dismissText = stringResource(Res.string.arrqueue_cancel),
        tone = ConfirmTone.DESTRUCTIVE,
        icon = Tabler.Outline.Trash,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onConfirm(false, false); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.arrqueue_remove_only))
                }
                OutlinedButton(
                    onClick = { onConfirm(false, true); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Tabler.Outline.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.arrqueue_remove_search))
                }
                OutlinedButton(
                    onClick = { onConfirm(true, true); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Tabler.Outline.Ban, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.arrqueue_blocklist_search))
                }
            }
        },
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────

@Composable
private fun serviceName(kind: ArrServiceKind): String = when (kind) {
    ArrServiceKind.RADARR -> stringResource(Res.string.arrqueue_brand_radarr)
    ArrServiceKind.SONARR -> stringResource(Res.string.arrqueue_brand_sonarr)
}

private fun Long.toReadableBytes(): String = when {
    this >= 1_073_741_824 -> "%.1f GB".format(this / 1_073_741_824.0)
    this >= 1_048_576 -> "%.1f MB".format(this / 1_048_576.0)
    this >= 1024 -> "%.0f KB".format(this / 1024.0)
    else -> "$this B"
}

private val ArrQueueItem.rowKey: String
    get() = "${serverKind.name}|$queueId|${serverId.ifEmpty { "_" }}"
