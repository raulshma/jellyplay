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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTasksScreen(
    onBack: () -> Unit,
    viewModel: ScheduledTasksViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColor = rememberScreenBackgroundColor()

    JellyPlayScreenScaffold(
        title = "Scheduled Tasks",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Tabler.Outline.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        when {
            state.isLoading -> {
                ScreenLoadingState(modifier = Modifier.fillMaxSize())
            }
            state.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.loadTasks() }) { Text("Retry") }
                    }
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = adaptiveInfo.contentPadding(false) - 8.dp,
                            end = adaptiveInfo.contentPadding(false) - 8.dp,
                            top = 8.dp,
                            bottom = adaptiveInfo.bottomPadding(isTv),
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val groupedTasks = state.tasks.groupBy { it.category ?: "General" }
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
                            items(items = tasks, key = { "${category}_${it.id}_${it.name}" }) { task ->
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
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Last run: ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                last.startTimeUtc?.replace("T", " ")?.take(19) ?: "Never",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (last.errorMessage != null) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "(Failed)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
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
                            modifier = Modifier.height(36.dp),
                        ) {
                            Icon(Tabler.Outline.PlayerPause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stop", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TaskState.IDLE -> {
                        FilledTonalButton(
                            onClick = onStart,
                            modifier = Modifier.height(36.dp),
                        ) {
                            Icon(Tabler.Outline.PlayerPlay, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Run", style = MaterialTheme.typography.labelMedium)
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
                if (progress != null) {
                    Spacer(Modifier.height(8.dp))
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    JellyPlayLinearProgressIndicator(
                        progress = { (progress / 100).toFloat() },
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
            if (hours > 24) "Every ${hours / 24}d" else "Every ${hours}h"
        }
        type == "DailyTrigger" -> "Daily"
        type == "WeeklyTrigger" -> dayOfWeek?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Weekly"
        type == "StartupTrigger" -> "On Startup"
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
