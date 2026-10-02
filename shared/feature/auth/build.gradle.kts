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
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped with the syncplay move: no auth file imports it —
            // AuthNavigation uses entry<Route> from the nav3 runtime/ui
            // artifacts only.)
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

// The compose-resources `packageOfResClass`
// (`...feature.auth.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
