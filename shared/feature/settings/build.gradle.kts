plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.settings"
        // Robolectric Compose-UI lane for the search-reveal choreography
        // (SettingsGroup expansion + row highlight/focus — behaviors the
        // source-scan guards can only pin textually). Flags mirrored
        // verbatim from shared/core/ui: real resource serving for
        // compose-resources lookups and unstubbed-Context tolerance.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        // The ViewModels bind the full core:data store/repository cluster
        // (media/auth/seerr/arr/search-history and more), which resolves only
        // from the android+jvm DI graph. The java.io seams were reworked for
        // commonMain: SettingsBackupIo narrowed from java.io streams to a
        // text-level payload seam (the backup payload is one JSON document),
        // FileSize moved to jvmShared (java.io.File walk used only by the
        // android/desktop IO actuals), and the ConnectionProbe job table got
        // an expect/actual factory (ConcurrentHashMap is JVM-only).
        // core:data's jvmShared AdminRepository went behind the feature-local
        // ServerAdminActions seam (QuickDownloadActions template: jvmShared
        // adapter over the real single, gate folded into the ViewModel).
        //
        // The JVM-side actuals shared by android + desktop (the FileSize walk
        // consumed by the storage/cache IO actuals, the ServerAdminActions
        // adapter over core:data's jvmShared AdminRepository single) — the
        // newsletter/requests jvmShared pattern. (The jvmShared middle source
        // set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // Preference slice stores + PreferencesEditor + SettingsBackup.
            implementation(project(":shared:core:datastore"))
            // runCatchingRethrowingCancellation (resolveDiffLabels' resource read).
            implementation(project(":shared:core:concurrency"))
            implementation(project(":shared:core:ui"))
            // EngineCapabilityMatrix gates the subtitle custom-color swatch
            // on the selected engine's free-form + ASS-override support.
            implementation(project(":shared:core:player-contract"))
            implementation(libs.coil.compose)
            // LicensesScreen consumes aboutlibraries Library entities (KMP
            // artifact; JSON is loaded through the asset-reader seam).
            implementation(libs.aboutlibraries.core)
            // Backup JSON + home-layout preset share payloads.
            implementation(libs.kotlinx.serialization.json)
            // The scheduled-theme hour read (AppearanceSettingsScreen) runs
            // kotlinx.datetime — java.util.Calendar stays JVM-side.
            implementation(libs.kotlinx.datetime)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // Second documented shared→legacy edge (library/livetv precedent):
        // the Android messenger actual bridges legacy LocalUserMessageBus so
        // the Hilt-owned bus singleton stays the single instance.
        getByName("androidMain").dependencies {
        }
    }
}

// The Robolectric lane (robolectric.properties pins sdk=35 — Robolectric 4.16
// emulates at most 36 while the merged manifest targets 37). AGP 9.4's
// withHostTest names the lane's source set androidHostTest
// (src/androidHostTest/kotlin) and materializes it only in afterEvaluate, so
// the dependency wiring rides a configureEach — an eager lookup would run
// before the source set exists. Same shape as shared/core/ui's lane.
kotlin.sourceSets.configureEach {
    if (name == "androidHostTest") {
        dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.coroutines.test)
            // The PlaybackSettingsViewModel handle the real group bodies take
            // (only their click lambdas touch it; relaxed at composition).
            implementation(libs.mockk)
            // Compose UI tests under Robolectric (the search-reveal
            // focus/highlight regression suites).
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.settings.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
