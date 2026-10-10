package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.detailEntrance
import com.raulshma.jellyplay.core.designsystem.theme.rememberDetailEntrance
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TriStateCheckbox
import com.raulshma.jellyplay.core.ui.animation.SwipeActionBox
import com.raulshma.jellyplay.core.ui.components.SelectionActionBar
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import com.raulshma.jellyplay.core.ui.components.floatingNavClearanceDp
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.ResyncCategory
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.formatEta
import com.raulshma.jellyplay.core.model.formatSpeed
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
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
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_pause
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_resume
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_select_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_close
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_delete
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
import com.raulshma.jellyplay.feature.downloads.sheets.DownloadsResyncSheet
import com.raulshma.jellyplay.feature.downloads.sheets.ForceResyncSheet

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
    // Live per-row progress, collected ONCE into a State whose .value is
    // never read at this level. Collecting here subscribes the upstream
    // (that's intended — it keeps the tick hot); only RECOMPOSITION is
    // deferred: just the DOWNLOADING rows' item lambdas read .value below,
    // so a 2 s progress tick re-executes those row scopes alone and this
    // screen body stays put (rows and dialogs now live in DownloadItemRow.kt
    // and DownloadsDialogs.kt; same deferred hot-read pattern as
    // VideoPlayerScreen's ChapterPickerBinder / SleepTimerSheetBinder
    // leaves).
    val progressById = viewModel.progressById.collectAsStateWithLifecycle()
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
    // post each emitted message through the app-wide UserMessageBus.
    val bus = LocalUserMessageBus.current
    val deletedText = stringResource(Res.string.downloads_deleted_message)
    LaunchedEffect(bus) {
        viewModel.messages.collect { message ->
            when (message) {
                DownloadsUserMessage.Deleted -> bus.info(deletedText)
                is DownloadsUserMessage.Raw -> bus.error(message.text)
            }
        }
    }

    // Pending delete confirmations live on the ViewModel now — two
    // [ConfirmationHost] machines ([DownloadsViewModel.pendingDelete], single
    // item, and [DownloadsViewModel.pendingBulkDelete], bulk selection) whose
    // synchronous settle arm subsumes this screen's old "confirm write never
    // cleared" screen-held remember{} machines. The dialogs are driven by the
    // hosts via [DownloadsDeleteDialogs] (DownloadsDialogs.kt) at the bottom
    // of this body.
    var showResyncSheet by remember { mutableStateOf(false) }
    var showForceResyncSheet by remember { mutableStateOf(false) }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    // Bottom-pinned action bar must clear the app's floating navigation bar
    // (it paints above screen content at BottomCenter). floatingNavClearanceDp
    // is presence-aware — where no bar is painted (TV/expanded/full-screen)
    // only the system nav-bar inset remains — and the bar's clearFloatingNav
    // ride-up slides it in lockstep with the nav's hide animation.

    val selectionMode = uiState.selectionMode
    val selectedIds = uiState.selectedIds
    // Action-bar predicates folded into the pure [DownloadActions] admission
    // table: a bulk control is enabled only when the current selection (or the
    // full list, for the global actions) actually contains an item the action
    // admits, so the bar never offers a no-op (e.g. Pause with only paused
    // items selected).
    val selectedItems = remember(selectionMode, downloads, selectedIds) {
        if (selectionMode) downloads.filter { it.id in selectedIds } else emptyList()
    }
    val hasPauseable = remember(downloads, selectedIds) {
        DownloadActions.supports(DownloadBulkAction.PAUSE, downloads, selectedIds, DownloadActionScope.Selected)
    }
    val hasResumable = remember(downloads, selectedIds) {
        DownloadActions.supports(DownloadBulkAction.RESUME, downloads, selectedIds, DownloadActionScope.Selected)
    }
    val hasCancellable = remember(downloads, selectedIds) {
        DownloadActions.supports(DownloadBulkAction.CANCEL, downloads, selectedIds, DownloadActionScope.Selected)
    }
    // Global action predicates: the app-bar Pause All / Retry all failed
    // buttons are only enabled when the matching status exists anywhere in the
    // list, so neither offers a no-op (mirrors the selection-bar predicates).
    val hasAnyDownloading = remember(downloads) {
        DownloadActions.supports(DownloadBulkAction.PAUSE, downloads, emptySet(), DownloadActionScope.All)
    }
    val hasAnyFailed = remember(downloads) {
        DownloadActions.supports(DownloadBulkAction.RETRY_FAILED, downloads, emptySet(), DownloadActionScope.All)
    }

    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()

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
        backgroundColorState = backgroundColorState,
        actions = {
            // Global actions (reachable without entering selection mode).
            // Pause All halts every active transfer; Retry all failed
            // re-queues every Failed download. Each is disabled when there's
            // nothing to act on so neither offers a no-op, mirroring the
            // selection-bar predicates.
            if (hasAnyDownloading) {
                val pauseFocus = rememberTvFocusState()
                IconButton(
                    onClick = { viewModel.applyBulkAction(DownloadBulkAction.PAUSE, DownloadActionScope.All) },
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
                    onClick = { viewModel.applyBulkAction(DownloadBulkAction.RETRY_FAILED, DownloadActionScope.All) },
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
        // "Storage used" rides the live progress tick (bytes accumulate
        // mid-transfer), so it is collected INSIDE this leaf — the screen
        // body above is not invalidated per tick, only this one Text. Same
        // `totalStorageBytes > 0` visibility rule the inline Text had.
        DownloadsStorageUsedText(
            totalStorageBytes = viewModel.totalStorageBytes,
            formatBytes = Long::formatBytes,
            horizontalPadding = adaptiveInfo.contentPadding(isTv),
        )

        if (downloads.isEmpty()) {
            com.raulshma.jellyplay.core.ui.components.ScreenEmptyState(
                icon = Tabler.Outline.Download,
                title = stringResource(Res.string.downloads_empty_title),
                description = stringResource(Res.string.downloads_empty_description),
            )
        } else {
            // The rows' three formatters are the shared core-model ByteFormatter
            // extensions, passed as static references — identical across rows,
            // no per-row allocation (the list recomposes on every progress
            // tick / speed sample) and no ViewModel hop (the former
            // DownloadsViewModel forwards are gone).
            val formatBytes = Long::formatBytes
            val formatSpeed = Long::formatSpeed
            val formatEta = ::formatEta
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
                        bottom = if (selectionMode) floatingNavClearanceDp + 72.dp else adaptiveInfo.bottomPadding(isTv),
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
                        // Hot read scoped to the only rows that move: just the
                        // DOWNLOADING rows' lambdas subscribe to the live
                        // progress map, so a tick re-executes those item
                        // lambdas only — never the enclosing screen body.
                        val liveProgress = if (download.status == DownloadStatus.DOWNLOADING) {
                            progressById.value[download.id]
                        } else {
                            null
                        }
                        val row: @Composable (Modifier) -> Unit = { rowModifier ->
                            DownloadItemRow(
                                // Deferred draw-phase read of the shared entrance
                                // progress: alpha + a slide of 1/10 of the row
                                // height reproduce the old fadeIn +
                                // slideInVertically(it / 10) entrance with zero
                                // per-row state, coroutine, or animation node.
                                modifier = rowModifier,
                                item = download,
                                liveProgress = liveProgress,
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
                                onCancel = { viewModel.applyBulkAction(DownloadBulkAction.CANCEL, DownloadActionScope.Item(download.id)) },
                                onPause = { viewModel.applyBulkAction(DownloadBulkAction.PAUSE, DownloadActionScope.Item(download.id)) },
                                onResume = { viewModel.applyBulkAction(DownloadBulkAction.RESUME, DownloadActionScope.Item(download.id)) },
                                onDelete = { viewModel.pendingDelete.show(download) },
                                onRetry = { viewModel.applyBulkAction(DownloadBulkAction.RETRY_FAILED, DownloadActionScope.Item(download.id)) },
                                onMoveToFront = { viewModel.moveToFront(download) },
                                onLowerPriority = { viewModel.lowerPriority(download) },
                                onToggleSelection = { viewModel.toggleSelection(download) },
                            )
                        }
                        // Swipe-to-delete shortcut (touch, outside selection
                        // mode): reveals the trash background and opens the
                        // SAME confirm dialog the row's delete action uses —
                        // the destructive step keeps its confirmation. TV rows
                        // skip the wrapper (D-pad focus, no touch).
                        val entranceModifier = Modifier.detailEntrance(
                            progress = { entrance.value },
                            slideDivisor = 10f,
                        )
                        if (!selectionMode && !isTv) {
                            SwipeActionBox(
                                onAction = { viewModel.pendingDelete.show(download) },
                                actionContentDescription = stringResource(Res.string.downloads_delete),
                                modifier = entranceModifier,
                                shape = ShapeCache.smooth12,
                            ) {
                                row(Modifier)
                            }
                        } else {
                            row(entranceModifier)
                        }
                    }
                }

                // Selection-mode bottom action bar. The shared core/ui shell
                // (smooth12 / surfaceContainerHigh / shadow 8) with the
                // downloads-specific cluster slotted in: pause / resume /
                // cancel lead (each gated on its own per-list predicate, the
                // slot contract explicitly allows ignoring the folded boolean)
                // and the destructive bulk delete trails.
                if (selectionMode) {
                    SelectionActionBar(
                        countLabel = stringResource(Res.string.downloads_selected_count, selectedIds.size),
                        selectedCount = selectedIds.size,
                        selectAllLabel = stringResource(Res.string.downloads_action_select_all),
                        clearLabel = stringResource(Res.string.downloads_action_clear_selection),
                        onSelectAll = { viewModel.selectAll() },
                        onClear = { viewModel.clearSelection() },
                        leadingActions = { _ ->
                            CompactIconButton(
                                onClick = { viewModel.applyBulkAction(DownloadBulkAction.PAUSE, DownloadActionScope.Selected) },
                                enabled = hasPauseable,
                            ) {
                                Icon(Tabler.Outline.PlayerPause, contentDescription = stringResource(Res.string.downloads_action_pause), modifier = Modifier.size(20.dp))
                            }
                            CompactIconButton(
                                onClick = { viewModel.applyBulkAction(DownloadBulkAction.RESUME, DownloadActionScope.Selected) },
                                enabled = hasResumable,
                            ) {
                                Icon(Tabler.Outline.PlayerPlay, contentDescription = stringResource(Res.string.downloads_action_resume), modifier = Modifier.size(20.dp))
                            }
                            CompactIconButton(
                                onClick = { viewModel.applyBulkAction(DownloadBulkAction.CANCEL, DownloadActionScope.Selected) },
                                enabled = hasCancellable,
                            ) {
                                Icon(Tabler.Outline.PlayerStop, contentDescription = stringResource(Res.string.downloads_action_cancel), modifier = Modifier.size(20.dp))
                            }
                        },
                        actions = { actionsEnabled ->
                            FilledTonalButton(
                                onClick = { viewModel.pendingBulkDelete.show(Unit) },
                                enabled = actionsEnabled,
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
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            // Sit above the floating nav (presence-aware
                            // clearance) and slide up in lockstep when the nav
                            // hides itself.
                            .clearFloatingNav(extraBottom = 0.dp),
                    )
                }
            }
        }
    }

    DownloadsDeleteDialogs(
        viewModel = viewModel,
        selectedItems = selectedItems,
        selectedIds = selectedIds,
    )

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
