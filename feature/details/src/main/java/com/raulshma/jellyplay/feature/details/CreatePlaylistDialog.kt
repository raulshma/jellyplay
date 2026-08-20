package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus

/**
 * Create-and-add playlist dialog for the detail-screen Add-to-Playlist flow.
 * Mirrors the music feature's `PlaylistNameDialog` (name + optional
 * description) but lives here so it can resolve the detail feature's strings
 * and reuse the TV focus helper.
 *
 * On confirm the VM creates a playlist seeded with the current item and emits
 * an "Added to {name}" snackbar; the dialog is dismissed by the VM toggling
 * `showCreatePlaylistDialog` once the create call resolves.
 */
@Composable
internal fun CreatePlaylistDialog(
    isLoading: Boolean,
    onConfirm: (name: String, overview: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var overview by remember { mutableStateOf("") }
    val nameFocusRequester = remember { FocusRequester() }
    RequestOrRestoreFocus(nameFocusRequester, "create_playlist_name")

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        // decorFitsSystemWindows=false dispatches IME insets into the dialog so
        // imePadding can lift fields + buttons above the soft keyboard.
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier.imePadding(),
        title = { Text(stringResource(R.string.detail_playlist_new_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.detail_playlist_name_label)) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = overview,
                    onValueChange = { overview = it },
                    label = { Text(stringResource(R.string.detail_playlist_desc_label)) },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, overview) },
                enabled = !isLoading && name.isNotBlank(),
            ) {
                if (isLoading) {
                    JellyPlayLoadingIndicator(
                        modifier = Modifier.height(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(stringResource(R.string.detail_playlist_create))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.detail_cancel))
            }
        },
    )
}
