package com.raulshma.jellyplay.feature.music.playlists

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.raulshma.jellyplay.core.ui.components.PullToRefreshBox
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
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_cancel
import com.raulshma.jellyplay.feature.music.generated.resources.music_create
import com.raulshma.jellyplay.feature.music.generated.resources.music_delete
import com.raulshma.jellyplay.feature.music.generated.resources.music_delete_playlist
import com.raulshma.jellyplay.feature.music.generated.resources.music_delete_playlist_confirm
import com.raulshma.jellyplay.feature.music.generated.resources.music_description_optional
import com.raulshma.jellyplay.feature.music.generated.resources.music_edit
import com.raulshma.jellyplay.feature.music.generated.resources.music_edit_playlist
import com.raulshma.jellyplay.feature.music.generated.resources.music_more_options
import com.raulshma.jellyplay.feature.music.generated.resources.music_name_label
import com.raulshma.jellyplay.feature.music.generated.resources.music_new_playlist
import com.raulshma.jellyplay.feature.music.generated.resources.music_open
import com.raulshma.jellyplay.feature.music.generated.resources.music_playlist_tracks_count
import com.raulshma.jellyplay.feature.music.generated.resources.music_playlists
import com.raulshma.jellyplay.feature.music.generated.resources.music_save
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onPlaylistClick: (id: String, name: String) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaylistsViewModel = koinViewModel(),
) {
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = viewModel.isLoading,
        hasError = false,
        networkStatus = networkStatus,
    )

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    // TV focus-on-launch: focus the first playlist once data arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = viewModel.playlists.size,
        tag = "playlists_init",
    )

    LaunchedEffect(viewModel.error) {
        // Errors are surfaced via the dialogs/state; we keep state-based clearing
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.music_playlists),
        onBack = onBack,
        actions = {
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = viewModel.isLoading && viewModel.playlists.isNotEmpty(),
                onRefresh = {
                    viewModel.load()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    viewModel.error != null && viewModel.playlists.isEmpty() -> {
                        ErrorScreen(
                            message = viewModel.error!!,
                            onRetry = { viewModel.load() },
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
                            items(
                                items = viewModel.playlists,
                                key = { it.id },
                                contentType = { "playlist" },
                            ) { playlist ->
                                PlaylistItemRow(
                                    playlist = playlist,
                                    onClick = { onPlaylistClick(playlist.id, playlist.name) },
                                    onEdit = { viewModel.openEditDialog(playlist) },
                                    onDelete = { viewModel.openDeleteDialog(playlist) },
                                )
                            }
                        }
                    }
                }
            }

            val newPlaylistFocusState = rememberTvFocusState(focusedScale = 1.05f)
            ExtendedFloatingActionButton(
                onClick = { viewModel.openCreateDialog() },
                icon = { Icon(Tabler.Outline.Plus, contentDescription = null) },
                text = { Text(stringResource(Res.string.music_new_playlist)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(newPlaylistFocusState.focusModifier)
                    .tvFocusIndicator(newPlaylistFocusState, ShapeCache.smooth16)
                    .padding(end = 16.dp)
                    .clearFloatingNav(),
            )
        }
    }

    PlaylistDialogHost(viewModel = viewModel)
}

@Composable
private fun PlaylistDialogHost(viewModel: PlaylistsViewModel) {
    when (val state = viewModel.dialogState) {
        PlaylistDialogState.None -> Unit
        is PlaylistDialogState.Create -> {
            PlaylistNameDialog(
                title = stringResource(Res.string.music_new_playlist),
                initialName = state.name,
                initialOverview = state.overview,
                confirmLabel = stringResource(Res.string.music_create),
                isLoading = viewModel.isMutating,
                onConfirm = { name, overview -> viewModel.createPlaylist(name, overview) },
                onDismiss = { viewModel.dismissDialog() },
            )
        }
        is PlaylistDialogState.Edit -> {
            PlaylistNameDialog(
                title = stringResource(Res.string.music_edit_playlist),
                initialName = state.name,
                initialOverview = state.overview,
                confirmLabel = stringResource(Res.string.music_save),
                isLoading = viewModel.isMutating,
                onConfirm = { name, overview ->
                    viewModel.updatePlaylist(state.playlist.id, name, overview)
                },
                onDismiss = { viewModel.dismissDialog() },
            )
        }
        is PlaylistDialogState.Delete -> {
            ConfirmDialog(
                title = stringResource(Res.string.music_delete_playlist),
                message = stringResource(Res.string.music_delete_playlist_confirm, state.playlist.name),
                confirmText = stringResource(Res.string.music_delete),
                dismissText = stringResource(Res.string.music_cancel),
                tone = ConfirmTone.DESTRUCTIVE,
                confirmEnabled = !viewModel.isMutating,
                onConfirm = { viewModel.deletePlaylist(state.playlist) },
                onDismiss = { viewModel.dismissDialog() },
            )
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    initialOverview: String,
    confirmLabel: String,
    isLoading: Boolean,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var overview by remember { mutableStateOf(initialOverview) }
    val nameFocusRequester = remember { FocusRequester() }
    com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus(nameFocusRequester, "playlist_name")

    ImeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.music_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = overview,
                    onValueChange = { overview = it },
                    label = { Text(stringResource(Res.string.music_description_optional)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, overview) },
                enabled = !isLoading && name.isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(Res.string.music_cancel))
            }
        },
    )
}

@Composable
private fun PlaylistItemRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val menuFocusState = rememberTvFocusState()
    val openFocusState = rememberTvFocusState()
    Row(
        modifier = Modifier
            .fillMaxWidth()

            .focusIndicator(ShapeCache.smooth16)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Tabler.Outline.Music,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            playlist.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(Res.string.music_playlist_tracks_count, playlist.itemCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.then(menuFocusState.focusModifier).tvFocusIndicator(menuFocusState, CircleShape),
            ) {
                Icon(
                    imageVector = Tabler.Outline.DotsVertical,
                    contentDescription = stringResource(Res.string.music_more_options),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.music_edit)) },
                    leadingIcon = { Icon(Tabler.Outline.Edit, contentDescription = null) },
                    enabled = playlist.canEdit,
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.music_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Tabler.Outline.Trash,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    enabled = playlist.canDelete,
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier.then(openFocusState.focusModifier).tvFocusIndicator(openFocusState, CircleShape),
        ) {
            Icon(
                imageVector = Tabler.Outline.PlayerPlay,
                contentDescription = stringResource(Res.string.music_open),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )
        }
    }
}
