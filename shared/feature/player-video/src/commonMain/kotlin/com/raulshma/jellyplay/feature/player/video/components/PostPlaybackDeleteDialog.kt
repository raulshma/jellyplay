package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_delete_offer_delete
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_delete_offer_keep
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_delete_offer_title
import org.jetbrains.compose.resources.stringResource

/**
 * The #14 offer-delete-after-playback dialog: "Delete '<title>' from server?"
 * with Delete / Keep — raised by the [com.raulshma.jellyplay.feature.player.video.PostPlaybackActionsController]
 * after playback concluded naturally for an item whose prefs + permissions
 * allow it.
 *
 * Rendered as a plain Compose layer (NOT a Material `AlertDialog`/popup),
 * exactly like the error dialog it is hosted beside: a popup opens a new
 * top-level window that does not carry the player's immersive window flags,
 * so the status/navigation bars would flash.
 */
@Composable
fun PostPlaybackDeleteDialog(
    itemTitle: String,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = ShapeCache.smooth20,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.player_video_delete_offer_title, itemTitle),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onKeep,
                        modifier = Modifier.weight(1f),
                        shape = ShapeCache.smooth12,
                    ) {
                        Text(stringResource(Res.string.player_video_delete_offer_keep))
                    }
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = ShapeCache.smooth12,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(stringResource(Res.string.player_video_delete_offer_delete))
                    }
                }
            }
        }
    }
}
