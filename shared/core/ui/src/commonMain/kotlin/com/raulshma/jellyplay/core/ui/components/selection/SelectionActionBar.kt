package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

/**
 * The bulk-selection bottom bar shared by every list screen's selection mode
 * (downloads, arr queue, requests).
 *
 * Chrome is fixed, not parameterized: `ShapeCache.smooth12` /
 * `surfaceContainerHigh` / `shadowElevation = 8.dp` — the downloads + arr-queue
 * majority shape. The third former variant (requests: smooth16 /
 * `surfaceContainer` / `tonalElevation = 3.dp`, clear button parked at the row's
 * end) had no per-screen rationale and is gone; a screen that needs different
 * chrome needs a reason recorded here first.
 *
 * The bulk-action gating invariant lives INSIDE this bar as
 * [selectionActionsEnabled]: both action slots receive the folded boolean so no
 * screen can drop a leg of `!actionInProgress && selectedCount > 0`. Screens
 * with no in-flight concept (downloads) pass the default `false`, which reduces
 * the fold to the count check alone. Select-all / clear are additionally
 * disabled while an action is in flight. Admin's MediaCleanupScaffold
 * select-all row is deliberately NOT this bar (recorded single-home decision).
 *
 * The count label ellipsizes and takes only the width the controls leave
 * behind (`weight(1f, fill = false)`): giving it `fill = true` would let it
 * squeeze the control column to ~0 width on narrow screens and stack letters
 * vertically.
 *
 * State algebra is NOT here — core:model's `SelectionState` owns the
 * toggle / select-all / clear writes; this bar only draws and calls back.
 *
 * @param countLabel       localized "n selected" label; ellipsizes when long.
 * @param selectedCount    drives the action gating fold, not the label.
 * @param selectAllLabel   localized content description for the select-all icon.
 * @param clearLabel       localized content description for the clear icon.
 * @param leadingActions   optional controls BEFORE select-all / clear (the
 *                         downloads bar's pause / resume / cancel cluster);
 *                         receives the gating boolean, may ignore it for
 *                         per-list predicates.
 * @param actions          bulk actions AFTER clear; receives the gating boolean.
 */
@Composable
fun SelectionActionBar(
    countLabel: String,
    selectedCount: Int,
    selectAllLabel: String,
    clearLabel: String,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    actionInProgress: Boolean = false,
    modifier: Modifier = Modifier,
    leadingActions: @Composable (actionsEnabled: Boolean) -> Unit = {},
    actions: @Composable (actionsEnabled: Boolean) -> Unit = {},
) {
    val actionsEnabled = selectionActionsEnabled(actionInProgress, selectedCount)
    Surface(
        modifier = modifier,
        shape = ShapeCache.smooth12,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            leadingActions(actionsEnabled)
            IconButton(onClick = onSelectAll, enabled = !actionInProgress) {
                Icon(
                    Tabler.Outline.Check,
                    contentDescription = selectAllLabel,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onClear, enabled = !actionInProgress) {
                Icon(
                    Tabler.Outline.X,
                    contentDescription = clearLabel,
                    modifier = Modifier.size(20.dp),
                )
            }
            actions(actionsEnabled)
        }
    }
}

/**
 * The bulk-action admission fold shared by every selection bar: an action is
 * enabled only while no bulk action is IN FLIGHT and at least one row is
 * selected. Formerly duplicated per-screen as two separate `enabled =`
 * expressions that could (and once did) drift apart.
 */
fun selectionActionsEnabled(actionInProgress: Boolean, selectedCount: Int): Boolean =
    !actionInProgress && selectedCount > 0
