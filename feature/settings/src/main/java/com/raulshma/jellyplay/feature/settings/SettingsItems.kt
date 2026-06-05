package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.ui.animation.pressScaleValue
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
internal fun SettingListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    index: Int = 0,
    count: Int = 1,
    trailingText: String? = null,
    isDestructive: Boolean = false,
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

    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = headlineColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = pressAlpha
            }
            .clip(shape)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    )
}

@Composable
internal fun SettingToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    index: Int = 0,
    count: Int = 1,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
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
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "toggleIconColor",
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
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = pressAlpha
            }
            .clip(shape)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick?.invoke() ?: onCheckedChange(!checked) },
    )
}

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

