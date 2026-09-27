package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Ban
import com.composables.icons.tabler.outline.Search

/**
 * The ONE three-way queue-delete action cluster both *arr queue surfaces
 * render from — the arrqueue screen's destructive confirm dialog (stacked
 * full-width legs inside the dialog's content slot) and the requests detail
 * sheet's inline pair (side-by-side compact legs). It was extracted when the
 * two sites' hand-copied button clusters (remove-only / remove + search /
 * blocklist + search over the same `(blocklist, searchAgain)` choice) had
 * drifted apart; the matching deep choreography lives in the repository's
 * `deleteQueueRow` member (the `DeleteDownloadedEpisodesSheet` precedent:
 * shared destructive-action UI in core:ui, labels supplied by the caller).
 *
 * INVARIANT: every leg reports through the single [onChoose] funnel with its
 * `(blocklist, searchAgain)` pair — the composable never dismisses a host
 * dialog or fires anything else, so hosts own confirm/dismiss policy (the
 * arrqueue dialog dismisses after a choice, the requests sheet fires
 * directly).
 *
 * Labels are plain resolved strings, not resources: the two hosts' label
 * resources differ meaningfully per locale (e.g. requests' German
 * "Blockliste & Suchen" vs arrqueue's "Blockliste & Suche"), so each host
 * passes its own and neither string was deleted or merged.
 *
 * @param onChoose invoked with the chosen leg's flags:
 *   remove-only `(false, false)`, remove + search `(false, true)`,
 *   blocklist + search `(true, true)`.
 * @param removeSearchLabel label of the remove-and-search leg.
 * @param blocklistSearchLabel label of the blocklist-and-search leg (always
 *   rendered in the error color with the Ban icon).
 * @param modifier applied to the cluster's container.
 * @param removeOnlyLabel label of the remove-only leg; null (the default)
 *   hides the leg — the requests sheet deliberately offers only the two
 *   search legs, so it passes nothing here.
 * @param horizontal renders the legs side-by-side (equal weight, the sheet's
 *   compact row) instead of stacked full-width (the dialog's column, the
 *   default).
 * @param searchActionIcon icon of the remove-and-search leg; the dialog uses
 *   the default Search, the sheet passes its historical X mark.
 * @param buttonShape per-leg button shape; null keeps the OutlinedButton
 *   default (the dialog's look) — the sheet passes its smooth-12 shape.
 */
@Composable
fun QueueDeleteConfirmActions(
    onChoose: (blocklist: Boolean, searchAgain: Boolean) -> Unit,
    removeSearchLabel: String,
    blocklistSearchLabel: String,
    modifier: Modifier = Modifier,
    removeOnlyLabel: String? = null,
    horizontal: Boolean = false,
    searchActionIcon: ImageVector = Tabler.Outline.Search,
    buttonShape: Shape? = null,
) {
    if (horizontal) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedAction(
                onClick = { onChoose(false, true) },
                modifier = Modifier.weight(1f),
                shape = buttonShape,
            ) {
                Icon(searchActionIcon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(removeSearchLabel)
            }
            OutlinedAction(
                onClick = { onChoose(true, true) },
                modifier = Modifier.weight(1f),
                shape = buttonShape,
            ) {
                Icon(Tabler.Outline.Ban, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(blocklistSearchLabel)
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (removeOnlyLabel != null) {
                OutlinedAction(
                    onClick = { onChoose(false, false) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = buttonShape,
                ) {
                    Text(removeOnlyLabel)
                }
            }
            OutlinedAction(
                onClick = { onChoose(false, true) },
                modifier = Modifier.fillMaxWidth(),
                shape = buttonShape,
            ) {
                Icon(searchActionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(removeSearchLabel)
            }
            OutlinedAction(
                onClick = { onChoose(true, true) },
                modifier = Modifier.fillMaxWidth(),
                shape = buttonShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Tabler.Outline.Ban, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(blocklistSearchLabel)
            }
        }
    }
}

/**
 * [OutlinedButton] with an optional shape: null keeps the component default
 * (passing a null shape through is impossible — the parameter is non-null on
 * the component itself). Colors default to the component's own.
 */
@Composable
private fun OutlinedAction(
    onClick: () -> Unit,
    modifier: Modifier,
    shape: Shape?,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val buttonColors = colors ?: ButtonDefaults.outlinedButtonColors()
    if (shape != null) {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = shape, colors = buttonColors, content = content)
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, colors = buttonColors, content = content)
    }
}
