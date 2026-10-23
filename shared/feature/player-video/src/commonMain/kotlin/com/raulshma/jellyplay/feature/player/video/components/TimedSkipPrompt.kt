package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

internal const val SKIP_PROMPT_DURATION_MS = 10_000

/** One timed invitation per segment; later user interactions reveal the normal controls. */
@Stable
internal class TimedSkipPromptState(initiallyActive: Boolean, private val initialInteractionCount: Int) {
    private val remaining = Animatable(1f)
    var isInitialPromptActive by mutableStateOf(initiallyActive)
        private set
    val progress: State<Float> = remaining.asState()

    fun isVisible(areControlsVisible: Boolean, userInteractionCount: Int): Boolean =
        isInitialPromptActive || (areControlsVisible && userInteractionCount != initialInteractionCount)

    internal suspend fun countDown(durationMs: Int) {
        remaining.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = (durationMs.coerceIn(3_000, 30_000) * remaining.value).roundToInt(),
                easing = LinearEasing,
            ),
        )
        isInitialPromptActive = false
    }
}

@Composable
internal fun rememberTimedSkipPrompt(
    promptKey: Any?,
    canShow: Boolean,
    userInteractionCount: Int,
    durationMs: Int = SKIP_PROMPT_DURATION_MS,
): TimedSkipPromptState {
    val state = remember(promptKey) {
        TimedSkipPromptState(initiallyActive = promptKey != null, initialInteractionCount = userInteractionCount)
    }
    // Hidden by a sheet, lock or PiP: preserve the remaining time until the
    // invitation can actually be seen. Control visibility never restarts it.
    LaunchedEffect(state, canShow, durationMs) {
        if (promptKey != null && canShow && state.isInitialPromptActive) state.countDown(durationMs)
    }
    return state
}
