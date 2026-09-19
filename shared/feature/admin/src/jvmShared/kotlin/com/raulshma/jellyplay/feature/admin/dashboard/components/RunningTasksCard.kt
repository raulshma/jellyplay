package com.raulshma.jellyplay.feature.admin.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_view_all

@Composable
fun RunningTasksCard(
    tasks: List<ScheduledTaskInfo>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewAllFocusState = rememberTvFocusState(focusedScale = 1.04f)

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = ShapeCache.smooth20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Running Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${tasks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                TextButton(
                    onClick = onViewAll,
                    modifier = Modifier
                        .then(viewAllFocusState.focusModifier)
                        .tvFocusIndicator(viewAllFocusState, ShapeCache.smooth12),
                ) { Text(stringResource(Res.string.admin_view_all)) }
            }
            Spacer(Modifier.height(8.dp))
            tasks.take(3).forEachIndexed { index, task ->
                TaskItem(
                    task = task,
                    showDivider = index < minOf(2, tasks.size - 1),
                )
            }
        }
    }
}

@Composable
private fun TaskItem(
    task: ScheduledTaskInfo,
    showDivider: Boolean,
) {
    Column(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskStateBadge(task.state)
                Spacer(Modifier.width(8.dp))
                Text(
                    task.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
            task.currentProgressPercentage?.let { progress ->
                Text(
                    "${progress.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        task.currentProgressPercentage?.let { progress ->
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
                    .height(6.dp)
                    .clip(ShapeCache.smooth4),
            )
        }
    }
}

@Composable
private fun TaskStateBadge(state: TaskState) {
    val color: Color = when (state) {
        TaskState.RUNNING -> MaterialTheme.colorScheme.primary
        TaskState.CANCELLING -> StatusColors.warning
        TaskState.IDLE -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color),
    )
}
