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
        // classifiers (JDK-only — no deps), one copy serving BOTH android and
        // desktop. (The jvmShared middle
        // source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            // mapConcurrent + DEFAULT_FANOUT_PARALLELISM for the health-probe
            // fan-out (arrqueue/details pattern).
            implementation(project(":shared:core:concurrency"))
            implementation(project(":shared:core:designsystem"))
            // AuthRepository + ServerDiscoveryRepository (both Koin singles in
            // dataJvmModule — the whole ctor graph is Koin-native on BOTH
            // platforms).
            implementation(project(":shared:core:data"))
            implementation(project(":shared:core:ui"))
            // AuthNavigation uses entry<Route> from the nav3 runtime/ui
            // artifacts only — no lifecycle-viewmodel-navigation3 edge.
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        getByName("androidMain").dependencies {
            // The local-network seam actuals bridge LocalNetworkAccess
            // (:shared:core:ui androidMain), which
            // keeps the Android 17 permission logic (and its MainActivity
            // consumer) in one place.
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.auth.generated.resources`) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
