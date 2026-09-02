package com.raulshma.jellyplay.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the wizard page geometry that OnboardingViewModel.setStep coerces
 * against (`0..OnboardingStep.count - 1`): the step list itself, its
 * contiguous zero-based indices, and the companion count alias. If a step is
 * ever inserted or dropped, the coercion bounds move with it — these tests
 * make that change visible instead of silent.
 */
class OnboardingStepTest {

    @Test
    fun wizard_declares_the_ten_steps_in_pager_order() {
        assertEquals(
            listOf(
                "WELCOME",
                "APPEARANCE",
                "PERFORMANCE",
                "HOME_LAYOUT",
                "VIDEO_PLAYER",
                "AUDIO_PLAYER",
                "SUBTITLES",
                "SECURITY",
                "SEERR",
                "COMPLETION",
            ),
            OnboardingStep.entries.map { it.name },
        )
    }

    @Test
    fun step_indices_are_contiguous_zero_based_and_match_the_declaration() {
        assertEquals(0, OnboardingStep.entries.first().index)
        OnboardingStep.entries.forEachIndexed { position, step ->
            assertEquals(position, step.index, "Step ${step.name} must carry index $position")
        }
        assertEquals(OnboardingStep.entries.size - 1, OnboardingStep.entries.last().index)
    }

    @Test
    fun companion_count_matches_the_entries_size() {
        assertEquals(OnboardingStep.entries.size, OnboardingStep.count)
        assertEquals(10, OnboardingStep.count)
        // The inclusive upper bound setStep coerces into.
        assertEquals(OnboardingStep.count - 1, OnboardingStep.COMPLETION.index)
    }
}
