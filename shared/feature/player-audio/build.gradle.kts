import org.gradle.api.plugins.ExtensionAware

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
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.animation)
            implementation(libs.jb.compose.material3)
            // Compose-resources runtime (stringResource/StringResource API).
            implementation(compose.components.resources)
            implementation(libs.tabler.icons.outline)
            implementation(libs.tabler.icons.filled)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.runtime.compose)
            // Koin owns the audio-player ViewModel (V3 feature conveyor: one
            // framework per type — the @HiltViewModel/@Inject annotations were
            // stripped at the move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
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

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy
// feature:player:audio so migrated files keep their
// `com.raulshma.jellyplay.feature.player.audio` imports; generated accessors
// land in `...feature.player.audio.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.player.audio.generated.resources"
