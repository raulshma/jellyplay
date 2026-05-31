package com.raulshma.jellyplay.core.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun MediaImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackUrls: List<String> = emptyList(),
    blurHash: String? = null,
    crossfade: Boolean = true,
    size: CoilSize = CoilSize(512, 512),
) {
    val context = LocalContext.current
    val imageRequest = remember(url, size, crossfade) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(crossfade)
            .size(size)
            .build()
    }

    val fallbackKey = fallbackUrls.joinToString("|")
    val allUrls = remember(url, fallbackKey) { listOf(url) + fallbackUrls }
    var currentIndex by remember(url, fallbackKey) { mutableIntStateOf(0) }
    var isError by remember(url, fallbackKey) { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (!blurHash.isNullOrEmpty()) {
            BlurHashImage(
                blurHash = blurHash,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }

        if (!isError && currentIndex < allUrls.size) {
            val currentRequest = if (currentIndex == 0) {
                imageRequest
            } else {
                remember(allUrls[currentIndex], size, crossfade) {
                    ImageRequest.Builder(context)
                        .data(allUrls[currentIndex])
                        .crossfade(crossfade)
                        .size(size)
                        .build()
                }
            }
            AsyncImage(
                model = currentRequest,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) {
                        if (currentIndex < allUrls.size - 1) {
                            currentIndex++
                        } else {
                            isError = true
                        }
                    }
                }
            )
        } else if (blurHash.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Tabler.Outline.User,
                    contentDescription = "Avatar Placeholder",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
