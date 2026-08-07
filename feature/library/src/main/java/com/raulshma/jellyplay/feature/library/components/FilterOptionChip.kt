package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer
import com.raulshma.jellyplay.feature.library.R

/**
 * A chip in the pinned filter row. Shows a [label] and, when the
 * filter has active selections ([selectedCount] > 0), a count badge so the user
 * sees at a glance which filters are applied. A trailing chevron signals it
 * opens a selection sheet. Selected state is implied by a non-zero count or by
 * [highlight] (e.g. for the Sort chip, which is always "set" but reads better
 * highlighted only when it deviates from the default).
 *
 * Shares its visual container (shape morph + press scale + TV focus glow) with
 * the other expressive chips via [ExpressiveChipContainer].
 */
@Composable
fun FilterOptionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedCount: Int = 0,
    highlight: Boolean = false,
) {
    val isActive = selectedCount > 0 || highlight
    val isLight = LocalIsLightTheme.current
    val bgColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        else -> if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
    }
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val badgeColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

    ExpressiveChipContainer(
        onClick = onClick,
        modifier = modifier,
        containerColor = bgColor,
        forceActive = isActive,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        if (selectedCount > 0) {
            Text(
                text = selectedCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.18f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Icon(
            imageVector = Tabler.Outline.ChevronDown,
            contentDescription = stringResource(R.string.library_filters),
            modifier = Modifier.size(14.dp),
            tint = contentColor,
        )
    }
}
