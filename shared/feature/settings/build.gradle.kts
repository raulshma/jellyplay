plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.settings"
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

// The compose-resources `packageOfResClass`
// (`...feature.settings.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
