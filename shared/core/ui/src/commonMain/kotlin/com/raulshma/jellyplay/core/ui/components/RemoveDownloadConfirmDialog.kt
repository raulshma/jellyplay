package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import com.raulshma.jellyplay.core.ui.generated.resources.core_remove_download_confirm
import com.raulshma.jellyplay.core.ui.generated.resources.core_remove_download_message
import com.raulshma.jellyplay.core.ui.generated.resources.core_remove_download_title
import org.jetbrains.compose.resources.stringResource

/**
 * The remove-download confirmation every quick-action host shares (library,
 * favorites, search, studio, detail rows — issue #147). Quick-action removal
 * only ever deletes the LOCAL download; the server copy is untouched, and the
 * message says so. Hosts hoist a `MediaItem?` pending-removal state so the
 * dialog survives its card leaving composition while open.
 */
@Composable
fun RemoveDownloadConfirmDialog(
    item: MediaItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(Res.string.core_remove_download_title),
        message = stringResource(Res.string.core_remove_download_message, item.name),
        confirmText = stringResource(Res.string.core_remove_download_confirm),
        dismissText = stringResource(Res.string.core_cancel),
        icon = Tabler.Outline.Trash,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
