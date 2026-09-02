package com.raulshma.jellyplay.feature.admin.dashboard.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DeviceDesktop
import com.composables.icons.tabler.outline.DeviceMobile
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.PlayerStop
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_active_sessions
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_stop_session_cd
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_view_all

@Composable
fun ActiveSessionsSection(
    sessions: List<SessionInfo>,
    onViewAll: () -> Unit,
    onStop: (SessionInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewAllFocusState = rememberTvFocusState(focusedScale = 1.04f)

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = ShapeCache.smooth20,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.admin_active_sessions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${sessions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                TextButton(
                    onClick = onViewAll,
                    modifier = Modifier
                        .then(viewAllFocusState.focusModifier)
                        .tvFocusIndicator(viewAllFocusState, ShapeCache.smooth12),
                ) { Text(stringResource(Res.string.admin_view_all)) }
            }
            Spacer(Modifier.height(8.dp))
            sessions.take(5).forEachIndexed { index, session ->
                SessionItem(
                    session = session,
                    onStop = { onStop(session) },
                    showDivider = index < minOf(4, sessions.size - 1),
                )
                if (index < minOf(4, sessions.size - 1)) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: SessionInfo,
    onStop: () -> Unit,
    showDivider: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val initial = session.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val avatarColors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        )
        val avatarColor = avatarColors[session.userName.hashCode().mod(avatarColors.size).let { if (it < 0) -it else it }]
        val onAvatarColor = when (avatarColor) {
            avatarColors[0] -> MaterialTheme.colorScheme.onPrimaryContainer
            avatarColors[1] -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onTertiaryContainer
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = onAvatarColor,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                session.userName.ifBlank { session.deviceName },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            session.nowPlayingItem?.let { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NowPlayingIndicator()
                    Spacer(Modifier.width(4.dp))
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } ?: run {
                Text(
                    session.deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val deviceIcon = when {
                session.client.contains("TV", ignoreCase = true) -> Tabler.Outline.DeviceTv
                session.client.contains("Android", ignoreCase = true) ||
                    session.client.contains("iOS", ignoreCase = true) -> Tabler.Outline.DeviceMobile
                else -> Tabler.Outline.DeviceDesktop
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    deviceIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Stop-playback affordance — only meaningful when something is
                // actively playing on the session. Opens a confirm dialog.
                if (session.nowPlayingItem != null) {
                    Spacer(Modifier.width(4.dp))
                    val stopFocusState = rememberTvFocusState()
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(28.dp)
                            .then(stopFocusState.focusModifier)
                            .tvFocusIndicator(stopFocusState, CircleShape),
                    ) {
                        Icon(
                            Tabler.Outline.PlayerStop,
                            contentDescription = stringResource(Res.string.admin_stop_session_cd),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                session.client,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NowPlayingIndicator() {
    val reducedMotion = LocalReducedMotion.current
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
                .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp * bar2)
                .clip(ShapeCache.smooth4)
                .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp * bar3)
                .clip(ShapeCache.smooth4)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
