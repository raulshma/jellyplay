plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.downloads"
    }

    sourceSets {
        // The two jvmShared engine handles the screen drives went behind
        // feature-local seams (QuickDownloadActions template):
        //  - DownloadQueue over DownloadRepository (list reads, live
        //    byte/speed progress — mirrored field-for-field as
        //    DownloadRowProgress — and the pause/resume/enqueue/cancel/retry/
        //    delete/priority controls);
        //  - OfflineResync over OfflineSyncManager (check-for-updates, batch
        //    resync + progress sheet).
        // The jvmShared fragment binds 1:1 adapters over the process-wide
        // singles (android/desktop behavior unchanged). The jvmShared middle
        // source set itself comes from the convention plugin.

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            implementation(project(":shared:core:ui"))
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped: no downloads file imports it — navigation entries use
            // entry<Route> from the nav3 runtime/ui artifacts only.)
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
// (`...feature.downloads.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
