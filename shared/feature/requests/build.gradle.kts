plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.requests"
    }

    sourceSets {
        // The java.time actuals for RequestTime.kt (JDK only — no deps) live
        // in jvmShared (created by the convention plugin).

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // ExperimentalStore — the DIRECT_ARR_INTEGRATION flag gate behind
            // the *arr download-progress enrichment.
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped with the syncplay move: no requests file imports it —
            // the navigation entry uses entry<Route> from the nav3 runtime/ui
            // artifacts only.)
            // LocalViewModelStoreOwner/LocalLifecycleOwner in
            // ProvidePlatformLocalsFallback — declared explicitly
            // rather than relying on the transitive koin-compose-viewmodel
            // edge; already a repo pin, no new version enters the graph.
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.coil.compose)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.requests.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
