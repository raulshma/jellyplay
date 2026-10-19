plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.library"
    }

    sourceSets {
        // The jvmShared slice is empty of Kotlin (core:data owns the
        // QuickDownloadActions seam), kept so android + desktop share any
        // future JVM-only Kotlin like the newsletter/requests jvmShared source
        // sets. (The jvmShared middle source set comes from the convention
        // plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:concurrency"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // LibraryStore (persisted library layout/filter slices).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            implementation(libs.jb.compose.saveable)
            // StudioDetailViewModel ctor arg (nav-entry SavedStateHandle, KMP
            // since lifecycle 2.9 — synthesized from CreationExtras by Koin's
            // Android parameters holder).
            implementation(libs.lifecycle.viewmodel.savedstate)
            implementation(libs.paging.compose)
            // coil3.size.Size for MediaImage sizing in LibraryListItem/
            // ThumbCard (feature/photos carries its own edge for the
            // PhotoViewerScreen).
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.serialization.json)
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
            // LocalUserMessageBus (:shared:core:ui androidMain).
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.library.generated.resources`) is a path-derived default from
// the convention plugin — see KmpLibraryComposePlugin.
