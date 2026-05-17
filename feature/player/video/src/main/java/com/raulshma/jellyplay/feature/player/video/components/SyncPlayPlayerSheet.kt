package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.SyncPlayChatMessage
import com.raulshma.jellyplay.core.ui.tv.tvFocusable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPlayPlayerSheet(
    groupName: String,
    participantCount: Int,
    isSynced: Boolean,
    isPlaying: Boolean,
    ignoreWait: Boolean,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onLeave: () -> Unit,
    onIgnoreWaitChange: (Boolean) -> Unit,
    chatMessages: List<SyncPlayChatMessage> = emptyList(),
    onSendChatMessage: (String) -> Unit = {},
    onToggleChatOverlay: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "SyncPlay",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )

            val statusColor = if (isSynced) Color(0xFF4CAF50) else Color(0xFFFFC107)
            Surface(
                shape = ShapeCache.smooth16,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.2f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusColor,
                        modifier = Modifier.size(12.dp),
                    ) {}
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            groupName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "$participantCount participant${if (participantCount != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Surface(
                        shape = ShapeCache.smoothPill,
                        color = statusColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            if (isSynced) "Synced" else "Buffering",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.weight(1f).tvFocusable(),
                    shape = ShapeCache.smoothPill,
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isPlaying) "Pause" else "Play")
                }
                FilledTonalButton(
                    onClick = onStop,
                    modifier = Modifier.tvFocusable(),
                    shape = ShapeCache.smoothPill,
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Ignore Wait",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Don't pause for buffering users",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ignoreWait,
                    onCheckedChange = onIgnoreWaitChange,
                    modifier = Modifier.tvFocusable(),
                )
            }

            FilledTonalButton(
                onClick = onToggleChatOverlay,
                modifier = Modifier.fillMaxWidth().tvFocusable(),
                shape = ShapeCache.smoothPill,
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Toggle Chat Overlay")
            }

            if (chatMessages.isNotEmpty()) {
                Surface(
                    shape = ShapeCache.smooth16,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                    SyncPlayChatPanel(
                        messages = chatMessages,
                        onSendMessage = onSendChatMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(12.dp),
                    )
                }
            }

            FilledTonalButton(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth().tvFocusable(),
                shape = ShapeCache.smoothPill,
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Leave Group")
            }
        }
    }
}
