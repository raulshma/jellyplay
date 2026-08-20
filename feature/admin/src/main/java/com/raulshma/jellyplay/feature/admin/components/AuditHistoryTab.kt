package com.raulshma.jellyplay.feature.admin.components

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Clock
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.defaultContentSizeSpec
import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.feature.admin.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun AuditHistoryTab(
    entries: List<AuditLogEntry>,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ScreenEmptyState(
                icon = Tabler.Outline.Clock,
                title = stringResource(R.string.admin_no_audit_history),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            AuditEntryCard(entry)
        }
    }
}

@Composable
private fun AuditEntryCard(entry: AuditLogEntry) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusIndicator(ShapeCache.smooth16)
            .clickable { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = defaultContentSizeSpec()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        formatRelativeTime(entry.timestamp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        formatAbsoluteTime(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(ShapeCache.smoothPill)
                        .background(
                            when (entry.actionType) {
                                CleanupActionType.STALE_REMOVAL -> MaterialTheme.colorScheme.tertiaryContainer
                                CleanupActionType.WATCHED_REMOVAL -> MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        when (entry.actionType) {
                            CleanupActionType.STALE_REMOVAL -> "Stale Removal"
                            CleanupActionType.WATCHED_REMOVAL -> "Watched Cleanup"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (entry.actionType) {
                            CleanupActionType.STALE_REMOVAL -> MaterialTheme.colorScheme.onTertiaryContainer
                            CleanupActionType.WATCHED_REMOVAL -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.admin_audit_by_user, entry.adminUserName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.admin_audit_items_removed, entry.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (expanded && entry.itemDetails.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.itemDetails.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (item.type.isNotBlank()) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(ShapeCache.smoothPill)
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(item.type, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} minutes ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} hours ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)} days ago"
        else -> {
            // Reuse a single SimpleDateFormat per locale instead of constructing
            // one (pattern parse + DateFormatSymbols graph) per card per
            // recomposition.
            val formatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
            formatter.format(Date(timestamp))
        }
    }
}

@Composable
private fun formatAbsoluteTime(timestamp: Long): String {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    return formatter.format(Date(timestamp))
}
