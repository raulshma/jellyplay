package com.raulshma.jellyplay.feature.syncplay

import androidx.compose.animation.AnimatedContent
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
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
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.components.TooltipIconButton
import kotlinx.coroutines.delay
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncPlayScreen(
    onBack: () -> Unit,
    onPlayItem: (String, Long) -> Unit,
    viewModel: SyncPlayViewModel = hiltViewModel(),
) {
    val groups = viewModel.groups
    val currentGroup = viewModel.currentGroup
    val isLoading = viewModel.isLoading
    val error = viewModel.error
    val isInGroup = viewModel.isInGroup
    val showCreateDialog = viewModel.showCreateDialog
    val navigateToPlayer by viewModel.navigateToPlayer.collectAsStateWithLifecycle()
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    val snackbarHostState = remember { SnackbarHostState() }
    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = isLoading,
        hasError = error != null,
        networkStatus = networkStatus,
    )

    LaunchedEffect(Unit) {
        viewModel.loadGroups()
    }

    LaunchedEffect(isInGroup) {
        if (!isInGroup) {
            while (true) {
                delay(5000)
                viewModel.refreshGroups()
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(data)
            }
        },
        topBar = {
            MediumTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SyncPlay")
                        com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                            status = headerStatus,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tooltipText = "Back",
                    )
                },
                actions = {
                    if (isInGroup) {
                        TooltipIconButton(
                            onClick = { viewModel.leaveGroup() },
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Leave group",
                            tooltipText = "Leave group",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isInGroup,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(targetOffsetY = { it }),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.updateShowCreateDialog(true) },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Create group") },
                    text = { Text("Create group") },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                error != null && groups.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.loadGroups() }) {
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
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No active SyncPlay groups",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Create a group to watch together",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
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
                                        onJoin = { viewModel.joinGroup(group.groupId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { viewModel.updateShowCreateDialog(false) },
            onCreate = { viewModel.createGroup(it) },
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
            .tvFocusable().clickable(onClick = onJoin),
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
                        Icons.Default.People,
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
            FilledTonalButton(onClick = onJoin) {
                Text("Join")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                        Icons.Default.People,
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
                    FilledTonalButton(onClick = onTogglePlayback) {
                        Icon(
                            if (groupInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (groupInfo.isPlaying) "Pause" else "Play",
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (groupInfo.isPlaying) "Pause" else "Play")
                    }
                    FilledTonalButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                    OutlinedButton(onClick = onLeave) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Leave")
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
                FilledTonalButton(onClick = { repeatExpanded = true }) {
                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
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
                FilledTonalButton(onClick = { shuffleExpanded = true }) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
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
            items(groupInfo.participants, contentType = { "participant" }) { participant ->
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
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
