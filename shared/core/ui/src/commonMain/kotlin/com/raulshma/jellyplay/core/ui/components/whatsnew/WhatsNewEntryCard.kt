package com.raulshma.jellyplay.core.ui.components.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Book
import com.composables.icons.tabler.outline.Book2
import com.composables.icons.tabler.outline.CircleCheck
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.LayoutNavbar
import com.composables.icons.tabler.outline.Message2
import com.composables.icons.tabler.outline.RowInsertBottom
import com.composables.icons.tabler.outline.Sparkles
import com.composables.icons.tabler.outline.ArrowRight
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.WhatsNewCategory
import com.raulshma.jellyplay.core.model.WhatsNewEntry
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_whatsnew_category_fix
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_whatsnew_category_improvement
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_whatsnew_category_new
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_whatsnew_take_me_there
import org.jetbrains.compose.resources.stringResource

/**
 * One structured What's-New entry as a card: icon + category chip, title,
 * summary, an optional "where to find it" line, and — when [onNavigate] is
 * provided (the entry's `target` resolved against the compiled-in route map
 * by the caller) — a "Take me there" button.
 *
 * Shared verbatim by the post-update sheet (app shell) and the Settings
 * archive screen, so a release reads identically in both places.
 */
@Composable
fun WhatsNewEntryCard(
    entry: WhatsNewEntry,
    onNavigate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = entry.icon(),
                    contentDescription = null,
                    tint = categoryColor(entry.category),
                    modifier = Modifier.size(20.dp),
                )
                CategoryChip(entry.category)
            }
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (entry.summary.isNotBlank()) {
                Text(
                    text = entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val howTo = entry.howTo
            if (!howTo.isNullOrBlank()) {
                Text(
                    text = howTo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (onNavigate != null) {
                TextButton(onClick = onNavigate, modifier = Modifier.padding(top = 2.dp)) {
                    Text(stringResource(Res.string.core_ui_whatsnew_take_me_there))
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Tabler.Outline.ArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(category: WhatsNewCategory) {
    val (labelRes, color) = when (category) {
        WhatsNewCategory.NEW ->
            Res.string.core_ui_whatsnew_category_new to MaterialTheme.colorScheme.primary
        WhatsNewCategory.IMPROVEMENT ->
            Res.string.core_ui_whatsnew_category_improvement to MaterialTheme.colorScheme.tertiary
        WhatsNewCategory.FIX ->
            Res.string.core_ui_whatsnew_category_fix to MaterialTheme.colorScheme.secondary
    }
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun categoryColor(category: WhatsNewCategory) = when (category) {
    WhatsNewCategory.NEW -> MaterialTheme.colorScheme.primary
    WhatsNewCategory.IMPROVEMENT -> MaterialTheme.colorScheme.tertiary
    WhatsNewCategory.FIX -> MaterialTheme.colorScheme.secondary
}

/**
 * The compiled-in icon vocabulary: feed `icon` names map to Tabler vectors;
 * unknown names (and null) fall back to the category's own icon.
 */
@Composable
private fun WhatsNewEntry.icon(): ImageVector = when (icon?.lowercase()) {
    "sparkles" -> Tabler.Outline.Sparkles
    "rows" -> Tabler.Outline.RowInsertBottom
    "home" -> Tabler.Outline.Home
    "book" -> Tabler.Outline.Book2
    "speech" -> Tabler.Outline.Message2
    else -> when (category) {
        WhatsNewCategory.NEW -> Tabler.Outline.Sparkles
        WhatsNewCategory.IMPROVEMENT -> Tabler.Outline.LayoutNavbar
        WhatsNewCategory.FIX -> Tabler.Outline.CircleCheck
    }
}
