package com.raulshma.jellyplay.update

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Download
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.MarkdownText
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Bottom sheet (mobile) / dialog (TV) driving the self-update flow. Renders
 * release notes when an update is available, a progress bar while the APK
 * downloads, and an Install button once it's ready.
 *
 * The sheet is only shown while [state] is not [UpdateState.Idle]; the caller
 * gates visibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateSheet(
    state: UpdateState,
    autoDownloadEnabled: Boolean,
    onAutoDownloadToggle: (Boolean) -> Unit,
    onDownload: (AppUpdateInfo) -> Unit,
    onInstall: (Intent) -> Unit,
    onRedownload: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    buildInstallIntent: () -> Intent?,
) {
    // No active update flow — nothing to render.
    if (state is UpdateState.Idle) return

    // Whether the collapsible release-notes view is open. Hoisted out here so
    // it can also drive the sheet's height: when expanded the sheet grows to
    // ~90% of the screen so the notes have room to breathe instead of being
    // crammed into the sheet's compact default.
    var showNotes by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TvSafeSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Size the *content* (not the sheet surface) to ~90% of the screen
                // when notes are open. ModalBottomSheet wraps its surface to the
                // content's height, so sizing the content keeps the surface and
                // content the same height — no empty (dark) gap underneath. The
                // notes region uses weight(1f) to absorb the remaining space.
                .then(if (showNotes) Modifier.fillMaxHeight(0.9f) else Modifier)
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 32.dp),
        ) {
            when (state) {
                is UpdateState.Checking -> CheckingContent()
                is UpdateState.NoUpdate -> NoUpdateContent(
                    info = state.info,
                    showNotes = showNotes,
                    onToggleNotes = { showNotes = !showNotes },
                    onDismiss = onDismiss,
                )
                is UpdateState.UpdateAvailable -> UpdateAvailableContent(
                    info = state.info,
                    autoDownloadEnabled = autoDownloadEnabled,
                    onAutoDownloadToggle = onAutoDownloadToggle,
                    onDownload = { onDownload(state.info) },
                    onDismiss = onDismiss,
                )
                is UpdateState.Downloading -> DownloadingContent(
                    fraction = state.fraction,
                    bytesRead = state.bytesRead,
                    total = state.total,
                    onCancel = onCancel,
                )
                is UpdateState.Downloaded -> DownloadedContent(
                    info = state.info,
                    onInstall = { buildInstallIntent()?.let(onInstall) },
                    onRedownload = onRedownload,
                    onDismiss = onDismiss,
                )
                is UpdateState.Error -> ErrorContent(
                    message = state.message,
                    onDismiss = onDismiss,
                )
                UpdateState.Idle -> Unit // handled above
            }
        }
    }
}

@Composable
private fun CheckingContent() {
    Text(
        text = stringResource(R.string.update_checking),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun ColumnScope.NoUpdateContent(
    info: AppUpdateInfo,
    showNotes: Boolean,
    onToggleNotes: () -> Unit,
    onDismiss: () -> Unit,
) {
    Text(
        text = stringResource(R.string.update_up_to_date_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.update_up_to_date_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Let the user inspect the current version's release notes even though no
    // update is pending — collapsible so the sheet stays compact by default.
    if (info.releaseNotes.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        val labelRes = if (showNotes) R.string.update_hide_notes else R.string.update_view_notes
        TextButton(
            onClick = onToggleNotes,
            modifier = Modifier.focusIndicator(),
        ) {
            Text(stringResource(labelRes))
        }
        if (showNotes) {
            // Grows to fill the expanded (90% height) sheet rather than a fixed
            // cap, so the markdown region takes whatever vertical space remains
            // after the header and the buttons row below it.
            MarkdownText(
                text = info.releaseNotes,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    ButtonsRow {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.focusIndicator(),
        ) { Text(stringResource(R.string.update_close)) }
    }
}

@Composable
private fun UpdateAvailableContent(
    info: AppUpdateInfo,
    autoDownloadEnabled: Boolean,
    onAutoDownloadToggle: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    Text(
        text = stringResource(R.string.update_available_title, info.latestVersion),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    if (info.downloadAssetName != null || info.releaseSize > 0L) {
        val sizePart = if (info.releaseSize > 0L) " · ${info.releaseSize.formatBytes()}" else ""
        Text(
            text = (info.downloadAssetName ?: "") + sizePart,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp),
    ) {
        MarkdownText(
            text = info.releaseNotes.ifBlank { stringResource(R.string.update_no_notes) },
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
    Spacer(Modifier.height(12.dp))
    val noApk = info.downloadAssetUrl == null
    // Lets the user flip the auto-download preference right where they're
    // deciding what to do with the update. Disabled when there's no matching
    // APK for this device (nothing to auto-download).
    AutoDownloadCompactToggle(
        checked = autoDownloadEnabled,
        enabled = !noApk,
        onCheckedChange = onAutoDownloadToggle,
    )
    Spacer(Modifier.height(16.dp))
    ButtonsRow {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.focusIndicator(),
        ) { Text(stringResource(R.string.update_later)) }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onDownload,
            enabled = !noApk,
            modifier = Modifier.focusIndicator(),
        ) {
            Text(stringResource(if (noApk) R.string.update_no_matching_apk else R.string.update_download))
        }
    }
}

@Composable
private fun AutoDownloadCompactToggle(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val focusState = rememberTvFocusState(focusedScale = 1.02f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(
                if (checked && enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            )
            .then(if (enabled) focusState.focusModifier else Modifier)
            .then(if (enabled) Modifier.tvFocusIndicator(focusState, ShapeCache.smooth16) else Modifier)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.Download,
                contentDescription = null,
                tint = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.update_auto_download_label),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
                ),
                color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                else if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = if (isTv || !enabled) null else onCheckedChange,
        )
    }
}

@Composable
private fun DownloadingContent(
    fraction: Float,
    bytesRead: Long,
    total: Long,
    onCancel: () -> Unit,
) {
    Text(
        text = stringResource(R.string.update_downloading),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    val percent = if (fraction > 0f) (fraction * 100).toInt() else null
    val summary = when {
        total > 0L && percent != null ->
            "${bytesRead.formatBytes()} / ${total.formatBytes()} · $percent%"
        total > 0L -> "${bytesRead.formatBytes()} / ${total.formatBytes()}"
        else -> bytesRead.formatBytes()
    }
    Text(
        text = summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    JellyPlayLinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .height(4.dp),
    )
    Spacer(Modifier.height(16.dp))
    ButtonsRow {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.focusIndicator(),
        ) { Text(stringResource(R.string.update_cancel)) }
    }
}

@Composable
private fun DownloadedContent(
    info: AppUpdateInfo,
    onInstall: () -> Unit,
    onRedownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    Text(
        text = stringResource(R.string.update_ready_title, info.latestVersion),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.update_ready_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    ButtonsRow {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.focusIndicator(),
        ) { Text(stringResource(R.string.update_later)) }
        Spacer(Modifier.width(8.dp))
        // Re-fetch the APK even though one is already on disk (e.g. the user
        // suspects it's corrupt, or wants the latest patch for the same
        // version). Overwrites the existing file + sidecar.
        OutlinedButton(
            onClick = onRedownload,
            modifier = Modifier.focusIndicator(),
        ) {
            Text(stringResource(R.string.update_download_again))
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onInstall,
            modifier = Modifier.focusIndicator(),
        ) { Text(stringResource(R.string.update_install)) }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.update_failed_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    ButtonsRow {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.focusIndicator(),
        ) { Text(stringResource(R.string.update_close)) }
    }
}

@Composable
private fun ButtonsRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
