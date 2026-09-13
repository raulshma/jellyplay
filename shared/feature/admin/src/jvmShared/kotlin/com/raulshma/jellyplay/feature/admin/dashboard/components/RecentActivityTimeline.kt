package com.raulshma.jellyplay.feature.admin.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.ActivityLogSeverity
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_view_all

@Composable
fun RecentActivityTimeline(
    entries: List<ActivityLogEntry>,
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
                Text(
                    "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = onViewAll,
                    modifier = Modifier
                        .then(viewAllFocusState.focusModifier)
                        .tvFocusIndicator(viewAllFocusState, ShapeCache.smooth12),
                ) { Text(stringResource(Res.string.admin_view_all)) }
            }
            Spacer(Modifier.height(8.dp))
            entries.take(5).forEachIndexed { index, entry ->
                TimelineEntry(
                    entry = entry,
                    isLast = index == minOf(4, entries.size - 1),
                )
            }
        }
    }
}

@Composable
private fun TimelineEntry(
    entry: ActivityLogEntry,
    isLast: Boolean,
) {
    val severityColor = when (entry.severity) {
        ActivityLogSeverity.ERROR,
        ActivityLogSeverity.FATAL -> MaterialTheme.colorScheme.error

        ActivityLogSeverity.WARNING -> StatusColors.warning

        ActivityLogSeverity.DEBUG,
        ActivityLogSeverity.TRACE -> MaterialTheme.colorScheme.outline

        else -> MaterialTheme.colorScheme.primary
    }

    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .padding(top = 6.dp)
                    .clip(CircleShape)
                    .background(severityColor),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(lineColor),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 2.dp, bottom = if (isLast) 0.dp else 14.dp),
        ) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.overview?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
