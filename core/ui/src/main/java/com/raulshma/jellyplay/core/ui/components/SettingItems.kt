package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronRight
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.ui.animation.pressScaleValue
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Canonical TV-aware settings row components, shared across feature modules.
 *
 * Moved here from `feature/settings/.../SettingsItems.kt` so non-settings surfaces (e.g. admin user
 * detail) can reuse the same focus/press-scale/bring-into-view/marquee machinery instead of
 * hand-rolling `ListItem` + `.clickable` rows that are invisible to the D-pad.
 *
 * Each item self-manages its own [FocusRequester] + [rememberTvFocusState] (focusedScale = 1.01f)
 * + [tvFocusIndicator]; callers do NOT pass focus modifiers. Pass `highlighted = true` to grab
 * focus and scroll the item into view (used by deep-link/highlight flows).
 */

/**
 * Drives the one-shot highlight glow used by [SettingListItemImpl] / [SettingToggleItemImpl]:
 * snaps to full intensity on `highlighted`, then animates down through a fixed keyframe curve so
 * the item pulses to draw attention for deep-link/scroll-into-view flows. Once animated it does
 * not re-fire until `highlighted` toggles back to false, so re-composition is cheap.
 *
 * Returns the current glow alpha in `0f..1f`; callers apply it to shadow/background/border.
 */
@Composable
private fun rememberHighlightGlow(highlighted: Boolean): Float {
    val highlightProgress = remember { Animatable(0f) }
    var hasAnimatedHighlight by rememberSaveable(highlighted) { mutableStateOf(false) }
    LaunchedEffect(highlighted) {
        if (highlighted && !hasAnimatedHighlight) {
            hasAnimatedHighlight = true
            highlightProgress.snapTo(1f)
            highlightProgress.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 2500
                    1f at 0 using FastOutSlowInEasing
                    0.65f at 600 using FastOutSlowInEasing
                    0.9f at 1200 using FastOutSlowInEasing
                    0.4f at 1800 using FastOutSlowInEasing
                    0f at 2500
                }
            )
        } else if (!highlighted) {
            hasAnimatedHighlight = false
            highlightProgress.snapTo(0f)
        }
    }
    return highlightProgress.value
}

/**
 * The shadow + background + border modifiers that visualize [rememberHighlightGlow]'s alpha.
 * Pass the glow alpha and the clip [shape]; returns [Modifier] (identity when `glowAlpha <= 0f`).
 */
private fun Modifier.highlightGlow(glowAlpha: Float, shape: androidx.compose.ui.graphics.Shape, primaryColor: Color): Modifier =
    this
        .then(
            if (glowAlpha > 0f) {
                Modifier.shadow(
                    elevation = 14.dp * glowAlpha,
                    shape = shape,
                    clip = false,
                    ambientColor = primaryColor.copy(alpha = 0.6f * glowAlpha),
                    spotColor = primaryColor.copy(alpha = 0.5f * glowAlpha),
                )
            } else Modifier
        )
        .clip(shape)
        .background(
            if (glowAlpha > 0f) primaryColor.copy(alpha = 0.2f * glowAlpha)
            else Color.Transparent
        )
        .then(
            if (glowAlpha > 0f) {
                Modifier.border(
                    width = 2.dp,
                    color = primaryColor.copy(alpha = glowAlpha),
                    shape = shape,
                )
            } else Modifier
        )

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    index: Int = 0,
    count: Int = 1,
    trailingText: String? = null,
    isDestructive: Boolean = false,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Inside a SettingsItemList, pick up the auto-incrementing index and the
    // caller-supplied total so call sites stop passing index/count by hand.
    val listIndex = LocalSettingsItemIndex.current
    val resolvedIndex = listIndex?.value ?: index
    val resolvedCount = if (listIndex != null) LocalSettingsItemCount.current else count
    if (listIndex != null) listIndex.intValue += 1
    SettingListItemImpl(
        icon = icon,
        title = title,
        subtitle = subtitle,
        index = resolvedIndex,
        count = resolvedCount,
        trailingText = trailingText,
        isDestructive = isDestructive,
        highlighted = highlighted,
        modifier = modifier,
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingListItemImpl(
    icon: ImageVector,
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    trailingText: String?,
    isDestructive: Boolean,
    highlighted: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = pressScaleValue(isPressed, 0.97f),
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "settingItemScale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "settingItemAlpha",
    )

    val headlineColor = if (isDestructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface
    val supportingColor = if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
    else MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor = if (isDestructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant

    val shape = expressiveListShape(index, count, innerRadius = 0.dp)

    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val glowAlpha = rememberHighlightGlow(highlighted)

    LaunchedEffect(highlighted) {
        if (highlighted) {
            // Wait for BOTH (a) the screen-level coarse group-scroll to bring this item into the
            // composition window and finish its animation, and (b) the parent SettingsGroup's
            // expand animation to settle. Without this delay the measured offset is still
            // mid-transition (or the item is not yet composed) and the bring-into-view either
            // misses its target or lands at the wrong position.
            kotlinx.coroutines.delay(400)
            focusRequester.tryRequestFocus("settings_item")
            bringIntoViewRequester.bringIntoView()
        }
    }

    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = headlineColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused),
            )
        },
        supportingContent = {
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
        },
        trailingContent = {
            if (trailingText != null) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Icon(
                Tabler.Outline.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = pressAlpha
            }
            .highlightGlow(glowAlpha, shape, primaryColor)
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoViewRequester)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    index: Int = 0,
    count: Int = 1,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val listIndex = LocalSettingsItemIndex.current
    val resolvedIndex = listIndex?.value ?: index
    val resolvedCount = if (listIndex != null) LocalSettingsItemCount.current else count
    if (listIndex != null) listIndex.intValue += 1
    SettingToggleItemImpl(
        icon = icon,
        title = title,
        subtitle = subtitle,
        checked = checked,
        index = resolvedIndex,
        count = resolvedCount,
        highlighted = highlighted,
        enabled = enabled,
        modifier = modifier,
        onCheckedChange = onCheckedChange,
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingToggleItemImpl(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    index: Int,
    count: Int,
    highlighted: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)?,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "toggleItemScale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "toggleItemAlpha",
    )
    val iconColor by animateColorAsState(
        targetValue = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "toggleIconColor",
    )
    val headlineColor = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.onSurface

    val shape = expressiveListShape(index, count, innerRadius = 0.dp)

    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val glowAlpha = rememberHighlightGlow(highlighted)

    LaunchedEffect(highlighted) {
        if (highlighted) {
            // Wait for BOTH (a) the screen-level coarse group-scroll to bring this item into the
            // composition window and finish its animation, and (b) the parent SettingsGroup's
            // expand animation to settle. Without this delay the measured offset is still
            // mid-transition (or the item is not yet composed) and the bring-into-view either
            // misses its target or lands at the wrong position.
            kotlinx.coroutines.delay(400)
            focusRequester.tryRequestFocus("settings_item")
            bringIntoViewRequester.bringIntoView()
        }
    }

    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = headlineColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused),
            )
        },
        supportingContent = {
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = pressAlpha
            }
            .highlightGlow(glowAlpha, shape, primaryColor)
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoViewRequester)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) { onClick?.invoke() ?: onCheckedChange(!checked) },
    )
}
