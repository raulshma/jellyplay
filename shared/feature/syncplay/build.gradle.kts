plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.syncplay"
        // Rehomed :app androidTest suite (SyncPlayScreenTest) — withHostTest
        // creates the androidUnitTest variant bound to the Kotlin test tree
        // (AGP-9 KMP library plugin). Flags mirrored verbatim from
        // shared/core/ui: real resource serving for compose-resources string
        // lookups and unstubbed-Context tolerance.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        // The manager the feature rides (SyncPlayManager — OkHttp api client
        // + WebSocket + TimeSync, all JVM-bound in core:data's jvmShared half)
        // went behind the feature-local SyncPlaySession seam
        // (QuickDownloadActions template): the jvmShared fragment binds a 1:1
        // adapter over the process-wide manager single (android/desktop
        // behavior unchanged). The VM's one System.currentTimeMillis() read
        // (the reconnect-grace window) ported to core:model's commonMain
        // wallNowMillis() platform seam.
        //
        // The JVM-side binding (SyncPlaySession -> the JvmSyncPlaySession
        // adapter over the SyncPlayManager single) — the player-audio
        // PlayerAudioPlatformModule jvmShared pattern. (The jvmShared middle
        // source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // SyncPlayCastStore (join-behavior + auto-accept-invites slice).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped: no syncplay file imports it — navigation entries use
            // entry<Route> from the nav3 runtime/ui artifacts only.)
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
// (`...feature.syncplay.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.

// Robolectric lane for the rehomed SyncPlayScreenTest. AGP 9.4's withHostTest
// names the lane's source set androidHostTest (src/androidHostTest/kotlin)
// and materializes it only in afterEvaluate, so the dependency wiring rides a
// configureEach — an eager lookup would run before the source set exists
// (same pattern as shared/core/ui).
kotlin.sourceSets.configureEach {
    if (name == "androidHostTest") {
        dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            // Compose UI tests under Robolectric (the rehomed screen
            // regression suite; mockk doubles the SyncPlayViewModel ctor
            // seams — same setup as the jvmTest ViewModel suite).
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.mockk)
        }
    }
}
