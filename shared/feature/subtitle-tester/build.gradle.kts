import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.subtitle.tester"
        compileSdk = 37
        minSdk = 28
        // The raw sample assets (host clip + srt/ass tracks) must generate an
        // R class for androidMain (designsystem font_certs precedent) —
        // PlaybackRequestFactory materializes them via openRawResource.
        androidResources {
            enable = true
            // Keep raw subtitle samples uncompressed. ExoPlayer's
            // RawResourceDataSource needs an AssetFileDescriptor, which Android
            // can't hand back for a compressed resource ("This file can not be
            // opened as a file descriptor; it is probably compressed"). The
            // tester materializes these to files anyway (so mpv/libVLC get
            // file:// paths), but leaving them uncompressed keeps
            // android.resource:// usable for any future in-process consumer.
            // (Carried over verbatim from the legacy build file.)
            noCompress.add("srt")
            noCompress.add("ass")
            noCompress.add("ssa")
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // No wasmJs target yet (same as every shared/feature module): the web
    // shell lands in plan §Phase W. This also keeps java.* legal in
    // commonMain — same precedent as shared/core:data and :feature:syncplay.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:ui"))
            // SubtitleLanguageStore (Koin-native in datastoreCommonModule).
            implementation(project(":shared:core:datastore"))
            // EngineCapabilityMatrix/EngineCapabilities for
            // SubtitleTesterUiState.engineCapabilities (moved here from
            // :feature:player:video with this conveyor feature).
            implementation(project(":shared:core:player-contract"))
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.material3)
            // Compose-resources runtime (stringResource/StringResource API).
            implementation(compose.components.resources)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 and
            // hilt-navigation-compose edges were dropped: no file imports
            // them — navigation entries use entry<Route> from the nav3
            // runtime/ui artifacts only, and the screen's ViewModel is
            // Koin-owned via koinViewModel.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.runtime.compose)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
        }
        // This feature is androidMain-heavy by design (admin WebView-quartet
        // precedent): the preview engines, surface hosts, SAF font picker and
        // raw-asset factory are Android-only, so the ViewModel + screen live
        // here rather than commonMain. Desktop has NO registration for this
        // module at all — the feature is unreachable there (the shared
        // settings-search row for Route.SubtitleTester dead-clicks, same
        // dormant state as every un-wired desktop route).
        getByName("androidMain").dependencies {
            // Largest documented shared→shared edge (was shared→legacy until
            // the wave 7C player-video migration): the tester shares the
            // player sheet's SubtitleStyleControls (799 LOC) and needs
            // PlayerEngineFactory + FontProvider — both Koin-owned singles in
            // shared/feature/player-video's androidPlayerVideoModule now.
            // The jvm target NEVER sees this edge.
            implementation(project(":shared:feature:player-video"))
            // PlayerEngineFactory's Context/media3 ctor args and the ViewModel
            // base class.
            implementation(libs.lifecycle.viewmodel)
            // Koin owns the tester ViewModel (V3 feature conveyor: one
            // framework per type — the Hilt annotations were stripped at the
            // move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy
// :feature:subtitle-tester so migrated files keep their
// `com.raulshma.jellyplay.feature.subtitle.tester` imports; generated
// accessors land in `...feature.subtitle.tester.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.subtitle.tester.generated.resources"
