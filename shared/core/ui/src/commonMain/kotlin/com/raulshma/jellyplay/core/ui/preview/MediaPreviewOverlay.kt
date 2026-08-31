package com.raulshma.jellyplay.core.ui.preview
import com.raulshma.jellyplay.core.ui.components.formatOneDecimal
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_preview_new_format
import com.raulshma.jellyplay.core.ui.generated.resources.core_preview_release_to_close
import com.raulshma.jellyplay.core.ui.generated.resources.core_preview_series

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Heart
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.rememberDominantColor

import com.raulshma.jellyplay.core.ui.image.MediaImage
import kotlin.math.hypot
import kotlin.math.max

/**
 * The single, root-hosted press-and-hold "peek" overlay. Renders the current
 * [MediaPreviewController] state as a rich detail card that **morphs out of the
 * long-pressed card's on-screen position** ([MediaPreview.sourceBounds]) into a
 * centered resting spot, with a dominant-color glow, a radial vignette scrim,
 * and a spring scale. The live content behind is blurred by the host (see
 * [com.raulshma.jellyplay.navigation.JellyPlayApp]).
 *
 * Dismissal is **release-to-dismiss** (Instagram-style): the card that opened
 * the preview watches its own press interaction and calls
 * [MediaPreviewController.hide] when the finger lifts. The system Back button
 * is also wired here as a fallback.
 *
 * The overlay is purely visual — it consumes no pointer events itself, so the
 * originating card keeps ownership of the gesture. That is what makes the
 * lift-to-dismiss hand-off trivial and robust.
 */
@Composable
fun MediaPreviewOverlay(
    controller: MediaPreviewController,
    modifier: Modifier = Modifier,
) {
    val preview by controller.state.collectAsStateWithLifecycle()

    // Hold the last non-null snapshot so the exit animation renders the real
    // card rather than an empty frame when `preview` flips to null on dismiss.
    var lastPreview by remember { mutableStateOf<MediaPreview?>(null) }
    LaunchedEffect(preview) {
        if (preview != null) lastPreview = preview
    }

    com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler(enabled = preview != null) { controller.hide() }

    // Window size — needed to resolve sourceBounds (window coordinates) into the
    // overlay's own coordinate space and to drive the radial vignette.
    var windowSize by remember { mutableStateOf(IntSize.Zero) }

    // A SINGLE persistent progress driver owns the entire overlay's lifetime and
    // motion. progress = 0 → collapsed onto the source card; 1 → fully open.
    //
    // We deliberately do NOT use AnimatedVisibility here: it tears content down
    // on its own (faster) exit timeline, which (a) skips the enter morph because
    // animateFloatAsState snaps to its initial target on fresh composition, and
    // (b) truncates the exit morph. Instead the overlay stays composed while
    // `current != null`, animates progress 0→1 on open and 1→0 on dismiss, and
    // is only removed once progress settles back to 0 (so the close plays fully).
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    var current by remember { mutableStateOf<MediaPreview?>(null) }
    LaunchedEffect(preview) {
        if (preview != null) {
            // Open: adopt the target immediately and spring open.
            current = preview
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = peekSpring())
        } else if (current != null) {
            // Dismiss: spring closed, THEN drop the content so the morph plays fully.
            progress.animateTo(0f, animationSpec = peekSpring())
            current = null
            lastPreview = null
        }
    }

    if (current == null) return
    val snapshot = current ?: return

    val accent = rememberDominantColor(
        snapshot.backdropUrl ?: snapshot.posterUrl,
        itemId = snapshot.item.id,
    )
    // Held as a State and read only inside graphicsLayer lambdas below, so the
    // per-frame morph values invalidate the draw phase without recomposing the
    // overlay subtree.
    val progressState = progress.asState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { windowSize = it },
        contentAlignment = Alignment.Center,
    ) {
        // ── Scrim: radial vignette tinted by the artwork's dominant color ──
        // Purely visual; does NOT intercept touches, so the card behind still
        // receives the finger-up that drives release-to-dismiss. Fades with the
        // same progress so the backdrop dims in sync with the card opening.
        Scrim(
            accent = accent,
            size = windowSize,
            progress = progressState,
            modifier = Modifier.fillMaxSize(),
        )

        // ── The morphing preview card ──
        MorphingPreviewCard(
            preview = snapshot,
            accent = accent,
            windowSize = windowSize,
            progress = progressState,
        )
    }
}

