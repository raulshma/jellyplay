package com.raulshma.jellyplay.feature.livetv.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DvrTimer
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
import com.raulshma.jellyplay.feature.livetv.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
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
        itemCount = uiState.activeRecordings.size + uiState.upcomingGroups.sumOf { it.timers.size },
        tag = "schedule_init",
    )

    when {
        uiState.isLoading && uiState.activeRecordings.isEmpty() && uiState.upcomingGroups.isEmpty() -> {
            ScreenLoadingState(modifier = Modifier.fillMaxSize())
        }
        uiState.error != null && uiState.activeRecordings.isEmpty() && uiState.upcomingGroups.isEmpty() -> {
            ErrorScreen(message = uiState.error!!, onRetry = { viewModel.load() })
        }
        uiState.activeRecordings.isEmpty() && uiState.upcomingGroups.isEmpty() -> {
            ScreenEmptyState(
                icon = Tabler.Outline.CalendarClock,
                title = stringResource(R.string.livetv_no_scheduled_recordings),
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
                ) {
                    if (uiState.activeRecordings.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.livetv_section_active_recordings), contentPad)
                        }
                        items(items = uiState.activeRecordings, key = { "active-${it.id}" }) { rec ->
                            TimerRow(
                                title = rec.name,
                                subtitle = rec.channelName,
                                meta = stringResource(R.string.livetv_recording_now),
                                contentPad = contentPad,
                                onClick = { },
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                    if (uiState.upcomingGroups.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.livetv_section_upcoming_recordings), contentPad)
                        }
                        uiState.upcomingGroups.forEach { group ->
                            item(key = "header-${group.dateLabel}") {
                                DateLabel(group.dateLabel, contentPad)
                            }
                            items(items = group.timers, key = { "timer-${it.id}" }) { timer ->
                                TimerRow(
                                    title = timer.programName,
                                    subtitle = timer.channelName,
                                    meta = formatStart(timer.startDate),
                                    contentPad = contentPad,
                                    onClick = { viewModel.showTimerDetail(timer) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.selectedTimer?.let { timer ->
        TimerDetailDialog(
            timer = timer,
            onCancel = { viewModel.cancelTimer(timer.id) },
            onDismiss = { viewModel.dismissDetail() },
        )
    }
}

@Composable
private fun SectionHeader(title: String, contentPad: androidx.compose.ui.unit.Dp) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = contentPad, vertical = 6.dp),
    )
}

@Composable
private fun DateLabel(label: String, contentPad: androidx.compose.ui.unit.Dp) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = contentPad, vertical = 4.dp),
    )
}

@Composable
private fun TimerRow(
    title: String,
    subtitle: String?,
    meta: String,
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
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Tabler.Outline.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TimerDetailDialog(
    timer: DvrTimer,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = timer.programName,
        confirmText = stringResource(R.string.livetv_cancel_recording),
        dismissText = stringResource(R.string.livetv_action_close),
        tone = ConfirmTone.NEUTRAL,
        onConfirm = onCancel,
        onDismiss = onDismiss,
        content = {
            timer.channelName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.livetv_schedule_start, formatStart(timer.startDate)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.livetv_schedule_end, formatStart(timer.endDate)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

private fun formatStart(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        java.time.OffsetDateTime.parse(iso, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
    }.recoverCatching {
        java.time.LocalDateTime.parse(
            iso.replace("Z", "").replace("T", " ").substringBefore('+').trim()
        ).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
    }.getOrElse { iso }
}
