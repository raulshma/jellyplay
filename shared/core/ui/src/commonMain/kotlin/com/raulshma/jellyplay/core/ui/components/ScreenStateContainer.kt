package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The high-level state a screen can be in. [ScreenStateContainer] crossfades
 * between them so loading->content->empty swaps don't snap.
 */
enum class ScreenState {
    LOADING,
    EMPTY,
    CONTENT,
}

/**
 * Crossfades between screen states using [MaterialTheme.motionScheme] effects
 * specs. Under reduced motion the scheme returns 0ms tweens, so this collapses
 * to an instant swap automatically — no explicit guard needed.
 *
 * Pass the actual content via [content]; loading/empty states use the existing
 * [ScreenLoadingState]/[ScreenEmptyState] composables.
 */
@Composable
fun ScreenStateContainer(
    state: ScreenState,
    modifier: Modifier = Modifier,
    loadingMessage: String? = null,
    emptyState: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Read motion tokens in the @Composable body — AnimatedContent's
    // transitionSpec lambda is NOT composable, so capture them as locals first
    // (same pattern as HomeHero). Under reduced motion these resolve to 0ms
    // tweens, collapsing the crossfade to an instant swap.
    val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = enterSpec) togetherWith
                fadeOut(animationSpec = exitSpec)
        },
        label = "screenState",
    ) { current ->
        when (current) {
            ScreenState.LOADING -> ScreenLoadingState(message = loadingMessage)
            ScreenState.EMPTY -> emptyState?.invoke() ?: ScreenLoadingState()
            ScreenState.CONTENT -> content()
        }
    }
}