/** The spring used for both the open and close morph — symmetric on purpose. */
private fun peekSpring() = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** Scrim opacity tracks progress so the backdrop dims in sync with the card. */
private fun scrimAlpha(progress: Float) = smoothstep(0f, 0.6f, progress)

/**
 * Translucent scrim with a soft radial vignette. Darkens the center slightly and
 * the edges more strongly, tinted by the artwork's [accent] color so the peek
 * feels color-connected to the card the user is holding. [progress] fades the
 * whole scrim in/out in sync with the card morph; it is read inside the layer
 * lambda so animation frames don't recompose the scrim.
 */
@Composable
private fun Scrim(
    accent: Color,
    size: IntSize,
    progress: State<Float>,
    modifier: Modifier = Modifier,
) {
    val center = if (size.width > 0) {
        Offset(size.width / 2f, size.height / 2f)
    } else Offset.Zero
    val radius = if (size.width > 0) {
        hypot(center.x.toDouble(), center.y.toDouble()).toFloat()
    } else 0f

    Box(
        modifier
            .graphicsLayer { this.alpha = scrimAlpha(progress.value) }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.35f),
                        Color.Black.copy(alpha = 0.78f),
                    ),
                    center = center,
                    radius = max(radius, 1f),
                )
            )
            // Subtle accent wash so the backdrop picks up the artwork's mood.
            .background(accent.copy(alpha = 0.06f)),
    )
}

/**
 * The preview card with a position-anchored spring morph. With [progress] = 0 it
 * sits collapsed onto the source card's bounds; with [progress] = 1 it is fully
 * open and centered. The card is scaled **uniformly** (single factor anchored on
 * width) so its content never distorts even though the source poster (2:3) and
 * the resting card (~square) have different aspect ratios — the morph reads like
 * a shared-element transition without the cross-subtree scope requirement.
 */
@Composable
private fun MorphingPreviewCard(
    preview: MediaPreview,
    accent: Color,
    windowSize: IntSize,
    progress: State<Float>,
    modifier: Modifier = Modifier,
) {
    val source = preview.sourceBounds
    val hasSource = source != null && source.width > 0 && source.height > 0

    val windowCx = windowSize.width / 2f
    val windowCy = windowSize.height / 2f

    PreviewCard(
        preview = preview,
        accent = accent,
        // Reveal metadata as the card grows past ~halfway, so the morph reads as
        // the card "opening up" rather than text popping in at full size.
        contentAlpha = { smoothstep(0.45f, 0.9f, progress.value) },
        modifier = modifier.graphicsLayer {
            val p = progress.value
            // Uniform scale from the source card's width to the resting card's
            // width — keeps the card's aspect ratio (no squash/stretch).
            val scale = if (hasSource && size.width > 0) {
                lerp(source.width / size.width, 1f, p)
            } else 1f

            // The card's laid-out center is (windowCx, windowCy) because the parent
            // centers it. Move it so the center tracks the morph: collapsed onto the
            // source card's center at progress=0, centered at progress=1.
            val displayedCx = if (hasSource) lerp(source.center.x, windowCx, p) else windowCx
            val displayedCy = if (hasSource) lerp(source.center.y, windowCy, p) else windowCy
            translationX = displayedCx - windowCx
            translationY = displayedCy - windowCy
            scaleX = scale
            scaleY = scale
        },
    )
}

/**
 * The rich detail card shown during the peek. Purely presentational — built
 * entirely from the [MediaPreview] snapshot (no network). The card fills its
 * intrinsic content size; the morph in [MorphingPreviewCard] scales/translates
 * it into place, so this composable itself is laid out at the resting size.
 */
