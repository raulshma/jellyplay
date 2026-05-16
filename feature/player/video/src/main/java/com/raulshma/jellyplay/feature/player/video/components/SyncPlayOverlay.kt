package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.tvFocusable

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
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)),
        exit = fadeOut(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)),
        modifier = modifier,
    ) {
        Surface(
            shape = ShapeCache.smooth16,
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.tvFocusable(),
        ) {
            Row(
                modifier = Modifier
                    .tvFocusable().clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isSynced -> Color(0xFF4CAF50)
                        isSyncing -> Color(0xFF2196F3)
                        else -> Color(0xFFFFC107)
                    },
                    modifier = Modifier.size(8.dp),
                ) {}
                Text(
                    text = when {
                        isSynced -> "Synced"
                        isSyncing -> "Syncing"
                        else -> "Buffering"
                    },
                    color = when {
                        isSynced -> Color(0xFF4CAF50)
                        isSyncing -> Color(0xFF2196F3)
                        else -> Color(0xFFFFC107)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = groupName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "$participantCount",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
