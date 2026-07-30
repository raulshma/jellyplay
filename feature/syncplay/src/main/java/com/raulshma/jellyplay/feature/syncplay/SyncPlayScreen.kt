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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
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
    onPlayItem: (String, Long) -> Unit,
    viewModel: SyncPlayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val currentGroup = uiState.currentGroup
    val isLoading = uiState.isLoading
    val error = uiState.error
    val isInGroup = uiState.isInGroup
    val showCreateDialog = uiState.showCreateDialog
    val navigateToPlayer by viewModel.navigateToPlayer.collectAsStateWithLifecycle()
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    val snackbarHostState = remember { SnackbarHostState() }
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

    navigateToPlayer?.let { request ->
        LaunchedEffect(request) {
            onPlayItem(request.itemId, request.positionTicks)
            viewModel.onNavigateToPlayerHandled()
        }
    }

    JellyPlayScreenScaffold(
        title = "SyncPlay",
        onBack = onBack,
        actions = {
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(start = 12.dp),
            )
            if (isInGroup) {
                TooltipIconButton(
                    onClick = { viewModel.leaveGroup() },
                    imageVector = Tabler.Outline.Logout,
                    contentDescription = "Leave group",
                    tooltipText = "Leave group",
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
                            Text("Retry")
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
                             onLeave = { viewModel.leaveGroup() },
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
                            title = "No active SyncPlay groups",
                            description = "Create a group to watch together",
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

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
            ) { data ->
                Snackbar(data)
            }

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
                    icon = { Icon(Tabler.Outline.Plus, contentDescription = "Create group") },
                    text = { Text("Create group") },
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

    uiState.pendingJoin?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelJoin() },
            title = { Text("Join group?") },
            text = { Text("Join \"${pending.groupName}\"?") },
            confirmButton = {
                FilledTonalButton(
                    onClick = { viewModel.confirmJoin() },
                    modifier = Modifier.focusIndicator(),
                ) { Text("Join") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.cancelJoin() },
                    modifier = Modifier.focusIndicator(),
                ) { Text("Cancel") }
            },
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
                        "${group.participantCount} participant${if (group.participantCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                group.playingItemName?.let { name ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Playing: $name",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledTonalButton(onClick = onJoin, modifier = Modifier.focusIndicator()) {
                Text("Join")
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
                        "${groupInfo.participants.size} participant${if (groupInfo.participants.size != 1) "s" else ""}",
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
                    if (groupInfo.isPlaying) "Playing" else "Paused",
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
                            contentDescription = if (groupInfo.isPlaying) "Pause" else "Play",
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (groupInfo.isPlaying) "Pause" else "Play")
                    }
                    FilledTonalButton(onClick = onStop, modifier = Modifier.focusIndicator()) {
                        Icon(Tabler.Outline.PlayerStop, contentDescription = "Stop")
                    }
                    OutlinedButton(onClick = onLeave, modifier = Modifier.focusIndicator()) {
                        Icon(Tabler.Outline.Logout, contentDescription = "Leave")
                        Spacer(Modifier.width(4.dp))
                        Text("Leave")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Group Settings",
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
            "Participants",
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
                            if (participant.isConnected) "Connected" else "Disconnected",
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
        title = { Text("Create SyncPlay Group") },
        text = {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group name") },
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
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.focusIndicator()) {
                Text("Cancel")
            }
        },
    )
}
