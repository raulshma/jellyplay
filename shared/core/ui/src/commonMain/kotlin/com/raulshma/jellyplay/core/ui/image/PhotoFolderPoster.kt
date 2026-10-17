package com.raulshma.jellyplay.core.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Folder

private const val FOLDER_CELL_CORNER_RADIUS_DP = 4

/**
 * The 16 possible corner-radius combos for a ≤2×2 folder-poster cell grid,
 * precomputed at file level (the PhotoViewerScreen file-level-shapes idiom):
 * the old body built a [RoundedCornerShape] per cell per composition.
 * Indexed by a 4-bit mask of which corners are on the grid edge.
 */
private val folderCellShapes: List<RoundedCornerShape> = List(16) { mask ->
    val r = FOLDER_CELL_CORNER_RADIUS_DP.dp
    RoundedCornerShape(
        topStart = if (mask and 1 != 0) r else 0.dp,
        topEnd = if (mask and 2 != 0) r else 0.dp,
        bottomStart = if (mask and 4 != 0) r else 0.dp,
        bottomEnd = if (mask and 8 != 0) r else 0.dp,
    )
}

private fun folderCellShape(rowIndex: Int, rowCount: Int, colIndex: Int, colCount: Int): RoundedCornerShape {
    val top = rowIndex == 0
    val bottom = rowIndex == rowCount - 1
    val left = colIndex == 0
    val right = colIndex == colCount - 1
    val mask = (if (top && left) 1 else 0) or
        (if (top && right) 2 else 0) or
        (if (bottom && left) 4 else 0) or
        (if (bottom && right) 8 else 0)
    return folderCellShapes[mask]
}

@Composable
fun PhotoFolderPoster(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalPlatformContext.current
    val gap = 2.dp
    val urls = remember(imageUrls) { imageUrls.take(4) }

    if (urls.isEmpty()) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Tabler.Outline.Folder,
                contentDescription = contentDescription,
                modifier = Modifier.padding(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val rows = remember(urls) {
        when (urls.size) {
            1 -> listOf(listOf(urls[0]))
            2 -> listOf(urls)
            else -> {
                val mid = (urls.size + 1) / 2
                listOf(urls.subList(0, mid), urls.subList(mid, urls.size))
            }
        }
    }

    Column(
        modifier = modifier,
    ) {
        rows.forEachIndexed { rowIndex, rowUrls ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        top = if (rowIndex == 0) 0.dp else gap / 2,
                        bottom = if (rowIndex == rows.lastIndex) 0.dp else gap / 2,
                    ),
            ) {
                rowUrls.forEachIndexed { colIndex, url ->
                    val request = remember(url, context) {
                        ImageRequest.Builder(context)
                            .data(url)
                            .crossfade(false)
                            .size(CoilSize(200, 200))
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(
                                start = if (colIndex == 0) 0.dp else gap / 2,
                                end = if (colIndex == rowUrls.lastIndex) 0.dp else gap / 2,
                            )
                            .clip(folderCellShape(rowIndex, rows.size, colIndex, rowUrls.size)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
