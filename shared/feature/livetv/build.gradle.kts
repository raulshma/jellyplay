plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.livetv"
    }

    sourceSets {
        // The ViewModels' clock dep narrowed from the jvmShared TimeSource to
        // its commonMain EpochMillisSource slice (the only read they ever
        // made), so no core:data type crosses the seam.
        //
        // jvmShared: the wall-clock/date-label formatters'
        // JVM actual resolves AM/PM and month/day names from the default
        // FORMAT locale via java.time. Desktop/android keep the localized
        // output the java.time formatters always had. (The jvmShared middle
        // source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // AppRuntimeStateStore (favorite-channel persisted slice).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // The Live-TV timestamp vocabulary (LiveTvTimeFormat.kt) and the
            // EPG window math run kotlinx.datetime — java.time stays JVM-side.
            implementation(libs.kotlinx.datetime)
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped: no livetv file imports it — navigation entries use
            // entry<Route> from the nav3 runtime/ui artifacts only.)
            // coil3.size.Size in ChannelDetailBackdrop (library precedent).
            implementation(libs.coil.compose)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
            // The shared FakeTimeSource double (test-scoped only — see the
            // fixtures module's house rules).
            implementation(project(":shared:core:test-fixtures"))
        }
        getByName("androidMain").dependencies {
            // The user-messenger actual bridges to the app-wide
            // LocalUserMessageBus (:shared:core:ui androidMain since the
            // cutover dissolved the legacy :core:ui shim).
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.livetv.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
