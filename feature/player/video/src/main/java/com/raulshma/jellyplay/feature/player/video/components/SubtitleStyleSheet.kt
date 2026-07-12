package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.components.DpadSlider
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import kotlin.math.roundToLong
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.AssOverrideMode
import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubtitleStyleSheet(
    currentStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
    capabilities: EngineCapabilities = EngineCapabilities(),
    onPickFont: () -> Unit = {},
) {
    var applyCustomStyle by remember { mutableStateOf(currentStyle.applyCustomStyle) }
    var fontSize by remember { mutableIntStateOf(currentStyle.fontSize) }
    var fontColor by remember { mutableStateOf(currentStyle.fontColor) }
    var backgroundColor by remember { mutableStateOf(currentStyle.backgroundColor) }
    var backgroundOpacity by remember { mutableFloatStateOf(currentStyle.backgroundOpacity) }
    var edgeType by remember { mutableStateOf(currentStyle.edgeType) }
    var edgeColor by remember { mutableStateOf(currentStyle.edgeColor) }
    var offsetMs by remember { mutableLongStateOf(currentStyle.offsetMs) }
    var verticalPosition by remember { mutableFloatStateOf(currentStyle.verticalPosition) }

    // --- ASS / rich styling additions (Task 9) ---
    var assOverride by remember { mutableStateOf(currentStyle.assOverride) }
    var fontFamilyName by remember { mutableStateOf(currentStyle.fontFamilyName) }
    var borderStyle by remember { mutableStateOf(currentStyle.borderStyle) }
    var borderWidth by remember { mutableFloatStateOf(currentStyle.borderWidth) }
    var shadowOffset by remember { mutableFloatStateOf(currentStyle.shadowOffset) }
    var bold by remember { mutableStateOf(currentStyle.bold) }
    var italic by remember { mutableStateOf(currentStyle.italic) }
    // ARGB free-form colors; null = use preset. (Swatch picker writes these.)
    var fontColorArgb by remember { mutableStateOf(currentStyle.fontColorArgb) }
    var backgroundColorArgb by remember { mutableStateOf(currentStyle.backgroundColorArgb) }
    var edgeColorArgb by remember { mutableStateOf(currentStyle.edgeColorArgb) }
    // Free-form color picker dialog state (hue/sat/val driven).
    var showFontColorPicker by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showEdgeColorPicker by remember { mutableStateOf(false) }

    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            focusRequester.tryRequestFocus("sheet")
        }
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Subtitle Settings",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(16.dp))

            // Engine renders subtitles but not full ASS (libVLC). Inform the user.
            if (!capabilities.supportsAssOverride && capabilities.supportsSubtitleStyle) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(ShapeCache.smooth8)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                ) {
                    Text(
                        "Full ASS/SSA styling is supported on ExoPlayer and mpv. " +
                            "This engine renders subtitles with basic styling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Style Override Toggle
            val toggleFocusState = rememberTvFocusState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCache.smooth8)
                    .then(if (isTv) Modifier.focusRequester(focusRequester).then(toggleFocusState.focusModifier).tvFocusIndicator(toggleFocusState, ShapeCache.smooth8).clickable {
                        applyCustomStyle = !applyCustomStyle
                        onStyleChange(currentStyle.copy(applyCustomStyle = applyCustomStyle))
                    } else Modifier)
                    .padding(vertical = 8.dp, horizontal = if (isTv) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Override Subtitle Styles",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        "Apply custom size, colors, and borders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = applyCustomStyle,
                    onCheckedChange = if (isTv) null else { checked ->
                        applyCustomStyle = checked
                        onStyleChange(currentStyle.copy(applyCustomStyle = checked))
                    },
                )
            }
            Spacer(Modifier.height(16.dp))

            Text(
                "Font Size: ${fontSize}sp",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isTv) {
                DpadSlider(
                    value = fontSize.toFloat(),
                    enabled = applyCustomStyle,
                    onValueChange = {
                        fontSize = it.toInt()
                        onStyleChange(currentStyle.copy(fontSize = fontSize))
                    },
                    valueRange = 16f..48f,
                    steps = 32,
                    dpadStep = 1f,
                    colors = SliderDefaults.colors(
                        thumbColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray,
                        activeTrackColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Slider(
                    value = fontSize.toFloat(),
                    enabled = applyCustomStyle,
                    onValueChange = { fontSize = it.toInt() },
                    onValueChangeFinished = {
                        onStyleChange(currentStyle.copy(fontSize = fontSize))
                    },
                    valueRange = 16f..48f,
                    steps = 32,
                    colors = SliderDefaults.colors(
                        thumbColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray,
                        activeTrackColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // --- Typography: font family + bold/italic (capability + override gated) ---
            if (applyCustomStyle) {
                if (capabilities.supportsFontFamily) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Font",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                    Spacer(Modifier.height(4.dp))
                    val fontRowFocus = rememberTvFocusState(focusedScale = 1.03f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCache.smooth8)
                            .then(fontRowFocus.focusModifier)
                            .tvFocusIndicator(fontRowFocus, ShapeCache.smooth8)
                            .clickable { onPickFont() }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            fontFamilyName ?: "Bundled Default",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Pick…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextStyleToggle(
                        label = "Bold",
                        selected = bold,
                    ) {
                        bold = it
                        onStyleChange(currentStyle.copy(bold = it))
                    }
                    TextStyleToggle(
                        label = "Italic",
                        selected = italic,
                    ) {
                        italic = it
                        onStyleChange(currentStyle.copy(italic = it))
                    }
                }
            }

            // --- ASS Override: SCALE keeps embedded styling, FORCE overrides it ---
            if (capabilities.supportsAssOverride && applyCustomStyle) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "ASS Styling",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssOverrideChip(
                        label = "Respect",
                        isSelected = assOverride == AssOverrideMode.SCALE,
                    ) {
                        assOverride = AssOverrideMode.SCALE
                        onStyleChange(currentStyle.copy(assOverride = AssOverrideMode.SCALE))
                    }
                    // FORCE (full override of ASS colors/edges/font) is only honored by
                    // engines that can apply user style overrides to ASS tracks (mpv via
                    // libass --ass-override=force). ExoPlayer renders ASS as-authored, so
                    // the chip would be a no-op there — hide it. Respect (SCALE) stays
                    // because font scale is honored on both engines.
                    if (capabilities.supportsAssStyleOverride) {
                        AssOverrideChip(
                            label = "Force",
                            isSelected = assOverride == AssOverrideMode.FORCE,
                        ) {
                            assOverride = AssOverrideMode.FORCE
                            onStyleChange(currentStyle.copy(assOverride = AssOverrideMode.FORCE))
                        }
                    }
                }
                Text(
                    if (assOverride == AssOverrideMode.SCALE)
                        "Keep embedded styling; apply only your size & position"
                    else
                        "Override embedded styling with your colors, font & borders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Honesty note for engines that render ASS but cannot override its
                // styling (ExoPlayer: ass-media 0.4.0 has no compile-time override API).
                if (!capabilities.supportsAssStyleOverride) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "On this engine, ASS/SSA subtitles render with their embedded " +
                            "styling. Custom colors, borders, and Force override apply to " +
                            "SRT/VTT only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Font Color",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleColor.entries.forEach { color ->
                    ColorChip(
                        color = Color(color.value),
                        // Free-form ARGB takes precedence when set.
                        isSelected = fontColorArgb == null && fontColor == color,
                        enabled = applyCustomStyle,
                        onClick = {
                            fontColor = color
                            fontColorArgb = null
                            onStyleChange(currentStyle.copy(fontColor = color, fontColorArgb = null))
                        },
                    )
                }
                // Free-form color picker swatch (capability-gated).
                if (capabilities.supportsFreeFormColors && capabilities.supportsAssStyleOverride && applyCustomStyle) {
                    ColorChip(
                        color = if (fontColorArgb != null) Color(fontColorArgb!!) else Color.Transparent,
                        isSelected = fontColorArgb != null,
                        enabled = true,
                        onClick = { showFontColorPicker = true },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Background Color",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleColor.entries.forEach { color ->
                    ColorChip(
                        color = Color(color.value),
                        // Free-form ARGB takes precedence when set.
                        isSelected = backgroundColorArgb == null && backgroundColor == color,
                        enabled = applyCustomStyle,
                        onClick = {
                            backgroundColor = color
                            backgroundColorArgb = null
                            onStyleChange(currentStyle.copy(backgroundColor = color, backgroundColorArgb = null))
                        },
                    )
                }
                // Free-form color picker swatch (capability-gated).
                if (capabilities.supportsFreeFormColors && capabilities.supportsAssStyleOverride && applyCustomStyle) {
                    ColorChip(
                        color = if (backgroundColorArgb != null) Color(backgroundColorArgb!!) else Color.Transparent,
                        isSelected = backgroundColorArgb != null,
                        enabled = true,
                        onClick = { showBackgroundColorPicker = true },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Background Opacity: ${(backgroundOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isTv) {
                DpadSlider(
                    value = backgroundOpacity,
                    enabled = applyCustomStyle,
                    onValueChange = {
                        backgroundOpacity = it
                        onStyleChange(currentStyle.copy(backgroundOpacity = backgroundOpacity))
                    },
                    valueRange = 0f..1f,
                    steps = 10,
                    dpadStep = 0.1f,
                    colors = SliderDefaults.colors(
                        thumbColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray,
                        activeTrackColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Slider(
                    value = backgroundOpacity,
                    enabled = applyCustomStyle,
                    onValueChange = { backgroundOpacity = it },
                    onValueChangeFinished = {
                        onStyleChange(currentStyle.copy(backgroundOpacity = backgroundOpacity))
                    },
                    valueRange = 0f..1f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray,
                        activeTrackColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Edge Type",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleEdgeType.entries.forEach { type ->
                    val isSelected = edgeType == type
                    val chipFocusState = rememberTvFocusState(focusedScale = 1.05f)
                    FilterChip(
                        selected = isSelected,
                        enabled = applyCustomStyle,
                        onClick = {
                            edgeType = type
                            onStyleChange(currentStyle.copy(edgeType = type))
                        },
                        label = {
                            Text(
                                when (type) {
                                    SubtitleEdgeType.NONE -> "None"
                                    SubtitleEdgeType.OUTLINE -> "Outline"
                                    SubtitleEdgeType.DROP_SHADOW -> "Shadow"
                                    SubtitleEdgeType.RAISED -> "Raised"
                                    SubtitleEdgeType.DEPRESSED -> "Depressed"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        shape = ShapeCache.smoothPill,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.Transparent,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            enabled = applyCustomStyle,
                            selected = isSelected,
                        ),
                        modifier = Modifier
                            .then(chipFocusState.focusModifier)
                            .then(Modifier.tvFocusIndicator(chipFocusState, ShapeCache.smoothPill)),
                    )
                }
            }

            if (applyCustomStyle && edgeType != SubtitleEdgeType.NONE) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Edge Color",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SubtitleColor.entries.forEach { color ->
                        ColorChip(
                            color = Color(color.value),
                            // Free-form ARGB takes precedence when set.
                            isSelected = edgeColorArgb == null && edgeColor == color,
                            enabled = applyCustomStyle,
                            onClick = {
                                edgeColor = color
                                edgeColorArgb = null
                                onStyleChange(currentStyle.copy(edgeColor = color, edgeColorArgb = null))
                            },
                        )
                    }
                    // Free-form color picker swatch (capability-gated).
                    if (capabilities.supportsFreeFormColors && capabilities.supportsAssStyleOverride && applyCustomStyle) {
                        ColorChip(
                            color = if (edgeColorArgb != null) Color(edgeColorArgb!!) else Color.Transparent,
                            isSelected = edgeColorArgb != null,
                            enabled = true,
                            onClick = { showEdgeColorPicker = true },
                        )
                    }
                }
            }

            // --- Border Style: outline+shadow, opaque box, or background box ---
            // Gated on supportsAssStyleOverride: ExoPlayer renders ASS as-authored,
            // so the border-style control would be a no-op on ASS tracks there.
            if (capabilities.supportsBorderStyles && capabilities.supportsAssStyleOverride && applyCustomStyle) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Border Style",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SubtitleBorderStyle.entries.forEach { bs ->
                        val label = when (bs) {
                            SubtitleBorderStyle.OUTLINE_AND_SHADOW -> "Outline+Shadow"
                            SubtitleBorderStyle.OPAQUE_BOX -> "Opaque Box"
                            SubtitleBorderStyle.BACKGROUND_BOX -> "Background Box"
                        }
                        val isSelected = borderStyle == bs
                        val chipFocusState = rememberTvFocusState(focusedScale = 1.05f)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                borderStyle = bs
                                onStyleChange(currentStyle.copy(borderStyle = bs))
                            },
                            label = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            shape = ShapeCache.smoothPill,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.Transparent,
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                enabled = true,
                                selected = isSelected,
                            ),
                            modifier = Modifier
                                .then(chipFocusState.focusModifier)
                                .then(Modifier.tvFocusIndicator(chipFocusState, ShapeCache.smoothPill)),
                        )
                    }
                }

                // Border width slider (0.0..6.0). Background box uses opacity, not width.
                if (borderStyle != SubtitleBorderStyle.BACKGROUND_BOX) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Border Width: ${"%.1f".format(borderWidth)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                    if (isTv) {
                        DpadSlider(
                            value = borderWidth,
                            onValueChange = {
                                borderWidth = it
                                onStyleChange(currentStyle.copy(borderWidth = borderWidth))
                            },
                            valueRange = 0f..6f,
                            steps = 59,
                            dpadStep = 0.2f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Slider(
                            value = borderWidth,
                            onValueChange = { borderWidth = it },
                            onValueChangeFinished = {
                                onStyleChange(currentStyle.copy(borderWidth = borderWidth))
                            },
                            valueRange = 0f..6f,
                            steps = 59,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Shadow offset slider (only relevant for outline+shadow).
                    if (borderStyle == SubtitleBorderStyle.OUTLINE_AND_SHADOW) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Shadow Offset: ${"%.1f".format(shadowOffset)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                        if (isTv) {
                            DpadSlider(
                                value = shadowOffset,
                                onValueChange = {
                                    shadowOffset = it
                                    onStyleChange(currentStyle.copy(shadowOffset = shadowOffset))
                                },
                                valueRange = 0f..4f,
                                steps = 39,
                                dpadStep = 0.2f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Slider(
                                value = shadowOffset,
                                onValueChange = { shadowOffset = it },
                                onValueChangeFinished = {
                                    onStyleChange(currentStyle.copy(shadowOffset = shadowOffset))
                                },
                                valueRange = 0f..4f,
                                steps = 39,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (capabilities.supportsSubtitleDelay) {
                Spacer(Modifier.height(12.dp))
                val offsetSec = offsetMs / 1000.0
                val offsetLabel = when {
                    offsetMs == 0L -> "0.0s"
                    offsetMs > 0 -> "+${"%.1f".format(offsetSec)}s"
                    else -> "${"%.1f".format(offsetSec)}s"
                }
                Text(
                    "Subtitle Offset: $offsetLabel",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (isTv) {
                    DpadSlider(
                        value = offsetMs.toFloat(),
                        onValueChange = {
                            offsetMs = (it / 100f).roundToLong() * 100
                            onStyleChange(currentStyle.copy(offsetMs = offsetMs))
                        },
                        valueRange = -10000f..10000f,
                        steps = 199,
                        dpadStep = 500f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Slider(
                        value = offsetMs.toFloat(),
                        onValueChange = { offsetMs = (it / 100f).roundToLong() * 100 },
                        onValueChangeFinished = {
                            onStyleChange(currentStyle.copy(offsetMs = offsetMs))
                        },
                        valueRange = -10000f..10000f,
                        steps = 199,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (capabilities.supportsSubtitleVerticalPosition) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Vertical Position: ${(verticalPosition * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (isTv) {
                    DpadSlider(
                        value = verticalPosition,
                        onValueChange = {
                            verticalPosition = it
                            onStyleChange(currentStyle.copy(verticalPosition = verticalPosition))
                        },
                        valueRange = 0f..0.4f,
                        dpadStep = 0.02f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Slider(
                        value = verticalPosition,
                        onValueChange = { verticalPosition = it },
                        onValueChangeFinished = {
                            onStyleChange(currentStyle.copy(verticalPosition = verticalPosition))
                        },
                        valueRange = 0f..0.4f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                val resetFocusState = rememberTvFocusState(focusedScale = 1.05f)
                FilterChip(
                    selected = false,
                    enabled = applyCustomStyle,
                    onClick = {
                        val default = SubtitleStyle(applyCustomStyle = true)
                        fontSize = default.fontSize
                        fontColor = default.fontColor
                        backgroundColor = default.backgroundColor
                        backgroundOpacity = default.backgroundOpacity
                        edgeType = default.edgeType
                        edgeColor = default.edgeColor
                        offsetMs = default.offsetMs
                        verticalPosition = default.verticalPosition
                        // Reset ASS / rich styling additions (Task 9).
                        assOverride = default.assOverride
                        fontFamilyName = default.fontFamilyName
                        borderStyle = default.borderStyle
                        borderWidth = default.borderWidth
                        shadowOffset = default.shadowOffset
                        bold = default.bold
                        italic = default.italic
                        fontColorArgb = default.fontColorArgb
                        backgroundColorArgb = default.backgroundColorArgb
                        edgeColorArgb = default.edgeColorArgb
                        onStyleChange(default)
                    },
                    label = { Text("Reset", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium) },
                    shape = ShapeCache.smoothPill,
                    modifier = Modifier
                        .then(resetFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(resetFocusState, ShapeCache.smoothPill)),
                )
            }
        }

        // Free-form color picker dialog (capability-gated entry point above).
        if (showFontColorPicker) {
            FreeFormColorPickerDialog(
                initialColor = if (fontColorArgb != null) Color(fontColorArgb!!) else Color(fontColor.value),
                onDismiss = { showFontColorPicker = false },
                onColorSelected = { picked ->
                    fontColorArgb = picked
                    onStyleChange(currentStyle.copy(fontColorArgb = picked))
                },
            )
        }
        if (showBackgroundColorPicker) {
            FreeFormColorPickerDialog(
                initialColor = if (backgroundColorArgb != null) Color(backgroundColorArgb!!) else Color(backgroundColor.value),
                onDismiss = { showBackgroundColorPicker = false },
                onColorSelected = { picked ->
                    backgroundColorArgb = picked
                    onStyleChange(currentStyle.copy(backgroundColorArgb = picked))
                },
            )
        }
        if (showEdgeColorPicker) {
            FreeFormColorPickerDialog(
                initialColor = if (edgeColorArgb != null) Color(edgeColorArgb!!) else Color(edgeColor.value),
                onDismiss = { showEdgeColorPicker = false },
                onColorSelected = { picked ->
                    edgeColorArgb = picked
                    onStyleChange(currentStyle.copy(edgeColorArgb = picked))
                },
            )
        }
    }
}

@Composable
private fun ColorChip(
    color: Color,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val alpha = if (enabled) 1f else 0.4f
    val focusState = rememberTvFocusState(focusedScale = 1.15f)
    Box(
        modifier = Modifier
            .size(if (isTv) 40.dp else 34.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha * color.alpha))
            .then(
                if (isSelected && enabled) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, Color.Gray.copy(alpha = 0.5f * alpha), CircleShape)
                }
            )
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

/**
 * Bold/Italic toggle for subtitle typography. Uses a FilterChip so D-pad focus
 * mirrors the existing Edge Type / Border Style chips.
 */
@Composable
private fun TextStyleToggle(
    label: String,
    selected: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        shape = ShapeCache.smoothPill,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            enabled = true,
            selected = selected,
        ),
        modifier = Modifier
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smoothPill)),
    )
}

/**
 * ASS Override mode chip (Respect = SCALE, Force = FORCE). Mirrors [ColorChip] focus handling.
 */
@Composable
private fun AssOverrideChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        shape = ShapeCache.smoothPill,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            enabled = true,
            selected = isSelected,
        ),
        modifier = Modifier
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smoothPill)),
    )
}

/**
 * Minimal HSV color picker dialog for free-form subtitle colors (v1).
 * Three sliders drive hue (0..360), saturation (0..1), value (0..1); the resulting
 * ARGB int is returned via [onColorSelected]. Closes itself on confirm or dismiss.
 */
@Composable
private fun FreeFormColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    val hsv = remember {
        floatArrayOf(0f, 1f, 1f).apply {
            android.graphics.Color.colorToHSV(initialColor.toArgb(), this)
        }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val previewColor = remember(hue, saturation, value) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a color") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(ShapeCache.smooth8)
                        .background(previewColor),
                )
                Spacer(Modifier.height(12.dp))
                Text("Hue", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Saturation", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Value", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onColorSelected(previewColor.toArgb())
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
