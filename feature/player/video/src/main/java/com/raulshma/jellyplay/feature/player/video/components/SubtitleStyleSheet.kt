package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubtitleStyleSheet(
    currentStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    var fontSize by remember { mutableIntStateOf(currentStyle.fontSize) }
    var fontColor by remember { mutableStateOf(currentStyle.fontColor) }
    var backgroundColor by remember { mutableStateOf(currentStyle.backgroundColor) }
    var backgroundOpacity by remember { mutableFloatStateOf(currentStyle.backgroundOpacity) }
    var edgeType by remember { mutableStateOf(currentStyle.edgeType) }
    var edgeColor by remember { mutableStateOf(currentStyle.edgeColor) }
    var offsetMs by remember { mutableLongStateOf(currentStyle.offsetMs) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Subtitle Settings",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))

            Text("Font Size: ${fontSize}sp", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { fontSize = it.toInt() },
                onValueChangeFinished = {
                    onStyleChange(currentStyle.copy(fontSize = fontSize))
                },
                valueRange = 16f..48f,
                steps = 32,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Text("Font Color", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleColor.entries.forEach { color ->
                    ColorChip(
                        color = Color(color.value),
                        isSelected = fontColor == color,
                        onClick = {
                            fontColor = color
                            onStyleChange(currentStyle.copy(fontColor = color))
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Background Color", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleColor.entries.forEach { color ->
                    ColorChip(
                        color = Color(color.value),
                        isSelected = backgroundColor == color,
                        onClick = {
                            backgroundColor = color
                            onStyleChange(currentStyle.copy(backgroundColor = color))
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Background Opacity: ${(backgroundOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = backgroundOpacity,
                onValueChange = { backgroundOpacity = it },
                onValueChangeFinished = {
                    onStyleChange(currentStyle.copy(backgroundOpacity = backgroundOpacity))
                },
                valueRange = 0f..1f,
                steps = 10,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text("Edge Type", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleEdgeType.entries.forEach { type ->
                    FilterChip(
                        selected = edgeType == type,
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
                                fontSize = 12.sp,
                            )
                        },
                    )
                }
            }

            if (edgeType != SubtitleEdgeType.NONE) {
                Spacer(Modifier.height(8.dp))
                Text("Edge Color", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SubtitleColor.entries.forEach { color ->
                        ColorChip(
                            color = Color(color.value),
                            isSelected = edgeColor == color,
                            onClick = {
                                edgeColor = color
                                onStyleChange(currentStyle.copy(edgeColor = color))
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            val offsetSec = offsetMs / 1000.0
            val offsetLabel = when {
                offsetMs == 0L -> "0.0s"
                offsetMs > 0 -> "+${"%.1f".format(offsetSec)}s"
                else -> "${"%.1f".format(offsetSec)}s"
            }
            Text("Subtitle Offset: $offsetLabel", style = MaterialTheme.typography.bodyMedium)
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

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        val default = SubtitleStyle()
                        fontSize = default.fontSize
                        fontColor = default.fontColor
                        backgroundColor = default.backgroundColor
                        backgroundOpacity = default.backgroundOpacity
                        edgeType = default.edgeType
                        edgeColor = default.edgeColor
                        offsetMs = default.offsetMs
                        onStyleChange(default)
                    },
                    label = { Text("Reset", fontSize = 12.sp) },
                )
            }
        }
    }
}

@Composable
private fun ColorChip(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                }
            )
            .clickable(onClick = onClick),
    )
}
