package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Reusable confirmation dialog for destructive / consequential actions.
 *
 * Mirrors the idiomatic pattern at `SettingsScreen.kt` (sign-out) and
 * `AdminDashboardScreen.kt` (restart/shutdown): an [AlertDialog] whose confirm
 * button is tinted with [MaterialTheme.colorScheme.error] when [isDestructive].
 *
 * Callers typically gate it on local state:
 *
 * ```
 * var showConfirm by remember { mutableStateOf(false) }
 * IconButton(onClick = { showConfirm = true }) { ... }
 * if (showConfirm) {
 *     ConfirmDialog(
 *         title = "Remove server?",
 *         message = "This removes the server and all saved users on it.",
 *         confirmText = "Remove",
 *         onConfirm = { viewModel.removeServer(id) },
 *         onDismiss = { showConfirm = false },
 *     )
 * }
 * ```
 *
 * For one-off flows, use [rememberConfirmState] so the confirm lambda can be
 * deferred until the user actually confirms (see `rememberConfirmState` KDoc).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    isDestructive: Boolean = true,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(
                    confirmText,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        },
    )
}
