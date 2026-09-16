import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.player.audio"
        compileSdk = 37
        minSdk = 28
        // Compose-resources packaging (device-pass finding): with the
        // AGP-9 KMP library plugin, android resources are OFF by default, so
        // copyAndroidMainComposeResourcesToAndroidAssets never runs and the
        // app APK ships this module's Res accessors with NO backing .cvr
        // assets — runtime MissingResourceException on the first string read.
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // web breadth: the target compiles. The ViewModel still binds the
    // core:data store/repository cluster (queue/effects managers, repositories,
    // DownloadIntake, UserDataMutator), which resolves only from the
    // android+jvm DI graph — the web stack registers no player bindings, so
    // web wiring stays with the orchestrator's shared-wiring pass (shortcuts
    // precedent). Two seams carried the target:
    //  - the ViewModel/controller narrowed their sleep-timer dep from
    //    core:data's jvmShared SleepTimerManager class to its commonMain
    //    AudioSleepTimerManager interface — the JVM graph already binds the
    //    interface to that single, and the wasm fragment binds a wall-clock
    //    impl (honest for a sleep timer);
    //  - the jvmShared DownloadRepository read went behind core:data's
    //    commonMain TrackDownloadStatusWindow seam (download-actions seam
    //    consolidation: the former feature-local AudioTrackDownloads seam
    //    was folded onto it): core:data binds the jvmShared adapter over the
    //    real single in dataJvmModule and the honest no-op on web in
    //    dataWasmModule, with isSupported gating the download CTA in the
    //    screen.
    // The karma/Chrome browser run stays off like core:ui/core:network —
    // jvmTest pins the semantics.
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // The JVM-side bindings (the AudioSleepTimerManager → the
        // SleepTimerManager single stays in dataJvmModule) — the
        // newsletter/requests jvmShared pattern. Empty of Kotlin since the
        // download-actions seam consolidation moved the AudioTrackDownloads
        // adapter/fragment actuals into core:data.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
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
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
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

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface. Scoped
// to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before (the
// identical block lives in shared/core/ui, shared/feature/requests and the
// other web modules).
configurations.configureEach {
    if (name.lowercase().contains("wasmjs")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.navigation3:navigation3-ui"))
                .using(module(libs.jb.navigation3.ui.get().toString()))
                .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy
// feature:player:audio so migrated files keep their
// `com.raulshma.jellyplay.feature.player.audio` imports; generated accessors
// land in `...feature.player.audio.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.player.audio.generated.resources"
