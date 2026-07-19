package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.ui.animation.pressScaleValue
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus

// Thin delegating re-exports of the canonical settings row components, which now live in
// `core/ui/.../components/SettingItems.kt` so non-settings surfaces (e.g. admin user-detail) can
// reuse the same TV-aware focus/press-scale/bring-into-view/marquee machinery. The `internal`
// wrappers preserve existing call sites within this module without import changes.
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingListItem(
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
) = com.raulshma.jellyplay.core.ui.components.SettingListItem(
    icon = icon,
    title = title,
    subtitle = subtitle,
    index = index,
    count = count,
    trailingText = trailingText,
    isDestructive = isDestructive,
    highlighted = highlighted,
    modifier = modifier,
    onClick = onClick,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingToggleItem(
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
) = com.raulshma.jellyplay.core.ui.components.SettingToggleItem(
    icon = icon,
    title = title,
    subtitle = subtitle,
    checked = checked,
    index = index,
    count = count,
    highlighted = highlighted,
    enabled = enabled,
    modifier = modifier,
    onCheckedChange = onCheckedChange,
    onClick = onClick,
)

@Composable
internal fun SettingReorderableToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    index: Int = 0,
    count: Int = 1,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "reorderableToggleScale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "reorderableToggleAlpha",
    )
    val iconColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "reorderableToggleIconColor",
    )

    val shape = expressiveListShape(index, count, innerRadius = 0.dp)

    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(32.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    onDragStart()
                                },
                                onDragEnd = {
                                    onDragEnd()
                                },
                                onDragCancel = {
                                    onDragEnd()
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Tabler.Outline.GripVertical,
                        contentDescription = "Reorder section",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDragging) 0.9f else 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
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
                this.alpha = if (isDragging) 0.85f else pressAlpha
            }
            .clip(shape)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick ?: { onCheckedChange(!checked) },
            ),
    )
}

@Composable
internal fun SettingInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    index: Int = 0,
    count: Int = 1,
) {
    val shape = expressiveListShape(index, count, innerRadius = 0.dp)

    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AdvancedSettingsToggleButton(
    showAdvanced: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val userMessageBus = LocalUserMessageBus.current
    val tooltipText = if (showAdvanced) "Hide advanced settings" else "Show advanced settings"
    val onClickAction = {
        onToggle()
        val toastMessage = if (showAdvanced) "Advanced settings hidden" else "Advanced settings shown"
        userMessageBus.info(toastMessage)
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(tooltipText)
            }
        },
        state = rememberTooltipState(),
    ) {
        if (isTv) {
            val focusState = rememberTvFocusState(focusedScale = 1.15f)
            Box(
                modifier = modifier
                    .size(36.dp)
                    .then(focusState.focusModifier)
                    .tvFocusIndicator(focusState, ShapeCache.smooth10)
                    .background(
                        color = if (showAdvanced) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = ShapeCache.smooth10
                    )
                    .clickable(onClick = onClickAction),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (showAdvanced) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                    contentDescription = tooltipText,
                    tint = if (showAdvanced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            IconButton(
                onClick = onClickAction,
                modifier = modifier,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (showAdvanced) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (showAdvanced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (showAdvanced) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                    contentDescription = tooltipText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
internal fun HiddenSettingsHint(
    hiddenCount: Int,
    onShowAdvanced: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .focusIndicator()
            .clickable(onClick = onShowAdvanced)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Tabler.Outline.EyeOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$hiddenCount advanced setting${if (hiddenCount != 1) "s" else ""} hidden",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

