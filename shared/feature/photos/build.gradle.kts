import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.photos"
    }

    sourceSets {
        // The photo suite (PhotoAlbum/PhotoViewer screens + VMs, the
        // PhotoExport seam and its per-platform actuals, PhotoGridCard)
        // extracted from feature/library (X5). jvmShared (created by the
        // convention plugin) stays empty of Kotlin — the export actuals are
        // androidMain (MediaStore/FileProvider) and jvmMain (inert no-op),
        // the library-module split they already had.

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
            // rememberSaveable in PhotoAlbumScreen (slideshow state).
            implementation(libs.jb.compose.saveable)
            // Compose-resources runtime (stringResource/StringResource API).
            implementation(compose.components.resources)
            // The album/viewer chrome icons (outline set only).
            implementation(libs.tabler.icons.outline)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (photosSection's entry<Route> DSL; the calendar/syncplay
            // runtime+ui pair.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            // JellyPlayViewModel's lifecycle supertype (core:ui keeps the
            // edge implementation-scoped, so the extender carries it).
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in PhotoAlbumScreen.
            implementation(libs.lifecycle.runtime.compose)
            // Paging in PhotoAlbumViewModel/PhotoAlbumScreen (the album grid
            // pages folders exactly like the library grids did).
            implementation(libs.paging.compose)
            // The androidMain AndroidPhotoExport actual decodes through Coil
            // (coil-compose → coil-core; MediaImage sizing needs no direct
            // coil edge here — the PhotoViewerScreen use lives in core:ui).
            implementation(libs.coil.compose)
            // Koin owns the photo ViewModels (the library-module pattern the
            // definitions moved out of).
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
        // (coil-compose → coil-core-android; core-ktx via :shared:core:ui),
        // byte-identical to the library module's wiring before the move.
        getByName("androidMain").dependencies {
            // The user-messenger actual bridges to the app-wide
            // LocalUserMessageBus (:shared:core:ui androidMain since the
            // cutover dissolved the legacy :core:ui shim).
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. The photo strings moved here verbatim
// from feature/library's composeResources (values + all 8 locales, same
// resource names); the two strings library still shares (library_reset,
// library_failed_to_load_more) were re-keyed photos_reset /
// photos_failed_to_load_more.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.photos.generated.resources"
