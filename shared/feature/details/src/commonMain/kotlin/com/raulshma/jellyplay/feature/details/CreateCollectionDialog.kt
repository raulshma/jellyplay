package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cancel
import com.raulshma.jellyplay.feature.details.generated.resources.detail_collection_create
import com.raulshma.jellyplay.feature.details.generated.resources.detail_collection_name_label
import com.raulshma.jellyplay.feature.details.generated.resources.detail_collection_new_title
import org.jetbrains.compose.resources.stringResource

/**
 * Create-and-add collection dialog for the detail-screen Add-to-Collection
 * flow. A name-only mirror of [CreatePlaylistDialog]: Jellyfin's
 * `createCollection` endpoint takes no overview, so this dialog collects a
 * name alone.
 *
 * On confirm the VM creates a collection seeded with the current item and
 * emits a "Created {name}" snackbar; the dialog is dismissed by the VM
 * toggling `showCreateCollectionDialog` once the create call resolves.
 */
@Composable
internal fun CreateCollectionDialog(
    isLoading: Boolean,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val nameFocusRequester = remember { FocusRequester() }
    RequestOrRestoreFocus(nameFocusRequester, "create_collection_name")

    ImeAlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(Res.string.detail_collection_new_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.detail_collection_name_label)) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = !isLoading && name.isNotBlank(),
            ) {
                if (isLoading) {
                    JellyPlayLoadingIndicator(
                        modifier = Modifier.height(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(stringResource(Res.string.detail_collection_create))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(Res.string.detail_cancel))
            }
        },
    )
}
