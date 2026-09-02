package com.raulshma.jellyplay.feature.onboarding.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.components.AccentColorPicker
import com.raulshma.jellyplay.core.ui.components.ColorStylePicker
import com.raulshma.jellyplay.feature.onboarding.supportsDynamicColor
import com.raulshma.jellyplay.feature.onboarding.generated.resources.Res
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_contrast
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_dynamic_theming
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_dynamic_theming_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_home_hero
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_home_hero_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_oled_mode
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_oled_mode_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_theme
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_theme_dark
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_theme_light
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_theme_scheduled
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_theme_system
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_appearance_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppearanceStep(
    themeMode: ThemeMode,
    dynamicTheming: Boolean,
    oledMode: Boolean,
    contrastLevel: ContrastLevel,
    accentColorSwatch: String,
    colorStyle: ColorStyle,
    homeHeroEnabled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicThemingChange: (Boolean) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onContrastLevelChange: (ContrastLevel) -> Unit,
    onAccentColorSwatchChange: (String) -> Unit,
    onColorStyleChange: (ColorStyle) -> Unit,
    onHomeHeroEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OnboardingStepScaffold(
            title = stringResource(Res.string.onboarding_appearance_title),
            subtitle = stringResource(Res.string.onboarding_appearance_subtitle),
            icon = Tabler.Outline.Palette,
            onNext = {},
        ) {
            Text(
                text = stringResource(Res.string.onboarding_appearance_theme),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val selected = mode == themeMode
                    OnboardingOptionCard(
                        label = when (mode) {
                            ThemeMode.SYSTEM -> stringResource(Res.string.onboarding_appearance_theme_system)
                            ThemeMode.LIGHT -> stringResource(Res.string.onboarding_appearance_theme_light)
                            ThemeMode.DARK -> stringResource(Res.string.onboarding_appearance_theme_dark)
                            ThemeMode.SCHEDULED -> stringResource(Res.string.onboarding_appearance_theme_scheduled)
                        },
                        icon = when (mode) {
                            ThemeMode.SYSTEM -> Tabler.Outline.BrightnessHalf
                            ThemeMode.LIGHT -> Tabler.Outline.BrightnessUp
                            ThemeMode.DARK -> Tabler.Outline.Moon
                            ThemeMode.SCHEDULED -> Tabler.Outline.Clock
                        },
                        selected = selected,
                        onClick = { onThemeModeChange(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.onboarding_appearance_contrast),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ContrastLevel.entries.forEach { level ->
                    val selected = level == contrastLevel
                    OnboardingOptionCard(
                        label = level.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = selected,
                        onClick = { onContrastLevelChange(level) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            AccentColorPicker(
                selectedSwatch = accentColorSwatch,
                onSwatchSelected = onAccentColorSwatchChange,
            )

            Spacer(Modifier.height(8.dp))

            ColorStylePicker(
                selectedStyle = colorStyle,
                onStyleSelected = onColorStyleChange,
            )

            Spacer(Modifier.height(8.dp))

            if (supportsDynamicColor) {
                OnboardingToggleRow(
                    title = stringResource(Res.string.onboarding_appearance_dynamic_theming),
                    subtitle = stringResource(Res.string.onboarding_appearance_dynamic_theming_subtitle),
                    checked = dynamicTheming,
                    onCheckedChange = onDynamicThemingChange,
                )
            }

            OnboardingToggleRow(
                title = stringResource(Res.string.onboarding_appearance_oled_mode),
                subtitle = stringResource(Res.string.onboarding_appearance_oled_mode_subtitle),
                checked = oledMode,
                onCheckedChange = onOledModeChange,
            )

            OnboardingToggleRow(
                title = stringResource(Res.string.onboarding_appearance_home_hero),
                subtitle = stringResource(Res.string.onboarding_appearance_home_hero_subtitle),
                checked = homeHeroEnabled,
                onCheckedChange = onHomeHeroEnabledChange,
            )
        }
    }
}

@Composable
fun OnboardingOptionCard(
    label: String,
    icon: ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> AnimationTokens.CardPressScale
            selected -> 1f
            else -> 1f
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "optionCardScale",
    )
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "optionCardSelectionScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "optionCardColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "optionCardContentColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "optionCardBorder",
    )

    Box(
        modifier = modifier
            .height(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ShapeCache.smooth16)
            .background(containerColor)
            .drawBehind {
                if (selectionScale > 0.01f) {
                    val strokeWidth = 2.dp.toPx()
                    val cornerRadius = 16.dp.toPx()
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                        style = Stroke(width = strokeWidth),
                        alpha = selectionScale,
                    )
                }
            }
            .focusIndicator()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
            )
        }
    }
}

@Composable
fun OnboardingToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) AnimationTokens.CardPressScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "toggleRowScale",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "toggleRowBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ShapeCache.smooth16)
            .background(backgroundColor)
            .focusIndicator()
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}
