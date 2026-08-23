package com.raulshma.jellyplay.feature.settings

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.AddListRow
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import androidx.compose.foundation.shape.CircleShape
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.settings.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserManagementScreen(
    onBack: () -> Unit,
    onAddUser: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: ServerSettingsViewModel = hiltViewModel(),
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val serverUsers by viewModel.currentServerUsers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingUsers.collectAsStateWithLifecycle()
    val networkStatus by LocalNetworkStatus.current
        .collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = isLoading,
        hasError = false,
        networkStatus = networkStatus,
    )

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val navOffsetPx = com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset.current
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    val focusRequester = remember { FocusRequester() }
    val addUserFocusState = rememberTvFocusState(focusedScale = 1.05f)
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = if (!isLoading) serverUsers.size else 0,
        tag = "user_management_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(R.string.settings_switch_user),
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(start = 12.dp),
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && serverUsers.isEmpty() -> {
                    com.raulshma.jellyplay.core.ui.components.LoadingScreen()
                }
                serverUsers.isEmpty() -> {
                    ScreenEmptyState(
                        icon = Tabler.Outline.User,
                        title = stringResource(R.string.settings_no_users),
                        description = stringResource(R.string.settings_no_users_description),
                        actionLabel = stringResource(R.string.settings_add_user),
                        onAction = onAddUser,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .tvFocusRestorer()
                            .focusRequester(focusRequester),
                        contentPadding = PaddingValues(
                            start = contentPad,
                            end = contentPad,
                            top = contentPad,
                            bottom = contentPad + com.raulshma.jellyplay.core.designsystem.theme.Dimensions.floatingNavHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        itemsIndexed(serverUsers, key = { _, it -> it.id }, contentType = { _, _ -> "user" }) { index, user ->
                            val isCurrentUser = currentUser?.id == user.id
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
                                UserManagementCard(
                                    user = user,
                                    isCurrentUser = isCurrentUser,
                                    onClick = {
                                        if (!isCurrentUser) {
                                            viewModel.switchUser(user.id) {
                                                onBack()
                                            }
                                        }
                                    },
                                    onRemove = {
                                        viewModel.removeUser(user.id)
                                    },
                                )
                            }
                        }

                        // TV has no FAB, so the add action lives in the list.
                        if (isTv) {
                            item(key = "add_user", contentType = "add_user") {
                                AddListRow(
                                    label = stringResource(R.string.settings_add_user),
                                    onClick = onAddUser,
                                )
                            }
                        }
                    }
                }
            }

            if (!isTv) {
                AnimatedVisibility(
                    visible = serverUsers.isNotEmpty(),
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                            slideInVertically(initialOffsetY = { it }),
                    exit = androidx.compose.animation.fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            androidx.compose.animation.slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = onAddUser,
                        modifier = Modifier
                            .then(addUserFocusState.focusModifier)
                            .tvFocusIndicator(addUserFocusState, ShapeCache.smooth16)
                            .padding(
                                end = 16.dp,
                                bottom = 16.dp + com.raulshma.jellyplay.core.designsystem.theme.Dimensions.floatingNavHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                            )
                            .offset {
                                val maxOffset = com.raulshma.jellyplay.core.designsystem.theme.Dimensions.floatingNavHeight.toPx()
                                val yOffset = (-navOffsetPx()).coerceAtMost(maxOffset)
                                androidx.compose.ui.unit.IntOffset(x = 0, y = yOffset.toInt())
                            },
                        icon = { Icon(Tabler.Outline.Plus, contentDescription = null) },
                        text = { Text(stringResource(R.string.settings_add_user)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserManagementCard(
    user: UserInfo,
    isCurrentUser: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .focusIndicator()
            .clickable(onClick = onClick),
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
                    tint = if (isCurrentUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    Text(
                        text = user.name.ifBlank { stringResource(R.string.settings_user_fallback) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (isCurrentUser) {
                        Text(
                            text = stringResource(R.string.settings_signed_in),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (isCurrentUser) {
                Icon(
                    Tabler.Outline.Check,
                        contentDescription = stringResource(R.string.settings_current_user_cd),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.focusIndicator(CircleShape),
                ) {
                    Icon(
                        Tabler.Outline.Trash,
                        contentDescription = stringResource(R.string.settings_remove_user_cd),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
