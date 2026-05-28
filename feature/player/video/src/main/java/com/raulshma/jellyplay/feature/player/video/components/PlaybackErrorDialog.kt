package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PlayerType
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun PlaybackErrorDialog(
    errorMessage: String,
    currentPlayerType: PlayerType,
    onRetryWithEngine: (PlayerType) -> Unit,
    onDismiss: () -> Unit,
) {
    val alternativeEngines = PlayerType.entries.filter {
        it != PlayerType.EXTERNAL && it != currentPlayerType
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Tabler.Outline.AlertCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text("Playback Error")
        },
        text = {
            Column {
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Try another player engine:",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                alternativeEngines.forEach { engine ->
                    Button(
                        onClick = { onRetryWithEngine(engine) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    ) {
                        Text("Retry with ${engine.displayName}")
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}
