package com.raulshma.jellyplay.navigation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.roundToInt
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

internal const val OVERFLOW_MENU_INITIAL_SCALE = 0.2f
internal const val OVERFLOW_MENU_TARGET_EXIT_SCALE = 0.2f
internal const val OVERFLOW_MENU_ENTER_DURATION_MS = 280
internal const val OVERFLOW_MENU_EXIT_DURATION_MS = 180
internal const val OVERFLOW_MENU_ENTER_DELAY_STEP_MS = 35
internal const val OVERFLOW_MENU_EXIT_DELAY_STEP_MS = 25
internal const val OVERFLOW_MENU_FADE_DURATION_MS = 200
/** Item slot pitch relative to the item height — a 15% gap between pills. */
internal const val OVERFLOW_MENU_ANCHOR_STEP_FACTOR = 1.15f

/**
 * macOS dock item style spring and fluid easing curves:
 * - Enter scale uses a subtle overshoot spring ([0.34, 1.35, 0.64, 1.0]) mimicking dock item
 *   magnification / maximize transition.
 * - Enter slide uses fluid deceleration ([0.16, 1.0, 0.3, 1.0]).
 * - Exit uses accelerated retreat ([0.4, 0.0, 0.2, 1.0]) minimizing back into the 3-dot icon.
 */
internal val MacOsDockScaleEnterEasing = CubicBezierEasing(0.34f, 1.35f, 0.64f, 1f)
internal val MacOsDockSlideEnterEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
internal val MacOsDockExitEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/**
 * Computes the enter stagger index for an item in the overflow menu.
 * When [alignToStart] is false (phone), items expand from the 3-dot icon at the bottom-end,
 * meaning the bottom item (closest to the 3-dot icon, index = totalItems - 1) animates first (0)
 * and items cascade upward to the top (totalItems - 1).
 * When [alignToStart] is true (tablet), items flow top-down from the rail anchor.
 */
internal fun overflowEnterStaggerIndex(index: Int, totalItems: Int, alignToStart: Boolean): Int {
    if (totalItems <= 1) return 0
    return if (alignToStart) index else (totalItems - 1 - index)
}

/**
 * Computes the exit stagger index for an item in the overflow menu.
 * When [alignToStart] is false (phone), items minimize back toward the 3-dot icon at the bottom-end,
 * meaning the top item collapses first (0) and items cascade downward into the 3-dot icon.
 * When [alignToStart] is true (tablet), items collapse bottom-up toward the top-start anchor.
 */
internal fun overflowExitStaggerIndex(index: Int, totalItems: Int, alignToStart: Boolean): Int {
    if (totalItems <= 1) return 0
    return if (alignToStart) (totalItems - 1 - index) else index
}

/**
 * Returns the transform origin for the scale transition:
 * - Phone (alignToStart = false): bottom-end (1f, 1f) where the 3-dot icon sits.
 * - Tablet (alignToStart = true): top-start (0f, 0f) beside the rail.
 */
internal fun overflowMenuTransformOrigin(alignToStart: Boolean): TransformOrigin =
    if (alignToStart) {
        TransformOrigin(pivotFractionX = 0f, pivotFractionY = 0f)
    } else {
        TransformOrigin(pivotFractionX = 1f, pivotFractionY = 1f)
    }

/**
 * Total duration in milliseconds for all overflow menu items to complete their
 * exit / minimize transition, accounting for cascading stagger delays.
 */
internal fun overflowMenuTotalExitDurationMs(totalItems: Int): Int {
    if (totalItems <= 1) return OVERFLOW_MENU_EXIT_DURATION_MS
    return (totalItems - 1) * OVERFLOW_MENU_EXIT_DELAY_STEP_MS + OVERFLOW_MENU_EXIT_DURATION_MS
}

/**
 * Computes the vertical offset from an item's final slot to the 3-dot anchor —
 * consumed BOTH as the enter transition's initial offset (items fly out from
 * the anchor) and as the exit transition's target offset (items collapse back
 * into it), so the two stay symmetric by construction.
 *
 * All items originate from / minimize into the 3-dot icon:
 * - Phone (alignToStart = false): The 3-dot icon is below the column.
 *   Item index 0 is at the top, index (totalItems - 1) is at the bottom closest to the toggle.
 *   To start at (or collapse into) the 3-dot icon, each item carries an offset equal to the
 *   distance from its final slot to the 3-dot icon: (slotsBelow * step + itemHeight).
 * - Tablet (alignToStart = true): The 3-dot icon is above the column in the NavigationRail.
 *   Each item carries an upward offset equal to the distance to the rail anchor:
 *   -(slotsAbove * step + itemHeight).
 */
