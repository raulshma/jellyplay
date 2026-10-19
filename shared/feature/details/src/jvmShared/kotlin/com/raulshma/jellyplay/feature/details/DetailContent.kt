package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import com.raulshma.jellyplay.core.ui.components.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.designsystem.theme.backgroundBrush
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_share_via
import org.jetbrains.compose.resources.stringResource

/**
 * Max frames to wait for the detail content's [FocusRequester] to attach on TV
 * before giving up the focus request. ~0.3s at 60fps. See [DetailContent].
 */
private const val FOCUS_REQUEST_RETRY_FRAMES = 20

/**
 * The private orchestrator for the media-detail content tree.
 *
 * Receives only [state] + [callbacks] bundles (two parameters instead of the
 * former 50 flat ones) so the Compose compiler treats it as skippable and
 * children stop recomposing on every unrelated lambda reallocation.
 *
 * Behaviour is identical to the former `DetailContent` in `MediaDetailScreen.kt`;
 * scroll, backdrop, top-bar, download-dialog, and TV-focus concerns have been
 * hoisted into sibling files. The portrait/landscape layout branches are
 * de-duplicated so [DetailContentBody] is invoked exactly once per branch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailContent(
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    availableStorageProvider: suspend (isAudio: Boolean) -> Long,
) {
    val item = state.core.detail?.item
    val listState = rememberLazyListState()
    val isAudio = item?.mediaType?.isAudioType == true
    val isAlbum = item?.mediaType == MediaType.ALBUM
    val isSeries = item?.mediaType == MediaType.SERIES

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact
    val isTv = LocalTvMode.current
    val contentVisible = state.core.detail != null && item != null

    val scrollState = rememberDetailScrollState(listState, contentVisible)

    val targetBackdropId = item
        ?.takeIf { showsParentSeriesContext(it.mediaType) }?.seriesId
        ?: state.itemId

    val contentFocusRequester = remember { FocusRequester() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    if (isTv) {
        LaunchedEffect(contentVisible) {
            if (!contentVisible) return@LaunchedEffect
            // The Play button carrying contentFocusRequester lives inside
            // AnimatedVisibility(contentVisible), so it may not be composed/
            // attached on the very frame contentVisible flips true. Poll once
            // per frame until the requester attaches (or up to
            // [FOCUS_REQUEST_RETRY_FRAMES] frames — ~0.3s at 60fps) so the
            // focus request is not silently swallowed by tryRequestFocus.
            //
            // FocusRequester has no public isAttached() API, so a frame-bounded
            // retry loop is the canonical workaround.
            repeat(FOCUS_REQUEST_RETRY_FRAMES) {
                withFrameNanos { }
                if (contentFocusRequester.tryRequestFocus("detail_content")) return@LaunchedEffect
            }
        }
    }

    // Share seam: chooser title pre-resolved in composition; the Android
    // actual keeps the legacy ACTION_SEND chooser body, desktop is a no-op.
    val shareMedia = rememberShareMediaAction(
        itemId = state.itemId,
        chooserTitle = stringResource(Res.string.detail_share_via),
    )

    // The TV focus-gated trailer preview's port — the single holder
    // instance the Play button (deep in the body) writes and DetailBackdrop
    // reads; provided below so the button's writes reach it on both the
    // touch and TV branches.
    val trailerFocusPreview = remember { TrailerFocusPreviewState() }

    // Manual up-next queue (the player drains it queue-first). The controller
    // is process-wide, so "play next" here feeds the player's queue sheet even
    // before the player opens. koinInject — the book feature's desktop-host
    // precedent for a process-wide core:data single a screen consumes directly.
    val videoQueue = org.koin.compose.koinInject<com.raulshma.jellyplay.core.data.playback.VideoQueueController>()
    val imageUrlProvider = org.koin.compose.koinInject<com.raulshma.jellyplay.core.data.util.ImageUrlProvider>()
    fun enqueueInQueue(item: com.raulshma.jellyplay.core.model.MediaItem?, next: Boolean) {
        if (item == null) return
        val queueItem = com.raulshma.jellyplay.core.data.playback.VideoQueueItem(
            itemId = item.id,
            title = item.name,
            subtitle = item.seriesName ?: item.year?.toString(),
            imageUrl = imageUrlProvider.getImageUrl(item.id, maxWidth = 300),
            runTimeTicks = item.runTimeTicks,
        )
        if (next) videoQueue.enqueueNext(queueItem) else videoQueue.enqueueLast(queueItem)
    }

    // "Play (with pre-rolls)": the SyncPlay veto is host-level (the
    // process-wide SyncPlayManager, the videoQueue koinInject precedent) — a
    // group session owns the playback order, so the per-invocation intro
    // override is meaningless there. The media-type + offline gates live
    // with the menu entry ([MediaOptionsMenu.offersPlayWithPrerolls]).
    val syncPlayManager = org.koin.compose.koinInject<com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager>()

    val mediaOptions = rememberMediaOptions(
        onPlayNextInQueue = {
            enqueueInQueue(item, next = true)
        },
        onPlayLastInQueue = {
            enqueueInQueue(item, next = false)
        },
        canPlayWithPrerolls = !isSeries && !syncPlayManager.isInSyncPlaySession,
        onPlayWithPrerolls = callbacks.playback.onPlayWithPrerolls,
        item = item,
        detail = state.core.detail,
        itemId = state.itemId,
        isAudio = isAudio,
        isSeries = isSeries,
        seasons = state.core.seasons,
        preferences = state.preferences,
        isDownloading = state.downloads.isDownloading,
        isDownloadingSeries = state.downloads.isDownloadingSeries,
        canManageSeries = state.canManageSeries,
        canDeleteDownloadedSeries = state.core.origin?.isLocal == true &&
            isSeries &&
            (state.core.detailContext?.seriesAggregate?.downloadedEpisodeCount ?: 0) > 0,
        canEditMetadata = state.core.capabilities.remoteDiscovery,
        canRefreshMetadata = state.core.canManageMetadata && state.core.capabilities.remoteDiscovery,
        canIdentify = state.core.canManageMetadata && state.core.capabilities.remoteDiscovery &&
            (item?.mediaType == MediaType.SERIES || item?.mediaType == MediaType.MOVIE),
        canPickVersion = !isAudio && !isSeries && (state.core.detail?.mediaSources?.size ?: 0) > 1,
        onVersionPicker = callbacks.playback.onOpenVersionPicker,
        // The Merge/Split endpoints are Movies-only server-side
        // (/Videos/MergeVersions, /Videos/{id}/AlternateSources) and
        // admin-gated (RequiresElevation) — the same gate the refresh/identify
        // entries resolve. Split only applies to an already-merged item.
        canMergeVersions = state.core.canManageMetadata && state.core.capabilities.remoteDiscovery &&
            item?.mediaType == MediaType.MOVIE,
        onMergeVersions = callbacks.metadata.onOpenMergeVersions,
        canSplitVersions = state.core.canManageMetadata && state.core.capabilities.remoteDiscovery &&
            item?.mediaType == MediaType.MOVIE && (state.core.detail?.mediaSources?.size ?: 0) > 1,
        onSplitVersions = callbacks.metadata.onSplitVersions,
        canAddToPlaylist = state.core.capabilities.remoteDiscovery,
        canAddToCollection = state.core.capabilities.remoteDiscovery,
        canInstantMix = isAudio && state.core.capabilities.remoteDiscovery,
        canStartRadio = isAudio && state.core.capabilities.remoteDiscovery,
        canStartWatchParty = state.core.capabilities.remoteWorkAllowed,
        isOffline = state.core.origin?.isLocal == true,
        onClose = { /* menus close themselves */ },
        onEditClick = callbacks.navigation.onEditClick,
        onShare = shareMedia,
        onDownload = callbacks.download.onOpenDownloadPicker,
        onDownloadSeries = callbacks.download.onDownloadSeriesClick,
        onDeleteDownload = callbacks.download.onDeleteDownload,
        onDeleteDownloadedSeries = callbacks.download.onDeleteDownloadedEpisodes,
        onHideFromNextUp = callbacks.userData.onHideFromNextUp,
        onShowFromNextUp = callbacks.userData.onShowFromNextUp,
        onHideFromContinueWatching = callbacks.userData.onHideFromContinueWatching,
        onShowFromContinueWatching = callbacks.userData.onShowFromContinueWatching,
        onHideDetailUpNext = callbacks.userData.onHideDetailUpNext,
        onShowDetailUpNext = callbacks.userData.onShowDetailUpNext,
        // The per-series notify entry rides the platform seam — desktop
        // (no notification stack) never sees it.
        seriesNotificationsEnabled = state.core.seriesNotificationsEnabled,
        platformSupportsNotifications = platformHasNotificationStack,
        onToggleSeriesNotifications = callbacks.userData.onToggleSeriesNotifications,
        onManageSeries = callbacks.navigation.onManageSeries,
        onRefreshMetadata = callbacks.metadata.onRefreshMetadata,
        onIdentify = callbacks.metadata.onIdentify,
        onTechnicalInfo = { callbacks.navigation.onNavigate(Route.MediaInfo(state.itemId)) },
        onAddToPlaylist = callbacks.addTo.onAddToPlaylist,
        onAddToCollection = callbacks.addTo.onAddToCollection,
        onStartInstantMix = callbacks.playback.onStartInstantMix,
        onStartRadio = callbacks.playback.onStartRadio,
        onStartWatchParty = callbacks.playback.onStartWatchParty,
    )

    // ── promoted rail resolution ─────────────────────────────────────
    // The fixed promoted subset of [mediaOptions] lifts into the landscape
    // rail; the ⋮ menu suppresses exactly those ids while the rail shows them
    // (no dupes). Effective only where the rail exists — the landscape body —
    // and gated on the display preference; both off → the ⋮ list passes
    // through unchanged.
    val showsLandscapeRail = isExpanded && adaptiveInfo.isLandscape
    val promotedOptionIds = if (showsLandscapeRail && state.preferences.showPromotedActionsLandscape) {
        PromotedMediaOptionIds.toSet()
    } else {
        emptySet()
    }
    val overflowOptions = remember(mediaOptions, promotedOptionIds) { mediaOptions.excludingIds(promotedOptionIds) }
    val promotedActions = remember(mediaOptions, promotedOptionIds) { mediaOptions.promotedOptions(promotedOptionIds) }

    val themeVariant = com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant.current
    val backgroundColorState = scrollState.backgroundColorState
    val backgroundModifier = remember(themeVariant) {
        val variantBrush = themeVariant.backgroundBrush()
        if (variantBrush != null) {
            Modifier.background(variantBrush)
        } else {
            // Read the snapshot state inside drawBehind so a scroll-driven
            // background-colour change re-draws without recomposing DetailContent.
            Modifier.drawBehind { drawRect(backgroundColorState.value) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
            .onDpadKeyEvent(
                onBack = { e ->
                    if (e.isKeyUp) { callbacks.navigation.onBack() }
                    true
                },
            ),
    ) {
        // Loading / error overlays are only painted while there is no content yet
        // (contentVisible == false). Once detail resolves the content tree renders and any
        // subsequent Refreshing / Loaded / silent-failure state is invisible underneath it.
        // On TV the loading surface must be focusable (LoadingScreen grabs focus) so the
        // D-pad isn't orphaned until data arrives; on touch a delayed spinner keeps fast
        // loads from flickering.
        when (val loadState = state.core.loadState) {
            is DetailUiLoadState.Loading -> {
                if (!contentVisible) {
                    if (isTv) {
                        LoadingScreen(modifier = Modifier.fillMaxSize())
                    } else {
                        DelayedLoadingScreen(modifier = Modifier.fillMaxSize())
                    }
                }
            }
            is DetailUiLoadState.Error -> {
                if (!contentVisible) {
                    ErrorScreen(
                        message = loadState.message,
                        onRetry = if (loadState.accessDenied) null else callbacks.screen.onRetry,
                        icon = Tabler.Outline.AlertTriangle,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            DetailUiLoadState.Refreshing, DetailUiLoadState.Loaded -> {
                // Content renders below; overlays stay hidden.
            }
        }

        // Books ship no 16:9 backdrop — the portrait cover IS the hero art, so
        // every book hero sources the PRIMARY (cover) art below. Large screens
        // (tablet/TV/desktop) keep the blurred cover-mode hero plus the poster
        // card; compact phones crop the cover sharp edge-to-edge and drop the
        // poster card (DetailBodyPortrait) since the hero already shows it.
        val isBook = item?.mediaType == MediaType.BOOK
        val coverHero = when {
            !isBook -> BookCoverHero.NONE
            !isTv && !isExpanded -> BookCoverHero.SHARP
            else -> BookCoverHero.BLURRED
        }
        DetailBackdrop(
            targetBackdropId = targetBackdropId,
            backdropBlurHash = if (isBook) item?.blurHashes?.primary else item?.blurHashes?.backdrop,
            getBackdropUrl = if (isBook) callbacks.artwork.getImageUrl else callbacks.artwork.getBackdropUrl,
            trailers = state.core.trailers,
            preferences = state.preferences,
            scrollState = scrollState,
            localBackdropPath = if (isBook) state.core.assets.posterPath else state.core.assets.backdropPath,
            coverHero = coverHero,
            themeVideoStreamUrl = state.core.themeVideoStreamUrl,
            trailerFocusPreview = trailerFocusPreview,
        )

        // The port reaches the Play button through the composition
        // local (the holder itself travels to the backdrop as a parameter
        // above) — the local scopes exactly the body subtrees that render
        // the button.
        CompositionLocalProvider(LocalTrailerFocusPreview provides trailerFocusPreview) {
            // Pull-to-refresh lets the user force a fresh fetch (invalidating the
            // in-memory caches) by pulling the content down. Disabled on TV — the
            // D-pad has no drag gesture, and the LazyColumn's tvFocusRestorer stays
            // the outermost modifier there.
            if (!isTv) {
                PullToRefreshBox(
                    isRefreshing = state.core.loadState is DetailUiLoadState.Refreshing,
                    onRefresh = callbacks.screen.onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    DetailScrollContent(
                        state = state,
                        callbacks = callbacks,
                        listState = listState,
                        scrollState = scrollState,
                        contentVisible = contentVisible,
                        contentFocusRequester = contentFocusRequester,
                        isExpanded = isExpanded,
                        isTv = isTv,
                        item = item,
                        isAudio = isAudio,
                        isAlbum = isAlbum,
                        promotedActions = promotedActions,
                    )
                }
            } else {
                DetailScrollContent(
                    state = state,
                    callbacks = callbacks,
                    listState = listState,
                    scrollState = scrollState,
                    contentVisible = contentVisible,
                    contentFocusRequester = contentFocusRequester,
                    isExpanded = isExpanded,
                    isTv = isTv,
                    item = item,
                    isAudio = isAudio,
                    isAlbum = isAlbum,
                    promotedActions = promotedActions,
                )
            }
        }

        DetailTopBar(
            itemName = item?.name ?: "",
            scrollState = scrollState,
            onBack = callbacks.navigation.onBack,
            mediaOptions = overflowOptions,
            contentFocusRequester = contentFocusRequester,
            scrollBehavior = scrollBehavior,
            // The mute chip exists only while theme audio is actually
            // audible; its icon reflects the per-series memory (the same
            // theme-source keying the VM's enrichment gate uses).
            themeMusicVisible = state.core.themeMusicPlaying,
            themeMusicMuted = state.preferences.isThemeMusicMutedFor(
                item?.seriesId ?: state.itemId,
            ),
            onToggleThemeMusicMuted = callbacks.screen.onToggleThemeMusicMuted,
        )
    }

    if (state.downloads.downloadPicker.visible) {
        val source = state.core.detail?.mediaSources?.firstOrNull()
        // External/deliverable subtitle streams eligible for offline bundling.
        // Shares MediaStream.isBundleableSubtitle with the download writer + sync
        // comparator so the multi-select shows exactly the subtitles that save.
        val subtitleStreams: List<MediaStream> = remember(source) {
            source?.mediaStreams
                ?.filter { it.isBundleableSubtitle }
                ?: emptyList()
        }
        DownloadPickerSheet(
            fileSize = source?.size,
            isAudio = isAudio,
            availableStorageProvider = availableStorageProvider,
            subtitleStreams = subtitleStreams,
            pendingQuality = state.downloads.downloadPicker.quality,
            pendingSubtitleSelection = state.downloads.downloadPicker.subtitleSelection,
            onPendingQualityChange = callbacks.download.onPendingQualityChange,
            onPendingSubtitleSelectionChange = callbacks.download.onPendingSubtitleSelectionChange,
            onConfirm = { callbacks.download.onDownloadClick() },
            onDismiss = callbacks.download.onDismissDownloadPicker,
        )
    }
}

/**
 * The scrollable detail body (spacer + backdrop-fade + body). Extracted so the
 * touch path can wrap it in a [PullToRefreshBox] while the TV path keeps the
 * [LazyColumn] at the top of the modifier chain (for [tvFocusRestorer]).
 */
@Composable
private fun DetailScrollContent(
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollState: DetailScrollState,
    contentVisible: Boolean,
    contentFocusRequester: androidx.compose.ui.focus.FocusRequester,
    isExpanded: Boolean,
    isTv: Boolean,
    item: com.raulshma.jellyplay.core.model.MediaItem?,
    isAudio: Boolean,
    isAlbum: Boolean,
    /** The ⋮ options' promoted subset for the landscape rail (empty = none). */
    promotedActions: List<MediaOption>,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(if (isTv) Modifier.tvFocusRestorer() else Modifier),
    ) {
        item { Spacer(modifier = Modifier.height(scrollState.baseBackdropHeight - 150.dp)) }

        item {
            val gradientEndPx = with(LocalDensity.current) { 150.dp.toPx() }
            // Read the colour here (the item's own scope) so colour-animation
            // frames rebuild the brush without recomposing anything above, and
            // scroll-offset frames — which don't touch the colour — rebuild
            // nothing at all.
            val fadeColor by scrollState.backgroundColorState
            val fadeBrush = remember(fadeColor, gradientEndPx) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        fadeColor.copy(alpha = 0.9f),
                        fadeColor,
                    ),
                    startY = 0f,
                    endY = gradientEndPx,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind { drawRect(fadeBrush) },
            ) {
                if (isExpanded && adaptiveInfo.isLandscape) {
                    DetailBodyLandscape(
                        state = state,
                        callbacks = callbacks,
                        scrollState = scrollState,
                        contentVisible = contentVisible,
                        contentFocusRequester = contentFocusRequester,
                        isAudio = isAudio,
                        isAlbum = isAlbum,
                        isExpanded = isExpanded,
                        item = item,
                        promotedActions = promotedActions,
                    )
                } else {
                    DetailBodyPortrait(
                        state = state,
                        callbacks = callbacks,
                        scrollState = scrollState,
                        contentVisible = contentVisible,
                        contentFocusRequester = contentFocusRequester,
                        isAudio = isAudio,
                        isAlbum = isAlbum,
                        isExpanded = isExpanded,
                        isTv = isTv,
                        item = item,
                    )
                }
            }
        }
    }
}
