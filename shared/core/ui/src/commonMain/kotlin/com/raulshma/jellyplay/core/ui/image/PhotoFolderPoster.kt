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

@Composable
fun PhotoFolderPoster(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalPlatformContext.current
    val gap = 2.dp
    val cornerRadius = 4.dp
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
                            .clip(
                                RoundedCornerShape(
                                    topStart = if (rowIndex == 0 && colIndex == 0) cornerRadius else 0.dp,
                                    topEnd = if (rowIndex == 0 && colIndex == rowUrls.lastIndex) cornerRadius else 0.dp,
                                    bottomStart = if (rowIndex == rows.lastIndex && colIndex == 0) cornerRadius else 0.dp,
                                    bottomEnd = if (rowIndex == rows.lastIndex && colIndex == rowUrls.lastIndex) cornerRadius else 0.dp,
                                )
                            ),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
