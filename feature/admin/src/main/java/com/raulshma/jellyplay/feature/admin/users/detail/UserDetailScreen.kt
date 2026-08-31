package com.raulshma.jellyplay.feature.admin.users.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.PasswordTextField
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.StaggeredSection
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.R
import com.raulshma.jellyplay.feature.admin.users.detail.components.AccessTab
import com.raulshma.jellyplay.feature.admin.users.detail.components.AccountTab
import com.raulshma.jellyplay.feature.admin.users.detail.components.ParentalControlTab
import com.raulshma.jellyplay.feature.admin.users.detail.components.ProfileTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: UserDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(userId) { viewModel.loadUser(userId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backgroundColorState = rememberScreenBackgroundColorState()
    val isTv = LocalTvMode.current

    // TV focus-on-launch: land on the first editor tab once the user loads so
    // D-pad input reaches the form instead of the navigation drawer.
    val initialFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = initialFocusRequester,
        itemCount = if (state.isLoading || state.error != null) 0 else 1,
        tag = "user_detail_init",
    )

    var newPassword by remember { mutableStateOf("") }

    val user = state.user
    val effectivePolicy = state.editedPolicy ?: user?.policy
    val effectiveName = state.editedName ?: user?.name ?: ""

    if (state.showDeleteDialog && user != null) {
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_user_title),
            message = stringResource(R.string.admin_delete_user_body, user.name),
            confirmText = stringResource(R.string.admin_delete),
            dismissText = stringResource(R.string.admin_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            onConfirm = { viewModel.deleteUser(onDone = onBack) },
            onDismiss = { viewModel.dismissDeleteDialog() },
        )
    }

    if (state.showPasswordDialog) {
        val confirmFocusRequester = remember { FocusRequester() }
        LaunchedEffect(isTv) {
            if (isTv) confirmFocusRequester.tryRequestFocus("user_password_confirm")
        }
        ImeAlertDialog(
            onDismissRequest = {
                viewModel.dismissPasswordDialog()
                newPassword = ""
            },
            title = { Text(if (newPassword.isBlank()) stringResource(R.string.admin_reset_password) else stringResource(R.string.admin_set_password)) },
            text = {
                Column {
                    PasswordTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.admin_new_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        contentType = ContentType.NewPassword,
                    )
                    if (newPassword.isBlank()) {
                        Text(
                            stringResource(R.string.admin_leave_blank_password),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updatePassword(newPassword.ifBlank { null })
                        newPassword = ""
                    },
                    modifier = Modifier.focusRequester(confirmFocusRequester),
                ) { Text(if (newPassword.isBlank()) stringResource(R.string.admin_reset) else stringResource(R.string.admin_set)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissPasswordDialog()
                    newPassword = ""
                }) { Text(stringResource(R.string.admin_cancel)) }
            },
        )
    }

    JellyPlayScreenScaffold(
        title = user?.name ?: stringResource(R.string.admin_user_fallback),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) {
        when {
            state.isLoading -> ScreenLoadingState(modifier = Modifier.fillMaxSize())
            state.error != null -> ErrorScreen(
                message = state.error ?: stringResource(R.string.admin_unknown_error),
                onRetry = { viewModel.loadUser(userId) },
                modifier = Modifier.fillMaxSize(),
            )
            user != null && effectivePolicy != null -> {
                // JellyPlayScreenScaffold's content lambda renders into a Column. Wrap
                // in our own Box so the sticky BottomAppBar can align BottomCenter.
                val tabs = UserEditTab.entries
                val pagerState = rememberPagerState(pageCount = { tabs.size })
                val scope = rememberCoroutineScope()

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Transient operation failures (e.g. a failed delete /
                        // save) surface here; the detail screen stays put so
                        // the user can retry instead of being navigated away.
                        state.saveError?.let { saveError ->
                            SaveErrorBanner(
                                message = saveError,
                                onDismiss = { viewModel.consumeSaveError() },
                            )
                        }
                        UserEditTabBar(
                            selectedTabIndex = pagerState.currentPage,
                            profileCount = viewModel.profileDirtyCount(),
                            accessCount = viewModel.accessDirtyCount(),
                            parentalCount = viewModel.parentalDirtyCount(),
                            onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            backgroundColor = backgroundColorState.value,
                            firstTabFocusRequester = initialFocusRequester,
                        )

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (isTv) Modifier.tvFocusRestorer().focusGroup() else Modifier),
                        ) { page ->
                            // Lazy-load whatever aux data this tab needs, once.
                            LaunchedEffect(tabs[page]) { viewModel.loadAuxFor(tabs[page]) }
                            // Staggered fade-and-rise reveal so the active tab's
                            // content animates in (matches other admin detail screens).
                            StaggeredSection(delayIndex = page) {
                                when (tabs[page]) {
                                    UserEditTab.PROFILE -> ProfileTab(
                                        name = effectiveName,
                                        policy = effectivePolicy,
                                        isSelf = state.isSelf,
                                        isLastAdmin = state.isLastAdmin,
                                        onNameChange = viewModel::editName,
                                        onPolicyChange = viewModel::onPolicyChange,
                                    )
                                    UserEditTab.ACCESS -> AccessTab(
                                        policy = effectivePolicy,
                                        libraries = state.libraries,
                                        devices = state.devices,
                                        channels = state.channels,
                                        onPolicyChange = viewModel::onPolicyChange,
                                    )
                                    UserEditTab.PARENTAL -> ParentalControlTab(
                                        policy = effectivePolicy,
                                        parentalRatings = state.parentalRatings,
                                        tags = state.tags,
                                        onPolicyChange = viewModel::onPolicyChange,
                                    )
                                    UserEditTab.ACCOUNT -> AccountTab(
                                        isSelf = state.isSelf,
                                        onChangePassword = viewModel::showPasswordDialog,
                                        onDelete = viewModel::showDeleteDialog,
                                    )
                                }
                            }
                        }
                    }

                    // Sticky Save/Discard bar (unchanged logic). imePadding keeps it
                    // above the soft keyboard while editing tab fields.
                    AnimatedVisibility(
                        visible = state.isDirty && !state.isSaving,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .imePadding(),
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        BottomAppBar(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = viewModel::discard, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.admin_discard)) }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = viewModel::save, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(R.string.admin_save_changes)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UserEditTabBar(
    selectedTabIndex: Int,
    profileCount: Int,
    accessCount: Int,
    parentalCount: Int,
    onTabSelected: (Int) -> Unit,
    backgroundColor: androidx.compose.ui.graphics.Color,
    firstTabFocusRequester: FocusRequester? = null,
) {
    val labels = listOf(R.string.admin_tab_profile, R.string.admin_tab_access, R.string.admin_tab_parental, R.string.admin_tab_account)
    val counts = listOf(profileCount, accessCount, parentalCount, 0)

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = backgroundColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 12.dp,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(
                    selectedTabIndex = selectedTabIndex,
                    matchContentSize = true,
                ),
                width = androidx.compose.ui.unit.Dp.Unspecified,
                height = 6.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = ShapeCache.smoothPill,
            )
        },
        divider = {},
    ) {
        labels.forEachIndexed { index, label ->
            val count = counts[index]
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                modifier = if (index == 0 && firstTabFocusRequester != null) {
                    Modifier.focusRequester(firstTabFocusRequester)
                } else {
                    Modifier
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    BadgedBox(
                        badge = { if (count > 0) Badge { Text("$count") } },
                    ) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                },
            )
        }
    }
}

/** Inline error banner for failed save / delete / password operations. */
@Composable
private fun SaveErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = ShapeCache.smooth16,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Tabler.Outline.AlertTriangle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_dismiss)) }
        }
    }
}
