plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.editor"
    }

    sourceSets {
        // The JVM-only edges moved behind seams: the subtitle-upload Base64
        // became the stdlib kotlin.io.encoding encoder (RFC 4648 with padding,
        // byte-identical to java.util.Base64.getEncoder()), java.util.UUID
        // became kotlin.uuid.Uuid, and core:data's jvmShared
        // StreamingSubtitleStore is consumed through the commonMain
        // EditorSubtitleStore seam.
        //
        // The jvmShared actuals for the seams (EditorSubtitleStore wrapper
        // over core:data's jvmShared StreamingSubtitleStore single, the
        // PlatformFormats String.format bodies), shared verbatim by android +
        // desktop like the newsletter/requests jvmShared source sets. (The
        // jvmShared middle source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            implementation(project(":shared:core:ui"))
            // (The legacy build's lifecycle-viewmodel-navigation3 and
            // hilt-navigation-compose edges were dropped: no editor file
            // imports them — navigation entries use entry<Route> from the
            // nav3 runtime/ui artifacts only, and the screen's ViewModel is
            // Koin-owned via koinViewModel.)
            implementation(libs.coil.compose)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // SAF file-picker actual (settings BackupFilePicker precedent):
        // androidx.activity.compose (rememberLauncherForActivityResult) rides
        // navigation3-ui's transitive Android edge, so androidMain needs no
        // new dependency.
        getByName("androidMain").dependencies {
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.editor.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
