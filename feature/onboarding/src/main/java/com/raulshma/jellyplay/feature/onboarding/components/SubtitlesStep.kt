package com.raulshma.jellyplay.feature.onboarding.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle

@Composable
fun SubtitlesStep(
    subtitleStyle: SubtitleStyle,
    onSubtitleStyleChange: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OnboardingStepScaffold(
            title = "Subtitles",
            subtitle = "Customize subtitle appearance and defaults",
            icon = Tabler.Outline.Subtitles,
            onNext = {},
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCache.smooth16)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Subtitle Preview",
                    fontSize = subtitleStyle.fontSize.sp,
                    color = when (subtitleStyle.fontColor) {
                        SubtitleColor.WHITE -> Color.White
                        SubtitleColor.YELLOW -> Color.Yellow
                        SubtitleColor.GREEN -> Color.Green
                        SubtitleColor.CYAN -> Color.Cyan
                        SubtitleColor.RED -> Color.Red
                        SubtitleColor.BLACK -> Color.Black
                        SubtitleColor.BLUE -> Color.Blue
                    },
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Font Size",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = subtitleStyle.fontSize.toFloat(),
                onValueChange = { onSubtitleStyleChange(subtitleStyle.copy(fontSize = it.toInt())) },
                valueRange = 14f..48f,
                steps = 8,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Font Color",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(SubtitleColor.WHITE, SubtitleColor.YELLOW, SubtitleColor.GREEN, SubtitleColor.CYAN).forEach { color ->
                    val selected = color == subtitleStyle.fontColor
                    val displayColor = when (color) {
                        SubtitleColor.WHITE -> Color.White
                        SubtitleColor.YELLOW -> Color.Yellow
                        SubtitleColor.GREEN -> Color.Green
                        SubtitleColor.CYAN -> Color.Cyan
                        else -> Color.White
                    }
                    val borderColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "subtitleColorBorder_$color",
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .weight(1f)
                            .clip(ShapeCache.smooth8)
                            .background(displayColor)
                            .then(
                                if (selected) Modifier.background(borderColor, ShapeCache.smooth8)
                                else Modifier
                            )
                            .clickable { onSubtitleStyleChange(subtitleStyle.copy(fontColor = color)) }
                            .padding(3.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Edge Type",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SubtitleEdgeType.entries.forEach { edge ->
                    val selected = edge == subtitleStyle.edgeType
                    OnboardingOptionCard(
                        label = edge.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                        selected = selected,
                        onClick = { onSubtitleStyleChange(subtitleStyle.copy(edgeType = edge)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
