package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.raulshma.jellyplay.core.data.download.toSeriesPolicyChoice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.composables.icons.tabler.Tabler
import kotlinx.coroutines.launch
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.rememberIsLightTheme
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.PendingConfirmation
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmState
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.downloads.DeleteDownloadedEpisodesSheet
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.QuickActionAdapter
import com.raulshma.jellyplay.core.ui.components.QuickActionIntakeHost
import com.raulshma.jellyplay.core.ui.components.downloads.RefreshMetadataSheet
import com.raulshma.jellyplay.core.ui.components.seerr.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.downloads.SeriesDownloadSheet
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.downloads.downloadedSeasonSlices
import com.raulshma.jellyplay.core.ui.components.rememberConfirmState
import com.raulshma.jellyplay.core.ui.components.rememberQuickActionIntake
import com.raulshma.jellyplay.core.ui.components.rememberVideoClickHandler
import com.raulshma.jellyplay.core.ui.feedback.SnackbarQueue
import com.raulshma.jellyplay.core.ui.feedback.SnackbarQueueEffect
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cancel
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cellular_download_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cellular_download_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete_download_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete_download_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete_episode_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete_episode_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_anyway
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_season_unwatched_confirm_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_season_unwatched_confirm_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_season_watched_confirm_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_season_watched_confirm_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_series_unwatched_confirm_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_series_unwatched_confirm_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_series_watched_confirm_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_series_watched_confirm_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_no_episodes_queued
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_watch_party_started
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_split_versions
import com.raulshma.jellyplay.feature.details.generated.resources.detail_split_confirm_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_split_confirm_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_episodes_queued
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.getString
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_confirm
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import org.jetbrains.compose.resources.pluralStringResource

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaDetailScreen(
    itemId: String,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long, subtitleStreamIndex: Int?, audioStreamIndex: Int?) -> Unit,
    onAudioClick: (itemId: String) -> Unit,
    /**
     * Open the book reader for a BOOK item (details screen Read button).
     * The jump pair is the one-shot "Contents" deep-link destination (EPUB
     * href / 0-based PDF page) — nulls for plain Read/Continue.
     */
    onReadClick: (itemId: String, jumpHref: String?, jumpPage: Int?) -> Unit = { _, _, _ -> },
    onItemClick: (itemId: String) -> Unit,
    onPersonClick: (personId: String) -> Unit,
    onNavigateToSeries: (seriesId: String) -> Unit,
    onManageSeries: (seriesId: String) -> Unit = {},
    onNavigate: (Route) -> Unit = {},
    onEditClick: (itemId: String) -> Unit = {},
    /** Seed for the series download sheet (route deep-link from a card
     * long-press Download on a series). Consumed once the item loads. */
    openDownloadSheetOnEntry: Boolean = false,
    onBack: () -> Unit,
    /**
     * The process-wide playback seams the play funnels read — ONE
     * constructor bundle instead of four mid-body `koinInject` pulls (the
     * constructor convention this screen already follows with [viewModel]):
     * the default resolves the same Koin singles the pulls did, so the
     * navigation wiring is unchanged, and a direct construction hands fakes.
     */
    seams: MediaDetailPlaybackSeams = rememberMediaDetailPlaybackSeams(),
    viewModel: DetailViewModel = koinViewModel(),
) {
    // Declared before the load-item effect below so the trailer-dismiss reset
    // can be merged into it (was previously a separate LaunchedEffect(itemId)).
    var activeTrailerKey by remember { mutableStateOf<String?>(null) }

    // The POST_NOTIFICATIONS runtime-flow seam (Android actual fires the
    // Android 13+ system dialog when not granted; desktop is a no-op). The
    // per-series notify toggle's first enable invokes it beside the config
    // write — the MainActivity cold-start prompt pattern, mirrored.
    val requestSeriesNotificationPermission = rememberSeriesNotificationPermissionRequest()

    LaunchedEffect(itemId) {
        viewModel.onEvent(DetailUiEvent.LoadItem(itemId))
        // Dismiss any open trailer dialog when navigating to a different item
        // (previously a separate LaunchedEffect(itemId) block).
        activeTrailerKey = null
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Download-lifecycle slice, slim projection: the always-composed
    // root needs only the button spinner + picker + downloaded-ids +
    // cellular-warning fields (they flow into DetailContentState below), so
    // collecting the slim rootState keeps a ~2 s transfer tick — which only
    // moves episodeDownloadProgress — from recomposing this root scope and
    // re-running the ~60-field bag construction. The per-tick map and the
    // sheets read the full state in their own narrow scopes below.
    val downloads by viewModel.downloads.rootState.collectAsStateWithLifecycle()
    // Resync slice: same story — the always-composed freshness banner reads it
    // through the bag; the resync sheet reads the same value.
    val resyncState by viewModel.resync.state.collectAsStateWithLifecycle()
    val detail = uiState.detail
    val canManageSeries by viewModel.canManageSeries.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    val currentItem = detail?.item
    val targetBackdropId = currentItem
        ?.takeIf { it.mediaType == MediaType.EPISODE }?.seriesId
        ?: currentItem?.id
        ?: itemId
    // On-entry freshness check (TTL-gated, safe on every entry) — ports the
    // old OfflineDetailScreen auto-fire. Only relevant when there's local
    // content to check (an attached download or a local origin); the sync
    // manager no-ops within the per-item 1h TTL or while offline.
    val hasLocalContent = uiState.detailContext?.download != null || uiState.origin?.isLocal == true
    LaunchedEffect(currentItem?.id, hasLocalContent) {
        if (currentItem != null && hasLocalContent) {
            viewModel.resync.checkForUpdates()
        }
    }
    // Memoized so the URL isn't rebuilt on every recomposition (e.g. on each
    // scroll-derived state change funnelling through ArtworkThemeWrapper).
    // Books have no Backdrop image — the hero (and the cover-tinted theme)
    // source the PRIMARY (cover) art instead. Item-backed ids resolve through
    // the tag-aware resolver (keyed on the ITEM, not its id, so a re-tagged
    // artwork remints the URL); the parent-series leg (episodes render the
    // SERIES backdrop) has no series backdrop tag on MediaImageTags, so it
    // stays the raw untagged getter — same target id as before.
    val backdropUrl = remember(currentItem) {
        when {
            currentItem == null -> viewModel.getBackdropUrl(targetBackdropId)
            currentItem.mediaType == MediaType.BOOK -> viewModel.artwork.poster(currentItem)
            currentItem.mediaType == MediaType.EPISODE ->
                viewModel.getBackdropUrl(currentItem.seriesId ?: currentItem.id)
            else -> viewModel.artwork.backdrop(currentItem)
        }
    }

    val outerIsLightTheme = rememberIsLightTheme()

    // ── Sheet/dialog session. The seven screen-local sheet-visibility flags
    // (series download / refresh-metadata / version picker / split confirm /
    // delete-episodes / resync / download-details) plus their open/dismiss
    // cascades live in the tested [DetailDialogSession] — the series sheet's
    // reset arm alone used to be hand-repeated in three call sites. The
    // hosting `if`s stay below (Compose needs them there); the confirm
    // MACHINES further down ([PendingConfirmation] pair, mark-played
    // [ConfirmState]) stay screen-local — deferred-call machines, not
    // open/close flags. ──
    val dialogSession = remember(viewModel) {
        DetailDialogSession(
            cascades = object : DetailDialogSession.Cascades {
                override fun prepareSeriesDownloadSheet() {
                    viewModel.downloads.loadDownloadedEpisodeIds()
                    viewModel.downloads.prepareDownloadSheetEpisodes()
                }

                override fun resetSeriesDownloadSheetState() {
                    viewModel.downloads.resetDownloadSheetState()
                }

                override fun loadDownloadFileInventory() {
                    viewModel.downloads.loadDownloadFileInventory()
                }

                override fun clearDownloadFileInventory() {
                    viewModel.downloads.clearDownloadFileInventory()
                }

                override fun clearResyncState() {
                    viewModel.resync.clearResyncState()
                }
            },
        )
    }
    /**
     * The version picked in the version picker for the CURRENT item; null =
     * the server's default (first) source. Keyed reset on item change so the
     * pending pick never leaks across items. (The picker's OPEN flag is on
     * [dialogSession]; this is the selection value the play path reads.)
     */
    var selectedVersionId by remember(detail?.item?.id) { mutableStateOf<String?>(null) }

    // Route deep-link: a card long-press Download on a series lands here so
    // the user picks seasons/episodes. Seed the sheet once the detail resolves
    // as a series (the sheet host below also gates on that), re-running when
    // the loaded id changes so the flag survives a slow first load.
    LaunchedEffect(openDownloadSheetOnEntry, detail?.item?.id) {
        if (openDownloadSheetOnEntry && detail?.item?.mediaType == MediaType.SERIES) {
            dialogSession.openSeriesDownloadSheet()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()

    // ── Unified-provider action dialog state. Delete / resync /
    // re-download get the same TV/mobile focus, back, and snackbar handling as
    // the existing detail actions. ──
    /**
     * Delete-confirm machines ([PendingConfirmation]). The deletes are
     * fire-and-forget (the VM-owned `OfflineDeleteActions` launches on its own
     * scope and returns immediately), so the machine settles SYNCHRONOUSLY at
     * the confirm tap — `clear()` runs before the handler returns, which makes
     * double-fire structurally impossible and leaves the machine's in-flight
     * guard arm unreachable here (dismiss/confirm are called with
     * `inFlight = false`). Settle arm: an explicit [PendingConfirmation.clear]
     * at confirm, so the pending item never outlives the dialog.
     */
    var pendingDelete by remember { mutableStateOf(PendingConfirmation<PendingDelete>()) }
    /** Pending delete of a downloaded episode from the seasons section. */
    var pendingDeleteEpisode by remember { mutableStateOf(PendingConfirmation<PendingEpisodeDelete>()) }

    // Series/season mark-played cascades recurse into every episode and clear
    // all resume positions, so they're gated behind a confirm. ONE machine:
    // the shared [ConfirmState] holds the deferred call, and the ONE pending
    // [MarkPlayedConfirmRequest] carries what the dialog must say (scope x
    // direction) - one value covers series/season confirm and the watched
    // mirror.
    val markPlayedConfirm = rememberConfirmState()
    var markPlayedRequest by remember { mutableStateOf<MarkPlayedConfirmRequest?>(null) }

    // The four mark-played callbacks (item/season × watched/unwatched) fold
    // into one remembered dispatcher: [DetailPlayPolicies.dispatchMarkPlayedAction]
    // owns the confirm-vs-direct table over (mediaType × season action) and this
    // lambda only arms the single request value + ViewModel call.
    // mediaType is read fresh at invocation (not captured) so the gate always
    // sees the loaded item's type.
    val dispatchMarkPlayedAction = remember(viewModel, markPlayedConfirm) {
        { isSeasonAction: Boolean, toWatched: Boolean, seasonId: String? ->
            val request = MarkPlayedConfirmRequest(
                scope = if (isSeasonAction) {
                    MarkPlayedConfirmRequest.Scope.Season(requireNotNull(seasonId))
                } else {
                    MarkPlayedConfirmRequest.Scope.Item
                },
                toWatched = toWatched,
                confirm = markPlayedConfirm,
            )
            val confirm: () -> Unit = {
                markPlayedConfirm.request {
                    when (val scope = request.scope) {
                        MarkPlayedConfirmRequest.Scope.Item ->
                            if (toWatched) viewModel.onEvent(DetailUiEvent.MarkPlayed)
                            else viewModel.onEvent(DetailUiEvent.MarkUnplayed)
                        is MarkPlayedConfirmRequest.Scope.Season ->
                            if (toWatched) viewModel.onEvent(DetailUiEvent.MarkSeasonPlayed(scope.seasonId))
                            else viewModel.onEvent(DetailUiEvent.MarkSeasonUnplayed(scope.seasonId))
                    }
                }
                markPlayedRequest = request
            }
            // Direct dispatch: reachable only for item-level marks on
            // movies/episodes (the season row of the gate always confirms, so
            // the season callbacks never take this branch).
            val action: () -> Unit = {
                if (toWatched) viewModel.onEvent(DetailUiEvent.MarkPlayed)
                else viewModel.onEvent(DetailUiEvent.MarkUnplayed)
            }
            DetailPlayPolicies.dispatchMarkPlayedAction(
                mediaType = uiState.detail?.item?.mediaType,
                isSeasonAction = isSeasonAction,
                confirm = confirm,
                action = action,
            )
        }
    }

    // Quick actions for row items (related/collection/episode cards) and the
    // TV Menu key on the focused card, on the shared intake (core/ui — see
    // QuickActionIntake). The controller is provided to every
    // PosterCard/EpisodeCard below via CompositionLocal. Download / Remove
    // download ride the same intake as the library grid (#147): a downloaded
    // row card flips the slot to "Remove download". The downloaded set is
    // read through the VM flow's .value at resolve time (not collected) —
    // the sheet re-resolves on every open, so no snapshot churn here.
    val quickActionIntake = rememberQuickActionIntake(
        scope = MediaQuickActionScope.DETAIL,
        includeDownload = true,
        isDownloaded = remember(viewModel) {
            { item: MediaItem -> viewModel.quickActionDownloadedIds.value.contains(item.id) }
        },
        adapter = remember(viewModel, onPlayClick, onItemClick) {
            QuickActionAdapter(
                onPlay = { item ->
                    onPlayClick(
                        item.id,
                        null,
                        item.playbackPositionTicks ?: 0L,
                        viewModel.selectedSubtitleIndex,
                        viewModel.selectedAudioIndex,
                    )
                },
                onOpenDetail = { item -> onItemClick(item.id) },
                onMarkPlayed = { item, played ->
                    viewModel.onEvent(DetailUiEvent.MarkRowItemPlayed(item = item, played = played))
                },
                onDownload = { item ->
                    viewModel.onEvent(DetailUiEvent.DownloadRowItem(item = item, onOpenDetail = onItemClick))
                },
                onRemoveDownload = { item ->
                    viewModel.onEvent(DetailUiEvent.RemoveRowItemDownload(item = item))
                },
            )
        },
    )

    // ── Queued-episodes plural snackbar (see the SeriesDownload branch
    // below) — the core/ui SnackbarQueue. The queue owns the one-at-a-time +
    // FIFO discipline (burst-queued SeriesDownload counts must queue, not
    // overwrite; the head pops only AFTER showSnackbar returns), and the
    // count→text resolution is injected here as a @Composable because CMP's
    // suspend plural resolver is internal. The queue's residual edge — a
    // directly-following DetailMessage.Text can still win the
    // SnackbarHostState mutex before this effect's next frame — is documented
    // and pinned as accepted behaviour on [SnackbarQueue]. ──
    val snackbarQueue = remember { SnackbarQueue<Int>() }
    SnackbarQueueEffect(
        queue = snackbarQueue,
        snackbarHostState = snackbarHostState,
        resolve = { count -> pluralStringResource(Res.plurals.detail_episodes_queued, count, count) },
    )

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            when (message) {
                is DetailMessage.Text -> snackbarHostState.showSnackbar(message.text)
                is DetailMessage.SeriesDownload -> {
                    // Plain strings resolve via the suspend compose-resources
                    // resolver inside collect (syncplay flow-escape pattern).
                    // The PLURAL cannot: CMP's suspend plural resolver is
                    // internal, so the count is queued in composition-owned
                    // state ([SnackbarQueue]) and rendered above with the
                    // @Composable pluralStringResource, one-at-a-time, FIFO.
                    if (message.error != null) {
                        snackbarHostState.showSnackbar(message.error)
                    } else if (message.queuedCount > 0) {
                        snackbarQueue.enqueue(message.queuedCount)
                    } else {
                        snackbarHostState.showSnackbar(getString(Res.string.detail_msg_no_episodes_queued))
                    }
                }
                is DetailMessage.WatchPartyStarted -> {
                    // Fire-and-forget confirmation; navigation (below) is the
                    // primary feedback. Launched on screenScope so it does not
                    // block the navigate-to-player handoff.
                    screenScope.launch {
                        snackbarHostState.showSnackbar(
                            getString(Res.string.detail_msg_watch_party_started)
                        )
                    }
                    // Open the player, reusing the SAME Route.VideoPlayer builder
                    // the play button uses (DetailsNavigation wires onPlayClick →
                    // Route.VideoPlayer). startPositionTicks = 0 = fresh group
                    // start; the SyncPlayBridge auto-detects the active session.
                    onPlayClick(message.itemId, null, 0L, null, null)
                }
            }
        }
    }

    val cellularWarningMb = downloads.cellularDownloadWarningMb
    if (cellularWarningMb != null) {
        ConfirmDialog(
            title = stringResource(Res.string.detail_cellular_download_title),
            message = stringResource(Res.string.detail_cellular_download_message, cellularWarningMb),
            confirmText = stringResource(Res.string.detail_download_anyway),
            dismissText = stringResource(Res.string.detail_cancel),
            tone = ConfirmTone.NEUTRAL,
            onConfirm = { viewModel.downloads.confirmCellularDownload() },
            onDismiss = { viewModel.downloads.dismissCellularDownloadWarning() },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onDpadKey(
                onMenu = {
                    // TV remote Menu button: open the focused card's quick
                    // actions. The focused card is tracked by the rows via
                    // onFocusedMediaItem; an unfocused Menu propagates — the
                    // only host with the non-consuming contract, hence the
                    // returned flag instead of `true`.
                    quickActionIntake.openFocusedItem()
                },
            ),
    ) {
        ArtworkThemeWrapper(
            imageUrl = backdropUrl,
            dynamicTheming = preferences.theme.dynamicTheming,
            darkTheme = !outerIsLightTheme,
            oledMode = preferences.theme.oledMode,
            colorStyle = preferences.theme.colorStyle,
            accentColorSwatch = preferences.theme.accentColorSwatch,
        ) {
            // Seerr integration state (the data holder's single snapshot,
            // folded into uiState as-is) plus the dialog holder's frozen-at-open
            // item — the render gate for the request dialog below.
            val seerrRequest = uiState.seerrRequest
            val seerrDialogItem by viewModel.seerrRequestDialog.item.collectAsStateWithLifecycle()

            // Seerr card loading state for prefetch animation: one root-level
            // bundle of the callback + loading state; descendant rows read them
            // via the composition locals.
            com.raulshma.jellyplay.core.ui.components.seerr.ProvideSeerrCardPrefetching(
                prefetchDetail = { tmdbId, mediaType, onDone ->
                    viewModel.seerrRequests.prefetchDetails(tmdbId, mediaType, onDone)
                }
            ) {
                val rememberedGetChapterImageUrl = remember(viewModel) {
                    { id: String, index: Int, tag: String? -> viewModel.getChapterImageUrl(id, index, tag) }
                }

                // Series id used by season-episode fetches. Keyed on BOTH the
                // item id and the resolved series id so it recomputes once the
                // detail arrives. Previously this keyed only on `itemId`, so on
                // first composition (detail still null) an episode resolved to
                // its own id as the series id — producing wrong/empty season
                // fetches and wasted refetches.
                val seriesIdForSeasons = remember(itemId, detail?.item?.seriesId) {
                    detail?.item?.seriesId ?: itemId
                }

                // Constructed directly (not via `remember`) — DetailContentState
                // is a @Immutable data class, so Compose structural-equals it for
                // free and `DetailContent` is skipped whenever no field actually
                // changed. The former 22-key `remember` added per-recomposition
                // key-comparison cost and silently drifted whenever a new uiState
                // field was added without updating the key list.
                val state = DetailContentState(
                    core = uiState,
                    itemId = itemId,
                    // Resolve the user's last-pinned season for this series from
                    // the projected preferences. An active resume still wins
                    // (decided in SeasonStartResolver); null when nothing is
                    // pinned → prior behaviour.
                    persistedSeasonId = preferences.lastViewedSeasonBySeries[seriesIdForSeasons],
                    selectedVersionId = selectedVersionId,
                    canManageSeries = canManageSeries,
                    preferences = preferences,
                    resyncState = resyncState,
                    downloads = DetailContentState.DownloadsExtras(
                        isDownloading = downloads.isDownloading,
                        isDownloadingSeries = downloads.isDownloadingSeries,
                        downloadedEpisodeIds = downloads.downloadedEpisodeIds,
                        downloadPicker = downloads.downloadPicker,
                    ),
                )

                // Remembered with no keys: the lambda captures the delegated
                // MutableState itself, so the assignment resolves at invocation
                // and stays correct across recompositions. A fresh lambda per
                // pass would re-key rememberVideoClickHandler — and every
                // bundle downstream that captures its result — each time.
                val onPlayYouTube = remember { { key: String -> activeTrailerKey = key } }
                val onVideoClick = rememberVideoClickHandler(
                    uriHandler = uriHandler,
                    onPlayYouTube = onPlayYouTube,
                )

                // ── Per-concern callback bundles. Each bundle is remembered on
                // exactly the inputs its lambdas capture, so a new capability
                // edits its bundle here + its section file — not a growing flat
                // constructor. Two bundles additionally key on plain local vals
                // their lambdas capture (`detail`, `seriesIdForSeasons`): the
                // former single remember was effectively refreshed every
                // recomposition, and these captures must not go stale now that
                // every bundle key (incl. `onVideoClick`, stable since
                // `onPlayYouTube` was remembered) is stable. Delegated reads
                // (`uiState`, `var … by remember`) resolve at invocation time
                // and need no keys. ──
                // The resolver itself is the stateless core:data single the VM
                // holds — sections tag URLs off the MediaItems they already
                // render; only the chapter getter (coordinates, no id) stays a
                // lambda.
                val artworkCallbacks = remember(viewModel, rememberedGetChapterImageUrl) {
                    ArtworkCallbacks(
                        urls = viewModel.artwork,
                        getChapterImageUrl = rememberedGetChapterImageUrl,
                        // The raw untagged backdrop getter — the parent-series
                        // leg of the content tree's backdrop fold (an id with
                        // no MediaItem in scope). The same read the screen's
                        // own backdropUrl fold makes below.
                        getBackdropUrl = viewModel::getBackdropUrl,
                    )
                }

                // The process-wide playback seams arrive as ONE constructor
                // bundle ([MediaDetailPlaybackSeams]), hoisted to the screen's
                // signature.
                val playbackCallbacks = remember(viewModel, seams, onPlayClick, onAudioClick, onReadClick, itemId) {
                    PlaybackCallbacks(
                        onPlayClick = { playItemId: String, sourceId: String?, start: Long ->
                            // The play click's whole decision is the pure
                            // [DetailPlaySequencer] fold — the stream
                            // selection ([DetailPlayPolicies.resolveDetailPlayDispatch])
                            // plus the trailer-then-feature sequencing
                            // (with the queue-seeding order its KDoc
                            // documents); this lambda only executes the
                            // returned effects in order.
                            DetailPlaySequencer.sequence(
                                DetailPlaySequencer.DetailPlayContext(
                                    requestedItemId = playItemId,
                                    requestedMediaSourceId = sourceId,
                                    requestedStartPositionTicks = start,
                                    currentItem = uiState.detail?.item,
                                    origin = uiState.origin,
                                    localSubtitleIndex = viewModel.selectedLocalSubtitleIndex,
                                    remoteSubtitleIndex = viewModel.selectedSubtitleIndex,
                                    audioStreamIndex = viewModel.selectedAudioIndex,
                                    trailers = uiState.trailers,
                                    // The loaded detail item IS the route id; fall
                                    // back to the raw getter for the pre-load window.
                                    currentItemPosterUrl = uiState.detail?.item
                                        ?.takeIf { it.id == itemId }
                                        ?.let { viewModel.artwork.poster(it) }
                                        ?: viewModel.getImageUrl(itemId),
                                    playTrailerBeforeMovie = seams.videoPlayerStore.videoPlayer.value.playTrailerBeforeMovie,
                                    isSyncPlayActive = seams.syncPlayManager.isInSyncPlaySession,
                                ),
                            ).forEach { effect ->
                                when (effect) {
                                    is DetailPlaySequencer.DetailPlayEffect.EnqueueNext ->
                                        seams.videoQueue.enqueueNext(effect.item)
                                    is DetailPlaySequencer.DetailPlayEffect.PlaySession -> onPlayClick(
                                        effect.itemId,
                                        effect.mediaSourceId,
                                        effect.startPositionTicks,
                                        effect.subtitleStreamIndex,
                                        effect.audioStreamIndex,
                                    )
                                }
                            }
                        },
                        onPlayChapter = { start ->
                            // Resume the current item at a chapter position. Reuses the
                            // same play path + stream selection as the primary play button
                            // via the shared [DetailPlayPolicies.resolveDetailPlayDispatch]
                            // fold; this lambda only dispatches.
                            val dispatch = DetailPlayPolicies.resolveDetailPlayDispatch(
                                origin = uiState.origin,
                                localSubtitleIndex = viewModel.selectedLocalSubtitleIndex,
                                remoteSubtitleIndex = viewModel.selectedSubtitleIndex,
                                audioStreamIndex = viewModel.selectedAudioIndex,
                            )
                            onPlayClick(
                                itemId,
                                null,
                                start,
                                dispatch.subtitleStreamIndex,
                                dispatch.audioStreamIndex,
                            )
                        },
                        onPlayExtra = { extra ->
                            // Play a special feature / extra from the start. An extra is
                            // its own item (trailer / featurette / deleted scene), so it
                            // starts at position 0 and inherits no subtitle/audio selection
                            // from the parent item.
                            onPlayClick(extra.id, null, 0L, null, null)
                        },
                        onAudioClick = { onAudioClick(itemId) },
                        onReadClick = { readItemId: String, jumpHref: String?, jumpPage: Int? ->
                            onReadClick(readItemId, jumpHref, jumpPage)
                        },
                        onPlayAlbumTrack = { index: Int -> viewModel.onEvent(DetailUiEvent.PlayAlbum(startIndex = index)) },
                        onSubtitleSelect = { idx: Int? -> viewModel.onEvent(DetailUiEvent.SelectSubtitle(idx)) },
                        onAudioSelect = { idx: Int? -> viewModel.onEvent(DetailUiEvent.SelectAudio(idx)) },
                        onSelectLocalSubtitle = { index -> viewModel.onEvent(DetailUiEvent.SelectLocalSubtitle(index)) },
                        onStartInstantMix = { viewModel.onEvent(DetailUiEvent.StartInstantMix) },
                        onStartRadio = { viewModel.onEvent(DetailUiEvent.StartRadio) },
                        onStartWatchParty = { viewModel.watchParty.startScreenItem() },
                        // "Play (with pre-rolls)": arm the one-shot Cinema
                        // Mode override, then dispatch the current item from
                        // the start through the normal play path — the
                        // session's user-open funnel consumes the override
                        // into LoadRequest.forceCinemaMode and the pipeline's
                        // cinema gate ORs it over the global pref (remaining
                        // vetoes still apply). The stream selection is the
                        // same policy fold, forced via the override record.
                        onPlayWithPrerolls = {
                            seams.cinemaModeOverride.arm()
                            val dispatch = DetailPlayPolicies.resolveDetailPlayDispatch(
                                origin = uiState.origin,
                                localSubtitleIndex = viewModel.selectedLocalSubtitleIndex,
                                remoteSubtitleIndex = viewModel.selectedSubtitleIndex,
                                audioStreamIndex = viewModel.selectedAudioIndex,
                                forceCinemaMode = true,
                            )
                            onPlayClick(
                                itemId,
                                null,
                                0L,
                                dispatch.subtitleStreamIndex,
                                dispatch.audioStreamIndex,
                            )
                        },
                        onOpenVersionPicker = { dialogSession.openVersionPicker() },
                        // "Play collection in order": start at the FIRST
                        // UNWATCHED member (franchisePlayOrder — plain in-order
                        // when every member is watched or user data is absent)
                        // and append the members AFTER it to the manual queue
                        // (VideoQueueController.enqueueLast, queue-first
                        // semantics; suppressed under SyncPlay by the player's
                        // advance routing — VideoQueueController's own contract).
                        // The start rides the raw onPlayClick path (not the
                        // detail-dispatch wrapper): the target is another item,
                        // so the current item's stream selections must not
                        // carry over (the onPlayExtra precedent).
                        onPlayCollectionInOrder = { collection ->
                            franchisePlayOrder(collection.members)?.let { order ->
                                order.queuedTail.forEach { member ->
                                    seams.videoQueue.enqueueLast(
                                        com.raulshma.jellyplay.core.data.playback.VideoQueueItem(
                                            itemId = member.id,
                                            title = member.name,
                                            subtitle = member.seriesName ?: member.year?.toString(),
                                            imageUrl = viewModel.artwork.poster(member),
                                            runTimeTicks = member.runTimeTicks,
                                        ),
                                    )
                                }
                                onPlayClick(order.start.id, null, 0L, null, null)
                            }
                        },
                    )
                }

                val downloadCallbacks = remember(viewModel, itemId, detail) {
                    DownloadCallbacks(
                        onDownloadClick = { viewModel.downloads.startDownload() },
                        onOpenDownloadPicker = { viewModel.downloads.openDownloadPicker() },
                        onDismissDownloadPicker = { viewModel.downloads.dismissDownloadPicker() },
                        onPendingQualityChange = { viewModel.downloads.setPendingQuality(it) },
                        onPendingSubtitleSelectionChange = { viewModel.downloads.setPendingSubtitleSelection(it) },
                        onDownloadSeriesClick = { dialogSession.openSeriesDownloadSheet() },
                        onDeleteDownload = {
                            val target = detail?.item
                            val isEpisode = target?.mediaType == MediaType.EPISODE
                            pendingDelete = pendingDelete.hold(
                                PendingDelete(
                                    itemId = target?.id ?: itemId,
                                    name = target?.name ?: "",
                                    sizeBytes = uiState.detailContext?.download?.totalSizeBytes ?: 0L,
                                    isEpisode = isEpisode,
                                ),
                            )
                        },
                        onDeleteDownloadedEpisodes = { dialogSession.openDeleteEpisodesSheet() },
                        onDeleteEpisode = { episodeId ->
                            val ep = uiState.episodes.values.flatten().firstOrNull { it.id == episodeId }
                            pendingDeleteEpisode = pendingDeleteEpisode.hold(
                                PendingEpisodeDelete(
                                    episodeId = episodeId,
                                    name = ep?.name ?: "",
                                ),
                            )
                        },
                        onOpenResync = { dialogSession.openResyncSheet() },
                        onOpenDownloadDetails = {
                            // Load the on-disk inventory (media + sidecars) before showing
                            // the sheet so sizes are fresh; it re-reads on every open.
                            dialogSession.openDownloadDetailsSheet()
                        },
                    )
                }

                val seasonsCallbacks = remember(viewModel, seriesIdForSeasons, dispatchMarkPlayedAction) {
                    SeasonsCallbacks(
                        onSeasonSelected = { seasonId: String ->
                            viewModel.onEvent(
                                DetailUiEvent.LoadEpisodesForSeason(seriesId = seriesIdForSeasons, seasonId = seasonId),
                            )
                        },
                        onSeasonPinned = { seasonId: String ->
                            viewModel.onEvent(
                                DetailUiEvent.SetLastViewedSeason(seriesId = seriesIdForSeasons, seasonId = seasonId),
                            )
                        },
                        onEpisodesDescendingChange = { descending: Boolean ->
                            viewModel.onEvent(DetailUiEvent.SetEpisodesDescending(descending))
                        },
                        onCompactEpisodeListChange = { enabled: Boolean ->
                            viewModel.onEvent(DetailUiEvent.SetCompactEpisodeList(enabled))
                        },
                        onMarkSeasonPlayed = { seasonId -> dispatchMarkPlayedAction(true, true, seasonId) },
                        onMarkSeasonUnplayed = { seasonId -> dispatchMarkPlayedAction(true, false, seasonId) },
                    )
                }

                val userDataCallbacks = remember(viewModel, dispatchMarkPlayedAction) {
                    UserDataCallbacks(
                        onToggleFavorite = { viewModel.onEvent(DetailUiEvent.ToggleFavorite) },
                        // dispatchMarkPlayedAction(isSeasonAction, toWatched, seasonId)
                        onMarkPlayed = { dispatchMarkPlayedAction(false, true, null) },
                        onMarkUnplayed = { dispatchMarkPlayedAction(false, false, null) },
                        onHideFromNextUp = { viewModel.onEvent(DetailUiEvent.HideFromNextUp) },
                        onShowFromNextUp = { viewModel.onEvent(DetailUiEvent.ShowFromNextUp) },
                        onHideFromContinueWatching = { viewModel.onEvent(DetailUiEvent.HideFromContinueWatching) },
                        onShowFromContinueWatching = { viewModel.onEvent(DetailUiEvent.ShowFromContinueWatching) },
                        onShowDetailUpNext = { viewModel.onEvent(DetailUiEvent.SetShowDetailUpNext(enabled = true)) },
                        onHideDetailUpNext = { viewModel.onEvent(DetailUiEvent.SetShowDetailUpNext(enabled = false)) },
                        onHideFranchiseRow = { collectionId ->
                            viewModel.onEvent(DetailUiEvent.HideFranchiseRow(collectionId))
                        },
                        // The config write routes through the VM; the
                        // POST_NOTIFICATIONS runtime prompt (first enable on
                        // Android 13+, the MainActivity cold-start pattern)
                        // fires beside it via the platform seam. Desktop —
                        // where the surfaces are hidden — never reaches this.
                        onToggleSeriesNotifications = { enabled ->
                            viewModel.onEvent(DetailUiEvent.SetSeriesNotifications(enabled))
                            if (enabled) requestSeriesNotificationPermission()
                        },
                    )
                }

                val seerrCallbacks = remember(viewModel, onVideoClick) {
                    SeerrCallbacks(
                        onSeerrRequest = { item: SeerrSearchItem ->
                            viewModel.seerrRequestDialog.open(item)
                        },
                        onVideoClick = onVideoClick,
                    )
                }

                val addToCallbacks = remember(viewModel) {
                    AddToCallbacks(
                        onAddToPlaylist = { viewModel.playlists.openPicker() },
                        onAddToCollection = { viewModel.collections.openPicker() },
                    )
                }

                val navigationCallbacks = remember(
                    onItemClick, onPersonClick, onNavigateToSeries, onNavigate, onEditClick,
                    onManageSeries, onBack, itemId,
                ) {
                    NavigationCallbacks(
                        onBack = onBack,
                        onNavigate = onNavigate,
                        onItemClick = onItemClick,
                        onPersonClick = onPersonClick,
                        onSeeAllCast = {
                            onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.CastAndCrew(itemId))
                        },
                        onNavigateToSeries = onNavigateToSeries,
                        onManageSeries = { onManageSeries(itemId) },
                        onEditClick = { onEditClick(itemId) },
                    )
                }

                val screenCallbacks = remember(viewModel, itemId, quickActionIntake) {
                    ScreenCallbacks(
                        onRetry = { viewModel.onEvent(DetailUiEvent.LoadItem(itemId)) },
                        onRefresh = { viewModel.onEvent(DetailUiEvent.ForceRefresh) },
                        onMediaQuickActions = { item -> quickActionIntake.controller.show(item) },
                        onFocusedMediaItem = { item -> quickActionIntake.tvFocusedItem = item },
                        onToggleThemeMusicMuted = { muted ->
                            viewModel.onEvent(DetailUiEvent.SetThemeMusicMuted(muted))
                        },
                    )
                }

                val metadataCallbacks = remember(viewModel) {
                    MetadataCallbacks(
                        onRefreshMetadata = { dialogSession.openRefreshMetadataSheet() },
                        onIdentify = { viewModel.metadataAdmin.openIdentifyScreenItem() },
                        onOpenMergeVersions = { viewModel.metadataAdmin.openMergeVersions() },
                        onSplitVersions = { dialogSession.openSplitConfirm() },
                    )
                }

                val callbacks = remember(
                    artworkCallbacks,
                    playbackCallbacks,
                    downloadCallbacks,
                    seasonsCallbacks,
                    userDataCallbacks,
                    seerrCallbacks,
                    addToCallbacks,
                    navigationCallbacks,
                    screenCallbacks,
                    metadataCallbacks,
                ) {
                    DetailContentCallbacks(
                        artwork = artworkCallbacks,
                        playback = playbackCallbacks,
                        download = downloadCallbacks,
                        seasons = seasonsCallbacks,
                        userData = userDataCallbacks,
                        seerr = seerrCallbacks,
                        addTo = addToCallbacks,
                        navigation = navigationCallbacks,
                        screen = screenCallbacks,
                        metadata = metadataCallbacks,
                    )
                }

                val availableStorageProvider = remember(viewModel) {
                    val provider: suspend (Boolean) -> Long = { isAudio: Boolean -> viewModel.getAvailableStorageBytes(isAudio) }
                    provider
                }

                // The per-tick episodeDownloadProgress map is collected in
                // the narrow provider scope below — the only root-scope reader
                // of the full download state — so a ~2 s transfer tick
                // recomposes the provider plus the local's read-tracked rows,
                // never this scope's DetailContentState construction.
                EpisodeDownloadProgressProvider(viewModel.downloads.state) {
                    CompositionLocalProvider(
                        LocalMediaQuickActionController provides quickActionIntake.controller,
                        // The current item's live download row rides the flow
                        // local (not DetailContentState — see the local's KDoc):
                        // providing the flow object is stable, and only the leaf
                        // that reads the value (the ⋮ options-menu builder)
                        // collects it, so a ~2 s transfer tick never re-executes
                        // the DetailContentState construction above.
                        LocalActiveDownloadFlow provides
                            remember(itemId) { viewModel.downloads.downloadFlow(itemId) },
                    ) {
                        DetailContent(
                            state = state,
                            callbacks = callbacks,
                            availableStorageProvider = availableStorageProvider,
                            // The process-wide playback seams ride the screen's
                            // one bundle down to the content tree (the queue
                            // seeding + SyncPlay veto reads) — no mid-body
                            // koinInject pulls below the screen signature.
                            seams = seams,
                        )
                    }
                }

                // Seerr request dialog — the dialog holder owns the open
                // cascade and the dismiss ordering; the screen only gates the
                // render on its frozen-at-open item.
                seerrDialogItem?.let { item ->
                    SeerrRequestDialog(
                        item = item,
                        snapshot = seerrRequest,
                        onConfirm = { (serverId, profileId, rootFolder, tags, seasons) ->
                            viewModel.seerrRequests.requestMedia(item, seasons, serverId, profileId, rootFolder, tags)
                        },
                        onDismiss = { viewModel.seerrRequestDialog.dismiss() },
                    )
                }
            } // CompositionLocalProvider
        }

        // ── Add-to-Playlist sheet + create dialog. State is collected from the
        // owning helper at the composition site that reads it — a closed sheet
        // composes nothing and the content core never sees a playlist tick. ──
        val playlistState by viewModel.playlists.state.collectAsStateWithLifecycle()
        if (playlistState.showPicker && detail != null) {
            val playlistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            TvSafeSheet(
                onDismissRequest = { viewModel.playlists.dismissPicker() },
                sheetState = playlistSheetState,
            ) {
                AddToPlaylistSheet(
                    playlists = playlistState.targets,
                    isLoading = playlistState.isLoadingTargets,
                    isAdding = playlistState.isAdding,
                    onWatchLater = { viewModel.watchLater.addToWatchLater() },
                    onPick = { playlist -> viewModel.playlists.addTo(playlist) },
                    onCreateNew = { viewModel.playlists.openCreateDialog() },
                    onDismiss = { viewModel.playlists.dismissPicker() },
                )
            }
        }

        if (playlistState.showCreateDialog) {
            CreatePlaylistDialog(
                isLoading = playlistState.isAdding,
                onConfirm = { name, overview ->
                    viewModel.playlists.createAndAdd(name, overview)
                },
                onDismiss = { viewModel.playlists.dismissCreateDialog() },
            )
        }

        // ── Add-to-Collection sheet + create dialog (the same
        // AddToTargetActions module with the collection adapter). ──
        val collectionState by viewModel.collections.state.collectAsStateWithLifecycle()
        if (collectionState.showPicker && detail != null) {
            val collectionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            TvSafeSheet(
                onDismissRequest = { viewModel.collections.dismissPicker() },
                sheetState = collectionSheetState,
            ) {
                AddToCollectionSheet(
                    collections = collectionState.targets,
                    isLoading = collectionState.isLoadingTargets,
                    isAdding = collectionState.isAdding,
                    onPick = { collection -> viewModel.collections.addTo(collection) },
                    onCreateNew = { viewModel.collections.openCreateDialog() },
                    onDismiss = { viewModel.collections.dismissPicker() },
                )
            }
        }

        if (collectionState.showCreateDialog) {
            CreateCollectionDialog(
                isLoading = collectionState.isAdding,
                onConfirm = { name ->
                    viewModel.collections.createAndAdd(name)
                },
                onDismiss = { viewModel.collections.dismissCreateDialog() },
            )
        }

        val detailItem = detail?.item
        if (dialogSession.showSeriesDownloadSheet && detailItem?.mediaType == MediaType.SERIES) {
            val downloadSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            // Sheet-scoped full-state collect — these fields ride the
            // sheet cache, not the slim root projection; this subscription
            // lives only while the sheet is composed.
            val sheetDownloads by viewModel.downloads.state.collectAsStateWithLifecycle()
            // Seed the policy chips from the series' stored override (the
            // open reset put them on Default; the stored value wins once the
            // read lands).
            LaunchedEffect(detailItem.id) {
                dialogSession.seedSeriesDownloadPolicy(viewModel.downloads.seriesPolicy())
            }
            TvSafeSheet(
                onDismissRequest = { dialogSession.dismissSeriesDownloadSheet() },
                sheetState = downloadSheetState,
            ) {
                SeriesDownloadSheet(
                    seasons = uiState.seasons,
                    episodes = sheetDownloads.downloadSheetEpisodes,
                    loadingSeasons = sheetDownloads.downloadSheetLoadingSeasons,
                    downloadedEpisodeIds = sheetDownloads.downloadedEpisodeIds,
                    onLoadEpisodes = { seasonId ->
                        viewModel.downloads.loadDownloadSheetEpisodes(seasonId)
                    },
                    isDownloading = sheetDownloads.isDownloadingSeries,
                    selectedPolicy = dialogSession.seriesDownloadPolicy,
                    onPolicySelect = { dialogSession.selectSeriesDownloadPolicy(it) },
                    onDownload = { selectedEpisodes, policy ->
                        val nonEmpty = selectedEpisodes.filter { it.value.isNotEmpty() }
                        dialogSession.seriesDownloadConfirmed {
                            viewModel.downloads.downloadSeries(nonEmpty, policy.toSeriesPolicyChoice())
                        }
                    },
                    onDismiss = { dialogSession.dismissSeriesDownloadSheet() },
                )
            }
        }

        // ── Identify sheet (⋮ menu). State lives in the VM's metadataAdmin
        // helper; a successful apply bumps appliedCount → reload the item so
        // the screen reflects the server-replaced metadata. ──
        val identifyState by viewModel.metadataAdmin.identifyState.collectAsStateWithLifecycle()
        LaunchedEffect(identifyState.appliedCount) {
            if (identifyState.appliedCount > 0) {
                viewModel.onEvent(DetailUiEvent.ForceRefresh)
            }
        }
        identifyState.query?.let {
            IdentifySheet(
                state = identifyState,
                onQueryChange = viewModel.metadataAdmin::updateIdentifyQuery,
                onSearch = { viewModel.metadataAdmin.searchIdentify() },
                onApply = { result, replaceImages -> viewModel.metadataAdmin.applyIdentify(result, replaceImages) },
                onDismiss = { viewModel.metadataAdmin.dismissIdentify() },
            )
        }

        // ── Version picker (chevron beside Play / ⋮ menu → Version). A pure
        // UI selection: the pick rides the screen-local state until the next
        // Play dispatch. ──
        if (dialogSession.showVersionPicker && detail != null) {
            VersionPickerSheet(
                detail = detail,
                selectedSourceId = selectedVersionId,
                onSelect = { sourceId -> selectedVersionId = sourceId },
                onDismiss = { dialogSession.dismissVersionPicker() },
            )
        }

        // ── Merge versions sheet (⋮ menu, admin). State lives in the
        // metadataAdmin helper like Identify; a successful merge/split bumps
        // mutationCount → reload the item so the screen reflects the
        // server-side library mutation. ──
        val mergeState by viewModel.metadataAdmin.mergeState.collectAsStateWithLifecycle()
        if (mergeState.candidates != null) {
            MergeVersionsSheet(
                candidates = mergeState.candidates.orEmpty(),
                isLoading = mergeState.isLoading,
                isMerging = mergeState.isMerging,
                onMerge = { candidateIds -> viewModel.metadataAdmin.mergeVersions(candidateIds) },
                onDismiss = { viewModel.metadataAdmin.dismissMergeVersions() },
            )
        }
        LaunchedEffect(viewModel.metadataAdmin.mutationCount.collectAsStateWithLifecycle().value) {
            if (viewModel.metadataAdmin.mutationCount.value > 0) {
                viewModel.onEvent(DetailUiEvent.ForceRefresh)
            }
        }

        // ── Split versions confirm (⋮ menu, admin). Splitting dissolves the
        // merged entry server-side — strong-confirm before firing. ──
        if (dialogSession.showSplitConfirm && detailItem != null) {
            ConfirmDialog(
                title = stringResource(Res.string.detail_split_confirm_title),
                message = stringResource(Res.string.detail_split_confirm_message),
                confirmText = stringResource(Res.string.detail_option_split_versions),
                dismissText = stringResource(CoreUiRes.string.core_cancel),
                onConfirm = {
                    dialogSession.splitConfirmed { viewModel.metadataAdmin.splitScreenItem() }
                },
                onDismiss = { dialogSession.dismissSplitConfirm() },
            )
        }

        // ── "Refresh metadata" mode sheet (⋮ menu). The sheet body is shared
        // with the metadata editor (core/ui); the write itself runs through
        // the VM's MetadataAdminActions helper (admin-gated entry above). ──
        if (dialogSession.showRefreshMetadataSheet && detailItem != null) {
            RefreshMetadataSheet(
                itemName = detailItem.name.orEmpty(),
                onConfirm = { option ->
                    dialogSession.refreshMetadataConfirmed {
                        viewModel.metadataAdmin.refreshScreenItem(option)
                    }
                },
                onDismiss = { dialogSession.dismissRefreshMetadataSheet() },
            )
        }

        // ── Series batch-delete sheet. Replaces the old
        // OfflineSeriesScreen trash flow: multi-select downloaded episodes /
        // whole seasons / the entire series, then route through the merged
        // DetailViewModel offline-delete methods (which collapse a fully-
        // selected season into a single deleteOfflineSeason transaction). ──
        if (dialogSession.showDeleteEpisodesSheet && detailItem?.mediaType == MediaType.SERIES) {
            // For a LOCAL origin every episode in the snapshot is downloaded;
            // the sheet treats each listed episode as deletable. The shared
            // derivation drops seasons that carry no episodes so the sheet
            // renders no empty rows.
            val downloadedSlices = remember(uiState.seasons, uiState.episodes) {
                downloadedSeasonSlices(seasons = uiState.seasons, episodesBySeason = uiState.episodes)
            }
            val totalSizeBytes = uiState.detailContext?.seriesAggregate?.totalSizeBytes ?: 0L
            val downloadedEpisodeCount = remember(uiState.detailContext, downloadedSlices) {
                uiState.detailContext?.seriesAggregate?.downloadedEpisodeCount
                    ?: downloadedSlices.episodesBySeason.values.sumOf { it.size }
            }
            val deleteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            TvSafeSheet(
                onDismissRequest = { dialogSession.dismissDeleteEpisodesSheet() },
                sheetState = deleteSheetState,
            ) {
                DeleteDownloadedEpisodesSheet(
                    seasons = downloadedSlices.seasons,
                    episodes = downloadedSlices.episodesBySeason,
                    totalSizeBytes = totalSizeBytes,
                    episodeSizeBytes = uiState.detailContext?.seriesAggregate?.episodeSizeBytes ?: emptyMap(),
                    onDelete = { episodeIds ->
                        dialogSession.dismissDeleteEpisodesSheet()
                        viewModel.offline.deleteOfflineEpisodes(episodeIds.toList())
                        // If every downloaded episode was selected there's nothing
                        // left to show — pop back to where the user came from.
                        if (episodeIds.size >= downloadedEpisodeCount) {
                            onBack()
                        }
                    },
                    onDeleteEntireSeries = {
                        dialogSession.dismissDeleteEpisodesSheet()
                        viewModel.offline.deleteOfflineSeries(itemId)
                        onBack()
                    },
                    onDismiss = { dialogSession.dismissDeleteEpisodesSheet() },
                )
            }
        }

        com.raulshma.jellyplay.core.ui.components.JellyPlaySnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )

        // Mark-played confirm (series-wide or one season): watched vs
        // unwatched and series vs season differ only in verb/message, both
        // derived from the ONE pending request.
        val request = markPlayedRequest
        if (request != null && request.confirm.isVisible) {
            val isSeason = request.scope is MarkPlayedConfirmRequest.Scope.Season
            val toWatched = request.toWatched
            val title = stringResource(
                when {
                    isSeason && toWatched -> Res.string.detail_mark_season_watched_confirm_title
                    isSeason -> Res.string.detail_mark_season_unwatched_confirm_title
                    toWatched -> Res.string.detail_mark_series_watched_confirm_title
                    else -> Res.string.detail_mark_series_unwatched_confirm_title
                },
            )
            val message = stringResource(
                when {
                    isSeason && toWatched -> Res.string.detail_mark_season_watched_confirm_message
                    isSeason -> Res.string.detail_mark_season_unwatched_confirm_message
                    toWatched -> Res.string.detail_mark_series_watched_confirm_message
                    else -> Res.string.detail_mark_series_unwatched_confirm_message
                },
            )
            request.confirm.ConfirmDialog(
                title = title,
                message = message,
                confirmText = stringResource(CoreUiRes.string.core_confirm),
                dismissText = stringResource(CoreUiRes.string.core_cancel),
            )
        }

        activeTrailerKey?.let { key ->
            InlineTrailerDialog(
                videoKey = key,
                onDismiss = { activeTrailerKey = null },
            )
        }

        // Long-press / TV-Menu quick actions for row cards — the shared
        // intake hosts the sheet AND the remove-download confirm (removal
        // only ever deletes the local download; the server copy is
        // untouched).
        QuickActionIntakeHost(quickActionIntake)

        // ── Unified-provider delete confirmation. ──
        // Single item: deletes the current item's attached download. Episode: a
        // downloaded episode from the seasons section. Both route through the
        // merged DetailViewModel offline-delete methods.
        pendingDelete.item?.let { target ->
            ConfirmDialog(
                title = stringResource(Res.string.detail_delete_download_title),
                message = stringResource(
                    Res.string.detail_delete_download_message,
                    target.name,
                    target.sizeBytes.formatBytes(),
                ),
                confirmText = stringResource(Res.string.detail_delete),
                dismissText = stringResource(Res.string.detail_cancel),
                icon = Tabler.Outline.Trash,
                tone = ConfirmTone.DESTRUCTIVE,
                onConfirm = {
                    val item = pendingDelete.confirm(inFlight = false) ?: return@ConfirmDialog
                    viewModel.offline.deleteOfflineItem(item.itemId)
                    pendingDelete = pendingDelete.clear()
                    // A LOCAL origin has nothing left once its only download is
                    // removed — pop back instead of stranding the user on an
                    // empty detail (matches the series batch-delete behavior).
                    if (uiState.origin?.isLocal == true) onBack()
                },
                onDismiss = { pendingDelete = pendingDelete.dismiss(inFlight = false) },
            )
        }
        pendingDeleteEpisode.item?.let { ep ->
            ConfirmDialog(
                title = stringResource(Res.string.detail_delete_episode_title),
                message = stringResource(Res.string.detail_delete_episode_message, ep.name),
                confirmText = stringResource(Res.string.detail_delete),
                dismissText = stringResource(Res.string.detail_cancel),
                icon = Tabler.Outline.Trash,
                tone = ConfirmTone.DESTRUCTIVE,
                onConfirm = {
                    val item = pendingDeleteEpisode.confirm(inFlight = false) ?: return@ConfirmDialog
                    viewModel.offline.deleteOfflineEpisode(item.episodeId)
                    pendingDeleteEpisode = pendingDeleteEpisode.clear()
                },
                onDismiss = { pendingDeleteEpisode = pendingDeleteEpisode.dismiss(inFlight = false) },
            )
        }

        // ── Resync bottom sheet. Lists what changed and offers a
        // resync / re-download action with live status. ──
        if (dialogSession.showResyncSheet) {
            ResyncSheet(
                syncState = uiState.detailContext?.syncState,
                resyncState = resyncState,
                onResync = { viewModel.resync.resync() },
                onRedownloadMedia = { viewModel.resync.redownloadMedia() },
                onDismiss = { dialogSession.dismissResyncSheet() },
            )
        }

        // ── Full download-details bottom sheet. Consolidates the attached
        // download lifecycle, media identity + watch progress, per-source media
        // info, and the on-disk downloaded files into one scrollable surface.
        // Opened by tapping the DownloadInfoCard header. Gated on the snapshot
        // item so a mid-open clearance simply dismisses it. ──
        val detailsItem = detail?.item
        if (dialogSession.showDownloadDetailsSheet && detailsItem != null) {
            // Sheet-scoped full-state collect (inventory fields aren't on
            // the slim root projection); lives only while the sheet is composed.
            val sheetDownloads by viewModel.downloads.state.collectAsStateWithLifecycle()
            // Episode/season sheets render the SERIES backdrop — MediaImageTags
            // carries no series backdrop tag, so that leg stays the raw untagged
            // getter; every item-backed id resolves through the tag-aware
            // resolver (same target ids as before, + the item's own tag).
            val sheetBackdropUrl = uiState.assets.backdropPath
                // Smart-cast hoist: seriesId is a public API property of another
                // module, so the null check can't smart-cast it inline.
                ?: detailsItem.seriesId?.let { seriesId -> viewModel.getBackdropUrl(seriesId) }
                ?: viewModel.artwork.backdrop(detailsItem)

            val sheetPosterUrl = uiState.assets.posterPath
                ?: viewModel.artwork.poster(detailsItem)

            DownloadDetailsSheet(
                download = uiState.detailContext?.download,
                item = detailsItem,
                mediaSources = detail.mediaSources,
                inventory = sheetDownloads.downloadFileInventory,
                isLoadingInventory = sheetDownloads.isLoadingDownloadFiles,
                backdropUrl = sheetBackdropUrl,
                posterUrl = sheetPosterUrl,
                onDismiss = { dialogSession.dismissDownloadDetailsSheet() },
            )
        }
    } // Box
}

