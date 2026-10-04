package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel

/**
 * Standardized animated swap between the house
 * [JellyPlayViewModel.LoadingState] branches: a crossfade with a barely
 * perceptible scale (0.98) — the same restrained profile the nav transition
 * policy uses. Specs come from [MaterialTheme.motionScheme], so the swap snaps
 * under reduce-motion/performance-mode; [contentKey] keeps only the branch
 * class, so a new Success value does not replay the transition.
 *
 * Adopt this instead of hand-rolled `when (state)` branches at the
 * Loading ↔ Content ↔ Error seams of a screen.
 */
@Composable
fun <T : Any> UiStateAnimatedContent(
    state: JellyPlayViewModel.LoadingState<T>,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { ScreenLoadingState() },
    error: @Composable (JellyPlayViewModel.LoadingState.Error) -> Unit = { branch ->
        ErrorScreen(message = branch.message)
    },
    idle: @Composable () -> Unit = loading,
    content: @Composable (T) -> Unit,
) {
    // transitionSpec is not a composable scope — capture the specs here.
    val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val enterScaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(animationSpec = enterSpec) +
                    scaleIn(
                        initialScale = 0.98f,
                        animationSpec = enterScaleSpec,
                    )
                ).togetherWith(fadeOut(animationSpec = exitSpec))
        },
        contentKey = { it::class },
        label = "uiStateContent",
    ) { branch ->
        when (branch) {
            JellyPlayViewModel.LoadingState.Idle -> idle()
            JellyPlayViewModel.LoadingState.Loading -> loading()
            is JellyPlayViewModel.LoadingState.Error -> error(branch)
            is JellyPlayViewModel.LoadingState.Success -> content(branch.value)
        }
    }
}