@Composable
private fun PreviewCard(
    preview: MediaPreview,
    accent: Color,
    contentAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val item = preview.item
    val posterUrl = preview.posterUrl
    val backdropUrl = preview.backdropUrl
    val cardShape = ShapeCache.smooth24

    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .shadow(
                elevation = 24.dp,
                shape = cardShape,
                // Soft accent-colored glow ties the card to the scrim tint.
                ambientColor = accent.copy(alpha = 0.45f),
                spotColor = accent.copy(alpha = 0.55f),
            )
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // ── Backdrop / poster header ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            val headerUrl = backdropUrl ?: posterUrl
            if (!headerUrl.isNullOrBlank()) {
                MediaImage(
                    url = headerUrl,
                    contentDescription = item.name,
                    blurHash = preview.blurHash,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (backdropUrl != null) ContentScale.Crop else ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.2f)),
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.name.take(2).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Bottom gradient so the metadata reads cleanly over any backdrop.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )

            // Top-corner official-rating pill, sitting over the artwork.
            item.officialRating
                ?.takeIf { it.isNotBlank() }
                ?.let { rating ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(ShapeCache.smooth8)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = rating,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
        }

        // ── Metadata ──
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .graphicsLayer { alpha = contentAlpha() },
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            item.originalTitle
                ?.takeIf { it.isNotBlank() && !it.equals(item.name, ignoreCase = true) }
                ?.let { originalTitle ->
                    Text(
                        text = originalTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

            Spacer(Modifier.height(8.dp))

            // Info row: year • runtime • rating — same shape as the full detail
            // body, omitting any field that isn't present.
            InfoRow(item)

            item.genres
                .takeIf { it.isNotEmpty() }
                ?.let { genres ->
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        genres.take(3).forEach { genre ->
                            Box(
                                modifier = Modifier
                                    .clip(ShapeCache.smooth16)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }

            item.overview
                ?.takeIf { it.isNotBlank() }
                ?.let { overview ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.core_preview_release_to_close),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

/**
 * Compact `year • runtime • ♥ rating` row. Mirrors the info row in
 * [com.raulshma.jellyplay.feature.details.MediaDetailBody] but tolerates every
 * field being absent (Seerr items lack runtime/genres, for example). Separators
 * are interleaved only between actually-present segments so a missing field
 * never yields a double "• •".
 */
@Composable
private fun InfoRow(item: com.raulshma.jellyplay.core.model.MediaItem) {
    val isSeries = item.mediaType == MediaType.SERIES
    val runtimeMinutes = remember(item.runTimeTicks) {
        item.runTimeTicks?.let { it / 600_000_000 }?.takeIf { it > 0 }
    }
    // Resolve localized chip labels in composable scope; the buildList below
    // runs inside a non-composable remember block.
    val seriesLabel = stringResource(Res.string.core_preview_series)
    val seriesChip = if (isSeries) {
        val unplayed = item.unplayedItemCount
        if (unplayed == null || unplayed <= 0) seriesLabel
        else stringResource(Res.string.core_preview_new_format, unplayed)
    } else null

    val leadingChips = remember(item.year, isSeries, runtimeMinutes, seriesChip) {
        buildList {
            item.year?.let { add(it.toString()) }
            if (isSeries) {
                seriesChip?.let { add(it) }
            } else if (runtimeMinutes != null) {
                add("${runtimeMinutes}m")
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leadingChips.forEachIndexed { index, chip ->
            if (index > 0) Separator()
            InfoChip(chip)
        }
        item.communityRating?.let { rating ->
            if (leadingChips.isNotEmpty()) Separator()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Tabler.Outline.Heart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = remember(rating) { formatOneDecimal(rating.toDouble()) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}

@Composable
private fun Separator() {
    Text(
        text = "•",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
    )
}

// ── Small math helpers ──

/** Linear interpolation between [a] and [b]. */
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Smoothstep easing — eases in/out around [edge0], [edge1]. */
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
