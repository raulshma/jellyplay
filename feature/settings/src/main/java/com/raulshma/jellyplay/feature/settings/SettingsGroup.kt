package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.animation.pressScaleValue
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
internal fun SettingsGroup(
    icon: ImageVector,
    title: String,
    summary: @Composable (() -> String)? = null,
    badge: @Composable (() -> Unit)? = null,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var lastInitiallyExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    LaunchedEffect(initiallyExpanded) {
        if (initiallyExpanded && initiallyExpanded != lastInitiallyExpanded) {
            expanded = true
        }
        lastInitiallyExpanded = initiallyExpanded
    }

    val iconColor by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "groupIconColor",
    )

    val iconBgColor by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "groupIconBgColor",
    )

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevronRotation",
    )

    val headerTvFocusState = rememberTvFocusState(focusedScale = 1.02f)
    val headerInteractionSource = remember { MutableInteractionSource() }
    val headerPressed by headerInteractionSource.collectIsPressedAsState()
    val headerScale by animateFloatAsState(
        targetValue = pressScaleValue(headerPressed, 0.98f),
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "headerPressScale",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth24)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = headerScale
                    scaleY = headerScale
                }
                .clip(ShapeCache.smooth16)
                .then(headerTvFocusState.focusModifier)
                .tvFocusIndicator(headerTvFocusState, ShapeCache.smooth16)
                .clickable(
                    interactionSource = headerInteractionSource,
                    indication = null,
                ) { expanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(ShapeCache.smooth12)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!expanded) {
                    summary?.invoke()?.let { summaryText ->
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (badge != null) {
                badge()
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Tabler.Outline.ChevronDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            ) + expandVertically(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
            exit = fadeOut(
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            ) + shrinkVertically(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                content()
            }
        }
    }
}
