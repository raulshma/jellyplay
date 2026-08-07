package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ColorFilter
import com.composables.icons.tabler.outline.Palette
import com.composables.icons.tabler.outline.Wand
import com.raulshma.jellyplay.core.designsystem.theme.AccentColorSwatch
import com.raulshma.jellyplay.core.ui.R
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Composable
fun AccentColorPicker(
    selectedSwatch: String,
    onSwatchSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.core_ui_accent_color_title),
    subtitle: String = stringResource(R.string.core_ui_accent_color_subtitle),
    icon: ImageVector = Tabler.Outline.Palette,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val swatches = AccentColorSwatch.entries
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(count = swatches.size, key = { swatches[it].name }, contentType = { "swatch" }) { i ->
                val swatch = swatches[i]
                val isSelected = (selectedSwatch.lowercase() == swatch.name.lowercase()) ||
                    (selectedSwatch == "dynamic" && swatch == AccentColorSwatch.DYNAMIC)

                if (swatch == AccentColorSwatch.DYNAMIC) {
                    DynamicSwatchCircle(
                        isSelected = isSelected,
                        onClick = { onSwatchSelected("dynamic") }
                    )
                } else {
                    SwatchCircle(
                        swatch = swatch,
                        isSelected = isSelected,
                        onClick = { onSwatchSelected(swatch.name.lowercase()) }
                    )
                }
            }
        }
    }
}

