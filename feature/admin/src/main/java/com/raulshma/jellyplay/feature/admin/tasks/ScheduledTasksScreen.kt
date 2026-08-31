package com.raulshma.jellyplay.feature.admin.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.TaskExecutionInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTasksScreen(
    onBack: () -> Unit,
    viewModel: ScheduledTasksViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    // TV focus-on-launch: focus the first task once data arrives so D-pad input lands on content,
    // not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (state.isLoading || state.error != null) 0 else state.tasks.size.coerceAtLeast(1),
        tag = "scheduled_tasks_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(R.string.admin_scheduled_tasks_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            val refreshFocusState = rememberTvFocusState()
            IconButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.then(refreshFocusState.focusModifier).tvFocusIndicator(refreshFocusState, CircleShape),
            ) {
                Icon(Tabler.Outline.Refresh, contentDescription = stringResource(R.string.admin_refresh))
            }
        },
    ) {
        when {
            state.isLoading -> {
                ScreenLoadingState(modifier = Modifier.fillMaxSize())
            }
            state.error != null -> {
                ErrorScreen(
                    message = state.error,
                    onRetry = { viewModel.loadTasks() },
                )
            }
            else -> {
                // Group by Category, mirroring jellyfin-web's
                // getCategories()/getTasksByCategory(): tasks with a
                // blank category are dropped, categories and tasks are
                // sorted alphabetically (locale-aware).
                // Memoized at the composable scope (not inside LazyColumn's
                // LazyListScope, where @Composable calls aren't allowed) so the
                // filter/groupBy/sort chain runs only when the task list changes,
                // not on every recomposition.
                val groupedTasks = remember(state.tasks) {
                    groupScheduledTasksByCategory(state.tasks)
                }
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .tvFocusRestorer()
                            .focusRequester(listFocusRequester),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = adaptiveInfo.contentPadding(false) - 8.dp,
                            end = adaptiveInfo.contentPadding(false) - 8.dp,
                            top = 8.dp,
                            bottom = adaptiveInfo.bottomPadding(isTv),
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        groupedTasks.forEach { (category, tasks) ->
                            item(key = "header_$category") {
                                Text(
                                    category,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                            items(items = tasks, key = { "${category}_${it.id}_${it.name}" }, contentType = { "task" }) { task ->
                                TaskItem(
                                    task = task,
                                    onStart = { viewModel.startTask(task.id) },
                                    onCancel = { viewModel.cancelTask(task.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskItem(
    task: ScheduledTaskInfo,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "taskItemScale",
    )
    val stopFocusState = rememberTvFocusState()
    val runFocusState = rememberTvFocusState()

    val stateColor = when (task.state) {
        TaskState.RUNNING -> MaterialTheme.colorScheme.primary
        TaskState.CANCELLING -> StatusColors.warning
        TaskState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            task.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TaskStateBadge(state = task.state)
                    }
                    task.description?.let { desc ->
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    task.lastExecutionResult?.let { last ->
                        LastRunRow(last)
                    }
                }
                Spacer(Modifier.width(8.dp))
                when (task.state) {
                    TaskState.RUNNING -> {
                        OutlinedButton(
                            onClick = onCancel,
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .then(stopFocusState.focusModifier)
                                .tvFocusIndicator(stopFocusState, ShapeCache.smooth12),
                        ) {
                            Icon(Tabler.Outline.PlayerPause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.admin_stop), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TaskState.IDLE -> {
                        FilledTonalButton(
                            onClick = onStart,
                            modifier = Modifier
                                .height(36.dp)
                                .then(runFocusState.focusModifier)
                                .tvFocusIndicator(runFocusState, ShapeCache.smooth12),
                        ) {
                            Icon(Tabler.Outline.PlayerPlay, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.admin_run), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TaskState.CANCELLING -> {
                        Text(
                            "Cancelling...",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusColors.warning,
                        )
                    }
                }
            }

            if (task.state == TaskState.RUNNING) {
                val progress = task.currentProgressPercentage
                Spacer(Modifier.height(8.dp))
                if (progress != null) {
                    // Animate + clamp like RunningTasksCard so the bar eases between
                    // polled values instead of jumping every refresh.
                    val animatedProgress by animateFloatAsState(
                        targetValue = (progress / 100).toFloat().coerceIn(0f, 1f),
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "taskProgress",
                    )
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    JellyPlayLinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCache.smooth4)
                            .height(6.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${progress.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    // Jellyfin reports progress lazily (null for the first seconds of a
                    // run, or for task types that never expose a percentage) — show an
                    // indeterminate bar so RUNNING is visibly active.
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    JellyPlayLinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCache.smooth4)
                            .height(6.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Running…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (task.triggers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    task.triggers.forEach { trigger ->
                        TriggerChip(trigger.type, trigger.intervalTicks, trigger.dayOfWeek)
                    }
                }
            }
        }
    }
}

@Composable
private fun LastRunRow(last: TaskExecutionInfo) {
    val relative = remember(last.startTimeUtc) { formatRelativeTime(last.startTimeUtc) }
    val duration = remember(last.startTimeUtc, last.endTimeUtc) {
        formatTaskDuration(last.startTimeUtc, last.endTimeUtc)
    }
    // Map the SDK TaskCompletionStatus serialName to a short label + tone. Success
    // renders no badge (the timestamp already implies a good run).
    val statusLabel = when (last.status.lowercase()) {
        "failed" -> "Failed" to MaterialTheme.colorScheme.error
        "cancelled" -> "Cancelled" to StatusColors.warning
        "aborted" -> "Aborted" to StatusColors.warning
        else -> null to Color.Unspecified
    }

    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Last run ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (relative != null) {
            Text(
                relative,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (duration != null) {
            Text(
                " · $duration",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        statusLabel.first?.let { label ->
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = statusLabel.second,
            )
        }
    }
}

/**
 * Renders an ISO-8601 (offset-carrying) timestamp as a relative string:
 * "just now" / "Xm ago" / "Xh ago" / "Xd ago", falling back to a localized date.
 * Returns null if the string can't be parsed.
 */
private fun formatRelativeTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val start = java.time.OffsetDateTime.parse(iso)
        val duration = java.time.Duration.between(start, java.time.OffsetDateTime.now())
        when {
            duration.toMinutes() < 1 -> "just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
            duration.toHours() < 24 -> "${duration.toHours()}h ago"
            duration.toDays() < 7 -> "${duration.toDays()}d ago"
            else -> {
                val formatter = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                formatter.format(java.util.Date.from(start.toInstant()))
            }
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Formats the wall-clock duration between two ISO-8601 timestamps as a compact
 * "Xs" / "Xm Ys" / "Xh Ym" string, or null if either bound is missing/unparseable.
 */
private fun formatTaskDuration(startIso: String?, endIso: String?): String? {
    if (startIso.isNullOrBlank() || endIso.isNullOrBlank()) return null
    return try {
        val start = java.time.OffsetDateTime.parse(startIso)
        val end = java.time.OffsetDateTime.parse(endIso)
        val seconds = java.time.Duration.between(start, end).seconds
        when {
            seconds < 60 -> "${seconds}s"
            seconds < 3_600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3_600}h ${(seconds % 3_600) / 60}m"
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun TaskStateBadge(state: TaskState) {
    val (text, color) = when (state) {
        TaskState.RUNNING -> "Running" to MaterialTheme.colorScheme.primary
        TaskState.CANCELLING -> "Cancelling" to StatusColors.warning
        TaskState.IDLE -> "Idle" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(ShapeCache.smooth4)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun TriggerChip(type: String, intervalTicks: Long?, dayOfWeek: String?) {
    val label = when {
        type == "IntervalTrigger" && intervalTicks != null -> {
            val hours = intervalTicks / 36_000_000_000
            if (hours > 24) {
                stringResource(R.string.admin_trigger_every_days, (hours / 24).toInt())
            } else {
                stringResource(R.string.admin_trigger_every_hours, hours.toInt())
            }
        }
        type == "DailyTrigger" -> stringResource(R.string.admin_trigger_daily)
        type == "WeeklyTrigger" -> dayOfWeek?.lowercase()?.replaceFirstChar { it.uppercase() }
            ?: stringResource(R.string.admin_trigger_weekly)
        type == "StartupTrigger" -> stringResource(R.string.admin_trigger_on_startup)
        else -> type.removeSuffix("Trigger")
    }
    Box(
        modifier = Modifier
            .clip(ShapeCache.smooth4)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
