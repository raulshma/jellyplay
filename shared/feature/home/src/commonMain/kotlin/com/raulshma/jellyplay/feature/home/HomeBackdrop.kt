package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.raulshma.jellyplay.core.concurrency.Settlement
import com.raulshma.jellyplay.core.concurrency.settleDebounce
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkColors
import com.raulshma.jellyplay.core.designsystem.theme.rememberArtworkColors
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.AmbientColorBackdrop
import com.raulshma.jellyplay.core.ui.image.BlurHashBackdrop
import kotlinx.coroutines.flow.collectLatest

/**
 * Which visual treatment the home backdrop is currently showing. Used as the
 * [Crossfade] key so the orchestrator recomposes only when the *kind* of layer
 * changes (blurhash ↔ ambient ↔ none), not on every hero rotation.
 */
internal enum class BackdropLayer { NONE, BLUR_HASH, AMBIENT }

/**
 * The resolved inputs to [HomeBackdrop], snapped from `HomeUiState` + the hero
 * controller. Held in a [Stable] holder so passing it down doesn't widen
 * recomposition beyond the fields that actually changed.
 */
@Stable
internal data class HomeBackdropState(
    val enabled: Boolean,
    val performanceMode: Boolean,
    val oledMode: Boolean,
    val isLightTheme: Boolean,
    /** BlurHash of the current hero backdrop, if any. */
    val blurHash: String?,
    /** URL used to extract the ambient palette when no blur hash is available. */
    val backdropUrl: String?,
    /** The screen's resolved background colour (the flat fill behind this layer). */
    val backgroundColor: Color,
)

/**
 * Whether the backdrop should render at all. Suppressed when disabled, in
 * performance mode (matches `MediaImage`'s blurhash gating), and in light
 * themes. The colourful ambient/blurhash layers sit full-screen behind a
 * scrolling list, which destroys text contrast on a light background — so
 * light mode shows the plain [HomeBackdropState.backgroundColor] (the
 * resolved `colorScheme.background`) instead. This mirrors the detail screen,
 * which fades its hero into the plain theme background in light mode rather
 * than darkening it. Pure so it can be unit-tested without a composition.
 */
internal fun shouldRenderBackdrop(state: HomeBackdropState): Boolean =
    state.enabled && !state.performanceMode && !state.isLightTheme

/**
 * Picks the active [BackdropLayer] from the resolved state. Pure for testing.
 * BlurHash wins when present; otherwise the ambient gradient shows (including
 * when the hero section itself is off — the gradient still has a palette to
 * draw from, or falls back to [com.raulshma.jellyplay.core.designsystem.theme.AmbientColors]).
 */
internal fun resolveBackdropLayer(state: HomeBackdropState): BackdropLayer =
    if (!state.blurHash.isNullOrBlank()) BackdropLayer.BLUR_HASH else BackdropLayer.AMBIENT

/**
 * The settle window: D-pad focus must rest on a card this long before the
 * cost-carrying half of the reaction (the backdrop-URL fetch, the trickplay
 * tile fetch) runs. Arrival-time reactions (the in-memory BlurHash paint) do
 * NOT wait it out — see [com.raulshma.jellyplay.core.concurrency.settleDebounce].
 * The card-level peek reuses the same cadence so the two focus reactions
 * move in lockstep.
 */
internal const val HOME_FOCUS_SETTLE_MS = 400L

/**
 * The focus-driven backdrop inputs: what the currently-focused TV card
 * contributes to the ambient backdrop. [blurHash] arrives at focus arrival
 * (zero-cost — the string is already in memory); [backdropUrl] only at
 * settlement (it feeds the ambient palette extractor, which decodes a full
 * image), and stays null until then.
 */
@Stable
internal data class FocusBackdrop(
    val blurHash: String?,
    val backdropUrl: String?,
) {
    val hasContent: Boolean get() = !blurHash.isNullOrBlank() || !backdropUrl.isNullOrBlank()
}

