plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.calendar"
    }

    sourceSets {
        // The java.time actuals for CalendarDateLabels.kt (JDK + the kotlinx
        // java-converters only — no module deps) live in jvmShared (created by
        // the convention plugin).

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // The whole module runs kotlinx.datetime — grouping helpers,
            // VM month windows, and the date-picker epoch math included.
            implementation(libs.kotlinx.datetime)
            // ExperimentalStore (DIRECT_ARR_INTEGRATION flag slice).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped: no calendar file imports it — navigation entries use
            // entry<Route> from the nav3 runtime/ui artifacts only, syncplay
            // precedent.)
            // Coil itself is unnecessary here — the only image is rendered
            // through shared/core:ui's MediaImage, which brings its own coil
            // dependency (legacy build carried coil + coil-okhttp for the
            // module; the shared one rides MediaImage's).
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
// (`...feature.calendar.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
