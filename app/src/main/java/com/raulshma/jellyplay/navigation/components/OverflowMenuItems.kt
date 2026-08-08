package com.raulshma.jellyplay.navigation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Apps
import com.composables.icons.tabler.outline.Cast
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.Wand
import com.composables.icons.tabler.outline.Wifi
import com.composables.icons.tabler.outline.WifiOff
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.ui.components.focusIndicator

/**
 * Animated list of overflow destinations that expands upward from the nav bar's
 * "More" toggle (#115).
 *
 * Mirrors the Home FAB speed-dial's full option set (Surprise Me, SyncPlay,
 * Downloads, Go Online/Offline, Play On, Shortcuts, Settings) so every Home-FAB action is
 * reachable from anywhere. Each item matches the Material3
 * `FloatingActionButtonMenuItem` look — a full-rounded pill on
 * `secondaryContainer`, 24dp leading/trailing padding, a 24dp icon + `titleMedium`
 * label, 56dp tall — so the two menus read as one design language.
 *
 * Items enter with a staggered slide-up + fade + scale-in (anchored at the
 * bottom, nearest the toggle first). Items compose bottom-up: the last declared
 * entry sits closest to the toggle.
 *
 * Driven entirely by callbacks; the caller owns visibility via composition.
 */
@Composable
fun OverflowMenuItems(
    onSurpriseClick: () -> Unit,
    onSyncPlayClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onToggleOffline: () -> Unit,
    onPlayOnClick: () -> Unit,
    onShortcutsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    offlineMode: OfflineMode,
    isGoingOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    // Ordered nearest-to-toggle first (bottom of the column). Index drives the
    // stagger delay so the item closest to the toggle rises in first.
    val items = listOf(
        OverflowEntry(Tabler.Outline.Wand, R.string.menu_surprise_me, onSurpriseClick),
        OverflowEntry(Tabler.Outline.Users, R.string.menu_syncplay, onSyncPlayClick),
        OverflowEntry(Tabler.Outline.Download, R.string.menu_downloads, onDownloadsClick),
        OverflowEntry(
            icon = if (offlineMode != OfflineMode.ONLINE) Tabler.Outline.Wifi else Tabler.Outline.WifiOff,
            label = when {
                isGoingOnline -> R.string.menu_going_online
                offlineMode != OfflineMode.ONLINE -> R.string.menu_go_online
                else -> R.string.menu_go_offline
            },
            onClick = onToggleOffline,
            isGoingOnline = isGoingOnline,
        ),
        OverflowEntry(Tabler.Outline.Cast, R.string.menu_play_on, onPlayOnClick),
        OverflowEntry(Tabler.Outline.Apps, R.string.menu_shortcuts, onShortcutsClick),
        OverflowEntry(Tabler.Outline.Settings, R.string.menu_settings, onSettingsClick),
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
        modifier = modifier,
    ) {
        items.forEachIndexed { index, entry ->
            // Mounted fresh each open so the enter transition replays.
            val state = remember { MutableTransitionState(false) }
            state.targetState = true
            AnimatedVisibility(
                visibleState = state,
                // Stagger: nearest-to-toggle (index 0) has no delay; each later
                // item adds 40ms. Slide from below + slight scale + fade.
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 220, delayMillis = index * 40),
                    initialOffsetY = { it / 2 },
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 220, delayMillis = index * 40),
                ) + scaleIn(
                    animationSpec = tween(durationMillis = 220, delayMillis = index * 40),
                    initialScale = 0.85f,
                ),
                exit = fadeOut(animationSpec = tween(120)) +
                    slideOutVertically(animationSpec = tween(120), targetOffsetY = { it / 2 }),
            ) {
                OverflowPill(
                    icon = entry.icon,
                    label = stringResource(entry.label),
                    onClick = entry.onClick,
                    isGoingOnline = entry.isGoingOnline,
                )
            }
        }
    }
}

private data class OverflowEntry(
    val icon: ImageVector,
    val label: Int,
    val onClick: () -> Unit,
    val isGoingOnline: Boolean = false,
)

@Composable
private fun OverflowPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isGoingOnline: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        ),
        modifier = Modifier
            .focusIndicator(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .sizeIn(minHeight = 56.dp)
                .height(56.dp)
                .padding(start = 24.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (isGoingOnline) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(imageVector = icon, contentDescription = null)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
