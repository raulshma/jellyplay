package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.designsystem.theme.detailEntrance
import com.raulshma.jellyplay.core.designsystem.theme.rememberDetailEntrance
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.quickActions
import com.raulshma.jellyplay.feature.details.R
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.LayoutGrid
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.MediaQuickActionHost
import com.raulshma.jellyplay.core.ui.components.QuickAction
import com.raulshma.jellyplay.core.ui.components.RemoveDownloadConfirmHost
import com.raulshma.jellyplay.core.ui.components.rememberMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.rememberRemoveDownloadState
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionId: String,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(collectionId) {
        viewModel.loadCollection(collectionId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val success = state as? CollectionDetailUiState.Success
    val items = success?.items ?: emptyList()
    val title = success?.detail?.item?.name ?: "Collection"

    val backdropVisible = remember { mutableStateOf(false) }
    val contentVisible = remember { mutableStateOf(false) }

    // Shared entrance progress for the poster grid — the same
    // [rememberDetailEntrance] + [detailEntrance] pair as the downloads list.
    // ONE Animatable for the whole grid replaces the per-cell
    // `mutableStateOf + LaunchedEffect + AnimatedVisibility` triple; it starts
    // at the exact moment the per-cell animations used to (when the loaded
    // state arrives), and cells read it inside the modifier's draw-phase
    // lambda, so cells composed later during scroll just see the settled 1f
    // and render immediately.
    val gridEntrance = rememberDetailEntrance(start = success != null)

    LaunchedEffect(success) {
        if (success != null) {
            backdropVisible.value = true
            contentVisible.value = true
        }
    }

    // Item awaiting a remove-download confirm from the quick-action menu.
    // Hoisted so the dialog survives the card leaving composition while open.
    val removeDownloadState = rememberRemoveDownloadState()

    // Collected (not read as a .value snapshot inside the resolve lambda) so
    // the resolver is rebuilt when the downloaded set changes — a download
    // completing flips the card's Download↔Remove-download action without
    // waiting for an unrelated recomposition. The set is distinct-collapsed
    // upstream, so active transfers don't churn it.
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()

    // Long-press / TV-Menu quick actions for collection cards. Download /
    // Remove download ride the same intake as the library grid (#147).
    val quickActionController = rememberMediaQuickActionController(
        resolveActions = remember(viewModel, downloadedIds) {
            { item: MediaItem ->
                item.quickActions(
                    MediaQuickActionScope.DETAIL,
                    includeDownload = true,
                    isDownloaded = downloadedIds.contains(item.id),
                )
            }
        },
        executeAction = remember(viewModel, onItemClick) {
            { item: MediaItem, action: QuickAction ->
                when (action) {
                    QuickAction.PLAY -> onItemClick(item.id)
                    QuickAction.MARK_WATCHED -> viewModel.markItemPlayed(item, played = true)
                    QuickAction.MARK_UNWATCHED -> viewModel.markItemPlayed(item, played = false)
                    QuickAction.DOWNLOAD -> viewModel.downloadItem(item, onOpenDetail = onItemClick)
                    QuickAction.REMOVE_DOWNLOAD -> removeDownloadState.request(item)
                    QuickAction.DETAILS -> onItemClick(item.id)
                    else -> Unit
                }
            }
        },
    )
    // TV-only: the card currently holding D-pad focus, so the Menu key can open
    // its quick actions.
    var tvFocusedItem by remember { mutableStateOf<MediaItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onDpadKey(
                onMenu = {
                    tvFocusedItem?.let { quickActionController.show(it) }
                    true
                },
            ),
    ) {
        CompositionLocalProvider(LocalMediaQuickActionController provides quickActionController) {
        JellyPlayScreenScaffold(
            title = title,
            onBack = onBack,
            topBarStyle = TopBarStyle.Collapsing,
        ) { padding ->
        when (state) {
            CollectionDetailUiState.Loading -> {
                DelayedLoadingScreen(modifier = Modifier.padding(padding))
            }

            is CollectionDetailUiState.Error -> {
                ErrorScreen(
                    message = (state as CollectionDetailUiState.Error).message,
                    onRetry = { viewModel.loadCollection(collectionId) },
                    modifier = Modifier.padding(padding),
                )
            }

            is CollectionDetailUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    AnimatedVisibility(
                            visible = backdropVisible.value,
                            enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()) + slideInVertically(
                                initialOffsetY = { -it / 6 },
                                animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                            ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                            ) {
                                MediaImage(
                                    url = viewModel.getBackdropUrl(collectionId),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = 0.7f },
                                    // Full-width backdrop banner (~200 dp tall). Decode large
                                    // enough for tablets/TV rather than the blurry 512 default.
                                    size = CoilSize(1280, 720),
                                )
                            }
                        }

                    AnimatedVisibility(
                        visible = contentVisible.value,
                        enter = fadeIn(
                            MaterialTheme.motionScheme.defaultEffectsSpec()
                        ),
                    ) {
                        val adaptiveInfo = LocalAdaptiveInfo.current
                        val isTv = LocalTvMode.current
                        val contentPad = adaptiveInfo.contentPadding(isTv)
                        val gridMin = adaptiveInfo.gridMinSize(isTv)
                        val spacing = adaptiveInfo.itemSpacing(isTv)

                        // an empty collection previously rendered
                        // just the backdrop banner with nothing below. Surface a
                        // proper empty state instead of a bare grid.
                        if (items.isEmpty()) {
                            com.raulshma.jellyplay.core.ui.components.ScreenEmptyState(
                                icon = Tabler.Outline.LayoutGrid,
                                title = stringResource(R.string.detail_collection_empty_title),
                                description = stringResource(R.string.detail_collection_empty_description),
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                        TvFocusableGrid(
                            items = items,
                            key = { it.id },
                            columns = GridCells.Adaptive(gridMin),
                            contentPadding = PaddingValues(
                                horizontal = contentPad,
                                vertical = 8.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalArrangement = Arrangement.spacedBy(spacing),
                            modifier = Modifier.fillMaxSize(),
                            contentType = { "mediaItem" },
                            onFocusedIndexChange = { index -> items.getOrNull(index)?.let { tvFocusedItem = it } },
                        ) { index, item, focusModifier ->
                            val itemProgress = item.progressFraction()
                            PosterCard(
                                item = item,
                                imageUrl = viewModel.getImageUrl(item.id),
                                onClick = { onItemClick(item.id) },
                                // graphicsLayer before focusModifier so the reveal
                                // wraps the whole cell (focus indicator included),
                                // matching the AnimatedVisibility wrapper it
                                // replaces: fade + slide up from 1/8 of the cell
                                // height. The shared Animatable is read inside
                                // the modifier's draw-phase lambda, so cells
                                // composed later during scroll render at the
                                // settled 1f immediately.
                                modifier = Modifier
                                    .detailEntrance(progress = { gridEntrance.value })
                                    .then(focusModifier),
                                showProgress = itemProgress != null && itemProgress > 0f,
                                progressPercent = itemProgress ?: 0f,
                                sharedElementKey = "poster_${item.id}",
                            )
                        }
                        } // close else (items non-empty)
                    }
                }
            }
        }
        } // close scaffold content lambda
        } // close CompositionLocalProvider
    } // close Box
    MediaQuickActionHost(quickActionController)

    // Remove-download confirm: quick-action removal only ever deletes the
    // local download — the server copy is untouched.
    RemoveDownloadConfirmHost(
        state = removeDownloadState,
        onConfirmRemove = { viewModel.removeItemDownload(it) },
    )
} // close CollectionDetailScreen
