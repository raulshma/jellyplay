package com.raulshma.jellyplay.feature.onboarding.di

import com.raulshma.jellyplay.feature.onboarding.OnboardingViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the onboarding wizard (docs/kmp-migration-plan.md
 * §Phase V3 conveyor). The HiltViewModel/@Inject annotations were stripped at
 * the move — Koin is the single constructor owner (one framework per type).
 * All four ctor deps are Koin-native on BOTH platforms (calendar/requests
 * class — zero Hilt interop):
 *  - PreferenceProjections and PreferencesEditor resolve from
 *    datastoreCommonModule;
 *  - SeerrPreferencesStore resolves from datastoreCommonModule;
 *  - SeerrSecureCredentialsStore resolves from the platform datastore
 *    modules (androidMain AndroidDatastoreModule / jvmMain DesktopDatastore
 *    Module — both platforms define it).
 * The registration is fully live on desktop; nav v1 registers
 * onboardingSection (reachable via Shortcuts); a first-run gate is tracked
 * separately (the Android gate is app-side: JellyPlayApp renders
 * OnboardingScreen; TV auto-completes). The
 * Android app registers this module in JellyPlayApplication.
 */
val onboardingModule: Module = module {
    viewModel {
        OnboardingViewModel(
            projections = get(),
            seerrPreferencesStore = get(),
            seerrSecureCredentialsStore = get(),
            editor = get(),
        )
    }
}
