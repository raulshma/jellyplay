plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.insights"
    }

    sourceSets {
        // The JVM-only heatmap feature (java.time grid math + the jvmShared
        // WatchHistoryRepository binding), shared verbatim by android +
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
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped: no insights file imports it — the navigation entry
            // uses entry<Route> from the nav3 runtime/ui artifacts only,
            // syncplay/calendar/requests precedent.)
            // Coil for the screen's direct `coil3.size.Size` request-sizing
            // argument to MediaImage (admin PluginCatalogCard / editor
            // ImagesTab precedent — shared/core:ui's own coil dep is not
            // api-exported). The legacy build additionally carried
            // coil-network-okhttp; the day-detail rows' images load through
            // MediaImage, whose image loading already rides the app-level
            // network fetcher.
            implementation(libs.coil.compose)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // Heatmap share actual (admin StatisticsExport precedent): the
        // FileProvider/drawToBitmap bodies need only the Android framework
        // plus androidx.core, which rides the compose-ui transitive edge —
        // androidMain needs no new dependency.
        getByName("androidMain").dependencies {
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.insights.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
