package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.view.KeyEvent

import kotlin.math.roundToLong
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
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

    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // ignore
            }
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
                color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            val fontSizeFocusState = rememberTvFocusState()
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
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isTv) fontSizeFocusState.focusModifier else Modifier)
                    .then(if (isTv) Modifier.tvFocusIndicator(fontSizeFocusState) else Modifier)
                    .onPreviewKeyEvent { keyEvent ->
                        if (isTv && applyCustomStyle && keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    fontSize = (fontSize - 1).coerceIn(16, 48)
                                    onStyleChange(currentStyle.copy(fontSize = fontSize))
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    fontSize = (fontSize + 1).coerceIn(16, 48)
                                    onStyleChange(currentStyle.copy(fontSize = fontSize))
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "Font Color",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleColor.entries.forEach { color ->
                    ColorChip(
                        color = Color(color.value),
                        isSelected = fontColor == color,
                        enabled = applyCustomStyle,
                        onClick = {
                            fontColor = color
                            onStyleChange(currentStyle.copy(fontColor = color))
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Background Color",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleColor.entries.forEach { color ->
                    ColorChip(
                        color = Color(color.value),
                        isSelected = backgroundColor == color,
                        enabled = applyCustomStyle,
                        onClick = {
                            backgroundColor = color
                            onStyleChange(currentStyle.copy(backgroundColor = color))
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Background Opacity: ${(backgroundOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            val opacityFocusState = rememberTvFocusState()
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
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isTv) opacityFocusState.focusModifier else Modifier)
                    .then(if (isTv) Modifier.tvFocusIndicator(opacityFocusState) else Modifier)
                    .onPreviewKeyEvent { keyEvent ->
                        if (isTv && applyCustomStyle && keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    backgroundOpacity = (backgroundOpacity - 0.1f).coerceIn(0f, 1f)
                                    onStyleChange(currentStyle.copy(backgroundOpacity = backgroundOpacity))
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    backgroundOpacity = (backgroundOpacity + 0.1f).coerceIn(0f, 1f)
                                    onStyleChange(currentStyle.copy(backgroundOpacity = backgroundOpacity))
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Edge Type",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                            .then(if (isTv) chipFocusState.focusModifier else Modifier)
                            .then(if (isTv) Modifier.tvFocusIndicator(chipFocusState, ShapeCache.smoothPill) else Modifier),
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
                            isSelected = edgeColor == color,
                            enabled = applyCustomStyle,
                            onClick = {
                                edgeColor = color
                                onStyleChange(currentStyle.copy(edgeColor = color))
                            },
                        )
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
                val offsetFocusState = rememberTvFocusState()
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isTv) offsetFocusState.focusModifier else Modifier)
                        .then(if (isTv) Modifier.tvFocusIndicator(offsetFocusState) else Modifier)
                        .onPreviewKeyEvent { keyEvent ->
                            if (isTv && keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        offsetMs = (offsetMs - 500L).coerceIn(-10000L, 10000L)
                                        onStyleChange(currentStyle.copy(offsetMs = offsetMs))
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        offsetMs = (offsetMs + 500L).coerceIn(-10000L, 10000L)
                                        onStyleChange(currentStyle.copy(offsetMs = offsetMs))
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        },
                )
            }

            if (capabilities.supportsSubtitleVerticalPosition) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Vertical Position: ${(verticalPosition * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
                val positionFocusState = rememberTvFocusState()
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isTv) positionFocusState.focusModifier else Modifier)
                        .then(if (isTv) Modifier.tvFocusIndicator(positionFocusState) else Modifier)
                        .onPreviewKeyEvent { keyEvent ->
                            if (isTv && keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        verticalPosition = (verticalPosition - 0.02f).coerceIn(0f, 0.4f)
                                        onStyleChange(currentStyle.copy(verticalPosition = verticalPosition))
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        verticalPosition = (verticalPosition + 0.02f).coerceIn(0f, 0.4f)
                                        onStyleChange(currentStyle.copy(verticalPosition = verticalPosition))
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        },
                )
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
                        onStyleChange(default)
                    },
                    label = { Text("Reset", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium) },
                    shape = ShapeCache.smoothPill,
                    modifier = Modifier
                        .then(if (isTv) resetFocusState.focusModifier else Modifier)
                        .then(if (isTv) Modifier.tvFocusIndicator(resetFocusState, ShapeCache.smoothPill) else Modifier),
                )
            }
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
