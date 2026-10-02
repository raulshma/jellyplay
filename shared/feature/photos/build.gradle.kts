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
            // rememberSaveable in PhotoAlbumScreen (slideshow state).
            implementation(libs.jb.compose.saveable)
            // The album/viewer chrome icons (outline set only).
            // (photosSection's entry<Route> DSL; the calendar/syncplay
            // runtime+ui pair.)
            // JellyPlayViewModel's lifecycle supertype (core:ui keeps the
            // edge implementation-scoped, so the extender carries it).
            // Paging in PhotoAlbumViewModel/PhotoAlbumScreen (the album grid
            // pages folders exactly like the library grids did).
            implementation(libs.paging.compose)
            // The androidMain AndroidPhotoExport actual decodes through Coil
            // (coil-compose → coil-core; MediaImage sizing needs no direct
            // coil edge here — the PhotoViewerScreen use lives in core:ui).
            implementation(libs.coil.compose)
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

// The compose-resources `packageOfResClass`
// (`...feature.photos.generated.resources`, same as the legacy value — the
// photo strings moved here verbatim from feature/library's composeResources,
// values + all 8 locales, same resource names; the two strings library still
// shares (library_reset, library_failed_to_load_more) were re-keyed
// photos_reset / photos_failed_to_load_more) is a path-derived default from
// the convention plugin now — see KmpLibraryComposePlugin.
