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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.isOriginal
import coil3.size.pxOrElse
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
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
        // parent [MediaImage] is now stateless wrt the fallback index. The
        // chain also resets (and re-issues the request) when the host screen
        // bumps [LocalImageReloadEpoch] — the manual escape from a chain that
        // exhausted its auto-retries on a persistent failure, since the URL
        // itself never changes. Transient failures no longer strand the image
        // on its blurHash: the chain schedules bounded automatic retries on
        // its own (see [FallbackAsyncImage]).
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

/**
 * Backoff schedule (ms) for [FallbackAsyncImage]'s automatic retries of a
 * chain that exhausted every URL on a transient failure (server restart,
 * network blip, momentary offline). Three attempts spread over ~55 s ride out
 * a restarting Jellyfin server, which is usually accepting requests again
 * inside that window; the growing gaps keep a slow server from eating the
 * whole budget in the first seconds. Once the budget is spent the chain parks
 * dead exactly as before the retries existed — the blurHash (or the icon
 * placeholder when no hash) — until a manual escape hatch: the host screen
 * bumping [LocalImageReloadEpoch] (pull-to-refresh), or the composable
 * leaving and re-entering composition (hero rotation, back-stack return).
 */
private val AUTO_RETRY_BACKOFF_MS = longArrayOf(5_000L, 15_000L, 35_000L)

/**
 * The backoff to wait before the next automatic retry, or null when the
 * budget is spent and the chain must park dead. `retriesLeft` counts DOWN
 * from [AUTO_RETRY_BACKOFF_MS]'s size, so a full budget walks the ladder in
 * order (5 s, 15 s, 35 s). Out-of-range budgets fail safe to null rather than
 * indexing the schedule. Pure so the composable's retry posture is
 * unit-testable without a composition.
 */
internal fun autoRetryBackoffMs(retriesLeft: Int): Long? =
    AUTO_RETRY_BACKOFF_MS.getOrNull(AUTO_RETRY_BACKOFF_MS.size - retriesLeft)

/**
 * All mutable chain + retry state for one [FallbackAsyncImage] lifetime, held
 * in a single `remember`ed holder so every writer and reader — the onState
 * callback, the backoff ladder, the lifecycle observer — captures the SAME
 * snapshot-state fields for the holder's whole lifetime. This is the stale-
 * delegate trap the file documents, closed by construction: the lifetime
 * boundary is the holder identity (URL set + reload epoch), not per-field
 * `remember` keys, so no closure can outlive the delegates it writes to.
 * A retry deliberately does NOT recreate the holder — [FallbackAsyncImage]
 * remounts [AsyncImage] via `key(retryEpoch)` instead, which re-issues the
 * request while every closure keeps observing live state.
 */
private class FallbackImageState {
    /** Index into the URL chain; advances on a mid-chain Error. */
    var currentIndex by mutableIntStateOf(0)

    /** True once every URL in the chain has errored (the parked state). */
    var isError by mutableStateOf(false)

    /** Bumped per auto-retry / reload-epoch-driven refresh; remounts AsyncImage. */
    var retryEpoch by mutableIntStateOf(0)

    /** Remaining auto-retry attempts; counts down the [AUTO_RETRY_BACKOFF_MS] ladder. */
    var retriesLeft by mutableIntStateOf(AUTO_RETRY_BACKOFF_MS.size)
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
    // The reload epoch keys the holder: bumping it discards the chain state
    // (error flag cleared, index back to the primary URL, retry budget
    // restored) and re-enters AsyncImage, which issues a fresh request. A
    // succeeded image keeps the same Coil model, so it is unaffected.
    val reloadEpoch = LocalImageReloadEpoch.current
    val imageState = remember(primaryRequest.data, fallbackKey, reloadEpoch) { FallbackImageState() }

    // Hoist the onState lambda out of the AsyncImage call so a fresh closure
    // isn't allocated on every recomposition of this (very common) subtree.
    // The lambda writes through the stable [imageState] holder, so it can
    // never write to discarded delegates.
    val onState = remember(allUrls.size, primaryRequest.data, reloadEpoch) { { state: AsyncImagePainter.State ->
        if (state is AsyncImagePainter.State.Error) {
            if (imageState.currentIndex < allUrls.size - 1) {
                imageState.currentIndex++
            } else {
                imageState.isError = true
            }
        }
    } }

    // Terminal-error auto-retry: a chain that exhausted every URL waits out
    // the next backoff step, then bumps [FallbackImageState.retryEpoch] to
    // remount AsyncImage and re-issue the request. The effect re-launches on
    // every error transition (each retry resets isError to false, and the
    // next failure flips it true again), a pending delay cancels with the
    // composition, and a spent budget parks the chain dead — the blurHash or
    // icon placeholder beneath — until a manual escape hatch. Transient
    // failures (server restart, network blip) therefore self-heal instead of
    // stranding the image on its blurHash until the host screen's next
    // pull-to-refresh.
    LaunchedEffect(imageState.isError) {
        if (!imageState.isError) return@LaunchedEffect
        val backoff = autoRetryBackoffMs(imageState.retriesLeft) ?: return@LaunchedEffect
        delay(backoff)
        imageState.retriesLeft--
        imageState.retryEpoch++
    }

    // Foreground-return refresh: a long offline ride (subway, wifi blip)
    // burns the ladder while the screen sits error-parked, so returning to
    // the app is the natural "try again" moment — every ON_RESUME restores
    // the full budget and, when the chain is parked, remounts AsyncImage
    // immediately. Bounded per resume (the ladder still exhausts before the
    // next park), and a no-op when the budget is untouched so a plain mount
    // or a foreground flip never re-requests a healthy image. The same
    // observer idiom as the hero's clip gate (HomeHeroController); all state
    // goes through the stable holder, so the long-lived closure can't go
    // stale.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (imageState.retriesLeft == AUTO_RETRY_BACKOFF_MS.size) return@LifecycleEventObserver
            imageState.retriesLeft = AUTO_RETRY_BACKOFF_MS.size
            if (imageState.isError) imageState.retryEpoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        try {
            awaitCancellation()
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!imageState.isError && imageState.currentIndex < allUrls.size) {
        val currentRequest = if (imageState.currentIndex == 0) {
            primaryRequest
        } else {
            remember(allUrls[imageState.currentIndex], effectiveSize, effectiveCrossfade) {
                ImageRequest.Builder(context)
                    .data(allUrls[imageState.currentIndex])
                    .crossfade(effectiveCrossfade)
                    .size(effectiveSize)
                    .build()
            }
        }
        // key(retryEpoch): a retry keeps the same (successful or failed)
        // Coil model, and an equal model alone would be a no-op for a still-
        // composed AsyncImage — the key forces a remount so the re-issued
        // request actually runs. Mid-chain fallback advances keep the same
        // key and restart through the model change instead.
        key(imageState.retryEpoch) {
            AsyncImage(
                model = currentRequest,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                colorFilter = colorFilter,
                onState = onState,
            )
        }
    } else if (showPlaceholder && imageState.retriesLeft <= 0) {
        // Only park on the placeholder once the retry budget is spent; while
        // retries remain, render nothing so the blurHash beneath (or the
        // surrounding surface when there is none — the same look as a load
        // in flight) keeps showing through the pending retry.
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