/**
 * Overlays the focus-driven inputs onto the hero-driven [HomeBackdropState].
 * A focus with content replaces the hero's artwork pair wholesale — the
 * backdrop must read as THE focused card's, not a blend; a focus without
 * content (arrival on a card with no backdrop BlurHash, before the URL
 * settles, or the focus-exit reset) is the throttle fallback: the hero-driven
 * state passes through untouched so the backdrop never flashes to a default
 * palette mid-sweep. Pure so it can be unit-tested without a composition.
 */
internal fun HomeBackdropState.withFocusBackdrop(focus: FocusBackdrop?): HomeBackdropState {
    if (focus == null || !focus.hasContent) return this
    return copy(blurHash = focus.blurHash, backdropUrl = focus.backdropUrl)
}

/**
 * The focus → backdrop choreography: watches the screen's focused-card
 * stream through the shared settle debounce and produces the [FocusBackdrop]
 * the backdrop region applies via [withFocusBackdrop]. Per settlement phase:
 *  - **Arrival** — the focused card's in-memory BlurHash paints immediately
 *    (the crossfade the blurhash layer already gets); nothing is fetched.
 *  - **Settled** — the card's backdrop URL resolves and feeds the ambient
 *    palette (the cost-carrying half). A refocus mid-flight cancels the
 *    pending settlement *and* any settle-phase work via `collectLatest`.
 *  - **Focus exit** (`null`) — the inputs reset and the hero-driven state
 *    takes over again.
 *
 * Disabled (non-TV or the `home_focus_backdrop` pref off) the inputs stay
 * null — the hero-driven behavior applies unchanged.
 *
 * @param focusedItem state-reading provider for the currently focused card
 *  (null when no card holds focus); read inside [snapshotFlow] so changes
 *  re-emit.
 * @param backdropUrlFor the server backdrop-URL builder for a card (the same
 *  builder the wide rows use).
 */
@Composable
internal fun rememberFocusBackdropState(
    focusedItem: () -> MediaItem?,
    enabled: Boolean,
    backdropUrlFor: (MediaItem) -> String,
): FocusBackdrop? {
    var inputs by remember { mutableStateOf<FocusBackdrop?>(null) }
    val currentUrlFor by rememberUpdatedState(backdropUrlFor)
    LaunchedEffect(enabled) {
        if (!enabled) {
            inputs = null
            return@LaunchedEffect
        }
        snapshotFlow(focusedItem)
            .settleDebounce(settleMillis = HOME_FOCUS_SETTLE_MS)
            .collectLatest { settlement ->
                when (settlement) {
                    is Settlement.Arrived -> {
                        // Zero-cost half: the hash is already in memory. A null
                        // item (focus exit) lands here as the empty inputs,
                        // which [withFocusBackdrop] folds back to the hero.
                        inputs = settlement.value?.let { item ->
                            FocusBackdrop(blurHash = item.blurHashes.backdrop, backdropUrl = null)
                        }
                    }
                    is Settlement.Settled -> {
                        // Cost-carrying half: resolve the backdrop URL (the
                        // ambient palette's source). The blank filter keeps a
                        // failed/unresolvable URL from clearing the arrival
                        // blurhash that is already showing.
                        inputs = settlement.value?.let { item ->
                            FocusBackdrop(
                                blurHash = item.blurHashes.backdrop,
                                backdropUrl = currentUrlFor(item).takeIf { url -> url.isNotBlank() },
                            )
                        }
                    }
                }
            }
    }
    return inputs
}

