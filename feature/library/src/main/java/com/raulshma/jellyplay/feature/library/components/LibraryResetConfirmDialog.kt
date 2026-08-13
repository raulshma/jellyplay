package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.feature.library.R

/**
 * Confirmation shown before the top-bar Reset pill resets the library to
 * defaults. Explains what the reset clears and offers a persisted "Don't show
 * again" opt-out — once checked and confirmed, future reset taps reset
 * immediately (the ViewModel still shows this dialog only while
 * `confirmLibraryReset` is enabled).
 */
@Composable
fun LibraryResetConfirmDialog(
    onConfirm: (dontShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    ConfirmDialog(
        title = stringResource(R.string.library_reset_confirm_title),
        message = stringResource(R.string.library_reset_confirm_message),
        confirmText = stringResource(R.string.library_reset_all),
        dismissText = stringResource(com.raulshma.jellyplay.core.ui.R.string.core_cancel),
        tone = ConfirmTone.DESTRUCTIVE,
        onConfirm = { onConfirm(dontShowAgain) },
        onDismiss = onDismiss,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickable { dontShowAgain = !dontShowAgain },
            ) {
                Checkbox(
                    checked = dontShowAgain,
                    onCheckedChange = { dontShowAgain = it },
                )
                Text(
                    text = stringResource(R.string.library_reset_dont_show_again),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
    )
}
