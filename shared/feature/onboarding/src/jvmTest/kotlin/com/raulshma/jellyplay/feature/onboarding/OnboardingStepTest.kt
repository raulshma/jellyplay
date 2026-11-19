package com.raulshma.jellyplay.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the wizard page geometry that OnboardingViewModel.navigateToPage
 * coerces against (`0..OnboardingStep.count - 1`): the step list itself, its
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
        // The inclusive upper bound navigateToPage coerces into.
        assertEquals(OnboardingStep.count - 1, OnboardingStep.COMPLETION.index)
    }

    @Test
    fun progress_fraction_counts_the_current_step_and_reaches_full_on_completion() {
        val step = 1f / OnboardingStep.count.toFloat()

        assertEquals(step, OnboardingStep.progressFraction(0))
        assertEquals(step * 4, OnboardingStep.progressFraction(3))
        assertEquals(1f, OnboardingStep.progressFraction(OnboardingStep.COMPLETION.index))
    }

    @Test
    fun progress_fraction_coerces_pages_outside_the_wizard() {
        assertEquals(OnboardingStep.progressFraction(0), OnboardingStep.progressFraction(-4))
        assertEquals(1f, OnboardingStep.progressFraction(OnboardingStep.count + 3))
    }
}
