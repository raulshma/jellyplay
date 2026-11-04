package com.raulshma.jellyplay.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.components.LocalPerformanceConfig
import com.raulshma.jellyplay.core.ui.preview.MediaPreviewController

/**
 * The press-and-hold "peek" preview's shell state, extracted from
 * `MainContent`: the remembered [MediaPreviewController] (provided via
 * [com.raulshma.jellyplay.core.ui.preview.LocalMediaPreviewController] and
 * collected by the overlay) plus the backdrop-blur modifier the live-content
 * Box applies behind the overlay.
 */
@Immutable
internal data class PeekPreviewState(
    val controller: MediaPreviewController,
    val blurModifier: Modifier,
)

/**
 * Constructs the [PeekPreviewState]. The controller is the remembered half
 * (the `rememberMediaPeek` handle idiom: a plain holder returned per
 * composition over remembered internals); the blur modifier recomputes per
 * recomposition of the caller exactly as the former inline block did.
 */
@Composable
internal fun rememberPeekPreviewState(
    peekEnabled: Boolean,
    isTv: Boolean,
): PeekPreviewState {
    // Press-and-hold "peek" preview (Instagram-style). Remembered once at the
    // root and provided via LocalMediaPreviewController; the overlay collects it
    // below. Purely ephemeral UI state, so no DI/ViewModel involvement. The
    // whole feature is dormant unless the user opts in under Experimental
    // settings (off by default).
    val mediaPreviewController = remember {
        MediaPreviewController()
    }
    val mediaPreviewState by mediaPreviewController.state.collectAsStateWithLifecycle()
    // The resolved posture's backdrop row gates the blur (was: the raw
    // performanceMode boolean) — Modifier.blur over the full tree is
    // GPU-costly, so the aggressive posture opts out. PERFORMANCE suppresses
    // via AUTO; an explicit backdrop OFF kills the blur at any level.
    val backdropBlurAllowed = LocalPerformanceConfig.current.backdropBlur
    // Blur the live content behind the peek overlay. Skipped on TV (no peek),
    // when the posture suppresses the backdrop blur, and when the feature is
    // disabled — in which case mediaPreviewState is always null.
    val previewBlur by animateFloatAsState(
        targetValue = if (peekEnabled && !isTv && backdropBlurAllowed && mediaPreviewState != null) 14f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "previewBackdropBlur",
    )
    val previewBlurModifier =
        if (previewBlur > 0.5f) Modifier.blur(previewBlur.dp) else Modifier

    return PeekPreviewState(
        controller = mediaPreviewController,
        blurModifier = previewBlurModifier,
    )
}
