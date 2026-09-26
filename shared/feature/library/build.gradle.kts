import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.library"
    }

    sourceSets {
        // The jvmShared slice (now empty of Kotlin — the QuickDownloadActions
        // wrapper/fragment actuals were deleted when core:data took over the
        // seam), kept so android + desktop share any future JVM-only Kotlin
        // like the newsletter/requests jvmShared source sets. (The jvmShared
        // middle source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:concurrency"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // LibraryStore (persisted library layout/filter slices).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.animation)
            implementation(libs.jb.compose.material3)
            implementation(libs.jb.compose.saveable)
            // Compose-resources runtime (stringResource/StringResource API).
            implementation(compose.components.resources)
            implementation(libs.tabler.icons.outline)
            implementation(libs.tabler.icons.filled)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // StudioDetailViewModel ctor arg (nav-entry SavedStateHandle, KMP
            // since lifecycle 2.9 — synthesized from CreationExtras by Koin's
            // Android parameters holder, same extras the Hilt factory used).
            implementation(libs.lifecycle.viewmodel.savedstate)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.paging.compose)
            // coil3.size.Size for MediaImage sizing in LibraryListItem/
            // ThumbCard (the PhotoViewerScreen user moved to feature/photos
            // with the photo-suite extraction, which carries its own edge).
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.serialization.json)
            // Koin owns the library ViewModels (V3 feature conveyor: one
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
        // androidMain needs no explicit deps: the photo-export actual's
        // MediaStore/FileProvider/coil3-android bits ride transitive edges
        // (coil-compose → coil-core-android; core-ktx via :shared:core:ui).
        getByName("androidMain").dependencies {
            // The user-messenger actual bridges to the app-wide
            // LocalUserMessageBus (:shared:core:ui androidMain since the
            // cutover dissolved the legacy :core:ui shim).
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:library
// so migrated files keep their `com.raulshma.jellyplay.feature.library` imports;
// generated accessors land in `...feature.library.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.library.generated.resources"
