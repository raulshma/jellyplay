package com.raulshma.jellyplay.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)@Composable
fun UserSelectionScreen(
    serverId: String,
    serverAddress: String,
    serverName: String,
    onUserSelected: () -> Unit,
    onAddUser: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    var users by remember { mutableStateOf<List<UserInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(serverId) {
        viewModel.getUsersForServer(serverId) { result ->
            users = result
            isLoading = false
        }
    }

    val isSynthwave = LocalIsSynthwave.current
    val backgroundColor = rememberScreenBackgroundColor()

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text(serverName) },
                navigationIcon = { CircleBgBackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isSynthwave) Color.Transparent else MaterialTheme.colorScheme.surface,
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddUser,
                icon = { Icon(Tabler.Outline.Plus, contentDescription = null) },
                text = { Text("Add User") },
            )
        },
    ) { padding ->
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        val contentPad = adaptiveInfo.contentPadding(isTv)
        val spacing = adaptiveInfo.itemSpacing(isTv)

        when {
            isLoading -> {
                ScreenLoadingState(
                    message = "Loading users...",
                    modifier = Modifier.padding(padding),
                )
            }
            users.isEmpty() -> {
                ScreenEmptyState(
                    icon = Tabler.Outline.User,
                    title = "No users on this server",
                    description = "Add a user to get started",
                    actionLabel = "Add User",
                    onAction = onAddUser,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(contentPad),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    itemsIndexed(users, key = { _, it -> it.id }, contentType = { _, _ -> "user" }) { index, user ->
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
                            UserCard(
                                user = user,
                                onClick = {
                                    viewModel.switchUser(user.id) { result ->
                                        if (result.isSuccess) {
                                            onUserSelected()
                                        }
                                    }
                                },
                                onRemove = { viewModel.removeUser(user.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(
    user: UserInfo,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val isSynthwave = LocalIsSynthwave.current
    val isSoothing = LocalIsSoothingTheme.current
    val border = when {
        isSynthwave -> {
            androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
        }
        isSoothing -> {
            androidx.compose.foundation.BorderStroke(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        }
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = border,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Tabler.Outline.User,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = user.name.ifBlank { "User" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = user.serverAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = "Remove user",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
