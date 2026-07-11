package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Storage confirmation dialog shown before a single-item download.
 *
 * Reports the estimated file size and the currently-available storage on the
 * destination volume, disabling the confirm button when there is not enough
 * room.
 *
 * Extracted verbatim from the former `DetailContent` in `MediaDetailScreen.kt`,
 * except the available-storage probe now delegates to
 * [DetailViewModel.getAvailableStorageBytes] via [availableStorageProvider]
 * instead of inlining `StatFs`/`Environment` in the composable.
 */
@Composable
internal fun DownloadConfirmationDialog(
    fileSize: Long?,
    isAudio: Boolean,
    availableStorageProvider: suspend (Boolean) -> Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val availableBytes by produceState(initialValue = 0L, isAudio) {
        value = availableStorageProvider(isAudio)
    }
    val fileSizeText = fileSize?.let { size ->
        when {
            size >= 1_000_000_000 -> stringResource(R.string.detail_size_gb, size / 1_000_000_000.0)
            size >= 1_000_000 -> stringResource(R.string.detail_size_mb, size / 1_000_000.0)
            size >= 1_000 -> stringResource(R.string.detail_size_kb, size / 1_000.0)
            else -> stringResource(R.string.detail_size_b, size)
        }
    } ?: stringResource(R.string.detail_size_unknown)
    val availableText = when {
        availableBytes >= 1_000_000_000 -> stringResource(R.string.detail_size_gb, availableBytes / 1_000_000_000.0)
        availableBytes >= 1_000_000 -> stringResource(R.string.detail_size_mb, availableBytes / 1_000_000.0)
        else -> stringResource(R.string.detail_size_kb, availableBytes / 1_000_000.0)
    }
    val enoughSpace = fileSize == null || fileSize <= availableBytes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_download_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.detail_estimated_size, fileSizeText))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.detail_available_storage, availableText))
                if (!enoughSpace) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.detail_not_enough_storage),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = enoughSpace,
            ) {
                Text(stringResource(R.string.detail_download_dialog_title))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.detail_cancel))
            }
        },
    )
}
