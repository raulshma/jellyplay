plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.insights"
    }

    sourceSets {
        // The JVM-only heatmap feature (java.time grid math + the jvmShared
        // WatchHistoryRepository binding), shared by android +
        // desktop like the newsletter/requests jvmShared source sets. (The
        // jvmShared middle source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // Cancellation-safe suspend wrappers — HeatmapShare's share fan-out
            // must not swallow cancellation on either platform.
            implementation(project(":shared:core:concurrency"))
            implementation(project(":shared:core:ui"))
            // (Insights does not need lifecycle-viewmodel-navigation3: no
            // insights file imports it — the navigation entry uses entry<Route>
            // from the nav3 runtime/ui artifacts only.)
            // Coil for the screen's direct `coil3.size.Size` request-sizing
            // argument to MediaImage (admin PluginCatalogCard / editor
            // ImagesTab pattern — shared/core:ui's own coil dep is not
            // api-exported). coil-network-okhttp is not needed: the day-detail
            // rows' images load through MediaImage, whose image loading
            // already rides the app-level network fetcher.
            implementation(libs.coil.compose)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // Heatmap share actual (admin StatisticsExport pattern): the
        // FileProvider/drawToBitmap bodies need only the Android framework
        // plus androidx.core, which rides the compose-ui transitive edge —
        // androidMain needs no new dependency.
        getByName("androidMain").dependencies {
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.insights.generated.resources`) is a path-derived default from
// the convention plugin — see KmpLibraryComposePlugin.
