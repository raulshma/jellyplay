package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.ui.components.formatDurationMs
import com.raulshma.jellyplay.feature.player.video.PlatformBitmap
import com.raulshma.jellyplay.feature.player.video.asPlatformImageBitmap

/** The filmstrip's tile height; the width follows the manifest's tile aspect. */
private val FILMSTRIP_TILE_HEIGHT = 64.dp

/** Gap between adjacent tiles (one tile of "film"). */
private val FILMSTRIP_TILE_GAP = 2.dp

/**
 * The FILMSTRIP trickplay surface (#7): a horizontal strip of adjacent
 * sprite tiles centered on the seek position — the SAME
 * `TrickplayManager` tile lookups the bubble uses, composed as a Row instead
 * of a floating card ("no floating bubble chrome"). The tile window and its
 * slide-as-position-moves step are the policy's ([com.raulshma.jellyplay.feature.player.video.filmstripWindow]);
 * this composable only resolves the window's tiles (center first — the seek
 * position's tile is the one the user is aiming at) and renders them.
 *
 * Loading sits HERE, inside the visible overlay's own recompose scope: each
 * window change suspends-resolves only the tile bitmaps (the manager's
 * per-index cache makes re-renders free), never the parent composition.
 */
@Composable
internal fun TrickplayFilmstripOverlay(
    tileIndices: List<Int>,
    intervalMs: Int,
    tileAspect: Float,
    positionMs: Long,
    loadTile: suspend (positionMs: Long) -> PlatformBitmap?,
    modifier: Modifier = Modifier,
) {
    // Resolve the window's tiles; the state key resets per window so a slid
    // window re-renders immediately with placeholders for tiles not yet
    // decoded. Center-first load order: the tile at the seek position lands
    // before its neighbors.
    var tiles by remember(tileIndices) { mutableStateOf(tileIndices.map { null as PlatformBitmap? }) }
    LaunchedEffect(tileIndices, intervalMs) {
        val ordered = tileIndices.sortedBy { kotlin.math.abs(it - tileCenterIndex(tileIndices)) }
        for ((index, tileIndex) in ordered.withIndex()) {
            val bitmap = loadTile(tileIndex.toLong() * intervalMs.coerceAtLeast(1).toLong())
            tiles = tiles.toMutableList().also { list ->
                val slot = tileIndices.indexOf(tileIndex)
                if (slot in list.indices) list[slot] = bitmap
            }
            // Only yield between loads (the center slot's bitmap must paint
            // even if a neighbor load cancels); sequential-by-design.
            if (index != ordered.lastIndex) kotlinx.coroutines.yield()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FILMSTRIP_TILE_GAP),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(playerScrimColor().copy(alpha = 0.35f))
                .padding(FILMSTRIP_TILE_GAP),
        ) {
            val tileWidth = FILMSTRIP_TILE_HEIGHT * tileAspect
            // ONE keyed conversion (tiles are tiny; the list re-derives per
            // resolved tile) — a remember PER loop slot would shift positional
            // slots when a manifest's window size differs from the last drag's.
            val imageBitmaps = remember(tiles) { tiles.map { it?.asPlatformImageBitmap() } }
            imageBitmaps.forEach { imageBitmap ->
                Box(
                    modifier = Modifier
                        .width(tileWidth)
                        .height(FILMSTRIP_TILE_HEIGHT)
                        .clip(RoundedCornerShape(5.dp))
                        .background(playerScrimColor().copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(tileWidth)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
        Text(
            text = formatDurationMs(positionMs, paddedMinutes = true),
            style = MaterialTheme.typography.labelSmall,
            color = playerOnScrim().copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(playerScrimColor().copy(alpha = 0.4f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** The window's center tile (the seek position's tile) — the load order's anchor. */
private fun tileCenterIndex(window: List<Int>): Int = if (window.isEmpty()) 0 else window[window.size / 2]
