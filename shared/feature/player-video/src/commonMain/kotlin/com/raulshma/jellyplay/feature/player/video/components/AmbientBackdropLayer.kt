package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.player.video.shouldShowAmbientBackdrop

/**
 * The blur radius for the ambient copy — the same heavy blur the cast
 * companion dashboard's background uses, so the two ambient surfaces read as
 * one treatment.
 */
private val AMBIENT_BLUR_RADIUS = 80.dp

/** The black dim over the blurred copy, keeping it behind the actual video. */
private const val AMBIENT_DIM_ALPHA = 0.55f

/**
 * The ambient backdrop layer (#12): a blurred, scaled copy of the item's
 * artwork behind the video surface, so a letterboxed/pillarboxed video's
 * black bars carry a soft glow. The layer composes NOTHING when the policy
 * says off (disabled, unknown geometry, or the video fills the screen) —
 * zero effect is a zero-cost composition, not a transparent layer.
 *
 * The artwork rides `uiState.media.artworkUrl` — the item art the media
 * projection already carries (the `getImageUrl(id, 400)` seam). The player's
 * state has no separate backdrop URL (the projection's art seam is the
 * single image the session holds), and the heavy blur + fill scale makes the
 * art type immaterial.
 *
 * Blur is Compose's `Modifier.blur` — already a commonMain idiom in this
 * codebase (the companion dashboard's 80dp ambient), RenderEffect-backed on
 * Android and skia-backed on desktop, so the feature ships on BOTH targets
 * with no platform gate.
 *
 * Inserted directly BENEATH the engine surface in the player box (the call
 * site's composition order), so the video keeps its size and only the bars
 * area shows the blurred fill. [performanceModeAware = false] — a single
 * full-bleed background must not be crushed to the 256² performance decode.
 */
@Composable
internal fun AmbientBackdropLayer(
    imageUrl: String?,
    videoAspect: Float?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (imageUrl.isNullOrEmpty()) return
    if (!enabled) return
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenAspect = if (maxWidth.value > 0f && maxHeight.value > 0f) {
            maxWidth.value / maxHeight.value
        } else {
            null
        }
        // The aspect comparison is the pure policy; the value form (px
        // ratio) resolves here where the constraints live. An unknown or
        // empty constraint pair keeps the layer off.
        if (!shouldShowAmbientBackdrop(
                videoAspect = videoAspect,
                screenAspect = screenAspect,
                enabled = true,
            )
        ) {
            return@BoxWithConstraints
        }
        MediaImage(
            url = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(AMBIENT_BLUR_RADIUS),
            contentScale = ContentScale.Crop,
            performanceModeAware = false,
            crossfade = false,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = AMBIENT_DIM_ALPHA)),
        )
    }
}
