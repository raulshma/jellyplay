import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.onboarding"
        compileSdk = 37
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // No wasmJs target yet (same as every shared/feature/* module): the web
    // shell lands in plan §Phase W; android+jvm covers V3 consumers.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            // PreferenceProjections / SeerrPreferencesStore / SeerrSecure
            // CredentialsStore / PreferencesEditor for the wizard ViewModel.
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // NOTE: no :shared:core:data dependency — the legacy build file
            // carried one, but no onboarding file imports core.data types
            // (verified per-symbol at the move; the wizard writes prefs only).
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.animation)
            implementation(libs.jb.compose.material3)
            // Compose-resources runtime (stringResource/StringResource API).
            implementation(compose.components.resources)
            implementation(libs.tabler.icons.outline)
            implementation(libs.tabler.icons.filled)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped with the syncplay move: no onboarding file imports it —
            // OnboardingNavigation uses entry<Route> from the nav3 runtime/ui
            // artifacts only.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screen.
            implementation(libs.lifecycle.runtime.compose)
            // Koin owns the onboarding ViewModel (V3 feature conveyor: one
            // framework per type — the Hilt annotations were stripped at the
            // move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // Documented shared→legacy :core:ui androidMain edge (library/livetv/
        // admin/calendar/downloads/settings precedents; dies at Phase X): the
        // biometric-availability actual wraps the legacy BiometricAuthHelper,
        // whose strong-authentication check has no shared counterpart yet.
        getByName("androidMain").dependencies {
            implementation(project(":core:ui"))
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:onboarding
// so migrated files keep their `com.raulshma.jellyplay.feature.onboarding` imports;
// generated accessors land in `...feature.onboarding.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.onboarding.generated.resources"