/**
 * Pending delete of the current item's attached download (single item or the
 * current episode). Surfaced as a confirm dialog because removing the file
 * clears resume state and frees storage.
 */
@androidx.compose.runtime.Immutable
private data class PendingDelete(
    val itemId: String,
    val name: String,
    val sizeBytes: Long,
    val isEpisode: Boolean,
)

/** Pending delete of a downloaded episode from the seasons section. */
@androidx.compose.runtime.Immutable
private data class PendingEpisodeDelete(
    val episodeId: String,
    val name: String,
)

/**
 * ONE pending mark-played confirm: the [Scope] + direction pair the dialog's
 * verb/message derive from, plus the shared [ConfirmState] holding the
 * deferred ViewModel call. Replaces the two hand-synced confirm machines and
 * their `toWatched` mirrors; [ConfirmState.isVisible] stays the dialog-open
 * flag and settles on both dismiss and confirm, leaving a stale request value
 * that is benign until the next request overwrites it (same semantics the
 * `toWatched` mirrors had).
 */
private data class MarkPlayedConfirmRequest(
    val scope: Scope,
    val toWatched: Boolean,
    val confirm: ConfirmState,
) {
    /** What the confirm addresses: the item itself (a series) or one season. */
    sealed interface Scope {
        data object Item : Scope
        data class Season(val seasonId: String) : Scope
    }
}

