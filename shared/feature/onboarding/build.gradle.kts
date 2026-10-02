plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.onboarding"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            // PreferenceProjections / SeerrPreferencesStore / SeerrSecure
            // CredentialsStore / PreferencesEditor for the wizard ViewModel.
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // NOTE: no :shared:core:data dependency — the legacy build file
            // carried one, but no onboarding file imports core.data types
            // (verified per-symbol at the move; the wizard writes prefs only).
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped with the syncplay move: no onboarding file imports it —
            // OnboardingNavigation uses entry<Route> from the nav3 runtime/ui
            // artifacts only.)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // The biometric-availability actual wraps BiometricAuthHelper
        // (:shared:core:ui androidMain since the cutover), whose
        // strong-authentication check has no shared counterpart yet.
        getByName("androidMain").dependencies {
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.onboarding.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
