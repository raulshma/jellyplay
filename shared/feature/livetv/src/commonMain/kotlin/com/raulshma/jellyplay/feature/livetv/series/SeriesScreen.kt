package com.raulshma.jellyplay.feature.livetv.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.raulshma.jellyplay.core.ui.components.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.livetv.generated.resources.Res
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_action_close
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_cancel_series
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_no_series_recordings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val bottomPad = adaptiveInfo.bottomPadding(isTv)

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = uiState.seriesTimers.size,
        tag = "series_init",
    )

    when {
        uiState.isLoading && uiState.seriesTimers.isEmpty() -> {
            ScreenLoadingState(modifier = Modifier.fillMaxSize())
        }
        uiState.error != null && uiState.seriesTimers.isEmpty() -> {
            ErrorScreen(message = uiState.error!!, onRetry = { viewModel.load() })
        }
        uiState.seriesTimers.isEmpty() -> {
            ScreenEmptyState(
                icon = Tabler.Outline.CalendarRepeat,
                title = stringResource(Res.string.livetv_no_series_recordings),
            )
        }
        else -> {
            PullToRefreshBox(isRefreshing = uiState.isLoading, onRefresh = { viewModel.load() }) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup()
                        .tvFocusRestorer()
                        .focusRequester(focusRequester),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomPad),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    items(
                        items = uiState.seriesTimers,
                        key = { it.id },
                        contentType = { "series_timer" },
                    ) { timer ->
                        SeriesTimerCard(timer = timer, contentPad = contentPad) { viewModel.showDetail(timer) }
                    }
                }
            }
        }
    }

    uiState.selectedTimer?.let { timer ->
        SeriesDetailDialog(
            timer = timer,
            onCancel = { viewModel.cancelSeries(timer.id) },
            onDismiss = { viewModel.dismissDetail() },
        )
    }
}

@Composable
private fun SeriesTimerCard(
    timer: DvrSeriesTimer,
    contentPad: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = contentPad, vertical = 4.dp)
            .clip(ShapeCache.smooth12)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .focusIndicator(ShapeCache.smooth12)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Tabler.Outline.CalendarRepeat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timer.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            timer.channelName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (timer.days.isNotEmpty()) {
                Text(
                    text = timer.days.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(Tabler.Outline.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SeriesDetailDialog(
    timer: DvrSeriesTimer,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = timer.name,
        confirmText = stringResource(Res.string.livetv_cancel_series),
        dismissText = stringResource(Res.string.livetv_action_close),
        tone = ConfirmTone.NEUTRAL,
        onConfirm = onCancel,
        onDismiss = onDismiss,
        content = {
            timer.channelName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Days: ${if (timer.days.isEmpty()) "Any" else timer.days.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Keep up to: ${if (timer.keepUpTo <= 0) "All" else timer.keepUpTo}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Channel: ${if (timer.recordAnyChannel) "Any" else (timer.channelName ?: "—")}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
