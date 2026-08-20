package com.raulshma.jellyplay.feature.syncplay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.feature.syncplay.R
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ConfirmState
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.rememberConfirmState
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.TooltipIconButton
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import kotlinx.coroutines.delay
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncPlayScreen(
    onBack: () -> Unit,
    viewModel: SyncPlayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val currentGroup = uiState.currentGroup
    val isLoading = uiState.isLoading
    val error = uiState.error
    val isInGroup = uiState.isInGroup
    val showCreateDialog = uiState.showCreateDialog
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    val snackbarHostState = remember { SnackbarHostState() }
    val leaveConfirm = rememberConfirmState()
    val headerStatus = resolveHeaderStatus(
        isLoading = isLoading,
        hasError = error != null,
        networkStatus = networkStatus,
    )

    // TV focus-on-launch: focus the first group card or the create-group FAB once data arrives so
    // D-pad input lands on content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    val createGroupFocusState = rememberTvFocusState(focusedScale = 1.05f)
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (error != null) 0 else groups.size.coerceAtLeast(1),
        tag = "syncplay_init",
    )

    LaunchedEffect(Unit) {
        viewModel.loadGroups()
    }

    LaunchedEffect(isInGroup) {
        if (!isInGroup) {
            var pollInterval = 5000L
            while (true) {
                delay(pollInterval)
                viewModel.refreshGroups()
                pollInterval = (pollInterval * 1.5).toLong().coerceAtMost(30_000L)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.notifications.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    JellyPlayScreenScaffold(
        title = stringResource(R.string.syncplay_title),
        onBack = onBack,
        actions = {
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(start = 12.dp),
            )
            if (isInGroup) {
                TooltipIconButton(
                    onClick = { leaveConfirm.request { viewModel.leaveGroup() } },
                    imageVector = Tabler.Outline.Logout,
                    contentDescription = stringResource(R.string.syncplay_leave_group),
                    tooltipText = stringResource(R.string.syncplay_leave_group),
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                error != null && groups.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.loadGroups() }, modifier = Modifier.focusIndicator()) {
                            Text(stringResource(R.string.syncplay_retry))
                        }
                    }
                }

                isInGroup && currentGroup != null -> {
                    val defaultEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = currentGroup,
                        transitionSpec = {
                            (fadeIn(defaultEffectsSpec) + slideInHorizontally(initialOffsetX = { it }))
                                .togetherWith(fadeOut(defaultEffectsSpec) + slideOutHorizontally(targetOffsetX = { -it }))
                        },
                        label = "activeGroup",
                    ) { group ->
                        ActiveGroupView(
                             groupInfo = group,
                             onTogglePlayback = { viewModel.togglePlayback() },
                             onStop = { viewModel.stop() },
                             onLeave = { leaveConfirm.request { viewModel.leaveGroup() } },
                             onSetRepeatMode = { viewModel.setRepeatMode(it) },
                             onSetShuffleMode = { viewModel.setShuffleMode(it) },
                             onSetIgnoreWait = { viewModel.setIgnoreWait(it) },
                         )
                    }
                }

                else -> {
                    if (groups.isEmpty()) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Users,
                            title = stringResource(R.string.syncplay_no_active_groups),
                            description = stringResource(R.string.syncplay_create_group_together),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .tvFocusRestorer()
                                .focusRequester(listFocusRequester),
                            contentPadding = PaddingValues(contentPad),
                            verticalArrangement = Arrangement.spacedBy(spacing),
                        ) {
                            itemsIndexed(groups, key = { _, it -> it.groupId }, contentType = { _, _ -> "syncPlayGroup" }) { index, group ->
                                val visible = remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { visible.value = true }
                                AnimatedVisibility(
                                    visible = visible.value,
                                    enter = fadeIn(
                                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
                                    ) + slideInVertically(
                                        initialOffsetY = { it / 10 },
                                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    ),
                                ) {
                                    SyncPlayGroupCard(
                                        group = group,
                                        onJoin = { viewModel.requestJoin(group) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            com.raulshma.jellyplay.core.ui.components.JellyPlaySnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
            )

            AnimatedVisibility(
                visible = !isInGroup,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp)
                    .clearFloatingNav(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.updateShowCreateDialog(true) },
                    shape = ShapeCache.smooth16,
                    modifier = Modifier
                        .then(createGroupFocusState.focusModifier)
                        .tvFocusIndicator(createGroupFocusState, ShapeCache.smooth16),
                    icon = { Icon(Tabler.Outline.Plus, contentDescription = stringResource(R.string.syncplay_create_group)) },
                    text = { Text(stringResource(R.string.syncplay_create_group)) },
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { viewModel.updateShowCreateDialog(false) },
            onCreate = { viewModel.createGroup(it) },
        )
    }

    if (leaveConfirm.isVisible) {
        leaveConfirm.ConfirmDialog(
            title = stringResource(R.string.syncplay_leave_group_confirm_title),
            message = stringResource(R.string.syncplay_leave_group_confirm_message),
            confirmText = stringResource(R.string.syncplay_leave_group),
            dismissText = stringResource(com.raulshma.jellyplay.core.ui.R.string.core_cancel),
        )
    }

    uiState.pendingJoin?.let { pending ->
        ConfirmDialog(
            title = stringResource(R.string.syncplay_join_group_question),
            message = stringResource(R.string.syncplay_join_group_confirm, pending.groupName),
            confirmText = stringResource(R.string.syncplay_join),
            onConfirm = { viewModel.confirmJoin() },
            onDismiss = { viewModel.cancelJoin() },
            dismissText = stringResource(R.string.syncplay_cancel),
            tone = ConfirmTone.NEUTRAL,
        )
    }
}

@Composable
private fun SyncPlayGroupCard(
    group: SyncPlayGroup,
    onJoin: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusIndicator(ShapeCache.smooth12)
            .clickable(onClick = onJoin),
        shape = ShapeCache.smooth12,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.groupName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Tabler.Outline.Users,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        pluralStringResource(R.plurals.syncplay_participants_count, group.participantCount, group.participantCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                group.playingItemName?.let { name ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.syncplay_playing_item, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledTonalButton(onClick = onJoin, modifier = Modifier.focusIndicator()) {
                Text(stringResource(R.string.syncplay_join))
            }
        }
    }
}

@Composable
private fun ActiveGroupView(
    groupInfo: com.raulshma.jellyplay.core.model.SyncPlayGroupInfo,
    onTogglePlayback: () -> Unit,
    onStop: () -> Unit,
    onLeave: () -> Unit,
    onSetRepeatMode: (SyncPlayRepeatMode) -> Unit,
    onSetShuffleMode: (SyncPlayShuffleMode) -> Unit,
    onSetIgnoreWait: (Boolean) -> Unit,
) {
    var ignoreWait by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding(),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    groupInfo.groupName,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Tabler.Outline.Users,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        pluralStringResource(R.plurals.syncplay_participants_count, groupInfo.participants.size, groupInfo.participants.size),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                Spacer(Modifier.height(16.dp))

                groupInfo.playingItemName?.let { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    stringResource(if (groupInfo.isPlaying) R.string.syncplay_playing else R.string.syncplay_paused),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (groupInfo.isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = onTogglePlayback, modifier = Modifier.focusIndicator()) {
                        Icon(
                            if (groupInfo.isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                            contentDescription = stringResource(if (groupInfo.isPlaying) R.string.syncplay_pause else R.string.syncplay_play),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(if (groupInfo.isPlaying) R.string.syncplay_pause else R.string.syncplay_play))
                    }
                    FilledTonalButton(onClick = onStop, modifier = Modifier.focusIndicator()) {
                        Icon(Tabler.Outline.PlayerStop, contentDescription = stringResource(R.string.syncplay_stop))
                    }
                    OutlinedButton(onClick = onLeave, modifier = Modifier.focusIndicator()) {
                        Icon(Tabler.Outline.Logout, contentDescription = stringResource(R.string.syncplay_leave))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.syncplay_leave))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.syncplay_group_settings),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var repeatExpanded by remember { mutableStateOf(false) }
            Box {
                FilledTonalButton(onClick = { repeatExpanded = true }, modifier = Modifier.focusIndicator()) {
                    Icon(Tabler.Outline.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(groupInfo.repeatMode.name, style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(
                    expanded = repeatExpanded,
                    onDismissRequest = { repeatExpanded = false },
                ) {
                    SyncPlayRepeatMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.name) },
                            onClick = {
                                onSetRepeatMode(mode)
                                repeatExpanded = false
                            },
                        )
                    }
                }
            }

            var shuffleExpanded by remember { mutableStateOf(false) }
            Box {
                FilledTonalButton(onClick = { shuffleExpanded = true }, modifier = Modifier.focusIndicator()) {
                    Icon(Tabler.Outline.ArrowsShuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(groupInfo.shuffleMode.name, style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(
                    expanded = shuffleExpanded,
                    onDismissRequest = { shuffleExpanded = false },
                ) {
                    SyncPlayShuffleMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.name) },
                            onClick = {
                                onSetShuffleMode(mode)
                                shuffleExpanded = false
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.syncplay_participants),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(groupInfo.participants, key = { it.userId }, contentType = { "participant" }) { participant ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                participant.userName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                participant.userName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Text(
                            stringResource(if (participant.isConnected) R.string.syncplay_connected else R.string.syncplay_disconnected),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (participant.isConnected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var groupName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows=false dispatches IME insets into the dialog so
        // imePadding can lift fields + buttons above the soft keyboard.
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier.imePadding(),
        title = { Text(stringResource(R.string.syncplay_create_group_title)) },
        text = {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text(stringResource(R.string.syncplay_group_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { if (groupName.isNotBlank()) onCreate(groupName) },
                enabled = groupName.isNotBlank(),
                modifier = Modifier.focusIndicator(),
            ) {
                Text(stringResource(R.string.syncplay_create))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.focusIndicator()) {
                Text(stringResource(R.string.syncplay_cancel))
            }
        },
    )
}
