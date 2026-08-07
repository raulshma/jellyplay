package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Reusable confirmation dialog for destructive / consequential actions.
 *
 * Mirrors the idiomatic pattern at `SettingsScreen.kt` (sign-out) and
 * `AdminDashboardScreen.kt` (restart/shutdown): an [AlertDialog] whose confirm
 * button is tinted with [MaterialTheme.colorScheme.error] when [isDestructive].
 *
 * ## Two usage styles
 *
 * **1. Deferred action (preferred for one-off flows).** Use [rememberConfirmState]
 * so the confirm lambda is supplied at request time and reaped after the dialog
 * dismisses — no hand-rolled `var show by remember` per call site:
 *
 * ```
 * val confirm = rememberConfirmState()
 * IconButton(onClick = { confirm.request { viewModel.removeServer(id) } }) { ... }
 * confirm.ConfirmDialog(
 * title = "Remove server?",
 * message = "This removes the server and all saved users on it.",
 * confirmText = "Remove",
 * )
 * ```
 *
 * **2. Explicit gate.** Callers that already own a `Boolean` gate can render the
 * dialog directly:
 *
 * ```
 * if (showConfirm) {
 * ConfirmDialog(
 * title = "Remove server?",
 * message = "This removes the server and all saved users on it.",
 * confirmText = "Remove",
 * dismissText = "Cancel",
 * onConfirm = { viewModel.removeServer(id) },
 * onDismiss = { showConfirm = false },
 * )
 * }
 * ```
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
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

/**
 * Holds a single pending confirmation so a screen can defer the destructive
 * lambda until the user actually confirms.
 *
 * Why a holder instead of a plain `Boolean`: a `Boolean` gate forces every call
 * site to also stash the target id/lambda somewhere reachable from the dialog's
 * `onConfirm`. [request] captures the lambda inline, and [dismiss] clears it,
 * so there is nothing extra to track.
 *
 * Render via [ConfirmDialog]; the convenience extension renders it only while
 * a request is pending.
 */
@Stable
class ConfirmState internal constructor() {
    internal var pending: (() -> Unit)? by mutableStateOf(null)

    /** Whether a confirmation is currently awaiting the user. */
    val isVisible: Boolean get() = pending != null

    /**
     * Show the dialog; [onConfirm] runs only if the user confirms, then is
     * cleared regardless of the outcome.
     */
    fun request(onConfirm: () -> Unit) {
        pending = onConfirm
    }

    /** Dismiss the pending confirmation without running its action. */
    fun dismiss() {
        pending = null
    }
}

/**
 * Remember a [ConfirmState] scoped to the composition.
 */
@Composable
fun rememberConfirmState(): ConfirmState = remember { ConfirmState() }

/**
 * Render the dialog backing [state] while a request is pending.
 *
 * The confirm text should describe the action ("Remove", "Delete"); the dismiss
 * text is usually [com.raulshma.jellyplay.core.ui.R.string.core_cancel].
 */
@Composable
fun ConfirmState.ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    isDestructive: Boolean = true,
) {
    val onConfirm = pending ?: return
    ConfirmDialog(
        title = title,
        message = message,
        confirmText = confirmText,
        dismissText = dismissText,
        onConfirm = onConfirm,
        onDismiss = { dismiss() },
        isDestructive = isDestructive,
    )
}
