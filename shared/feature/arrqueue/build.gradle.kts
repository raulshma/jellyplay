plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.arrqueue"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // (the former core:network edge dropped: the /release cache-miss
            // leak folded into the data seam's ArrReleaseCacheUnavailable —
            // the sheet branches on the seam type, never a network exception.)
            // ExperimentalStore — the DIRECT_ARR_INTEGRATION flag gate behind
            // the combined-queue screen.
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped with the syncplay move: no arrqueue file imports it —
            // the navigation entry uses entry<Route> from the nav3 runtime/ui
            // artifacts only.)
            implementation(libs.coil.compose)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        getByName("androidMain").dependencies {
            // The user-messenger actual bridges to the app-wide
            // LocalUserMessageBus (:shared:core:ui androidMain since the
            // cutover dissolved the legacy :core:ui shim).
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.arrqueue.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