/**
 * The one place the always-composed content tree reads the FULL download
 * state — the per-tick [DownloadLifecycleState.episodeDownloadProgress] map
 * for the seasons section's bars. Collecting here (not at the screen root)
 * keeps a ~2 s transfer tick's invalidation inside this provider scope plus
 * [LocalEpisodeDownloadProgress]'s read-tracked rows (`compositionLocalOf` —
 * see the local's KDoc); the root scope's slim [DownloadRootState] collect
 * and the ~60-field DetailContentState construction never see a tick.
 */
@Composable
private fun EpisodeDownloadProgressProvider(
    state: StateFlow<DownloadLifecycleState>,
    content: @Composable () -> Unit,
) {
    val downloads by state.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalEpisodeDownloadProgress provides downloads.episodeDownloadProgress,
        content = content,
    )
}

/**
 * The process-wide playback seams the media-detail screen's play funnels
 * read — ONE constructor-shaped bundle instead of four mid-body `koinInject`
 * pulls (the screen's constructor convention, same as its `viewModel`
 * parameter). All four are process-wide core:data / datastore /
 * player-contract singles:
 *
 *  - [videoQueue]: the manual up-next queue (DetailContent's koinInject
 *    precedent) — the franchise row's "play collection in order" tail and
 *    the the preroll's feature seed append to it around the start dispatch;
 *  - [cinemaModeOverride]: the one-shot Cinema Mode override ("Play with
 *    pre-rolls" arms it);
 *  - [syncPlayManager]: the SyncPlay flag the trailer pre-roll sequencing
 *    vetoes on;
 *  - [videoPlayerStore]: the playback-prefs slice the trailer-then-feature
 *    toggle reads at dispatch time.
 *
 * [rememberMediaDetailPlaybackSeams] resolves the same Koin singles the
 * former pulls did, so the navigation wiring is unchanged; a direct
 * construction (tests) hands fakes.
 */
data class MediaDetailPlaybackSeams(
    val videoQueue: com.raulshma.jellyplay.core.data.playback.VideoQueueController,
    val cinemaModeOverride: com.raulshma.jellyplay.core.data.playback.CinemaModeOverride,
    val syncPlayManager: com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager,
    val videoPlayerStore: com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore,
)

/** The bundle's composition-time default: the four Koin singles, one seam. */
@Composable
fun rememberMediaDetailPlaybackSeams(): MediaDetailPlaybackSeams = MediaDetailPlaybackSeams(
    videoQueue = org.koin.compose.koinInject(),
    cinemaModeOverride = org.koin.compose.koinInject(),
    syncPlayManager = org.koin.compose.koinInject(),
    videoPlayerStore = org.koin.compose.koinInject(),
)
