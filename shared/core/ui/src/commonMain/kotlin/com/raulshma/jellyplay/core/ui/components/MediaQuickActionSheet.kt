package com.raulshma.jellyplay.core.ui.components
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_add_to_playlist
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_delete
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_details
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_download
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_favorite
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_mark_unwatched
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_mark_watched
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_play
import com.raulshma.jellyplay.core.ui.generated.resources.core_action_unfavorite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.Heart
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.DownloadOff
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Trash

import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Media quick actions surfaced from a card long-press (phones) or the TV Menu
 * button. Closes the gap that D-pad remotes cannot long-press Select, so the
 * only previously reachable long-press affordance (the read-only peek preview)
 * had no actions
 *
 * Hosts render only the actions applicable to the item; the sheet dismisses
 * after any action fires. "Add to playlist" is a plain callback — the host
 * then shows its own playlist picker (e.g. `feature/details/AddToPlaylistSheet`)
 * so the picker stays out of `core/ui`.
 *
 * [QuickAction] identity lives in `core/model` (pure, no UI coupling) so
 * predicate logic can sit next to the model. Presentation (label + icon) is
 * attached here as extensions.
 */
typealias QuickAction = com.raulshma.jellyplay.core.model.QuickAction

/** @see QuickAction */
val QuickAction.labelRes: org.jetbrains.compose.resources.StringResource
    get() = when (this) {
        QuickAction.PLAY -> Res.string.core_action_play
        QuickAction.MARK_WATCHED -> Res.string.core_action_mark_watched
        QuickAction.MARK_UNWATCHED -> Res.string.core_action_mark_unwatched
        QuickAction.FAVORITE -> Res.string.core_action_favorite
        QuickAction.UNFAVORITE -> Res.string.core_action_unfavorite
        QuickAction.DOWNLOAD -> Res.string.core_action_download
        QuickAction.ADD_TO_PLAYLIST -> Res.string.core_action_add_to_playlist
        QuickAction.REMOVE_DOWNLOAD -> Res.string.core_action_delete
        QuickAction.DETAILS -> Res.string.core_action_details
    }

/** @see QuickAction */
val QuickAction.icon: ImageVector
    get() = when (this) {
        QuickAction.PLAY -> Tabler.Outline.PlayerPlay
        QuickAction.MARK_WATCHED -> Tabler.Outline.Eye
        QuickAction.MARK_UNWATCHED -> Tabler.Outline.EyeOff
        // Outline = "add to favorites"; filled = "currently favorited, tap to remove".
        QuickAction.FAVORITE -> Tabler.Outline.Heart
        QuickAction.UNFAVORITE -> Tabler.Filled.Heart
        QuickAction.DOWNLOAD -> Tabler.Outline.Download
        QuickAction.ADD_TO_PLAYLIST -> Tabler.Outline.Bookmark
        QuickAction.REMOVE_DOWNLOAD -> Tabler.Outline.DownloadOff
        QuickAction.DETAILS -> Tabler.Outline.InfoCircle
    }

/**
 * @param actions The ordered actions to offer.
 * @param onAction Invoked with the chosen [QuickAction]; the host performs the
 * work and dismisses the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaQuickActionSheet(
    actions: List<QuickAction>,
    title: String,
    onAction: (QuickAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvSafeSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // navigationBars/IME clearance is provided by TvSafeSheet's mobile branch
                .padding(bottom = 8.dp),
        ) {
            SheetHeader(
                title = title,
                onClose = onDismiss,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                actions.forEach { action ->
                    QuickActionRow(action = action, onClick = { onAction(action) })
                }
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    action: QuickAction,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusIndicator(rememberTvFocusState())
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(action.labelRes),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
