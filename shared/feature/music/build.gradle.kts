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
        namespace = "com.raulshma.jellyplay.shared.feature.music"
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

    // web breadth: the target compiles. Two seams carried it
    // (AudioTrackDownloads/QuickDownloadActions templates), since the
    // ViewModels' play/enqueue pipeline and download reads bind core:data
    // jvmShared types whose constructor closures reach the JVM audio and
    // download engines:
    //  - MusicQueuePlayer over AudioQueueFacade (+ its jvmShared
    //    AudioQueueOutcome/TrackWithAlbumFallback result vocabulary): the
    //    jvmShared fragment binds a 1:1 adapter over the process-wide
    //    facade single; the wasmJs actual fails start/mix operations with
    //    an explicit cause and drops enqueues inertly — no fabricated
    //    playback;
    //  - the download reads formerly behind MusicTrackDownloads over
    //    DownloadRepository folded onto core:data's own seams with the
    //    download-actions seam consolidation (TrackDownloadStatusWindow for
    //    the album rows, ActiveDownloadCount for the home badge):
    //    core:data declares/implements/binds them on both platforms
    //    (dataJvmModule's adapters here, dataWasmModule's honest web no-ops).
    // The web stack still registers no music bindings — web wiring stays
    // with the orchestrator's shared-wiring pass. java.util.UUID (mood/
    // smart playlist ids) ported to the stdlib kotlin.uuid.Uuid (same v4
    // string shape —'s outbox-id precedent). The karma/Chrome browser
    // run stays off like core:ui/core:network — jvmTest pins the semantics.
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
        // The JVM-side bindings (MusicQueuePlayer -> JvmMusicQueuePlayer over
        // the AudioQueueFacade single) — the player-audio jvmShared
        // platform-module pattern. The former MusicTrackDownloads binding
        // moved to core:data (TrackDownloadStatusWindow / ActiveDownloadCount)
        // with the download-actions seam consolidation.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:concurrency"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // HomeDiscoveryStore (music home discovery prefs).
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
            // GenreDetailViewModel ctor arg (nav-entry SavedStateHandle, KMP
            // since lifecycle 2.9 — synthesized from CreationExtras by Koin's
            // Android parameters holder, same extras the Hilt factory used).
            implementation(libs.lifecycle.viewmodel.savedstate)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.paging.compose)
            // Koin owns the music ViewModels (V3 feature conveyor: one
            // framework per type — the Hilt annotations were stripped at the
            // move).
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
        // androidMain needs no deps: unlike the library conveyor item (whose
        // user-messenger actual lives in the module), music's MusicMessageBus
        // Android actual is app-provided — it bridges to the Koin-owned
        // UserMessageBus (:shared:core:ui androidMain since the
        // cutover).
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:music
// so migrated files keep their `com.raulshma.jellyplay.feature.music` imports;
// generated accessors land in `...feature.music.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.music.generated.resources"

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
