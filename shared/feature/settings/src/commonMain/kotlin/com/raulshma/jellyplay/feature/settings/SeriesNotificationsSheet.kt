package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.size.Size
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.BellOff
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_series_notifications_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_series_notifications_remove_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_series_notifications_restore_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_series_notifications_restore_all_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_series_notifications_title

/**
 * D12: the "Series notifications" management sheet — the notification
 * settings screen's per-series counterpart of its per-library picker
 * (opened the same way, a [TvSafeSheet] off a dialog state), with the row
 * anatomy lifted from feature/home's NextUpExcludedScreen: poster + name +
 * year rows, a per-row remove (bell-off), and Restore-all behind a
 * confirmation. Only ENABLED series opt-ins list (the store's opt-out
 * removes the entry, so the map keys are the opted-in set).
 *
 * TV-safe: every interactive surface is D-pad focusable (the same
 * rememberTvFocusState + tvFocusIndicator chassis the other drill-ins use).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeriesNotificationsSheet(
    onDismiss: () -> Unit,
    viewModel: NotificationSettingsViewModel,
) {
    val state by viewModel.seriesNotifications.collectAsStateWithLifecycle()
    var showRestoreAllDialog by remember { mutableStateOf(false) }

    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.settings_series_notifications_title),
                icon = Tabler.Outline.BellOff,
            )
            Spacer(Modifier.height(4.dp))
            if (state.series.isEmpty()) {
                Text(
                    text = stringResource(Res.string.settings_series_notifications_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                ) {
                    items(
                        count = state.series.size,
                        key = { index -> state.series[index].id },
                    ) { index ->
                        val series = state.series[index]
                        // Poster URL assembly is remembered per series id so a
                        // sheet-wide recomposition (a removal, state churn)
                        // doesn't rebuild the URL for every visible row.
                        val posterUrl = remember(series.id) { viewModel.seriesPosterUrl(series.id) }
                        SeriesNotificationRow(
                            row = series,
                            index = index,
                            count = state.series.size,
                            posterUrl = posterUrl,
                            onRemove = { viewModel.removeSeriesNotifications(series.id) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showRestoreAllDialog = true },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 24.dp),
                ) { Text(stringResource(Res.string.settings_series_notifications_restore_all)) }
            }
        }
    }

    if (showRestoreAllDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_series_notifications_restore_all),
            message = stringResource(
                Res.string.settings_series_notifications_restore_all_message,
                state.series.size,
            ),
            confirmText = stringResource(Res.string.settings_series_notifications_restore_all),
            onConfirm = {
                viewModel.removeAllSeriesNotifications()
                showRestoreAllDialog = false
            },
            onDismiss = { showRestoreAllDialog = false },
        )
    }
}

/**
 * One opted-in series row (the NextUpExcludedScreen row anatomy): poster,
 * name + year, and the remove action. The row itself is not clickable (the
 * remove button is the only action — the list has no drill-in), so D-pad
 * focus lands on the button and the row paints its TV focus ring from the
 * same focus state.
 */
@Composable
private fun SeriesNotificationRow(
    row: NotificationSettingsViewModel.SeriesNotificationRow,
    index: Int,
    count: Int,
    posterUrl: String,
    onRemove: () -> Unit,
) {
    val shape = expressiveListShape(index, count, innerRadius = 0.dp)
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    val item = row.item
    // A not-yet-hydrated (or vanished) series still shows and still removes
    // by id — the NextUpExcludedScreen "unknown" fallback.
    val displayName = item?.name ?: row.id

    ListItem(
        headlineContent = {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused),
            )
        },
        supportingContent = {
            Text(
                text = item?.year?.toString() ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 63.dp)
                    .clip(ShapeCache.smooth8),
            ) {
                MediaImage(
                    url = posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    size = Size(128, 192),
                )
            }
        },
        trailingContent = {
            val removeFocusState = rememberTvFocusState(focusedScale = 1.12f)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(removeFocusState.focusModifier)
                    .tvFocusIndicator(removeFocusState, RoundedCornerShape(8.dp))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.BellOff,
                    contentDescription = stringResource(
                        Res.string.settings_series_notifications_remove_cd,
                        displayName,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape),
    )
}
