package com.raulshma.jellyplay.feature.subtitle.tester.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities

/**
 * Capability-gated subtitle style controls for the tester.
 * v1 duplicates the minimum controls needed (font size, position, offset).
 * Additional controls (color pickers, border, shadow, font) can be added here
 * by mirroring the blocks in SubtitleStyleSheet.kt.
 */
@Composable
fun StyleControls(
    style: SubtitleStyle,
    capabilities: EngineCapabilities,
    onStyleChange: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Font size: ${style.fontSize}")
        Slider(
            value = style.fontSize.toFloat(),
            onValueChange = { onStyleChange(style.copy(fontSize = it.toInt())) },
            valueRange = 12f..72f,
        )
        if (capabilities.supportsSubtitleVerticalPosition) {
            Text("Vertical position: ${"%.0f".format(style.verticalPosition * 100)}%")
            Slider(
                value = style.verticalPosition,
                onValueChange = { onStyleChange(style.copy(verticalPosition = it)) },
                valueRange = 0f..0.5f,
            )
        }
        Text("Offset: ${style.offsetMs}ms")
        Slider(
            value = style.offsetMs.toFloat(),
            onValueChange = { onStyleChange(style.copy(offsetMs = it.toLong())) },
            valueRange = -1000f..1000f,
        )
    }
}
