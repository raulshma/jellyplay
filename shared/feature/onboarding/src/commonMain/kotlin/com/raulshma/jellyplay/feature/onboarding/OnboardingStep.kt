package com.raulshma.jellyplay.feature.onboarding

import androidx.compose.runtime.Immutable

@Immutable
enum class OnboardingStep(val index: Int) {
    WELCOME(0),
    APPEARANCE(1),
    PERFORMANCE(2),
    HOME_LAYOUT(3),
    VIDEO_PLAYER(4),
    AUDIO_PLAYER(5),
    SUBTITLES(6),
    SECURITY(7),
    SEERR(8),
    COMPLETION(9);

    companion object {
        val entries get() = OnboardingStep.entries
        val count get() = entries.size

        /**
         * Wizard completion at [page], in `0f..1f`: one step is already done on
         * the first page, so the progress line starts at one step rather than
         * empty. Out-of-range pages are coerced to the wizard bounds.
         */
        fun progressFraction(page: Int): Float {
            val clamped = page.coerceIn(0, entries.lastIndex)
            return (clamped + 1).toFloat() / count
        }
    }
}
