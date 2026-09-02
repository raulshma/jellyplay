package com.raulshma.jellyplay.core.ui.image
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_image_placeholder

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
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.isOriginal
import coil3.size.pxOrElse
import coil3.size.Size as CoilSize
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun MediaImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackUrls: List<String> = emptyList(),
    blurHash: String? = null,
    // The blurHash placeholder normally inherits the image's contentScale, but
    // callers that fit the image (e.g. the photo viewer) want the blur to fill
    // the whole surface as a background rather than letterboxing with the photo.
    blurHashContentScale: ContentScale = contentScale,
    crossfade: Boolean = true,
    // Default decode size lowered from 512² to 384² — visually
    // indistinguishable for posters and ~30% less decode memory. At lower
    // densities / TV (where isTv() is true and grids are denser) 512² was
    // roughly 2× over-sampling, doubling decode time and memory for each card
    // visible in a horizontal row of 8.
    size: CoilSize = CoilSize(384, 384),
    colorFilter: ColorFilter? = null,
    placeholderIcon: ImageVector = Tabler.Outline.Photo,
    // Performance mode lowers decode size to save memory. Full-screen images
    // (hero, detail backdrop) opt out via performanceModeAware = false — capping
    // a single full-bleed image to 256² looks badly blurry, and there is only
    // ever one such image on screen so the memory saving is negligible.
    performanceModeAware: Boolean = true,
) {
    val performanceMode = com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode.current
    val reducedMotion = com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current
    // Performance mode lowers decode size to save memory, but callers that
    // explicitly request [Size.ORIGINAL] (e.g. the full-screen photo viewer)
    // need the unmodified resolution — capping them to 256² produces blurry
    // photos. Clamp only the fixed-pixel defaults, never ORIGINAL.
    val effectiveSize = when {
        size.isOriginal -> size
        performanceMode && performanceModeAware -> {
            // Performance mode clamp, tiered by the requested size so full-bleed
            // callers (hero, detail backdrop) still get a sharp-enough decode
            // instead of being crushed to poster-thumbnail resolution.
            val largest = maxOf(
                size.width.pxOrElse { 0 },
                size.height.pxOrElse { 0 },
            )
            if (largest >= 1080) CoilSize(768, 768) else CoilSize(256, 256)
        }
        else -> size
    }
    val effectiveBlurHash = if (performanceMode) null else blurHash
    // Crossfade is a motion effect, so disable it under either performance mode
    // or reduce motion (blurHash is a memory/quality optimization and stays
    // gated on performance mode alone).
    val effectiveCrossfade = if (reducedMotion) false else crossfade
    val context = LocalPlatformContext.current
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
                contentScale = blurHashContentScale,
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
    placeholderIcon: ImageVector = Tabler.Outline.Photo,
) {
    val context = LocalPlatformContext.current
    val fallbackKey = remember(fallbackUrls) { fallbackUrls.joinToString("|") }
    val allUrls = remember(primaryRequest.data, fallbackKey) { listOf(primaryRequest.data.toString()) + fallbackUrls }
    var currentIndex by remember(primaryRequest.data, fallbackKey) { mutableIntStateOf(0) }
    var isError by remember(primaryRequest.data, fallbackKey) { mutableStateOf(false) }

    // Hoist the onState lambda out of the AsyncImage call so a fresh closure
    // isn't allocated on every recomposition of this (very common) subtree.
    // currentIndex/isError are MutableState delegates, so reading them inside a
    // stable lambda still observes changes correctly.
    val onState = remember(allUrls.size) { { state: AsyncImagePainter.State ->
        if (state is AsyncImagePainter.State.Error) {
            if (currentIndex < allUrls.size - 1) {
                currentIndex++
            } else {
                isError = true
            }
        }
    } }

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
            onState = onState,
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
                contentDescription = stringResource(Res.string.core_ui_image_placeholder),
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
