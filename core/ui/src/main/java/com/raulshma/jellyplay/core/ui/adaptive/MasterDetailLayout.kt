package com.raulshma.jellyplay.core.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus

/**
 * Renders a master-detail (two-pane) layout when [decision] is two-pane,
 * otherwise renders a single pane.
 *
 * On tablet / foldable (Expanded window size), both panes are shown
 * side-by-side with a divider. On phone (Compact), only the appropriate pane
 * is shown full-screen.
 *
 * **Dpad navigation:** Each pane is a focusable container. On TV, the panes
 * are not side-by-side (TV uses a single-pane push navigation), so this layout
 * is only active when [LocalTvMode] is `false`.
 *
 * @param decision The [MasterDetailLayoutDecision] controlling which panes to
 *   show. Computed via [resolveMasterDetailLayout].
 * @param masterPane Content for the list/master pane.
 * @param detailPane Content for the detail pane.
 */
@Composable
fun MasterDetailLayout(
    decision: MasterDetailLayoutDecision,
    modifier: Modifier = Modifier,
    masterPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
) {
    when {
        decision.isTwoPane -> TwoPaneRow(
            masterPane = masterPane,
            detailPane = detailPane,
            modifier = modifier,
        )
        decision.showDetailPane -> {
            Box(modifier.fillMaxSize()) { detailPane() }
        }
        else -> {
            Box(modifier.fillMaxSize()) { masterPane() }
        }
    }
}

@Composable
private fun TwoPaneRow(
    masterPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val masterFocusRequester = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    // Allocate ~40% to the master pane on typical tablets (≈ 800-1280dp),
    // but give at least 320dp so grid content is usable. The detail pane
    // gets the remainder.
    val masterWidthDp = (screenWidthDp * 0.40).toInt().coerceIn(320, 480)

    Row(modifier.fillMaxSize()) {
        Box(
            Modifier
                .width(masterWidthDp.dp)
                .fillMaxHeight()
        ) {
            masterPane()
        }
        VerticalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.fillMaxHeight(),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            detailPane()
        }
    }
}
