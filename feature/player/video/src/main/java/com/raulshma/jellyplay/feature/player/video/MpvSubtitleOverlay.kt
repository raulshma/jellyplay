package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.designsystem.theme.smoothCornerShape
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.subtitle.VttTagParser

/**
 * Renders MPV/VLC-style cue text (plain strings) with per-engine subtitle
 * styling: outline / drop-shadow / raised / depressed edges, background, font
 * size, and vertical position.
 *
 * Extracted verbatim from `VideoPlayerScreen.kt` (recommendation #2 — decompose
 * the 1.7 kLOC screen into smaller, self-contained stateless composables). All
 * state is hoisted to the caller ([cues], [style], [visible]); the composable is
 * a pure function of its arguments.
 */
@Composable
internal fun BoxScope.MpvSubtitleOverlay(
    cues: List<String>,
    style: SubtitleStyle,
    visible: Boolean,
) {
    if (!visible || cues.isEmpty()) return

    val bottomPadding = (24 + style.verticalPosition.coerceIn(0f, 0.4f) * 240).dp
    val topPadding = bottomPadding

    if (cues.size >= 2) {
        SubtitleCueBox(
            text = cues[0],
            style = style,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 32.dp, end = 32.dp, bottom = bottomPadding),
        )
        SubtitleCueBox(
            text = cues[1],
            style = style,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 32.dp, end = 32.dp, top = topPadding),
        )
    } else {
        SubtitleCueBox(
            text = cues[0],
            style = style,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 32.dp, end = 32.dp, bottom = bottomPadding),
        )
    }
}

@Composable
private fun SubtitleCueBox(
    text: String,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    val backgroundOpacity = style.backgroundOpacity.coerceIn(0f, 1f)
    val backgroundColor = Color(style.backgroundColor.value)
        .copy(alpha = backgroundOpacity)
    val edgeColor = Color(style.edgeColor.value)
    val fontSize = style.fontSize.coerceIn(12, 56)
    val textStyle = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize + 4).sp,
        fontWeight = FontWeight.Bold,
    )

    val annotatedText = remember(text) { VttTagParser.parseAnnotated(text) }

    Box(
        modifier = modifier
            .then(
                if (backgroundOpacity > 0f) {
                    Modifier.background(backgroundColor, smoothCornerShape(6.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (style.edgeType) {
            SubtitleEdgeType.NONE -> Unit
            SubtitleEdgeType.OUTLINE -> {
                val textMeasurer = rememberTextMeasurer()
                val measuredText = remember(annotatedText, textStyle) {
                    textMeasurer.measure(annotatedText, textStyle)
                }
                val density = androidx.compose.ui.platform.LocalDensity.current
                Canvas(modifier = Modifier.matchParentSize()) {
                    SubtitleOutlineOffsets.forEach { (x, y) ->
                        val offsetPx = with(density) { Offset(x.dp.toPx(), y.dp.toPx()) }
                        drawText(
                            measuredText,
                            topLeft = offsetPx,
                            color = edgeColor,
                        )
                    }
                }
            }
            SubtitleEdgeType.DROP_SHADOW -> {
                SubtitleTextLayer(
                    text = annotatedText,
                    color = edgeColor.copy(alpha = 0.85f),
                    textStyle = textStyle,
                    modifier = Modifier.offset(2.dp, 2.dp),
                )
            }
            SubtitleEdgeType.RAISED -> {
                SubtitleTextLayer(
                    text = annotatedText,
                    color = edgeColor.copy(alpha = 0.75f),
                    textStyle = textStyle,
                    modifier = Modifier.offset(1.dp, 1.dp),
                )
            }
            SubtitleEdgeType.DEPRESSED -> {
                SubtitleTextLayer(
                    text = annotatedText,
                    color = edgeColor.copy(alpha = 0.75f),
                    textStyle = textStyle,
                    modifier = Modifier.offset((-1).dp, (-1).dp),
                )
            }
        }

        SubtitleTextLayer(
            text = annotatedText,
            color = Color(style.fontColor.value),
            textStyle = textStyle,
        )
    }
}

@Composable
private fun SubtitleTextLayer(
    text: AnnotatedString,
    color: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = TextAlign.Center,
        style = textStyle,
    )
}

private val SubtitleOutlineOffsets = listOf(
    -1 to -1,
    0 to -1,
    1 to -1,
    -1 to 0,
    1 to 0,
    -1 to 1,
    0 to 1,
    1 to 1,
)
