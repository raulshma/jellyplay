package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Category
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.List
import com.composables.icons.tabler.outline.Resize
import com.composables.icons.tabler.outline.Stack2
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer
import com.raulshma.jellyplay.feature.library.R

/**
 * The labeled action row that replaces the former unlabeled floating toolbar.
 * Each chip carries a leading icon AND a label so its purpose is self-evident —
 * fixing the "no clear way to know what they do" complaint about the old
 * icon-only toolbar.
 *
 * Three chips:
 *  - **View** — leading icon + name of the current view mode. Tap cycles the
 *    grid/thumb/list/masonry modes (same cycle order the old toolbar used).
 *  - **Size** — `Resize` icon + "Size" label. Opens the poster-size slider sheet.
 *  - **Group** — `Category` icon + label of the current group-by (or "Group"
 *    when None). Opens the group-by selection sheet.
 *
 * Visual idiom matches [FilterOptionChip] (shape morph + press scale + TV focus
 * glow, via [ExpressiveChipContainer]) but adds a leading icon since these are
 * actions, not filter overflows.
 */
@Composable
fun LibraryActionChipRow(
    viewMode: LibraryViewMode,
    groupByLabel: String,
    onViewCycle: () -> Unit,
    onSizeClick: () -> Unit,
    onGroupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (viewIcon, viewLabel) = when (viewMode) {
        LibraryViewMode.GRID -> Tabler.Outline.LayoutGrid to stringResource(R.string.library_grid_view)
        LibraryViewMode.THUMB -> Tabler.Outline.Stack2 to stringResource(R.string.library_thumb_view)
        LibraryViewMode.LIST -> Tabler.Outline.List to stringResource(R.string.library_list_view)
        LibraryViewMode.MASONRY -> Tabler.Outline.LayoutGrid to stringResource(R.string.library_masonry_view)
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        item(key = "view") {
            LibraryActionChip(
                icon = viewIcon,
                label = viewLabel,
                onClick = onViewCycle,
            )
        }
        item(key = "size") {
            LibraryActionChip(
                icon = Tabler.Outline.Resize,
                label = stringResource(R.string.library_action_size),
                onClick = onSizeClick,
            )
        }
        item(key = "group") {
            LibraryActionChip(
                icon = Tabler.Outline.Category,
                label = groupByLabel,
                onClick = onGroupClick,
            )
        }
    }
}

/**
 * A labeled action chip with a leading icon. Uses the same
 * [ExpressiveChipContainer] as [FilterOptionChip] / GlassFilterChip (shape
 * morph + press scale + TV focus glow) but leads with an icon so the action is
 * identifiable at a glance — the old floating toolbar's icon-only buttons were
 * undiscoverable.
 */
@Composable
private fun LibraryActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = LocalIsLightTheme.current
    val bgColor = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
    val contentColor = MaterialTheme.colorScheme.onSurface

    ExpressiveChipContainer(
        onClick = onClick,
        modifier = modifier,
        containerColor = bgColor,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = contentColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