@Composable
fun SwatchCircle(
    swatch: AccentColorSwatch,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val colorLong = if (darkTheme) swatch.darkColor else swatch.lightColor
    val color = Color(colorLong)

    val tvFocusState = rememberTvFocusState(focusedScale = 1.15f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else if (isSelected) 1.1f else 1.0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatchScale"
    )

    val borderStroke = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), CircleShape)
    }

    val isLight = (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f) > 0.5f
    val checkTint = if (isLight) Color.Black else Color.White

    Box(
        modifier = modifier
            .size(44.dp)
            // each swatch was an unlabeled 44dp Box — TalkBack
            // only announced "selected". Expose the colour name as the node's
            // content description + RadioButton role + selected state so the
            // picker reads "Sapphire Blue, selected" / "Coral Orange".
            .semantics(mergeDescendants = true) {
                contentDescription = swatch.displayName
                role = Role.RadioButton
                selected = isSelected
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(borderStroke)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            // Decorative check — the merged-descendants semantics above already
            // convey selected state, so this icon must be unlabeled or TalkBack
            // would read "selected" twice.
            Icon(
                imageVector = Tabler.Outline.Check,
                contentDescription = null,
                tint = checkTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun DynamicSwatchCircle(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    swatchName: String = stringResource(R.string.core_ui_dynamic_colors),
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.15f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else if (isSelected) 1.1f else 1.0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "dynamicSwatchScale"
    )

    val borderStroke = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), CircleShape)
    }

    val gradient = Brush.sweepGradient(
        colors = listOf(
            Color(0xFF4285F4), // Blue
            Color(0xFF34A853), // Green
            Color(0xFFFBBC05), // Yellow
            Color(0xFAEA4335), // Red
            Color(0xFF4285F4) // Blue
        )
    )

    Box(
        modifier = modifier
            .size(44.dp)
            // same accessibility treatment as SwatchCircle — the
            // Dynamic swatch announces its label + selected state for TalkBack.
            .semantics(mergeDescendants = true) {
                contentDescription = swatchName
                role = Role.RadioButton
                selected = isSelected
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(borderStroke)
            .padding(4.dp)
            .clip(CircleShape)
            .background(gradient)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative wand — merged-descendants semantics already label the node.
            Icon(
                imageVector = Tabler.Outline.Wand,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ColorStylePicker(
    selectedStyle: ColorStyle,
    onStyleSelected: (ColorStyle) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.core_ui_color_style_title),
    subtitle: String = stringResource(R.string.core_ui_color_style_subtitle),
    icon: ImageVector = Tabler.Outline.ColorFilter,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val styles = ColorStyle.entries
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(count = styles.size, key = { styles[it].name }, contentType = { "style" }) { i ->
                val style = styles[i]
                val isSelected = style == selectedStyle

                val tvFocusState = rememberTvFocusState(focusedScale = 1.05f)
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1.0f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "styleChipScale"
                )

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "styleChipContainerColor"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "styleChipContentColor"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(ShapeCache.smooth14)
                        .background(containerColor)
                        .then(tvFocusState.focusModifier)
                        .tvFocusIndicator(tvFocusState, ShapeCache.smooth14)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onStyleSelected(style) }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = style.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun SynthwaveAccentPicker(
    selectedAccent: String,
    onAccentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Tabler.Outline.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.core_ui_synthwave_accent_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.core_ui_synthwave_accent_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val accents = listOf(
            "magenta" to Color(0xFFFF007F), // Neon Magenta
            "cyan" to Color(0xFF00F0FF), // Neon Cyan
            "violet" to Color(0xFF9D00FF), // Neon Violet
            "orange" to Color(0xFFFF5E00) // Neon Orange
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(count = accents.size, key = { accents[it].first }, contentType = { "accent" }) { i ->
                val (accentName, color) = accents[i]
                val isSelected = selectedAccent.lowercase() == accentName.lowercase()

                val tvFocusState = rememberTvFocusState(focusedScale = 1.15f)
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.9f else if (isSelected) 1.1f else 1.0f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "synthwaveSwatchScale"
                )

                val borderStroke = if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), CircleShape)
                }

                val isLight = (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f) > 0.5f
                val checkTint = if (isLight) Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        // mirror SwatchCircle a11y semantics so
                        // TalkBack reads "ocean, selected" instead of just
                        // "selected" (these themed pickers were missed when
                        // SwatchCircle / DynamicSwatchCircle were labelled).
                        .semantics(mergeDescendants = true) {
                            contentDescription = accentName
                            role = Role.RadioButton
                            selected = isSelected
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .then(borderStroke)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(tvFocusState.focusModifier)
                        .tvFocusIndicator(tvFocusState, CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onAccentSelected(accentName) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Tabler.Outline.Check,
                            // Decorative check — the merged-descendants semantics
                            // above already convey selected state.
                            contentDescription = null,
                            tint = checkTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SoothingAccentPicker(
    selectedAccent: String,
    onAccentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Tabler.Outline.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.core_ui_soothing_accent_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.core_ui_soothing_accent_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val accents = listOf(
            "ocean" to Color(0xFF1877F2),
            "lavender" to Color(0xFF8B7FE8),
            "sage" to Color(0xFF4CAF6E),
            "coral" to Color(0xFFE85D5D),
            "amber" to Color(0xFFE8A43A),
            "rose" to Color(0xFFE85A8A)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(count = accents.size, key = { accents[it].first }, contentType = { "accent" }) { i ->
                val (accentName, color) = accents[i]
                val isSelected = selectedAccent.lowercase() == accentName.lowercase()

                val tvFocusState = rememberTvFocusState(focusedScale = 1.15f)
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.9f else if (isSelected) 1.1f else 1.0f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "soothingSwatchScale"
                )

                val borderStroke = if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), CircleShape)
                }

                val isLight = (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f) > 0.5f
                val checkTint = if (isLight) Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        // mirror SwatchCircle a11y semantics so
                        // TalkBack reads "sage, selected" instead of just
                        // "selected" (themed pickers were missed when
                        // SwatchCircle / DynamicSwatchCircle were labelled).
                        .semantics(mergeDescendants = true) {
                            contentDescription = accentName
                            role = Role.RadioButton
                            selected = isSelected
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .then(borderStroke)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(tvFocusState.focusModifier)
                        .tvFocusIndicator(tvFocusState, CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onAccentSelected(accentName) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Tabler.Outline.Check,
                            // Decorative check — the merged-descendants semantics
                            // above already convey selected state.
                            contentDescription = null,
                            tint = checkTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
