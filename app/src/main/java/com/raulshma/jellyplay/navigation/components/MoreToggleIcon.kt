package com.raulshma.jellyplay.navigation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.R

/**
 * The ⋮/✕ glyph used by every "More" overflow toggle in the nav bar (#115) —
 * [ExpressiveFloatingNavigationBar] (joined Search+More pill and the standalone
 * no-Search variant) and the classic FloatingNavigationBar. Centralizes the
 * DotsVertical↔X swap and the localized content description so all three sites
 * stay in sync and don't drift back to a hardcoded English label.
 *
 * Callers own the container (Row / Box / Surface), its highlight color, and the
 * surrounding tap target — those vary per nav style. This only renders the glyph.
 */
@Composable
fun MoreToggleIcon(
    isExpanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    Icon(
        imageVector = if (isExpanded) Tabler.Outline.X else Tabler.Outline.DotsVertical,
        contentDescription = stringResource(R.string.nav_more_content_description),
        tint = tint,
        modifier = modifier.size(iconSize),
    )
}
