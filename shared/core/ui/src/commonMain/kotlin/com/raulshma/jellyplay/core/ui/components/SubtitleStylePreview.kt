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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.resolveAgainst

/**
 * Live preview of how [style] renders over a sample video frame. Lifted from
 * the onboarding `SubtitlesStep` and extended to honor the *full* SubtitleStyle
 * (background opacity, edge type/edge color, vertical position), not just
 * fontSize/fontColor. Shared between onboarding (SubtitlesStep) and the main
 * settings path (LanguageSettingsScreen subtitle group) so fine-tuning there
 * isn't blind.
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
    // Resolve through the shared entry point so the preview matches what the
    // player overlay renders for the same input. Previously this hand-rolled its
    // own color/edge/weight mapping and ignored the *Argb fields + applyCustomStyle.
    val resolved = style.resolveAgainst()
    val bgColor = resolvedColor(resolved.backgroundColorArgb).copy(alpha = resolved.backgroundAlpha)
    val fontColor = resolvedColor(resolved.fontColorArgb)
    // verticalPosition is a 0..0.5 fraction of the preview height from the bottom.
    val bottomOffsetDp = (resolved.verticalPosition * 200f).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2.35f)
            .clip(ShapeCache.smooth16)
            .background(
                Brush.verticalGradient(
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
                    horizontal = if (resolved.backgroundAlpha > 0f) 10.dp else 0.dp,
                    vertical = if (resolved.backgroundAlpha > 0f) 4.dp else 0.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = sampleText,
                fontSize = resolved.fontSizeSp.coerceIn(12, 48).sp,
                color = fontColor,
                textAlign = TextAlign.Center,
                fontWeight = resolvedFontWeight(resolved.bold),
                style = MaterialTheme.typography.bodyLarge.copy(
                    shadow = resolvedShadow(resolved),
                ),
            )
        }
    }
}

/** Map a [SubtitleColor] enum to its Compose [Color]. Shared by the preview and pickers. */
fun subtitleColorToCompose(color: SubtitleColor): Color = when (color) {
    SubtitleColor.WHITE -> Color.White
    SubtitleColor.YELLOW -> Color.Yellow
    SubtitleColor.GREEN -> Color.Green
    SubtitleColor.CYAN -> Color.Cyan
    SubtitleColor.RED -> Color.Red
    SubtitleColor.BLACK -> Color.Black
    SubtitleColor.BLUE -> Color.Blue
}
