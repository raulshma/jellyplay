package com.raulshma.jellyplay.feature.admin.users.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.raulshma.jellyplay.feature.admin.users.detail.components.AccessSection
import com.raulshma.jellyplay.feature.admin.users.detail.components.DangerSection
import com.raulshma.jellyplay.feature.admin.users.detail.components.GeneralSection
import com.raulshma.jellyplay.feature.admin.users.detail.components.LimitsSection
import com.raulshma.jellyplay.feature.admin.users.detail.components.PasswordSection
import com.raulshma.jellyplay.feature.admin.users.detail.components.PermissionsSection

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
                // JellyPlayScreenScaffold's content lambda renders into a Column, so to overlay
                // the sticky BottomAppBar we wrap the content branch in our own Box first. The
                // scrollable Column is child 1 and the AnimatedVisibility{BottomAppBar} is
                // child 2 with Modifier.align(Alignment.BottomCenter). This Box is ours, so
                // align works as expected.
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        var i = 0
                        StaggeredSection(delayIndex = i++) {
                            GeneralSection(
                                name = effectiveName,
                                policy = effectivePolicy,
                                isSelf = state.isSelf,
                                isLastAdmin = state.isLastAdmin,
                                onNameChange = viewModel::editName,
                                onPolicyChange = viewModel::onPolicyChange,
                            )
                        }
                        StaggeredSection(delayIndex = i++) {
                            AccessSection(
                                policy = effectivePolicy,
                                libraries = state.libraries,
                                onPolicyChange = viewModel::onPolicyChange,
                            )
                        }
                        StaggeredSection(delayIndex = i++) {
                            PermissionsSection(policy = effectivePolicy, onPolicyChange = viewModel::onPolicyChange)
                        }
                        StaggeredSection(delayIndex = i++) {
                            LimitsSection(policy = effectivePolicy, onPolicyChange = viewModel::onPolicyChange)
                        }
                        StaggeredSection(delayIndex = i++) {
                            PasswordSection(onChangePassword = viewModel::showPasswordDialog)
                        }
                        StaggeredSection(delayIndex = i) {
                            DangerSection(isSelf = state.isSelf, onDelete = viewModel::showDeleteDialog)
                        }
                    }
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
