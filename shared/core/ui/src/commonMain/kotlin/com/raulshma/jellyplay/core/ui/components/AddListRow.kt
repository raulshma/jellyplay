package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Plus
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

/**
 * D-pad focusable "Add …" row rendered as the trailing item of TV list screens
 * where the floating action button affordance is hidden (`if (!isTv)`), so the
 * add/create action stays reachable with a remote. Follows the in-list
 * create-new idiom of AddToCollectionSheet's rows.
 */
@Composable
fun AddListRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Tabler.Outline.Plus,
) {
    Surface(
        onClick = onClick,
        shape = ShapeCache.smooth12,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.focusIndicator().fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
