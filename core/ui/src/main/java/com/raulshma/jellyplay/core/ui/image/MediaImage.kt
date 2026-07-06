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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.isOriginal
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
    // Default decode size lowered from 512² to 384² — visually
    // indistinguishable for posters and ~30% less decode memory. At lower
    // densities / TV (where isTv() is true and grids are denser) 512² was
    // roughly 2× over-sampling, doubling decode time and memory for each card
    // visible in a horizontal row of 8.
    size: CoilSize = CoilSize(384, 384),
    colorFilter: ColorFilter? = null,
    placeholderIcon: ImageVector = Tabler.Outline.User,
) {
    val performanceMode = com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode.current
    // Performance mode lowers decode size to save memory, but callers that
    // explicitly request [Size.ORIGINAL] (e.g. the full-screen photo viewer)
    // need the unmodified resolution — capping them to 256² produces blurry
    // photos. Clamp only the fixed-pixel defaults, never ORIGINAL.
    val effectiveSize = when {
        size.isOriginal -> size
        performanceMode -> CoilSize(256, 256)
        else -> size
    }
    val effectiveBlurHash = if (performanceMode) null else blurHash
    val effectiveCrossfade = if (performanceMode) false else crossfade
    val context = LocalContext.current
    val imageRequest = remember(url, effectiveSize, effectiveCrossfade) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(effectiveCrossfade)
            .size(effectiveSize)
            .build()
    }

    Box(modifier = modifier) {
        if (!effectiveBlurHash.isNullOrEmpty()) {
            BlurHashImage(
                blurHash = effectiveBlurHash,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }

        // The fallback chain lives in a dedicated child composable so a state
        // change (advance to the next URL after an Error) only recomposes this
        // subtree instead of the whole Box (including the BlurHash layer). The
        // parent [MediaImage] is now stateless wrt the fallback index.
        FallbackAsyncImage(
            primaryRequest = imageRequest,
            fallbackUrls = fallbackUrls,
            effectiveSize = effectiveSize,
            effectiveCrossfade = effectiveCrossfade,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            showPlaceholder = effectiveBlurHash.isNullOrEmpty(),
            placeholderIcon = placeholderIcon,
        )
    }
}

@Composable
private fun FallbackAsyncImage(
    primaryRequest: ImageRequest,
    fallbackUrls: List<String>,
    effectiveSize: CoilSize,
    effectiveCrossfade: Boolean,
    contentDescription: String?,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    showPlaceholder: Boolean,
    placeholderIcon: ImageVector = Tabler.Outline.User,
) {
    val context = LocalContext.current
    val fallbackKey = remember(fallbackUrls) { fallbackUrls.joinToString("|") }
    val allUrls = remember(primaryRequest.data, fallbackKey) { listOf(primaryRequest.data.toString()) + fallbackUrls }
    var currentIndex by remember(primaryRequest.data, fallbackKey) { mutableIntStateOf(0) }
    var isError by remember(primaryRequest.data, fallbackKey) { mutableStateOf(false) }

    if (!isError && currentIndex < allUrls.size) {
        val currentRequest = if (currentIndex == 0) {
            primaryRequest
        } else {
            remember(allUrls[currentIndex], effectiveSize, effectiveCrossfade) {
                ImageRequest.Builder(context)
                    .data(allUrls[currentIndex])
                    .crossfade(effectiveCrossfade)
                    .size(effectiveSize)
                    .build()
            }
        }
        AsyncImage(
            model = currentRequest,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            colorFilter = colorFilter,
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
    } else if (showPlaceholder) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = "Image Placeholder",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
