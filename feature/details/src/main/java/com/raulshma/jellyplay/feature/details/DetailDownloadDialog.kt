package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone

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

    ConfirmDialog(
        title = stringResource(R.string.detail_download_dialog_title),
        message = null,
        confirmText = stringResource(R.string.detail_download_dialog_title),
        dismissText = stringResource(R.string.detail_cancel),
        tone = ConfirmTone.NEUTRAL,
        confirmEnabled = enoughSpace,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        content = {
            Text(
                text = stringResource(R.string.detail_estimated_size, fileSizeText),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.detail_available_storage, availableText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!enoughSpace) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.detail_not_enough_storage),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}
