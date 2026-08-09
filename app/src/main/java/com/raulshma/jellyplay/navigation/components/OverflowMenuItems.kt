package com.raulshma.jellyplay.navigation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer

/**
 * Animated list of overflow destinations that expands upward from the nav bar's
 * "More" (⋮) 3-dot toggle (#115).
 *
 * Designed using Material Design 3 Expressive Navigation and Library action chip
 * standards — pills feature interactive press scale, smooth shape morphing,
 * adaptive surface container colors, TV D-pad focus glow, and 22dp Tabler icons
 * paired with semi-bold typography.
 *
 * Items enter with a staggered slide-up + fade + scale-in (anchored at the
 * bottom, nearest the toggle first). Items compose bottom-up: the last declared
 * entry sits closest to the toggle.
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
    downloadCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val isOfflineActive = offlineMode != OfflineMode.ONLINE

    // Ordered nearest-to-toggle first (bottom of the column). Index drives the
    // stagger delay so the item closest to the toggle rises in first.
    val items = listOf(
        OverflowEntry(
            icon = Tabler.Outline.Wand,
            label = R.string.menu_surprise_me,
            onClick = onSurpriseClick,
            isHighlighted = true,
        ),
        OverflowEntry(
            icon = Tabler.Outline.Users,
            label = R.string.menu_syncplay,
            onClick = onSyncPlayClick,
        ),
        OverflowEntry(
            icon = Tabler.Outline.Download,
            label = R.string.menu_downloads,
            onClick = onDownloadsClick,
            badgeCount = downloadCount,
        ),
        OverflowEntry(
            icon = if (isOfflineActive) Tabler.Outline.Wifi else Tabler.Outline.WifiOff,
            label = when {
                isGoingOnline -> R.string.menu_going_online
                isOfflineActive -> R.string.menu_go_online
                else -> R.string.menu_go_offline
            },
            onClick = onToggleOffline,
            isGoingOnline = isGoingOnline,
            isHighlighted = isOfflineActive,
        ),
        OverflowEntry(
            icon = Tabler.Outline.Cast,
            label = R.string.menu_play_on,
            onClick = onPlayOnClick,
        ),
        OverflowEntry(
            icon = Tabler.Outline.Apps,
            label = R.string.menu_shortcuts,
            onClick = onShortcutsClick,
        ),
        OverflowEntry(
            icon = Tabler.Outline.Settings,
            label = R.string.menu_settings,
            onClick = onSettingsClick,
        ),
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
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
                    isHighlighted = entry.isHighlighted,
                    badgeCount = entry.badgeCount,
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
    val isHighlighted: Boolean = false,
    val badgeCount: Int = 0,
)

@Composable
private fun OverflowPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isGoingOnline: Boolean = false,
    isHighlighted: Boolean = false,
    badgeCount: Int = 0,
) {
    val isLight = LocalIsLightTheme.current
    val containerColor = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
    } else if (isLight) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
    }

    val contentColor by animateColorAsState(
        targetValue = if (isHighlighted) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "overflowPillContentColor",
    )

    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            },
        ),
    ) {
        ExpressiveChipContainer(
            onClick = onClick,
            containerColor = containerColor,
            forceActive = isHighlighted,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isGoingOnline) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = contentColor,
                    )
                } else if (badgeCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge { Text(badgeCount.coerceAtMost(99).toString()) }
                        },
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

