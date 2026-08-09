package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check

/**
 * Filled primary circle with a check, overlaid on a media card to indicate it is
 * part of the active multi-selection. The card-wrapping [Box] keeps the overlay
 * stable (rather than threading selection through PosterCard/ThumbCard/LibraryListItem)
 * to keep the core/ui card signatures stable.
 *
 * Callers pass the desired alignment via [modifier], e.g.
 * `Modifier.align(Alignment.TopEnd)` for grid/thumb cards or
 * `Modifier.align(Alignment.CenterStart)` for list rows.
 *
 * Hidden when not selected so non-selecting recomposition skips it entirely.
 */
@Composable
fun SelectionIndicatorOverlay(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!selected) return
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .padding(6.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(primary)
            .border(2.dp, onPrimary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Tabler.Outline.Check,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = onPrimary,
        )
    }
}
