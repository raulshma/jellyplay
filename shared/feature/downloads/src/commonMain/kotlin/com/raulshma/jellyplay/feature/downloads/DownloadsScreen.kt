package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.designsystem.theme.detailEntrance
import com.raulshma.jellyplay.core.designsystem.theme.rememberDetailEntrance
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TriStateCheckbox
import com.raulshma.jellyplay.core.designsystem.theme.Dimensions
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.episodeContextLine
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.ResyncCategory
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_cancel
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_clear_selection
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_delete
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_lower_priority
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_move_to_front
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_pause
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_play
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_resume
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_retry
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_select_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_cancel
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_close
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_delete
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_delete_download_message
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_delete_download_title
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_delete_downloads_message
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_delete_downloads_title
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_deleted_message
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_empty_description
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_empty_title
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_action
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_chapters
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_chapters_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_backdrop
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_chapters
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_chapters_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_backdrop_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_header
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_metadata
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_metadata_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_poster
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_poster_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_segments
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_segments_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_subtitles
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_subtitles_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_trickplay
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_trickplay_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_description
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_done
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_empty
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_in_progress
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_items_header
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_no_data
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_summary
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_title
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_frees_up_sentence
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_pause_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_action
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_batch_checking
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_batch_empty
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_batch_title
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_check_all_cd
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_media_changed
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_progress
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_resync_resync_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_retry_failed
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_screen_title
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_selected_count
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_selection_hint
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_cancelled
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_failed
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_paused
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_queued
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_waiting
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_storage_used

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    onItemClick: (String) -> Unit,
    onPlayOffline: (itemId: String, mediaType: com.raulshma.jellyplay.core.model.MediaType) -> Unit,
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloads = uiState.downloads
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = uiState.isLoading,
        hasError = uiState.error != null,
        networkStatus = networkStatus,
    )
    val updatesAvailable by viewModel.updatesAvailable.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val updateRows by viewModel.updateRows.collectAsStateWithLifecycle(initialValue = emptyList())
    val resyncProgress by viewModel.resyncProgress.collectAsStateWithLifecycle()

    // One-shot delete feedback (screen-forward seam): resolve the texts here,
    // forward each emitted message through the messenger actual.
    val messenger = rememberDownloadsMessenger()
    val deletedText = stringResource(Res.string.downloads_deleted_message)
    LaunchedEffect(messenger) {
        viewModel.messages.collect { message ->
            when (message) {
                DownloadsUserMessage.Deleted -> messenger?.info(deletedText)
                is DownloadsUserMessage.Raw -> messenger?.error(message.text)
            }
        }
    }

    // Pending delete confirmation. Deleting a completed download removes the
    // file from disk, so we confirm first — matching the unified
    // MediaDetailScreen delete confirmations.
    var pendingDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var pendingBulkDelete by remember { mutableStateOf(false) }
    var showResyncSheet by remember { mutableStateOf(false) }
    var showForceResyncSheet by remember { mutableStateOf(false) }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    // Bottom-pinned action bar must clear the app's floating navigation bar
    // (it paints above screen content at BottomCenter). Use the canonical nav
    // height + system nav-bar inset, and slide up in lockstep with the nav's
    // hide animation via LocalFloatingNavOffset (returns 0f where the nav is
    // absent — TV/expanded/full-screen).
    val navOffsetPx = LocalFloatingNavOffset.current
    val navBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val selectionBarClearance = Dimensions.floatingNavHeight + navBarBottomInset

    val selectionMode = uiState.selectionMode
    val selectedIds = uiState.selectedIds
    // Action-bar predicates: a bulk control is enabled only when the current
    // selection actually contains an item of the matching status, so the bar
    // never offers a no-op (e.g. Pause with only paused items selected).
    val selectedItems = remember(selectionMode, downloads, selectedIds) {
        if (selectionMode) downloads.filter { it.id in selectedIds } else emptyList()
    }
    val hasPauseable = remember(selectedItems) { selectedItems.any { it.status == DownloadStatus.DOWNLOADING } }
    val hasResumable = remember(selectedItems) { selectedItems.any { it.status == DownloadStatus.PAUSED } }
    val hasCancellable = remember(selectedItems) {
        selectedItems.any {
            it.status == DownloadStatus.PENDING ||
                it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.PAUSED
        }
    }
    // Global action predicates: the app-bar Pause All / Retry all failed
    // buttons are only enabled when the matching status exists anywhere in the
    // list, so neither offers a no-op (mirrors the selection-bar predicates).
    val hasAnyDownloading = remember(downloads) { downloads.any { it.status == DownloadStatus.DOWNLOADING } }
    val hasAnyFailed = remember(downloads) { downloads.any { it.status == DownloadStatus.FAILED } }

    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    // TV focus-on-launch: focus the first download row once data arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = downloads.size,
        tag = "downloads_init",
    )

    com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold(
        title = stringResource(Res.string.downloads_screen_title),
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            // Global actions (reachable without entering selection mode).
            // Pause All halts every active transfer; Retry all failed
            // re-queues every Failed download. Each is disabled when there's
            // nothing to act on so neither offers a no-op, mirroring the
            // selection-bar predicates.
            if (hasAnyDownloading) {
                val pauseFocus = rememberTvFocusState()
                IconButton(
                    onClick = { viewModel.pauseAll() },
                    modifier = Modifier
                        .then(pauseFocus.focusModifier)
                        .tvFocusIndicator(pauseFocus, CircleShape),
                ) {
                    Icon(
                        Tabler.Outline.PlayerPause,
                        contentDescription = stringResource(Res.string.downloads_pause_all),
                    )
                }
            }
            if (hasAnyFailed) {
                val retryFocus = rememberTvFocusState()
                IconButton(
                    onClick = { viewModel.retryAllFailed() },
                    modifier = Modifier
                        .then(retryFocus.focusModifier)
                        .tvFocusIndicator(retryFocus, CircleShape),
                ) {
                    Icon(
                        Tabler.Outline.Refresh,
                        contentDescription = stringResource(Res.string.downloads_retry_failed),
                    )
                }
            }
            // Resync action: checks every download for available updates and
            // opens the resync sheet. The badge dot appears when any item is
            // flagged, so the user knows updates are waiting without opening
            // the sheet.
            Box {
                val syncFocus = rememberTvFocusState()
                IconButton(
                    onClick = {
                        viewModel.checkAllForUpdates()
                        showResyncSheet = true
                    },
                    modifier = Modifier
                        .then(syncFocus.focusModifier)
                        .tvFocusIndicator(syncFocus, CircleShape),
                ) {
                    if (checking) {
                        JellyPlayCircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            Tabler.Outline.Refresh,
                            contentDescription = stringResource(Res.string.downloads_resync_check_all_cd),
                        )
                    }
                }
                if (updatesAvailable > 0) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }
            com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(start = 12.dp),
            )
        },
    ) {
        if (uiState.totalStorageBytes > 0) {
            Text(
                stringResource(Res.string.downloads_storage_used, viewModel.formatBytes(uiState.totalStorageBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = adaptiveInfo.contentPadding(isTv), end = adaptiveInfo.contentPadding(isTv), bottom = 8.dp),
            )
        }

        if (downloads.isEmpty()) {
            com.raulshma.jellyplay.core.ui.components.ScreenEmptyState(
                icon = Tabler.Outline.Download,
                title = stringResource(Res.string.downloads_empty_title),
                description = stringResource(Res.string.downloads_empty_description),
            )
        } else {
            // Hoist the three pure formatter lambdas once above the list. They
            // only delegate to viewModel and are identical across rows, so
            // allocating them per-row per-recomposition (the list recomposes on
            // every progress tick / speed sample) was pure churn (11 fresh
            // lambdas/row).
            val formatBytes = remember(viewModel) { { v: Long -> viewModel.formatBytes(v) } }
            val formatSpeed = remember(viewModel) { { v: Long -> viewModel.formatSpeed(v) } }
            val formatEta = remember(viewModel) { { d: Long, t: Long, s: Long -> viewModel.formatEta(d, t, s) } }
            // Index flagged-update rows by mediaItemId so each list row can
            // render an "update available" dot without a per-row scan. Computed
            // in composable scope (above the LazyColumn) so `remember` is valid.
            val updateIds = remember(updateRows) { updateRows.map { it.id }.toHashSet() }
            // Shared entrance reveal for the whole list — the same fix as
            // MediaDetailBody's LocalDetailEntrance, via the shared
            // [rememberDetailEntrance] + [detailEntrance] pair. ONE Animatable
            // driven once when the list mounts replaces the per-row
            // `mutableStateOf + LaunchedEffect + AnimatedVisibility` triple,
            // which allocated 2 state objects, a coroutine, and an animation
            // node for every scroll-composed row and then recomposed each row
            // twice. Rows read the progress inside the modifier's
            // graphicsLayer lambda (draw phase), so rows composed later during
            // scroll render at the settled 1f immediately — no animation,
            // coroutine, or extra recomposition. Re-mounting this branch
            // (empty -> non-empty) gets a fresh 0f, matching how newly
            // composed rows animated before.
            val entrance = rememberDetailEntrance()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusRestorer()
                        .focusRequester(listFocusRequester),
                    contentPadding = PaddingValues(
                        start = adaptiveInfo.contentPadding(isTv),
                        end = adaptiveInfo.contentPadding(isTv),
                        top = 8.dp,
                        // Grow bottom padding while selecting so the action bar
                        // clears the floating nav and doesn't cover the last row.
                        bottom = if (selectionMode) selectionBarClearance + 72.dp else adaptiveInfo.bottomPadding(isTv),
                    ),
                    verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                ) {
                    // Long-press hint: shown only until the user enters selection
                    // mode for the first time, so the affordance is discoverable
                    // without lingering on every session.
                    item(key = "selection_hint", contentType = "selectionHint") {
                        if (!selectionMode) {
                            SelectionHintRow()
                        }
                    }
                    itemsIndexed(items = downloads, key = { _, it -> it.id }, contentType = { _, _ -> "downloadItem" }) { index, download ->
                        DownloadItemRow(
                            // Deferred draw-phase read of the shared entrance
                            // progress: alpha + a slide of 1/10 of the row
                            // height reproduce the old fadeIn +
                            // slideInVertically(it / 10) entrance with zero
                            // per-row state, coroutine, or animation node.
                            modifier = Modifier.detailEntrance(
                                progress = { entrance.value },
                                slideDivisor = 10f,
                            ),
                            item = download,
                            formatBytes = formatBytes,
                            formatSpeed = formatSpeed,
                            formatEta = formatEta,
                            selected = download.id in selectedIds,
                            selectionMode = selectionMode,
                            hasUpdate = download.status == DownloadStatus.COMPLETED &&
                                download.mediaItemId in updateIds,
                            // Completed downloads open their detail page on tap
                            // (matching the online experience) rather than auto-playing.
                            // A distinct Play action is still available in the row.
                            onOpenDetail = {
                                if (download.status == DownloadStatus.COMPLETED) {
                                    onItemClick(download.mediaItemId)
                                }
                            },
                            onPlay = {
                                if (download.status == DownloadStatus.COMPLETED) {
                                    onPlayOffline(download.mediaItemId, download.mediaType)
                                }
                            },
                            onCancel = { viewModel.cancelDownload(download) },
                            onPause = { viewModel.pauseDownload(download) },
                            onResume = { viewModel.resumeDownload(download) },
                            onDelete = { pendingDelete = download },
                            onRetry = { viewModel.retryDownload(download) },
                            onMoveToFront = { viewModel.moveToFront(download) },
                            onLowerPriority = { viewModel.lowerPriority(download) },
                            onToggleSelection = { viewModel.toggleSelection(download) },
                        )
                    }
                }

                // Selection-mode bottom action bar.
                if (selectionMode) {
                    SelectionActionBar(
                        selectedCount = selectedIds.size,
                        hasPauseable = hasPauseable,
                        hasResumable = hasResumable,
                        hasCancellable = hasCancellable,
                        onSelectAll = { viewModel.selectAll() },
                        onClear = { viewModel.clearSelection() },
                        onPause = { viewModel.pauseSelected() },
                        onResume = { viewModel.resumeSelected() },
                        onCancel = { viewModel.cancelSelected() },
                        onBulkDelete = { pendingBulkDelete = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            // Sit above the floating nav (clearance) and slide up
                            // in lockstep when the nav hides itself.
                            .padding(bottom = selectionBarClearance)
                            .offset {
                                val maxOffset = Dimensions.floatingNavHeight.toPx()
                                val yOffset = (-navOffsetPx()).coerceAtMost(maxOffset)
                                IntOffset(x = 0, y = yOffset.roundToInt())
                            },
                    )
                }
            }
        }
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(Res.string.downloads_delete_download_title),
            message = stringResource(Res.string.downloads_delete_download_message, item.name, viewModel.formatBytes(item.totalSizeBytes)),
            confirmText = stringResource(Res.string.downloads_delete),
            dismissText = stringResource(Res.string.downloads_cancel),
            icon = Tabler.Outline.Trash,
            tone = ConfirmTone.DESTRUCTIVE,
            onConfirm = { viewModel.deleteDownload(item) },
            onDismiss = { pendingDelete = null },
        )
    }

    if (pendingBulkDelete) {
        val count = selectedIds.size
        val freedBytes = selectedItems.sumOf { it.totalSizeBytes }
        ConfirmDialog(
            title = stringResource(Res.string.downloads_delete_downloads_title),
            message = pluralStringResource(Res.plurals.downloads_delete_downloads_message, count, count) +
                if (freedBytes > 0) stringResource(Res.string.downloads_frees_up_sentence, viewModel.formatBytes(freedBytes)) else "",
            confirmText = stringResource(Res.string.downloads_delete),
            dismissText = stringResource(Res.string.downloads_cancel),
            icon = Tabler.Outline.Trash,
            tone = ConfirmTone.DESTRUCTIVE,
            onConfirm = { viewModel.deleteSelected() },
            onDismiss = { pendingBulkDelete = false },
        )
    }

    if (showResyncSheet) {
        DownloadsResyncSheet(
            updateRows = updateRows,
            checking = checking,
            progress = resyncProgress,
            onResyncAll = viewModel::resyncAll,
            onResyncOne = viewModel::resyncOne,
            onForceResync = {
                showResyncSheet = false
                viewModel.clearResyncProgress()
                showForceResyncSheet = true
            },
            onDismiss = {
                showResyncSheet = false
                viewModel.clearResyncProgress()
            },
        )
    }

    if (showForceResyncSheet) {
        // Candidates resolve straight from the DB on each open (suspend) so the
        // picker offers every eligible downloaded item — not just those inside
        // the UI list's 500-row window, and not an empty set when the sheet is
        // opened before the list flow's first emission.
        var forceResyncCandidates by remember { mutableStateOf<List<ForceResyncCandidate>>(emptyList()) }
        // Keyed on Unit: the enclosing `if` remounts this block on every sheet
        // open, so a sheet-keyed key would be a constant that can never re-fire.
        LaunchedEffect(Unit) {
            forceResyncCandidates = viewModel.forceResyncCandidates()
        }
        ForceResyncSheet(
            candidates = forceResyncCandidates,
            progress = resyncProgress,
            onSync = { ids, options -> viewModel.forceResync(ids, options) },
            onDismiss = {
                showForceResyncSheet = false
                viewModel.clearResyncProgress()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadItemRow(
    item: DownloadItem,
    modifier: Modifier = Modifier,
    formatBytes: (Long) -> String,
    formatSpeed: (Long) -> String,
    formatEta: (Long, Long, Long) -> String,
    selected: Boolean,
    selectionMode: Boolean,
    hasUpdate: Boolean = false,
    onOpenDetail: () -> Unit,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onMoveToFront: () -> Unit,
    onLowerPriority: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val progress = if (item.totalSizeBytes > 0) {
        item.downloadedBytes.toFloat() / item.totalSizeBytes
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "downloadProgress",
    )
    val cardRowFocusState = rememberTvFocusState(focusedScale = 1.01f)

    ElevatedCard(
        // Caller-supplied modifier comes first so the shared entrance
        // graphicsLayer wraps the whole card.
        modifier = modifier
            .fillMaxWidth()
            .then(cardRowFocusState.focusModifier)
            .tvFocusIndicator(cardRowFocusState, ShapeCache.smooth12)
            .combinedClickable(
                onClick = {
                    // In selection mode a tap toggles selection; otherwise it
                    // opens the detail page (completed items only).
                    if (selectionMode) onToggleSelection() else onOpenDetail()
                },
                onLongClick = onToggleSelection,
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() },
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(ShapeCache.smooth8),
                contentAlignment = Alignment.Center,
            ) {
            val imageUrl = item.imageUrl
            if (imageUrl != null) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = item.name,
                    blurHash = item.imageBlurHash,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                } else {
                    Icon(
                        when (item.mediaType) {
                            com.raulshma.jellyplay.core.model.MediaType.AUDIO,
                            com.raulshma.jellyplay.core.model.MediaType.MUSIC,
                            com.raulshma.jellyplay.core.model.MediaType.ALBUM -> Tabler.Outline.Music
                            else -> Tabler.Outline.Movie
                        },
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // "Update available" dot overlaid on the thumbnail so a flagged
                // item is visible at a glance without opening the resync sheet.
                if (hasUpdate) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // For series episodes, surface the parent series and an SXXEXX
                // tag below the episode title so rows are identifiable in a flat
                // download list (mirrors the context line on the unified
                // MediaDetailScreen).
                // The SxxExx + " · " + series shape is shared with the resync
                // sheets via [episodeContextLine] so a format change is one place.
                episodeContextLine(
                    mediaType = item.mediaType,
                    seriesName = item.seriesName,
                    seasonNumber = item.seasonNumber,
                    episodeNumber = item.episodeNumber,
                )?.let { annotatedContext ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        annotatedContext,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        JellyPlayLinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(2.dp))
                        val sizeText = "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalSizeBytes.coerceAtLeast(1))}"
                        val speedText = formatSpeed(item.speedBytesPerSec)
                        val etaText = formatEta(item.downloadedBytes, item.totalSizeBytes, item.speedBytesPerSec)
                        Text(
                            buildString {
                                append(sizeText)
                                if (speedText.isNotEmpty()) append(" · $speedText")
                                if (etaText.isNotEmpty()) append(" · $etaText")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadStatus.QUEUED -> {
                        Text(
                            stringResource(Res.string.downloads_status_queued),
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusColors.info,
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Text(
                            formatBytes(item.downloadedBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadStatus.PENDING -> {
                        Text(
                            stringResource(Res.string.downloads_status_waiting),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadStatus.FAILED -> {
                        Column {
                            Text(
                                text = stringResource(Res.string.downloads_status_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            item.errorMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        Text(
                            stringResource(Res.string.downloads_status_paused),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadStatus.CANCELLED -> {
                        Text(
                            stringResource(Res.string.downloads_status_cancelled),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Per-row actions are hidden while selecting — bulk controls live
            // in the bottom action bar instead (matches ArrQueueScreen).
            if (!selectionMode) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.ArrowDown,
                            contentDescription = stringResource(Res.string.downloads_action_lower_priority),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onLowerPriority,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.PlayerPause,
                            contentDescription = stringResource(Res.string.downloads_action_pause),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onPause,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = stringResource(Res.string.downloads_action_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.PENDING -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.ArrowUp,
                            contentDescription = stringResource(Res.string.downloads_action_move_to_front),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onMoveToFront,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = stringResource(Res.string.downloads_action_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.QUEUED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.ArrowUp,
                            contentDescription = stringResource(Res.string.downloads_action_move_to_front),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onMoveToFront,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = stringResource(Res.string.downloads_action_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.PAUSED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.PlayerPlay,
                            contentDescription = stringResource(Res.string.downloads_action_resume),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onResume,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = stringResource(Res.string.downloads_action_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.FAILED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.Refresh,
                            contentDescription = stringResource(Res.string.downloads_action_retry),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onRetry,
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.PlayerPlay,
                            contentDescription = stringResource(Res.string.downloads_action_play),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onPlay,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = stringResource(Res.string.downloads_action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onDelete,
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun DownloadActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.1f)
    Box(
        modifier = Modifier
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth10),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription,
                tint = tint,
            )
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    hasPauseable: Boolean,
    hasResumable: Boolean,
    hasCancellable: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            // Controls get their intrinsic width first; the count text takes
            // whatever remains and ellipsizes. Without this the row would
            // squeeze the text column to ~0 width and render letters stacked
            // vertically on narrow screens.
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(Res.string.downloads_selected_count, selectedCount),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // Compact icon-only actions keep the bar to one line on phones.
            CompactIconButton(onClick = onPause, enabled = hasPauseable) {
                Icon(Tabler.Outline.PlayerPause, contentDescription = stringResource(Res.string.downloads_action_pause), modifier = Modifier.size(20.dp))
            }
            CompactIconButton(onClick = onResume, enabled = hasResumable) {
                Icon(Tabler.Outline.PlayerPlay, contentDescription = stringResource(Res.string.downloads_action_resume), modifier = Modifier.size(20.dp))
            }
            CompactIconButton(onClick = onCancel, enabled = hasCancellable) {
                Icon(Tabler.Outline.PlayerStop, contentDescription = stringResource(Res.string.downloads_action_cancel), modifier = Modifier.size(20.dp))
            }
            CompactIconButton(onClick = onSelectAll, enabled = true) {
                Icon(Tabler.Outline.Check, contentDescription = stringResource(Res.string.downloads_action_select_all), modifier = Modifier.size(20.dp))
            }
            CompactIconButton(onClick = onClear, enabled = true) {
                Icon(Tabler.Outline.X, contentDescription = stringResource(Res.string.downloads_action_clear_selection), modifier = Modifier.size(20.dp))
            }
            FilledTonalButton(
                onClick = onBulkDelete,
                enabled = selectedCount > 0,
                shape = ShapeCache.smooth12,
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                colors = ButtonDefaults.filledTonalButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Tabler.Outline.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.downloads_delete))
            }
        }
    }
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        content()
    }
}

@Composable
private fun SelectionHintRow() {
    Surface(
        shape = ShapeCache.smooth8,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Tabler.Outline.HandMove,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.downloads_selection_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Bottom sheet listing every download flagged for an update, with a per-item
 * resync action and a batch "sync all". Renders live progress from the sync
 * manager: each row shows its current phase (pending/working/done/error) and an
 * aggregate progress line runs while the batch is active.
 *
 * "Force resync" lives in the bottom action row beside Close so the resync icon
 * stays the single freshness hub while still offering the granular,
 * user-directed flow via progressive disclosure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsResyncSheet(
    updateRows: List<com.raulshma.jellyplay.core.model.OfflineSyncUpdate>,
    checking: Boolean,
    progress: com.raulshma.jellyplay.core.model.ResyncBatchProgress,
    onResyncAll: (List<String>) -> Unit,
    onResyncOne: (String) -> Unit,
    onForceResync: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    TvSafeSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: just the title + icon. The "Force resync" action moved to
            // the bottom action row beside Close so the two terminal controls
            // live together (matches the force-resync sheet's footer).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Tabler.Outline.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(Res.string.downloads_resync_batch_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            when {
                checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    JellyPlayCircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(Res.string.downloads_resync_batch_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                updateRows.isEmpty() -> Text(
                    stringResource(Res.string.downloads_resync_batch_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    if (progress.active) {
                        val done = progress.completed
                        Text(
                            stringResource(Res.string.downloads_resync_progress, done, progress.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(updateRows, key = { _, row -> row.id }) { _, row ->
                            val itemProgress = progress.items[row.id]
                            ResyncSheetRow(
                                update = row,
                                phase = itemProgress?.phase,
                                onResync = { onResyncOne(row.id) },
                            )
                        }
                    }
                    androidx.compose.material3.Button(
                        onClick = { onResyncAll(updateRows.map { it.id }) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !progress.active,
                    ) {
                        Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.downloads_resync_resync_all))
                    }
                }
            }
            // Bottom action row: Force resync (progressive disclosure of the
            // granular flow) sits beside Close so the two terminal controls
            // share a row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.TextButton(
                    onClick = onForceResync,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Tabler.Outline.RefreshAlert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.downloads_force_resync_action))
                }
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.downloads_close))
                }
            }
        }
    }
}

@Composable
private fun ResyncSheetRow(
    update: com.raulshma.jellyplay.core.model.OfflineSyncUpdate,
    phase: com.raulshma.jellyplay.core.model.ResyncPhase?,
    onResync: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (phase) {
            com.raulshma.jellyplay.core.model.ResyncPhase.WORKING ->
                JellyPlayCircularProgressIndicator(modifier = Modifier.size(18.dp))
            com.raulshma.jellyplay.core.model.ResyncPhase.DONE ->
                Icon(Tabler.Outline.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            com.raulshma.jellyplay.core.model.ResyncPhase.ERROR ->
                Icon(Tabler.Outline.AlertTriangle, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            else ->
                Icon(Tabler.Outline.AlertCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                update.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Episode context line (SXXEXX · series) so episodes are identifiable
            // in the flat sheet list — same shape and styling as the downloads
            // list row (bold tag + plain series), via the shared helper.
            episodeContextLine(
                mediaType = update.mediaType,
                seriesName = update.seriesName,
                seasonNumber = update.seasonNumber,
                episodeNumber = update.episodeNumber,
            )?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (update.mediaFileChanged) {
                Text(
                    stringResource(Res.string.downloads_resync_media_changed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (phase == null || phase == com.raulshma.jellyplay.core.model.ResyncPhase.PENDING ||
            phase == com.raulshma.jellyplay.core.model.ResyncPhase.ERROR
        ) {
            androidx.compose.material3.TextButton(onClick = onResync) {
                Text(stringResource(Res.string.downloads_resync_action))
            }
        }
    }
}

/**
 * Force-resync sheet: a user-directed resync over an explicit set of downloaded
 * items, refreshing only the selected data categories (metadata / poster /
 * backdrop). Two phases — a picker (items + data checkboxes) and a progress
 * state that reuses [ResyncBatchProgress] from the sync manager, matching the
 * regular resync sheet's progress granularity.
 *
 * Entry is via the resync sheet's header action, so the resync icon remains the
 * single freshness hub. Mirrors the unified detail tree's
 * `DeleteDownloadedEpisodesSheet` multi-select pattern (tri-state select-all
 * header + per-item checkboxes) for consistency.
 *
 * Three phases, derived from live [progress] + the local [started] latch:
 *  - **picker** (default): editable items + data checkboxes;
 *  - **running**: read-only aggregate progress while [ResyncBatchProgress.active];
 *  - **done**: terminal view once a sync this sheet started has finished.
 * The `started` latch is keyed on [ResyncBatchProgress] so that reopening the
 * sheet while a background batch is still running (after a mid-batch dismiss)
 * surfaces running progress rather than a fresh editable picker.
 *
 * @param candidates downloaded items available for selection, with episode context.
 * @param progress live batch progress, shared with the regular resync flow.
 * @param onSync invoked with the selected item ids and data options.
 * @param onDismiss closes the sheet and clears batch progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForceResyncSheet(
    candidates: List<ForceResyncCandidate>,
    progress: com.raulshma.jellyplay.core.model.ResyncBatchProgress,
    onSync: (itemIds: List<String>, options: com.raulshma.jellyplay.core.model.ResyncOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Selection lives in the sheet so it resets on each open. Defaulting data
    // to all-on matches the historical resync behaviour; items start empty so
    // the user must opt in (an accidental full-library resync is costly).
    var selectedIds by remember { androidx.compose.runtime.mutableStateOf(emptySet<String>()) }
    var selectedOptions by remember {
        androidx.compose.runtime.mutableStateOf(com.raulshma.jellyplay.core.model.ResyncOptions.ALL)
    }
    // Latch: sticky once a sync has been kicked off (or is still running from a
    // prior mid-batch dismiss). Derived purely from live progress so an
    // orphaned background batch latches this sheet straight into the running/
    // done phase on reopen instead of offering an editable picker.
    val started = progress.active || progress.completed > 0
    // Phase precedence: a still-active batch (this sheet or orphaned) shows
    // running progress; once it finishes the sheet holds a terminal done view
    // until dismissed, so the editable picker never returns with a stale
    // selection that could be re-fired accidentally.
    val showRunning = progress.active
    val showDone = started && !progress.active

    TvSafeSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Tabler.Outline.RefreshAlert, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(Res.string.downloads_force_resync_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (showRunning) {
                // Running phase: mirror the regular resync sheet's aggregate line.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JellyPlayCircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(Res.string.downloads_force_resync_in_progress, progress.completed, progress.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (showDone) {
                // Terminal phase: a sync this sheet started has finished. Holds
                // a read-only summary until dismissed so the editable picker —
                // and its prior selection — never returns to be re-fired.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Tabler.Outline.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(Res.string.downloads_force_resync_done, progress.completed, progress.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Picker phase: the whole content scrolls so long item lists
                // (and the data section beneath them) stay reachable on small
                // screens. The header above and the footer below are pinned.
                // heightIn caps the scroll viewport regardless of the sheet's
                // height constraints (a ModalBottomSheet content column isn't
                // guaranteed to bound a weighted child), matching the regular
                // resync sheet's capped list.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(Res.string.downloads_force_resync_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (candidates.isEmpty()) {
                        Text(
                            stringResource(Res.string.downloads_force_resync_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        // ── Items section ──────────────────────────────────────
                        Text(
                            stringResource(Res.string.downloads_force_resync_items_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val triState = when {
                            selectedIds.isEmpty() -> ToggleableState.Off
                            selectedIds.size == candidates.size -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        }
                        val selectAllFocusState = rememberTvFocusState(focusedScale = 1.01f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(selectAllFocusState.focusModifier)
                                .tvFocusIndicator(selectAllFocusState, ShapeCache.smooth12)
                                .clickable {
                                    selectedIds = if (triState == ToggleableState.On) emptySet()
                                    else candidates.map { it.id }.toSet()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TriStateCheckbox(state = triState, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(Res.string.downloads_action_select_all),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        candidates.forEach { candidate ->
                            ForceResyncItemRow(
                                candidate = candidate,
                                checked = candidate.id in selectedIds,
                                onToggle = {
                                    selectedIds = if (candidate.id in selectedIds) selectedIds - candidate.id
                                    else selectedIds + candidate.id
                                },
                            )
                        }

                        // ── Data section ───────────────────────────────────────
                        Text(
                            stringResource(Res.string.downloads_force_resync_data_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ResyncCategory.entries.forEach { category ->
                            val (labelRes, descRes) = category.stringRes()
                            ForceResyncDataRow(
                                label = stringResource(labelRes),
                                description = stringResource(descRes),
                                checked = category in selectedOptions,
                                onToggle = {
                                    selectedOptions =
                                        if (category in selectedOptions) selectedOptions - category
                                        else selectedOptions + category
                                },
                            )
                        }
                        if (selectedOptions.isEmpty) {
                            Text(
                                stringResource(Res.string.downloads_force_resync_no_data),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Footer: Close always renders (all phases — a running/done batch
            // still needs a way out, and closing mid-batch leaves progress to
            // complete in the background, matching the regular resync sheet).
            // In the picker phase the primary Sync action sits beside it so the
            // two terminal controls share a row.
            val canSync = !showRunning && !showDone && selectedIds.isNotEmpty() && !selectedOptions.isEmpty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.downloads_close))
                }
                androidx.compose.material3.Button(
                    onClick = { onSync(selectedIds.toList(), selectedOptions) },
                    modifier = Modifier.weight(1f),
                    enabled = canSync,
                ) {
                    Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.downloads_force_resync_summary, selectedIds.size))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ForceResyncItemRow(
    candidate: ForceResyncCandidate,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.01f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12)
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                candidate.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Episode context (SXXEXX · series) so episodes are identifiable in
            // the picker — same shape and styling as the downloads list row.
            episodeContextLine(
                mediaType = candidate.mediaType,
                seriesName = candidate.seriesName,
                seasonNumber = candidate.seasonNumber,
                episodeNumber = candidate.episodeNumber,
            )?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Label + description strings for one resync data-category row, exhaustive
 *  so a new [ResyncCategory] fails compilation until it gets copy. */
private fun ResyncCategory.stringRes(): Pair<org.jetbrains.compose.resources.StringResource, org.jetbrains.compose.resources.StringResource> = when (this) {
    ResyncCategory.METADATA ->
        Res.string.downloads_force_resync_data_metadata to Res.string.downloads_force_resync_data_metadata_desc
    ResyncCategory.CHAPTERS ->
        Res.string.downloads_force_resync_data_chapters to Res.string.downloads_force_resync_data_chapters_desc
    ResyncCategory.POSTER ->
        Res.string.downloads_force_resync_data_poster to Res.string.downloads_force_resync_data_poster_desc
    ResyncCategory.BACKDROP ->
        Res.string.downloads_force_resync_data_backdrop to Res.string.downloads_force_resync_data_backdrop_desc
    ResyncCategory.SUBTITLES ->
        Res.string.downloads_force_resync_data_subtitles to Res.string.downloads_force_resync_data_subtitles_desc
    ResyncCategory.TRICKPLAY ->
        Res.string.downloads_force_resync_data_trickplay to Res.string.downloads_force_resync_data_trickplay_desc
    ResyncCategory.SEGMENTS ->
        Res.string.downloads_force_resync_data_segments to Res.string.downloads_force_resync_data_segments_desc
}

@Composable
private fun ForceResyncDataRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.01f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12)
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
