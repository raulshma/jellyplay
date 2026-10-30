package com.raulshma.jellyplay.feature.shortcuts.customize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.icons.ShortcutIconRegistry
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * The curated shortcut-icon picker — a grid of the [ShortcutIconRegistry]
 * entries, one selectable tile each. The [AccentColorPicker]SwatchCircle
 * grammar (selected border + RadioButton role + selected-state semantics +
 * TV focus glow), squared instead of round to seat icon glyphs.
 *
 * Bounded height so it drops into a sheet section and scrolls internally; TV
 * moves focus cell-by-cell through the same tiles (no separate focus model).
 */
@Composable
fun ShortcutIconGridPicker(
    selectedIconId: String,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember { ShortcutIconRegistry.ALL }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        modifier = modifier.heightIn(max = 264.dp),
    ) {
        items(entries, key = { it.id }, contentType = { "shortcut-icon" }) { entry ->
            ShortcutIconTile(
                icon = entry.icon,
                displayName = entry.displayName,
                isSelected = entry.id == selectedIconId,
                onClick = { onIconSelected(entry.id) },
            )
        }
    }
}

@Composable
private fun ShortcutIconTile(
    icon: ImageVector,
    displayName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.1f)
    val shape = ShapeCache.smooth14
    val border = if (isSelected) {
        Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape)
    } else {
        Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)), shape)
    }
    Box(
        modifier = modifier
            .size(52.dp)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .then(border)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = displayName
                role = Role.RadioButton
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
    }
}
