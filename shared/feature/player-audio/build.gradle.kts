plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.player.audio"
        // The CastButton device-picker Robolectric suite. Flags mirrored
        // verbatim from shared/core/ui: real resource serving for the
        // compose-resources lookups the picker's dialog strings ride, and
        // unstubbed-Context tolerance.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        // The JVM-side bindings (the AudioSleepTimerManager → the
        // SleepTimerManager single stays in dataJvmModule) — the
        // newsletter/requests jvmShared pattern. Empty of Kotlin since the
        // download-actions seam consolidation moved the AudioTrackDownloads
        // adapter/fragment actuals into core:data. (The jvmShared middle
        // source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // androidMain needs no extra deps: the CastButton actual (device
        // picker dialog) is driven entirely through the commonMain
        // AudioPlayerCast seam — the Hilt-owned legacy CastManager stays
        // APP-side (HiltInteropModule) because constructing it here would be
        // a second framework per type (details DetailAudioPlayback
        // precedent; dies at ).
    }
}

// The Robolectric lane (robolectric.properties pins sdk=35 — Robolectric 4.16
// emulates at most 36 while the merged manifest targets 37). AGP 9.4's
// withHostTest names the lane's source set androidHostTest
// (src/androidHostTest/kotlin) and materializes it only in afterEvaluate, so
// the dependency wiring rides a configureEach — an eager lookup would run
// before the source set exists. Same shape as shared/core/ui's /
// shared/feature/settings' lane.
kotlin.sourceSets.configureEach {
    if (name == "androidHostTest") {
        dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            // Compose UI tests under Robolectric (the CastButton device-picker
            // regression suite — platform AlertDialog + compose-resources
            // lookups).
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.player.audio.generated.resources`, same as the legacy value) is
// a path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
