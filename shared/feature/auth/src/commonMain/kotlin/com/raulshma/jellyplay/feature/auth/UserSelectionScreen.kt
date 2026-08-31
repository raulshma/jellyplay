package com.raulshma.jellyplay.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_add_user
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_cancel
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_loading_users
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_no_users_desc
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_no_users_title
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_remove
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_remove_user
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_remove_user_message
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_remove_user_title
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_user_placeholder
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.designsystem.theme.Dimensions
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.AddListRow
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
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
    viewModel: AuthViewModel = koinViewModel(),
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
    val backgroundColorState = rememberScreenBackgroundColorState()
    val navOffsetPx = LocalFloatingNavOffset.current

    Scaffold(
        containerColor = backgroundColorState.value,
        topBar = {
            TopAppBar(
                title = { Text(serverName) },
                navigationIcon = { CircleBgBackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isSynthwave) Color.Transparent else MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = if (isSynthwave) Color.Transparent else MaterialTheme.colorScheme.surface,
                )
            )
        },
    ) { padding ->
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        val contentPad = adaptiveInfo.contentPadding(isTv)
        val spacing = adaptiveInfo.itemSpacing(isTv)

        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> {
                    ScreenLoadingState(message = stringResource(Res.string.auth_loading_users))
                }
                users.isEmpty() -> {
                    ScreenEmptyState(
                        icon = Tabler.Outline.User,
                        title = stringResource(Res.string.auth_no_users_title),
                        description = stringResource(Res.string.auth_no_users_desc),
                        actionLabel = stringResource(Res.string.auth_add_user),
                        onAction = onAddUser,
                    )
                }
                else -> {
                    val firstItemFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        if (isTv) firstItemFocusRequester.tryRequestFocus("user_first")
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().tvFocusRestorer(),
                        contentPadding = PaddingValues(
                            start = contentPad,
                            end = contentPad,
                            top = contentPad,
                            bottom = contentPad + Dimensions.floatingNavHeight,
                        ),
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
                                    firstFocusModifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
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

                        // TV has no FAB, so the add action lives in the list.
                        if (isTv) {
                            item(key = "add_user", contentType = "add_user") {
                                AddListRow(
                                    label = stringResource(Res.string.auth_add_user),
                                    onClick = onAddUser,
                                )
                            }
                        }
                    }
                }
            }

            if (!isTv) {
                val fabFocusState = rememberTvFocusState(focusedScale = 1.05f)
                ExtendedFloatingActionButton(
                    onClick = onAddUser,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .then(fabFocusState.focusModifier)
                        .tvFocusIndicator(fabFocusState, ShapeCache.smooth16)
                        .padding(
                            end = 16.dp,
                            bottom = 16.dp + Dimensions.floatingNavHeight,
                        )
                        .offset {
                            val maxOffset = Dimensions.floatingNavHeight.toPx()
                            val yOffset = (-navOffsetPx()).coerceAtMost(maxOffset)
                            IntOffset(x = 0, y = yOffset.toInt())
                        },
                    icon = { Icon(Tabler.Outline.Plus, contentDescription = null) },
                    text = { Text(stringResource(Res.string.auth_add_user)) },
                )
            }
        }
    }
}

@Composable
private fun UserCard(
    user: UserInfo,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    var showRemoveConfirm by remember { mutableStateOf(false) }
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

    val shape = when {
        isSynthwave -> RoundedCornerShape(0.dp)
        isSoothing -> ShapeCache.smooth16
        else -> ShapeCache.smooth12
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(firstFocusModifier)
            .focusIndicator(shape)
            .clickable(onClick = onClick),
        shape = shape,
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
                        text = user.name.ifBlank { stringResource(Res.string.auth_user_placeholder) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = user.serverAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val removeFocusState = rememberTvFocusState()
            IconButton(
                onClick = { showRemoveConfirm = true },
                modifier = Modifier.then(removeFocusState.focusModifier).tvFocusIndicator(removeFocusState, CircleShape),
            ) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = stringResource(Res.string.auth_remove_user),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showRemoveConfirm) {
        com.raulshma.jellyplay.core.ui.components.ConfirmDialog(
            title = stringResource(Res.string.auth_remove_user_title),
            message = stringResource(Res.string.auth_remove_user_message, user.name),
            confirmText = stringResource(Res.string.auth_remove),
            dismissText = stringResource(Res.string.auth_cancel),
            onConfirm = onRemove,
            onDismiss = { showRemoveConfirm = false },
        )
    }
}
