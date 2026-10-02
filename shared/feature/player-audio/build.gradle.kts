plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.player.audio"
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

// The compose-resources `packageOfResClass`
// (`...feature.player.audio.generated.resources`, same as the legacy value) is
// a path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
