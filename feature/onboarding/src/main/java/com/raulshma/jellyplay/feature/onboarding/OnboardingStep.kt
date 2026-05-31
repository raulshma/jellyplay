package com.raulshma.jellyplay.feature.onboarding

import androidx.compose.runtime.Immutable

@Immutable
enum class OnboardingStep(val index: Int) {
    WELCOME(0),
    APPEARANCE(1),
    HOME_LAYOUT(2),
    VIDEO_PLAYER(3),
    AUDIO_PLAYER(4),
    SUBTITLES(5),
    SECURITY(6),
    SEERR(7),
    COMPLETION(8);

    companion object {
        val entries get() = OnboardingStep.entries
        val count get() = entries.size
    }
}
