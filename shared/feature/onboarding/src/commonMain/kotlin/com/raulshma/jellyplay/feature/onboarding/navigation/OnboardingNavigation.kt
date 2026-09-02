package com.raulshma.jellyplay.feature.onboarding.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.onboarding.OnboardingScreen

fun EntryProviderScope<NavKey>.onboardingSection(
    onComplete: () -> Unit,
) {
    entry<Route.Onboarding> {
        OnboardingScreen(onComplete = onComplete)
    }
}
