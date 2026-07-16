package com.raulshma.jellyplay.feature.admin.users.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.StaggeredSection
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
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
    val backgroundColor = rememberScreenBackgroundColor()

    var newPassword by remember { mutableStateOf("") }

    val user = state.user
    val effectivePolicy = state.editedPolicy ?: user?.policy
    val effectiveName = state.editedName ?: user?.name ?: ""

    if (state.showDeleteDialog && user != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Delete User") },
            text = { Text("Delete \"${user.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteUser(onDone = onBack) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissDeleteDialog() }) { Text("Cancel") } },
        )
    }

    if (state.showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissPasswordDialog()
                newPassword = ""
            },
            title = { Text(if (newPassword.isBlank()) "Reset password" else "Set password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (newPassword.isBlank()) {
                        Text(
                            "Leave blank to reset/clear the password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updatePassword(newPassword.ifBlank { null })
                    newPassword = ""
                }) { Text(if (newPassword.isBlank()) "Reset" else "Set") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissPasswordDialog()
                    newPassword = ""
                }) { Text("Cancel") }
            },
        )
    }

    JellyPlayScreenScaffold(
        title = user?.name ?: "User",
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) {
        when {
            state.isLoading -> ScreenLoadingState(modifier = Modifier.fillMaxSize())
            state.error != null -> ErrorScreen(
                message = state.error ?: "Unknown error",
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
                        UserEditTabBar(
                            selectedTabIndex = pagerState.currentPage,
                            profileCount = viewModel.profileDirtyCount(),
                            accessCount = viewModel.accessDirtyCount(),
                            parentalCount = viewModel.parentalDirtyCount(),
                            onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            backgroundColor = backgroundColor,
                        )

                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
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

                    // Sticky Save/Discard bar (unchanged logic).
                    AnimatedVisibility(
                        visible = state.isDirty && !state.isSaving,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        BottomAppBar(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = viewModel::discard, modifier = Modifier.padding(start = 8.dp)) { Text("Discard") }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = viewModel::save, modifier = Modifier.padding(end = 8.dp)) { Text("Save changes") }
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
) {
    val labels = listOf("Profile", "Access", "Parental", "Account")
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
                shape = RoundedCornerShape(50),
            )
        },
        divider = {},
    ) {
        labels.forEachIndexed { index, label ->
            val count = counts[index]
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    BadgedBox(
                        badge = { if (count > 0) Badge { Text("$count") } },
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                },
            )
        }
    }
}
