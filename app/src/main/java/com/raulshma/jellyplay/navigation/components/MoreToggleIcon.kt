package com.raulshma.jellyplay.navigation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
 *
 * When [badgeCount] is positive, a Material 3 [Badge] showing the count (capped
 * at 99) is layered over the glyph via [BadgedBox]. The badge only appears while
 * the toggle is collapsed (showing ⋮); once expanded the user sees the per-item
 * "Downloads" badge in the overflow list instead, so duplicating it here would
 * just clutter.
 */
@Composable
fun MoreToggleIcon(
    isExpanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    badgeCount: Int = 0,
) {
    val icon = @Composable {
        Icon(
            imageVector = if (isExpanded) Tabler.Outline.X else Tabler.Outline.DotsVertical,
            contentDescription = stringResource(R.string.nav_more_content_description),
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
    if (!isExpanded && badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(badgeCount.coerceAtMost(99).toString()) }
            },
            modifier = modifier,
        ) {
            icon()
        }
    } else {
        icon()
    }
}
