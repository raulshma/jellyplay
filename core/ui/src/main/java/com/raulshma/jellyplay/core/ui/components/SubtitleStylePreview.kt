package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle

/**
 * Live preview of how [style] renders over a sample video frame. Lifted from
 * the onboarding `SubtitlesStep` and extended to honor the *full* SubtitleStyle
 * (background opacity, edge type/edge color, vertical position), not just
 * fontSize/fontColor. Shared between onboarding (SubtitlesStep) and the main
 * settings path (LanguageSettingsScreen subtitle group) so fine-tuning there
 * isn't blind — see analysis finding "Subtitle style sheet has no live preview".
 *
 * The background is a dark gradient stand-in for video content so light/dark
 * subtitle colors are both legible.
 */
@Composable
fun SubtitleStylePreview(
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
    sampleText: String = "The quick brown fox\njumps over the lazy dog.",
) {
    val bgColor = subtitleColorToCompose(style.backgroundColor).copy(alpha = style.backgroundOpacity)
    val fontColor = subtitleColorToCompose(style.fontColor)
    val edgeColor = subtitleColorToCompose(style.edgeColor)
    // verticalPosition is a 0..0.5 fraction of the preview height from the bottom.
    val bottomOffsetDp = (style.verticalPosition * 200f).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2.35f)
            .clip(ShapeCache.smooth16)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF1B2330), Color(0xFF0A0E16)),
                ),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .offset(y = -bottomOffsetDp)
                .background(bgColor, ShapeCache.smooth8)
                .padding(
                    horizontal = if (style.backgroundOpacity > 0f) 10.dp else 0.dp,
                    vertical = if (style.backgroundOpacity > 0f) 4.dp else 0.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = sampleText,
                fontSize = style.fontSize.coerceIn(12, 48).sp,
                color = fontColor,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge.copy(
                    shadow = when (style.edgeType) {
                        SubtitleEdgeType.NONE -> null
                        SubtitleEdgeType.OUTLINE -> androidx.compose.ui.graphics.Shadow(
                            color = edgeColor,
                            blurRadius = 4f,
                        )
                        SubtitleEdgeType.DROP_SHADOW -> androidx.compose.ui.graphics.Shadow(
                            color = edgeColor.copy(alpha = 0.8f),
                            blurRadius = 8f,
                            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                        )
                        SubtitleEdgeType.RAISED -> androidx.compose.ui.graphics.Shadow(
                            color = edgeColor.copy(alpha = 0.7f),
                            blurRadius = 2f,
                            offset = androidx.compose.ui.geometry.Offset(1.5f, 1.5f),
                        )
                        SubtitleEdgeType.DEPRESSED -> androidx.compose.ui.graphics.Shadow(
                            color = edgeColor.copy(alpha = 0.7f),
                            blurRadius = 2f,
                            offset = androidx.compose.ui.geometry.Offset(-1.5f, -1.5f),
                        )
                    },
                ),
            )
        }
    }
}

/** Map a [SubtitleColor] enum to its Compose [Color]. */
private fun subtitleColorToCompose(color: SubtitleColor): Color = when (color) {
    SubtitleColor.WHITE -> Color.White
    SubtitleColor.YELLOW -> Color.Yellow
    SubtitleColor.GREEN -> Color.Green
    SubtitleColor.CYAN -> Color.Cyan
    SubtitleColor.RED -> Color.Red
    SubtitleColor.BLACK -> Color.Black
    SubtitleColor.BLUE -> Color.Blue
}
