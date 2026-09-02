package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Activity
import com.composables.icons.tabler.outline.User
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.feature.newsletter.generated.resources.Res
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_activity_digest
import org.jetbrains.compose.resources.stringResource

@Composable
fun NewsletterActivityDigest(
    entries: List<ActivityLogEntry>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 12.dp)) {
        Text(
            text = stringResource(Res.string.newsletter_activity_digest),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        entries.take(10).forEachIndexed { index, entry ->
            ActivityDigestItem(
                entry = entry,
                isLast = index == entries.take(10).lastIndex,
            )
        }
    }
}

@Composable
private fun ActivityDigestItem(
    entry: ActivityLogEntry,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLast) 0.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when {
                entry.type.contains("Session", ignoreCase = true) -> Tabler.Outline.User
                else -> Tabler.Outline.Activity
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.shortOverview?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatRelativeDate(entry.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// Built once at class-load rather than per digest entry.
private val ACTIVITY_DATE_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("MMM d")

private fun formatRelativeDate(dateStr: String): String {
    return try {
        val instant = java.time.Instant.parse(dateStr)
        val entryDate = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val today = java.time.LocalDate.now()
        when {
            entryDate == today -> "Today"
            entryDate == today.minusDays(1) -> "Yesterday"
            else -> entryDate.format(ACTIVITY_DATE_FORMATTER)
        }
    } catch (_: Exception) {
        dateStr
    }
}
