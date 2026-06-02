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
    }
}
