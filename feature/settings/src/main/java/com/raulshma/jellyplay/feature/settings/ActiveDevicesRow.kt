package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DeviceDesktop
import com.composables.icons.tabler.outline.DeviceMobile
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Message
import com.composables.icons.tabler.outline.PlayerPause
import androidx.compose.foundation.shape.CircleShape
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.SessionInfo

@Composable
internal fun ActiveDevicesRow(
    sessions: List<SessionInfo>,
    serverAddress: String,
    onSendMessage: (sessionId: String, header: String, text: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) return

    var messageTarget by remember { mutableStateOf<SessionInfo?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth24)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_active_devices),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(ShapeCache.smoothPill)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "${sessions.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .focusGroup()
                .tvFocusRestorer(),
        ) {
            items(sessions, key = { it.id }) { session ->
                ActiveDeviceCard(
                    session = session,
                    serverAddress = serverAddress,
                    onSendMessage = { messageTarget = session },
                )
            }
        }
    }

    messageTarget?.let { session ->
        SendMessageDialog(
            deviceName = session.deviceName,
            userName = session.userName,
            onDismiss = { messageTarget = null },
            onSend = { header, text ->
                onSendMessage(session.id, header, text)
                messageTarget = null
            },
        )
    }
}

@Composable
private fun ActiveDeviceCard(
    session: SessionInfo,
    serverAddress: String,
    onSendMessage: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "deviceCardScale",
    )

    val nowPlayingItem = session.nowPlayingItem
    val backgroundImageUrl = if (nowPlayingItem != null && serverAddress.isNotBlank()) {
        val tag = nowPlayingItem.backdropImageTag ?: nowPlayingItem.primaryImageTag
        if (!tag.isNullOrBlank()) {
            val type = if (nowPlayingItem.backdropImageTag != null) "Backdrop" else "Primary"
            "$serverAddress/Items/${nowPlayingItem.id}/Images/$type?tag=$tag&fillWidth=400&fillHeight=300&quality=90"
        } else null
    } else null

    Card(
        modifier = Modifier
            .width(260.dp)
            .height(160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = ShapeCache.smooth20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (backgroundImageUrl != null) {
                com.raulshma.jellyplay.core.ui.image.MediaImage(
                    url = backgroundImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    crossfade = true,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    // Header row: avatar + user info + device icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // User avatar
                        val initial = session.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                        val avatarColors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        )
                        val colorIndex = session.userName.hashCode().mod(avatarColors.size).let { if (it < 0) -it else it }
                        val avatarColor = avatarColors[colorIndex]
                        val onAvatarColor = when (colorIndex) {
                            0 -> MaterialTheme.colorScheme.onPrimaryContainer
                            1 -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(avatarColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                initial,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = onAvatarColor,
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                session.userName.ifBlank { stringResource(R.string.settings_unknown) },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (backgroundImageUrl != null) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Unspecified,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                session.deviceName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (backgroundImageUrl != null) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        val deviceIcon = when {
                            session.client.contains("TV", ignoreCase = true) -> Tabler.Outline.DeviceTv
                            session.client.contains("Android", ignoreCase = true) ||
                                session.client.contains("iOS", ignoreCase = true) ||
                                session.client.contains("Mobile", ignoreCase = true) -> Tabler.Outline.DeviceMobile
                            else -> Tabler.Outline.DeviceDesktop
                        }
                        Icon(
                            deviceIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (backgroundImageUrl != null) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Client app label
                    Text(
                        session.client,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (backgroundImageUrl != null) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Bottom Section: Now playing & actions
                Column(modifier = Modifier.fillMaxWidth()) {
                    session.nowPlayingItem?.let { item ->
                        val titleText = if (!item.seriesName.isNullOrBlank()) {
                            "${item.seriesName} - ${item.name}"
                        } else {
                            item.name
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (session.playState?.isPaused == true) {
                                Icon(
                                    Tabler.Outline.PlayerPause,
                                    contentDescription = stringResource(R.string.settings_paused_cd),
                                    modifier = Modifier.size(12.dp),
                                    tint = if (backgroundImageUrl != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                NowPlayingBars(color = if (backgroundImageUrl != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                titleText,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (backgroundImageUrl != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        val positionTicks = session.playState?.positionTicks ?: 0L
                        val runtimeTicks = item.runTimeTicks ?: 0L
                        if (runtimeTicks > 0) {
                            Spacer(Modifier.height(6.dp))
                            val progress = (positionTicks.toFloat() / runtimeTicks.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(ShapeCache.smoothPill),
                                color = if (session.playState?.isPaused == true)
                                    if (backgroundImageUrl != null) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    if (backgroundImageUrl != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.primary,
                                trackColor = if (backgroundImageUrl != null) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    } ?: Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(
                            onClick = onSendMessage,
                            modifier = Modifier.size(28.dp).focusIndicator(CircleShape),
                        ) {
                            Icon(
                                Tabler.Outline.Message,
                                contentDescription = stringResource(R.string.settings_send_message_cd),
                                modifier = Modifier.size(16.dp),
                                tint = if (backgroundImageUrl != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBars(color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    val reducedMotion = LocalReducedMotion.current
    // Each NowPlayingBars row runs three infinite animations; the active-devices
    // list can show several rows. In performance/reduced-motion mode freeze the
    // equalizer bars to a static mid height instead of driving a per-row redraw coroutine.
    val bar1: Float
    val bar2: Float
    val bar3: Float
    if (!reducedMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "nowPlaying")
        bar1 = infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar1",
        ).value
        bar2 = infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(350, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar2",
        ).value
        bar3 = infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(450, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar3",
        ).value
    } else {
        bar1 = 0.6f
        bar2 = 0.5f
        bar3 = 0.7f
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp * bar1)
                .clip(ShapeCache.smooth4)
                .background(color),
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp * bar2)
                .clip(ShapeCache.smooth4)
                .background(color),
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp * bar3)
                .clip(ShapeCache.smooth4)
                .background(color),
        )
    }
}

@Composable
private fun SendMessageDialog(
    deviceName: String,
    userName: String,
    onDismiss: () -> Unit,
    onSend: (header: String, text: String) -> Unit,
) {
    val defaultHeader = stringResource(R.string.settings_message_from_admin)
    var header by remember { mutableStateOf(defaultHeader) }
    var text by remember { mutableStateOf("") }

    ImeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.settings_send_message_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.settings_send_message_to, userName, deviceName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = header,
                    onValueChange = { header = it },
                    label = { Text(stringResource(R.string.settings_header_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.settings_message_label)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(header, text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.settings_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
