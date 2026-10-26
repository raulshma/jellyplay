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
import androidx.compose.ui.res.stringResource as stringAndroidResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer
import com.raulshma.jellyplay.core.ui.navigation.ResolvedShortcut
import com.raulshma.jellyplay.core.ui.navigation.resolvedShortcutLabel

/**
 * One render-ready overflow pill: the resolved shortcut plus the shell-owned
 * click wiring and dynamic decoration. Built by the shell (PhoneContent /
 * tablet rail) from [resolveShortcuts]' output — the menu itself knows
 * nothing about the catalog, the persisted config, or which targets exist
 * (the `applyNavCustomization` split: policy resolves, chrome renders).
 */
data class OverflowMenuItem(
    /** Stable identity (the resolved shortcut's id) — animation/keys only. */
    val key: String,
    val shortcut: ResolvedShortcut,
    val onClick: () -> Unit,
    /** State-dependent icon swap (the offline toggle flips Wifi/WifiOff). */
    val iconOverride: ImageVector? = null,
    /** State-dependent label swap (going online / go online / go offline) — an app-module resource id. */
    val labelOverrideRes: Int? = null,
    /** Downloads badge count. */
    val badgeCount: Int = 0,
    /** Primary-tinted pill (Surprise Me; offline-active). */
    val isHighlighted: Boolean = false,
    /** Offline→online transition spinner replaces the icon. */
    val isProgressing: Boolean = false,
)

/**
 * Animated list of overflow destinations that expands upward from the nav bar's
 * "More" (⋮) 3-dot toggle. The entries are the user's customized shortcut set
 * (the shipped default layout while unconfigured) — built by the shell.
 *
 * Designed using Material Design 3 Expressive Navigation and Library action chip
 * standards — pills feature interactive press scale, smooth shape morphing,
 * adaptive surface container colors, TV D-pad focus glow, and 22dp Tabler icons
 * paired with semi-bold typography.
 *
 * Items enter with a staggered slide + fade + scale-in. When [alignToStart] is
 * true (tablet NavigationRail) the column is anchored top-start beside the rail
 * and items flow top-down, sliding in from above. Otherwise (phone) it is
 * anchored bottom-end beside the floating nav toggle and items flow bottom-up,
 * sliding up from below.
 *
 * [alignToStart] left-aligns the pills (for the tablet NavigationRail, where the
 * "More" toggle lives on the left); the default right-aligns them beside the
 * phone floating nav bar's trailing toggle.
 */
@Composable
fun OverflowMenuItems(
    entries: List<OverflowMenuItem>,
    alignToStart: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = if (alignToStart) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(
            space = 6.dp,
            alignment = if (alignToStart) Alignment.Top else Alignment.Bottom,
        ),
        modifier = modifier,
    ) {
        entries.forEachIndexed { index, entry ->
            // Mounted fresh each open so the enter transition replays.
            val state = remember { MutableTransitionState(false) }
            state.targetState = true
            AnimatedVisibility(
                visibleState = state,
                // Stagger: index 0 has no delay; each later item adds 40ms.
                // Tablet (top-anchored) slides in from above; phone slides up
                // from below. Slight scale + fade in both cases.
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 220, delayMillis = index * 40),
                    initialOffsetY = { if (alignToStart) -it / 2 else it / 2 },
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 220, delayMillis = index * 40),
                ) + scaleIn(
                    animationSpec = tween(durationMillis = 220, delayMillis = index * 40),
                    initialScale = 0.85f,
                ),
                exit = fadeOut(animationSpec = tween(120)) +
                    slideOutVertically(
                        animationSpec = tween(120),
                        targetOffsetY = { if (alignToStart) -it / 2 else it / 2 },
                    ),
            ) {
                OverflowPill(
                    icon = entry.iconOverride ?: entry.shortcut.icon,
                    label = entry.labelOverrideRes?.let { stringAndroidResource(it) }
                        ?: resolvedShortcutLabel(entry.shortcut),
                    onClick = entry.onClick,
                    isGoingOnline = entry.isProgressing,
                    isHighlighted = entry.isHighlighted,
                    badgeCount = entry.badgeCount,
                )
            }
        }
    }
}

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
