package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/**
 * [AlertDialog] for dialogs containing text fields. decorFitsSystemWindows=false
 * dispatches IME insets into the dialog so imePadding can lift fields + buttons
 * above the soft keyboard.
 */
@Composable
fun ImeAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.imePadding(),
        dismissButton = dismissButton,
        title = title,
        text = text,
        properties = DialogProperties(decorFitsSystemWindows = false),
    )
}
