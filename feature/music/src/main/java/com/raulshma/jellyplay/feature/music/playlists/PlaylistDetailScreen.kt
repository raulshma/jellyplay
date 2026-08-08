package com.raulshma.jellyplay.feature.music.playlists

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.isVideoType
import com.raulshma.jellyplay.feature.music.R
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.animation.lessSpringySpec
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.UndoSnackbarOverlay
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    playlistName: String = "",
    onBack: () -> Unit,
    /**
     * Launched for a single (typically video) item — the caller branches on the
     * item's [com.raulshma.jellyplay.core.model.PlaylistItem.mediaType] to route
     * to the right player. Audio items instead go through [PlaylistDetailViewModel.playAll],
     * which enqueues the remaining tracks into the audio queue.
     */
    onPlayItem: (com.raulshma.jellyplay.core.model.PlaylistItem) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val initialPlaylistName = playlistName
    LaunchedEffect(playlistId) {
        viewModel.load(playlistId, initialPlaylistName)
    }
    val resolvedPlaylistName = viewModel.playlistName
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = viewModel.isLoading,
        hasError = viewModel.error != null,
        networkStatus = networkStatus,
    )

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    // A video playlist can't be "played all" through the audio queue, so the
    // Play All FAB is only meaningful when the list has audio items.
    val hasAudioItems = viewModel.items.any { !it.mediaType.isVideoType }

    // TV focus-on-launch: focus the first track once data arrives so D-pad input lands on content,
    // not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = viewModel.items.size,
        tag = "playlist_detail_init",
    )

    JellyPlayScreenScaffold(
        title = resolvedPlaylistName,
        onBack = onBack,
        actions = {
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isLoading && viewModel.items.isNotEmpty(),
            onRefresh = {
                viewModel.refreshPlaylist(playlistId)
            },
            modifier = Modifier.fillMaxSize(),
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // "Removed 'X' from playlist — Undo" recovery for removals
            // The screen previously had no SnackbarHost at all; this is the single host.
            UndoSnackbarOverlay(
                actions = viewModel.undoActions,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            when {
                viewModel.isLoading && viewModel.items.isEmpty() -> {
                    ScreenLoadingState()
                }
                viewModel.error != null && viewModel.items.isEmpty() -> {
                    ErrorScreen(
                        message = viewModel.error!!,
                        onRetry = { viewModel.load(playlistId) },
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .tvFocusRestorer()
                            .focusRequester(listFocusRequester),
                        contentPadding = PaddingValues(
                            start = contentPad,
                            end = contentPad,
                            bottom = adaptiveInfo.bottomPadding(isTv) + innerPadding.calculateBottomPadding(),
                        ),
                    ) {
                        items(viewModel.items.size, key = { viewModel.items[it].id }, contentType = { "playlistItem" }) { index ->
                            val item = viewModel.items[index]
                            // Video items launch the video player directly (they
                            // can't be queued by the audio playback manager);
                            // audio items play-and-queue the rest of the list.
                            val isVideo = item.mediaType.isVideoType
                            PlaylistTrackRow(
                                item = item,
                                onClick = {
                                    if (isVideo) onPlayItem(item) else viewModel.playAll(index)
                                },
                                onAddToQueue = if (isVideo) null else { { viewModel.addToQueue(item) } },
                                onRemoveFromPlaylist = { viewModel.removeFromPlaylist(item) },
                                onMoveUp = if (index > 0) { { viewModel.moveItem(item, index - 1) } } else null,
                                onMoveDown = if (index < viewModel.items.lastIndex) { { viewModel.moveItem(item, index + 1) } } else null,
                            )
                        }
                    }
                }
            }

            if (hasAudioItems) {
                val playAllFocusState = rememberTvFocusState(focusedScale = 1.05f)
                ExtendedFloatingActionButton(
                    onClick = { viewModel.playAll() },
                    icon = { Icon(Tabler.Outline.PlayerPlay, contentDescription = null) },
                    text = { Text(stringResource(R.string.music_play_all)) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .then(playAllFocusState.focusModifier)
                        .tvFocusIndicator(playAllFocusState, ShapeCache.smooth16)
                        .padding(end = 16.dp)
                        .clearFloatingNav(),
                )
            }
        }
        }
    }}

@Composable
private fun PlaylistTrackRow(
    item: com.raulshma.jellyplay.core.model.PlaylistItem,
    onClick: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = lessSpringySpec(),
        label = "trackScale",
    )

    var showMenu by remember { mutableStateOf(false) }
    val menuFocusState = rememberTvFocusState()
    val playFocusState = rememberTvFocusState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    if (onAddToQueue != null) {
                        showMenu = true
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(item.artist, item.album).joinToString(" — ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onAddToQueue != null) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.then(menuFocusState.focusModifier).tvFocusIndicator(menuFocusState, CircleShape),
            ) {
                Icon(
                    Tabler.Outline.DotsVertical,
                    contentDescription = stringResource(R.string.music_more_options),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.music_add_to_queue)) },
                    onClick = {
                        onAddToQueue()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Tabler.Outline.Playlist,
                            contentDescription = null,
                        )
                    },
                )
                // Reorder entries — works on touch + D-pad.
                if (onMoveUp != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.music_move_up)) },
                        onClick = {
                            onMoveUp()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Tabler.Outline.ArrowUp, contentDescription = null)
                        },
                    )
                }
                if (onMoveDown != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.music_move_down)) },
                        onClick = {
                            onMoveDown()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Tabler.Outline.ArrowDown, contentDescription = null)
                        },
                    )
                }
                if (onRemoveFromPlaylist != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.music_remove_from_playlist), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onRemoveFromPlaylist()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Tabler.Outline.Trash,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier.then(playFocusState.focusModifier).tvFocusIndicator(playFocusState, CircleShape),
        ) {
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = stringResource(R.string.music_play),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )
        }
    }
}
