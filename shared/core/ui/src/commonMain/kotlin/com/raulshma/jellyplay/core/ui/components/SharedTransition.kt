package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Builds the shared-element modifier for a card image when both transition
 * scopes are present in the composition, otherwise returns [Modifier] so the
 * caller composes unchanged (e.g. under Performance Mode, where
 * [LocalSharedTransitionScope] is null). Centralises the opt-in so callers
 * don't each need [ExperimentalSharedTransitionApi].
 *
 * Pass the same [key] to the source and destination of a transition
 * (e.g. `"poster_${item.id}"`) so the bounds morph between them.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun rememberSharedElementModifier(key: String?): Modifier {
    if (key == null) return Modifier
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return Modifier
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current ?: return Modifier
    return with(sharedTransitionScope) {
        Modifier.sharedElement(
            rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
