import org.gradle.api.plugins.ExtensionAware

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
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 and
            // hilt-navigation-compose edges were dropped: no editor file
            // imports them — navigation entries use entry<Route> from the
            // nav3 runtime/ui artifacts only, and the screen's ViewModel is
            // Koin-owned via koinViewModel.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.coil.compose)
            // Koin owns the editor ViewModel (V3 feature conveyor: one
            // framework per type — the Hilt annotations were stripped at the
            // move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
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

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:editor
// so migrated files keep their `com.raulshma.jellyplay.feature.editor` imports;
// generated accessors land in `...feature.editor.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.editor.generated.resources"