internal fun overflowItemAnchorOffsetY(
    index: Int,
    totalItems: Int,
    alignToStart: Boolean,
    itemHeight: Int,
): Int {
    if (totalItems <= 0 || itemHeight <= 0) return 0
    val step = (itemHeight * OVERFLOW_MENU_ANCHOR_STEP_FACTOR).roundToInt()
    return if (alignToStart) {
        val slotsAbove = index.coerceAtLeast(0)
        -(slotsAbove * step + itemHeight)
    } else {
        val slotsBelow = (totalItems - 1 - index).coerceAtLeast(0)
        slotsBelow * step + itemHeight
    }
}

/**
 * Animated list of overflow destinations that expands upward from the nav bar's
 * "More" (⋮) 3-dot toggle with a macOS dock item maximizing and minimizing transition.
 *
 * Designed using Material Design 3 Expressive Navigation and Library action chip
 * standards — pills feature interactive press scale, smooth shape morphing,
 * adaptive surface container colors, TV D-pad focus glow, and 22dp Tabler icons
 * paired with semi-bold typography.
 *
 * Maximizing (enter): Starts from the 3-dot icon (bottom-end on phone) with scale expanding
 * from 0.2 to full size with macOS dock spring curve, cascading from bottom to top.
 * Minimizing (exit): Shrinks back down into the 3-dot icon from top to bottom.
 */
@Composable
fun OverflowMenuItems(
    entries: List<OverflowMenuItem>,
    modifier: Modifier = Modifier,
    alignToStart: Boolean = false,
    visible: Boolean = true,
) {
    val transformOrigin = overflowMenuTransformOrigin(alignToStart)
    val totalItems = entries.size

    Column(
        horizontalAlignment = if (alignToStart) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(
            space = 6.dp,
            alignment = if (alignToStart) Alignment.Top else Alignment.Bottom,
        ),
        modifier = modifier,
    ) {
        entries.forEachIndexed { index, entry ->
            val enterStagger = overflowEnterStaggerIndex(index, totalItems, alignToStart)
            val exitStagger = overflowExitStaggerIndex(index, totalItems, alignToStart)

            val enterDelay = enterStagger * OVERFLOW_MENU_ENTER_DELAY_STEP_MS
            val exitDelay = exitStagger * OVERFLOW_MENU_EXIT_DELAY_STEP_MS

            val state = remember(entry.key) { MutableTransitionState(initialState = false) }
            state.targetState = visible

            AnimatedVisibility(
                visibleState = state,
                enter = slideInVertically(
                    animationSpec = tween(
                        durationMillis = OVERFLOW_MENU_ENTER_DURATION_MS,
                        delayMillis = enterDelay,
                        easing = MacOsDockSlideEnterEasing,
                    ),
                    initialOffsetY = { overflowItemAnchorOffsetY(index, totalItems, alignToStart, it) },
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = OVERFLOW_MENU_FADE_DURATION_MS,
                        delayMillis = enterDelay,
                    ),
                ) + scaleIn(
                    animationSpec = tween(
                        durationMillis = OVERFLOW_MENU_ENTER_DURATION_MS,
                        delayMillis = enterDelay,
                        easing = MacOsDockScaleEnterEasing,
                    ),
                    initialScale = OVERFLOW_MENU_INITIAL_SCALE,
                    transformOrigin = transformOrigin,
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = OVERFLOW_MENU_EXIT_DURATION_MS,
                        delayMillis = exitDelay,
                    ),
                ) + slideOutVertically(
                    animationSpec = tween(
                        durationMillis = OVERFLOW_MENU_EXIT_DURATION_MS,
                        delayMillis = exitDelay,
                        easing = MacOsDockExitEasing,
                    ),
                    targetOffsetY = { overflowItemAnchorOffsetY(index, totalItems, alignToStart, it) },
                ) + scaleOut(
                    animationSpec = tween(
                        durationMillis = OVERFLOW_MENU_EXIT_DURATION_MS,
                        delayMillis = exitDelay,
                        easing = MacOsDockExitEasing,
                    ),
                    targetScale = OVERFLOW_MENU_TARGET_EXIT_SCALE,
                    transformOrigin = transformOrigin,
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
