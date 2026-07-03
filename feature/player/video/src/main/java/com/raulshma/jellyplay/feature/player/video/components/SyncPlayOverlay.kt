package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.SyncStatusColors
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Composable
fun SyncPlayOverlay(
    isVisible: Boolean,
    groupName: String,
    participantCount: Int,
    isSynced: Boolean,
    isSyncing: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    val statusColor = when {
        isSynced -> SyncStatusColors.synced
        isSyncing -> SyncStatusColors.syncing
        else -> SyncStatusColors.else_
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(150, easing = AlphaEasing)),
        exit = fadeOut(tween(200, easing = AlphaEasing)),
        modifier = modifier,
    ) {
        Surface(
            shape = ShapeCache.smoothPill,
            color = playerOnScrim().copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.25f)),
            modifier = Modifier
                .then(focusState.focusModifier)
                .tvFocusIndicator(focusState, ShapeCache.smoothPill),
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusColor,
                    modifier = Modifier.size(10.dp),
                ) {}
                Text(
                    text = when {
                        isSynced -> "Synced"
                        isSyncing -> "Syncing"
                        else -> "Buffering"
                    },
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = groupName,
                    color = playerOnScrim(),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "$participantCount",
                    color = playerOnScrim().copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