/**
 * The home screen's ambient backdrop.
 *
 * Layer priority:
 *  1. **BlurHash** of the hero artwork when present — decoded tiny and upscaled
 *     full-screen, so the low resolution *is* the blur. Cheap and cached.
 *  2. **Ambient gradient** — slowly drifting palette blobs derived from the
 *     artwork (via [rememberArtworkColors]), falling back to [com.raulshma.jellyplay.core.designsystem.theme.AmbientColors].
 *  3. **Nothing** — the flat [HomeBackdropState.backgroundColor] shows through.
 *
 * Performance & accessibility:
 *  - Suppressed entirely in `performanceMode` (matches `MediaImage`'s blurhash
 *    nulling) so low-end devices pay zero cost.
 *  - Suppressed entirely in light themes (see [shouldRenderBackdrop]); the
 *    flat [HomeBackdropState.backgroundColor] shows through instead, keeping
 *    list content legible on a light surface.
 *  - The ambient layer's own animation freezes under reduced motion.
 *  - A scrim is drawn in the draw phase (no recompose) blending toward
 *    [backgroundColor] for legibility; OLED adds a near-black overlay, light
 *    themes keep it lighter so content stays readable.
 */
@Composable
internal fun HomeBackdrop(state: HomeBackdropState) {
    if (!shouldRenderBackdrop(state)) return

    // Extract the palette only when we'll actually need it (ambient path).
    val artworkColors = rememberArtworkColors(
        if (state.blurHash.isNullOrBlank() && !state.backdropUrl.isNullOrBlank()) {
            state.backdropUrl
        } else {
            null
        },
    )
    val ambientColors = remember(artworkColors) { extractAmbientColors(artworkColors) }

    val layer = resolveBackdropLayer(state)

    Crossfade(
        targetState = layer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "backdropLayer",
    ) { active ->
        when (active) {
            BackdropLayer.BLUR_HASH -> {
                if (!state.blurHash.isNullOrBlank()) {
                    BlurHashBackdrop(blurHash = state.blurHash!!)
                }
            }
            BackdropLayer.AMBIENT -> {
                AmbientColorBackdrop(colors = ambientColors)
            }
            BackdropLayer.NONE -> Unit
        }
    }

    // Legibility scrim, drawn in the draw phase so animated background colours
    // don't trigger recomposition. Drawn over the whole backdrop layer.
    ScrimOverlay(
        backgroundColor = state.backgroundColor,
        oledMode = state.oledMode,
        isLightTheme = state.isLightTheme,
    )
}

/**
 * Transparent overlay whose only job is to paint the legibility scrim on top
 * of the backdrop. Kept separate so the backdrop layer and scrim compose and
 * invalidate independently.
 */
@Composable
private fun ScrimOverlay(
    backgroundColor: Color,
    oledMode: Boolean,
    isLightTheme: Boolean,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val overlay = scrimColors(
                    backgroundColor = backgroundColor,
                    oledMode = oledMode,
                    isLightTheme = isLightTheme,
                )
                // Built in the cache phase so the Brush isn't re-allocated on
                // every draw pass.
                val overlayBrush = Brush.verticalGradient(overlay)
                onDrawBehind {
                    drawRect(brush = overlayBrush)
                }
            },
    )
}

private fun scrimColors(
    backgroundColor: Color,
    oledMode: Boolean,
    isLightTheme: Boolean,
): List<Color> {
    // Blend the backdrop toward the screen's background colour so list content
    // is legible. OLED crushes everything toward black (the whole point of the
    // mode); light themes keep a lighter scrim so dark text on top stays readable.
    val target = when {
        oledMode -> Color.Black
        isLightTheme -> lerp(backgroundColor, Color.White, 0.1f)
        else -> backgroundColor
    }
    val topAlpha = if (oledMode) 0.45f else 0.2f
    val bottomAlpha = if (oledMode) 0.85f else 0.6f
    return listOf(
        target.copy(alpha = topAlpha),
        target.copy(alpha = bottomAlpha),
    )
}

/** Replicates the audio player's private palette → colour list projection. */
private fun extractAmbientColors(artworkColors: ArtworkColors?): List<Color> {
    if (artworkColors == null) return emptyList()
    return listOfNotNull(
        artworkColors.vibrant,
        artworkColors.darkVibrant,
        artworkColors.lightVibrant,
        artworkColors.muted,
        artworkColors.darkMuted,
        artworkColors.lightMuted,
        artworkColors.dominant,
    ).map { it.copy(alpha = 1f) }
}
