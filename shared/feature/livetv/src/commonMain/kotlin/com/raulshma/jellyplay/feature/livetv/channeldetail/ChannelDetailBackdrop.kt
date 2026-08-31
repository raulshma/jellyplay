package com.raulshma.jellyplay.feature.livetv.channeldetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.ui.image.MediaImage

/**
 * Full-bleed backdrop behind the channel detail content. Shows the
 * currently-airing program's image (via [backdropUrl]) with a crossfade when
 * the program changes and a scrim gradient fading into [backgroundColorState].
 *
 * Mirrors the detail screen's [com.raulshma.jellyplay.feature.details.DetailBackdrop]
 * treatment so the channel detail feels native. Note these are Primary/poster
 * images (the only image type programs/channels expose), so [blurHash] is used
 * as a placeholder while the full image loads.
 */
@Composable
internal fun ChannelDetailBackdrop(
    backdropUrl: String,
    blurHash: String?,
    backgroundColorState: State<Color>,
    backdropHeightDp: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(backdropHeightDp),
    ) {
        AnimatedContent(
            targetState = backdropUrl,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "channelBackdrop",
        ) { url ->
            if (url.isNotBlank()) {
                MediaImage(
                    url = url,
                    contentDescription = null,
                    blurHash = blurHash,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    size = CoilSize(1920, 1080),
                )
            } else {
                // No image at all: solid dim background so the scrim still reads.
                Box(Modifier.fillMaxSize().drawBehind { drawRect(backgroundColorState.value.copy(alpha = 0.6f)) })
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val color = backgroundColorState.value
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                color.copy(alpha = 0.4f),
                                color.copy(alpha = 0.9f),
                                color,
                            ),
                            startY = (backdropHeightDp - 200.dp).toPx(),
                            endY = backdropHeightDp.toPx(),
                        ),
                    )
                },
        )
    }
}
