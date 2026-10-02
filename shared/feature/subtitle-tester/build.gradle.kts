plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.subtitle.tester"
        // The raw sample assets (host clip + srt/ass tracks) must generate an
        // R class for androidMain (designsystem font_certs precedent) —
        // PlaybackRequestFactory materializes them via openRawResource.
        // (androidResources.enable itself comes from the convention plugin;
        // see its KDoc for the MissingResourceException story.)
        androidResources {
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
    }

    sourceSets {
        // This dev/test utility is android+jvm only (its host surface lives in
        // androidMain); the jvm target comes from the convention plugin.

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:ui"))
            // SubtitleLanguageStore (Koin-native in datastoreCommonModule).
            implementation(project(":shared:core:datastore"))
            // EngineCapabilityMatrix/EngineCapabilities for
            // SubtitleTesterUiState.engineCapabilities (moved here from
            // feature:player:video with this conveyor feature).
            implementation(project(":shared:core:player-contract"))
            // (The legacy build's lifecycle-viewmodel-navigation3 and
            // hilt-navigation-compose edges were dropped: no file imports
            // them — navigation entries use entry<Route> from the nav3
            // runtime/ui artifacts only, and the screen's ViewModel is
            // Koin-owned via koinViewModel.)
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
            // the player-video migration): the tester shares the
            // player sheet's SubtitleStyleControls (799 LOC) and needs
            // PlayerEngineFactory + FontProvider — both Koin-owned singles in
            // shared/feature/player-video's androidPlayerVideoModule now.
            // The jvm target NEVER sees this edge.
            implementation(project(":shared:feature:player-video"))
            // PlayerEngineFactory's Context/media3 ctor args and the ViewModel
            // base class — lifecycle-viewmodel + the koin trio now ride the
            // convention plugin's universal commonMain bundle.
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.subtitle.tester.generated.resources`, same as the legacy value)
// is a path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
