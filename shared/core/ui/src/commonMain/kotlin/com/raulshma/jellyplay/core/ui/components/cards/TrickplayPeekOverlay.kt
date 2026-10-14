package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.TrickplayInfo

/**
 * One loaded trickplay preview (H13): the DECODED sprite sheet ([sheet]) plus
 * everything needed to crop the right tile cell out of it — the sheet's grid
 * spec ([info]), which thumbnail the preview shows ([thumbnailIndex], the
 * item's resume position resolved through the tile math), and the item the
 * tile belongs to ([itemId] — the loader stamps it so a row slot reusing a
 * composition can never show a predecessor's frame). The loader side (feature
 * modules — the fetch and decode live there, not here) produces one per
 * settled focus; this class is the render-side payload only.
 */
@Immutable
class TrickplayPeekTile(
    val sheet: ImageBitmap,
    val info: TrickplayInfo,
    val thumbnailIndex: Int,
    val itemId: String,
)

/**
 * H13's `TrickplayPeekOverlay`: the ~160 dp sprite crop that peeks above a
 * focused wide card's progress bar. The whole sprite sheet is drawn at
 * tile-cell display scale and offset so the wanted cell lands in the
 * viewport — the Box's smooth-clip masks the rest of the sheet, so no second
 * bitmap decode/crop pass is ever needed.
 *
 * Fade discipline: the overlay holds the LAST non-null [peek] through the
 * fade-out, so the loader flipping its state to null (the ~2.5 s hold elapsing
 * or a focus exit) animates the same frame away instead of popping it. While
 * alpha is 0 with no tile, nothing is composed.
 */
@Composable
internal fun TrickplayPeekOverlay(
    peek: TrickplayPeekTile?,
    modifier: Modifier = Modifier,
    /** The on-screen width of ONE tile cell (the overlay's width). */
    peekWidth: Dp = 160.dp,
) {
    // Retain the last tile so the fade-out has a frame to animate.
    var rendered by remember { mutableStateOf(peek) }
    if (peek != null) rendered = peek
    val alpha by animateFloatAsState(
        targetValue = if (peek != null) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "trickplayPeekFade",
    )
    val tile = rendered
    if (tile == null || alpha <= 0f) return

    val info = tile.info
    // Guards mirror the player's TrickplayManager: a malformed manifest
    // (zeros) must never divide by zero here either.
    val tilesPerSheet = (info.tileWidth * info.tileHeight).coerceAtLeast(1)
    val tileColumns = info.tileWidth.coerceAtLeast(1)
    val localIndex = tile.thumbnailIndex % tilesPerSheet
    val col = localIndex % tileColumns
    val row = localIndex / tileColumns
    val tileHeight = if (info.width > 0) {
        peekWidth * info.height.toFloat() / info.width
    } else {
        peekWidth * 9f / 16f
    }

    Box(
        modifier = modifier
            .size(width = peekWidth, height = tileHeight)
            // Alpha first so the hairline border fades with the frame.
            .graphicsLayer { this.alpha = alpha }
            .clip(ShapeCache.smooth8)
            .border(1.dp, Color.White.copy(alpha = 0.25f), ShapeCache.smooth8),
    ) {
        Image(
            bitmap = tile.sheet,
            contentDescription = null,
            // The sheet fills its computed display rect exactly; the parent
            // clip + offset below do the cropping.
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .requiredSize(
                    width = peekWidth * tileColumns,
                    height = tileHeight * info.tileHeight.coerceAtLeast(1),
                )
                .offset(x = -peekWidth * col, y = -tileHeight * row),
        )
    }
}

/**
 * The overlay's resting offset above the host card's progress bar — the bar
 * is 4 dp tall hugging the image's bottom edge; 10 dp clears it with the same
 * breathing room the card overlays (6 dp) use.
 */
internal val TrickplayPeekBottomPadding = 10.dp
