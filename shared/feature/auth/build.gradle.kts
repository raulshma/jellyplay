import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.auth"
    }

    sourceSets {
        // The javax/java.net actuals of the AddServerViewModel failure
        // classifiers (JDK-only — no deps), requests-module jvmShared shape:
        // one copy serves BOTH android and desktop. (The jvmShared middle
        // source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            // AuthRepository + ServerDiscoveryRepository (both Koin singles in
            // dataJvmModule — the whole ctor graph is Koin-native on BOTH
            // platforms, calendar/requests/shortcuts class).
            implementation(project(":shared:core:data"))
            implementation(project(":shared:core:ui"))
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
            // dropped with the syncplay move: no auth file imports it —
            // AuthNavigation uses entry<Route> from the nav3 runtime/ui
            // artifacts only.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            // Koin owns the auth ViewModels (feature conveyor: one framework
            // per type — the Hilt annotations were stripped at the move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        getByName("androidMain").dependencies {
            // The local-network seam actuals bridge LocalNetworkAccess
            // (:shared:core:ui androidMain since the cutover), which
            // keeps the Android 17 permission logic (and its MainActivity
            // consumer) in one place.
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:auth
// so migrated files keep their `com.raulshma.jellyplay.feature.auth` imports;
// generated accessors land in `...feature.auth.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.auth.generated.resources"
