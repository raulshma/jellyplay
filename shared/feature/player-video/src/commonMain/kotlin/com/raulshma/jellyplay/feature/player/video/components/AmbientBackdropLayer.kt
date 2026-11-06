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
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.player.video.AMBIENT_BACKDROP_DECODE_PX
import com.raulshma.jellyplay.feature.player.video.shouldBlurAmbientBackdrop
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
 * The ambient backdrop's decode request: a square [AMBIENT_BACKDROP_DECODE_PX]²
 * box, hoisted as a single val so a recomposition doesn't allocate a fresh
 * Size per invocation (the same reason `MediaImage`'s DefaultDecodeSize is
 * hoisted). Coil fits the source WITHIN the bounds with the aspect preserved,
 * so the layer's bitmap never exceeds ~720 px on its longest side regardless
 * of the artwork's native resolution (a 4K still decodes around 720×405).
 */
private val AMBIENT_DECODE_SIZE = CoilSize(AMBIENT_BACKDROP_DECODE_PX, AMBIENT_BACKDROP_DECODE_PX)

/**
 * The ambient backdrop layer (#12): a blurred, scaled copy of the item's
 * artwork behind the video surface, so a letterboxed/pillarboxed video's
 * black bars carry a soft glow. The layer composes NOTHING when the policy
 * says off (disabled, unknown geometry, or the video fills the screen) —
 * zero effect is a zero-cost composition, not a transparent layer.
 *
 * The artwork rides `uiState.media.artworkUrl` — the item art the media
 * projection already carries (the tagged [ArtworkUrls] poster slot; the
 * item's image tag rides the URL). The player's
 * state has no separate backdrop URL (the projection's art seam is the
 * single image the session holds), and the fill scale makes the
 * art type immaterial.
 *
 * The decode is EXPLICITLY DOWNSCALED, per the decode policy in
 * AmbientBackdropPolicy.kt: [AMBIENT_DECODE_SIZE] requests the artwork
 * inside the 720² box, and the request still rides `performanceModeAware =
 * false` — but that flag now selects the pass-through branch of
 * `mediaImageDecodeSize` for a size the layer chose deliberately, rather
 * than a free pass for a full-resolution decode. (With the clamp armed, the
 * tiered fold would crush a sub-1080 request to the 256² floor; the explicit
 * bound replaces the clamp's job without ever being crushed.) The blur hides
 * the detail a larger decode would carry anyway — a tens-of-MB 4K bitmap
 * composited under every letterboxed playback bought nothing visible.
 *
 * The blur itself is tier-gated ([shouldBlurAmbientBackdrop]): LOW-tier
 * devices keep the downscaled artwork and the scrim but drop
 * `Modifier.blur` — an 80 dp full-screen RenderEffect is a per-frame GPU
 * fill-rate cost that the weakest hardware shouldn't pay under an already
 * playing video, and the layer's remaining composite (a static, dimmed
 * bitmap) is nearly free. The scrim runs in BOTH branches, so the unblurred
 * copy stays as subdued as the blurred one. Blur is Compose's
 * `Modifier.blur` — already a commonMain idiom in this
 * codebase (the companion dashboard's 80dp ambient), RenderEffect-backed on
 * Android and skia-backed on desktop, so the feature ships on BOTH targets
 * with no platform gate.
 *
 * [performanceTier] carries the session's resolved device tier —
 * `uiState.uiPrefs.performanceTuning.tier` at the player screen, the same
 * §5.8 resolution the engine config consumes (no new plumbing; the state's
 * pre-resolution posture is `VideoPerformanceTuning.NONE`, whose tier is
 * HIGH, so the HIGH default mirrors "unrestricted" exactly and a call site
 * that has not wired the signal renders the full treatment).
 *
 * Inserted directly BENEATH the engine surface in the player box (the call
 * site's composition order), so the video keeps its size and only the bars
 * area shows the blurred fill.
 */
@Composable
internal fun AmbientBackdropLayer(
    imageUrl: String?,
    videoAspect: Float?,
    enabled: Boolean,
    performanceTier: DevicePerformanceTier = DevicePerformanceTier.HIGH,
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
                .then(
                    // The tier gate resolves per composition (a tier flip
                    // mid-session recomposes this subtree only). The no-blur
                    // branch appends nothing — `Modifier.then(Modifier)` is
                    // the identity — so LOW tier pays no effect node at all.
                    if (shouldBlurAmbientBackdrop(performanceTier)) {
                        Modifier.blur(AMBIENT_BLUR_RADIUS)
                    } else {
                        Modifier
                    },
                ),
            contentScale = ContentScale.Crop,
            size = AMBIENT_DECODE_SIZE,
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
