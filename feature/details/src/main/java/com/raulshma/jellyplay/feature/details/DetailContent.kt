package com.raulshma.jellyplay.feature.details

import android.content.Intent
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

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
    val item = state.detail?.item
    val listState = rememberLazyListState()
    val isAudio = item?.mediaType?.isAudioType == true
    val isAlbum = item?.mediaType == MediaType.ALBUM
    val isSeries = item?.mediaType == MediaType.SERIES
    var showDownloadDialog by remember { mutableStateOf(false) }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact
    val isTv = LocalTvMode.current
    val contentVisible = state.detail != null && item != null

    val scrollState = rememberDetailScrollState(listState, contentVisible)

    val targetBackdropId = item
        ?.takeIf { it.mediaType == MediaType.EPISODE }?.seriesId
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

    val context = LocalContext.current
    // Single resolved share intent, memoized so it isn't rebuilt per recomposition.
    val shareMedia = remember(state.itemId, context) {
        {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "jellyplay://media/${state.itemId}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.detail_share_via)))
        }
    }

    val mediaOptions = rememberMediaOptions(
        item = item,
        detail = state.detail,
        itemId = state.itemId,
        isAudio = isAudio,
        isSeries = isSeries,
        seasons = state.seasons,
        preferences = state.preferences,
        activeDownload = state.activeDownload,
        isDownloading = state.isDownloading,
        isDownloadingSeries = state.isDownloadingSeries,
        canManageSeries = state.canManageSeries,
        onClose = { /* menus close themselves */ },
        onEditClick = callbacks.onEditClick,
        onShare = shareMedia,
        onDownload = { showDownloadDialog = true },
        onDownloadSeries = callbacks.onDownloadSeriesClick,
        onHideFromNextUp = callbacks.onHideFromNextUp,
        onShowFromNextUp = callbacks.onShowFromNextUp,
        onHideFromContinueWatching = callbacks.onHideFromContinueWatching,
        onShowFromContinueWatching = callbacks.onShowFromContinueWatching,
        onManageSeries = callbacks.onManageSeries,
        onTechnicalInfo = { callbacks.onNavigate(Route.MediaInfo(state.itemId)) },
        onAddToPlaylist = callbacks.onAddToPlaylist,
    )

    val isSynthwave = com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave.current
    val backgroundColorState = scrollState.backgroundColorState
    val backgroundModifier = remember(isSynthwave) {
        if (isSynthwave) {
            Modifier.background(com.raulshma.jellyplay.core.designsystem.theme.synthwaveBackgroundBrush())
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
                    if (e.isKeyUp) { callbacks.onBack() }
                    true
                },
            ),
    ) {
        // While the detail is still loading (contentVisible == false) show a loading surface.
        // On TV it must be focusable (LoadingScreen grabs focus) so the D-pad isn't orphaned
        // until data arrives; on touch we use a delayed spinner so fast loads don't flicker.
        if (!contentVisible && state.error == null) {
            if (isTv) {
                LoadingScreen(modifier = Modifier.fillMaxSize())
            } else {
                DelayedLoadingScreen(modifier = Modifier.fillMaxSize())
            }
        }

        DetailBackdrop(
            targetBackdropId = targetBackdropId,
            backdropBlurHash = item?.blurHashes?.backdrop,
            getBackdropUrl = callbacks.getBackdropUrl,
            relatedVideos = state.relatedVideos,
            preferences = state.preferences,
            scrollState = scrollState,
            isExpanded = isExpanded,
        )

        // Pull-to-refresh lets the user force a fresh fetch (invalidating the
        // in-memory caches) by pulling the content down. Disabled on TV — the
        // D-pad has no drag gesture, and the LazyColumn's tvFocusRestorer stays
        // the outermost modifier there.
        if (!isTv) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = callbacks.onRefresh,
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
            )
        }

        DetailTopBar(
            itemName = item?.name ?: "",
            scrollState = scrollState,
            onBack = callbacks.onBack,
            mediaOptions = mediaOptions,
            contentFocusRequester = contentFocusRequester,
            scrollBehavior = scrollBehavior,
        )
    }

    if (showDownloadDialog) {
        val source = state.detail?.mediaSources?.firstOrNull()
        DownloadConfirmationDialog(
            fileSize = source?.size,
            isAudio = isAudio,
            availableStorageProvider = availableStorageProvider,
            onConfirm = {
                showDownloadDialog = false
                callbacks.onDownloadClick()
            },
            onDismiss = { showDownloadDialog = false },
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
            val fadeBrush = remember(scrollState.backgroundColor, gradientEndPx) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        scrollState.backgroundColor.copy(alpha = 0.9f),
                        scrollState.backgroundColor,
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
