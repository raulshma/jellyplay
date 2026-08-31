package com.raulshma.jellyplay.feature.admin.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.UserPlus
import com.raulshma.jellyplay.feature.admin.R
import com.raulshma.jellyplay.core.designsystem.theme.Dimensions
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.AddListRow
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.users.components.CreateUserDialog
import com.raulshma.jellyplay.feature.admin.users.components.UserRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    onBack: () -> Unit,
    onUserDetail: (String) -> Unit,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()
    val navOffsetPx = LocalFloatingNavOffset.current

    // TV focus-on-launch: focus the first user once the list arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (state.isLoading || state.error != null) 0 else state.users.size,
        tag = "users_init",
    )

    var createName by remember { mutableStateOf("") }
    var createPassword by remember { mutableStateOf("") }

    if (state.showCreateDialog) {
        CreateUserDialog(
            name = createName,
            password = createPassword,
            onNameChange = { createName = it },
            onPasswordChange = { createPassword = it },
            onConfirm = {
                viewModel.createUser(createName.trim(), createPassword.ifBlank { null })
                createName = ""
                createPassword = ""
            },
            onDismiss = {
                viewModel.dismissCreateDialog()
                createName = ""
                createPassword = ""
            },
        )
    }

    if (state.showDeleteDialog) {
        val target = state.selectedUser
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_user_title),
            message = stringResource(R.string.admin_delete_user_body, target?.name ?: ""),
            confirmText = stringResource(R.string.admin_delete),
            dismissText = stringResource(R.string.admin_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            onConfirm = { viewModel.deleteUser() },
            onDismiss = { viewModel.dismissDeleteDialog() },
        )
    }

    val refreshFocusState = rememberTvFocusState()
    JellyPlayScreenScaffold(
        title = stringResource(R.string.admin_users_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            IconButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.then(refreshFocusState.focusModifier).tvFocusIndicator(refreshFocusState, CircleShape),
            ) { Icon(Tabler.Outline.Refresh, contentDescription = stringResource(R.string.admin_refresh)) }
        },
    ) {
        // JellyPlayScreenScaffold has no floatingActionButton slot, so overlay the FAB inside a
        // fillMaxSize Box whose first child is the screen content and second child is the FAB
        // aligned bottom-end. Same visual result as a scaffold FAB slot.
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> ScreenLoadingState(modifier = Modifier.fillMaxSize())
                state.error != null -> {
                    ErrorScreen(
                        message = state.error,
                        onRetry = { viewModel.loadUsers() },
                    )
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                    ) {
                        if (state.users.isEmpty()) {
                            ScreenEmptyState(
                                icon = Tabler.Outline.UserPlus,
                                title = stringResource(R.string.admin_no_users),
                                actionLabel = stringResource(R.string.admin_add_user_cd),
                                onAction = { viewModel.showCreateDialog() },
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .tvFocusRestorer()
                                    .focusRequester(listFocusRequester),
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 8.dp,
                                    bottom = adaptiveInfo.bottomPadding(isTv),
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                itemsIndexed(items = state.users, key = { _, u -> u.id }) { _, user ->
                                    UserRow(
                                        user = user,
                                        isSelf = user.id == state.currentUserId,
                                        avatarUrl = null, // v1: initials fallback. Follow-up to build image URL from primaryImageTag.
                                        onClick = { onUserDetail(user.id) },
                                        onDelete = { viewModel.showDeleteDialog(user) },
                                    )
                                }

                                // TV has no FAB, so the create action lives in the list.
                                if (isTv) {
                                    item(key = "create_user", contentType = "create_user") {
                                        AddListRow(
                                            label = stringResource(R.string.admin_add_user_cd),
                                            icon = Tabler.Outline.UserPlus,
                                            onClick = { viewModel.showCreateDialog() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // FAB sits above the floating nav bar (clears it + the gesture-nav inset) and
            // slides up with the nav bar's hide/show animation via LocalFloatingNavOffset.
            if (!isTv) {
                val addUserFocusState = rememberTvFocusState(focusedScale = 1.05f)
                FloatingActionButton(
                    onClick = { viewModel.showCreateDialog() },
                    shape = ShapeCache.smooth16,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .then(addUserFocusState.focusModifier)
                        .tvFocusIndicator(addUserFocusState, ShapeCache.smooth16)
                        .padding(
                            end = 16.dp,
                            bottom = 16.dp + Dimensions.floatingNavHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        )
                        .offset {
                            val maxOffset = Dimensions.floatingNavHeight.toPx()
                            val yOffset = (-navOffsetPx()).coerceAtMost(maxOffset)
                            IntOffset(x = 0, y = yOffset.toInt())
                        },
                ) {
                    Icon(Tabler.Outline.UserPlus, contentDescription = stringResource(R.string.admin_add_user_cd))
                }
            }
        }
    }
}
