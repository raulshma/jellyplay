package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_cancel
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_delete
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_delete_download_message
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_delete_download_title
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_delete_downloads_message
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_delete_downloads_title
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_frees_up_sentence

@Composable
internal fun DownloadsDeleteDialogs(
    viewModel: DownloadsViewModel,
    selectedItems: List<DownloadItem>,
    selectedIds: Set<String>,
) {
    viewModel.pendingDelete.item?.let { item ->
        ConfirmDialog(
            title = stringResource(Res.string.downloads_delete_download_title),
            message = stringResource(Res.string.downloads_delete_download_message, item.name, item.totalSizeBytes.formatBytes()),
            confirmText = stringResource(Res.string.downloads_delete),
            dismissText = stringResource(Res.string.downloads_cancel),
            icon = Tabler.Outline.Trash,
            tone = ConfirmTone.DESTRUCTIVE,
            onConfirm = {
                // Settle: confirm clears the machine synchronously (the arm
                // that makes the old never-cleared bug impossible), then the
                // delete fires as a fire-and-forget VM call.
                val target = viewModel.pendingDelete.confirm() ?: return@ConfirmDialog
                viewModel.deleteDownload(target)
            },
            onDismiss = { viewModel.pendingDelete.dismiss(inFlight = false) },
        )
    }

    if (viewModel.pendingBulkDelete.isPending) {
        val count = selectedIds.size
        val freedBytes = selectedItems.sumOf { it.totalSizeBytes }
        ConfirmDialog(
            title = stringResource(Res.string.downloads_delete_downloads_title),
            message = pluralStringResource(Res.plurals.downloads_delete_downloads_message, count, count) +
                if (freedBytes > 0) stringResource(Res.string.downloads_frees_up_sentence, freedBytes.formatBytes()) else "",
            confirmText = stringResource(Res.string.downloads_delete),
            dismissText = stringResource(Res.string.downloads_cancel),
            icon = Tabler.Outline.Trash,
            tone = ConfirmTone.DESTRUCTIVE,
            onConfirm = {
                // Settle: same synchronous arm as the single-item machine.
                viewModel.pendingBulkDelete.confirm() ?: return@ConfirmDialog
                viewModel.applyBulkAction(DownloadBulkAction.DELETE, DownloadActionScope.Selected)
            },
            onDismiss = { viewModel.pendingBulkDelete.dismiss(inFlight = false) },
        )
    }
}
