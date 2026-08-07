package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.ui.R
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
 */
enum class QuickAction(
    val labelRes: Int,
    val icon: ImageVector,
) {
    PLAY(R.string.core_action_play, Tabler.Outline.PlayerPlay),
    MARK_WATCHED(R.string.core_action_mark_watched, Tabler.Outline.Eye),
    MARK_UNWATCHED(R.string.core_action_mark_unwatched, Tabler.Outline.EyeOff),
    DOWNLOAD(R.string.core_action_download, Tabler.Outline.Download),
    ADD_TO_PLAYLIST(R.string.core_action_add_to_playlist, Tabler.Outline.Bookmark),
    DETAILS(R.string.core_action_details, Tabler.Outline.InfoCircle),
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
        title = title,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            actions.forEachIndexed { index, action ->
                QuickActionRow(action = action, onClick = { onAction(action) })
                if (index < actions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
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
