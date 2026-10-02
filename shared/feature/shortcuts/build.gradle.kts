plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.shortcuts"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core:designsystem"))
            // AuthRepository (currentUser admin filtering) — already a
            // commonMain interface here; the impl resolves from dataJvmModule
            // on BOTH platforms, so the shortcuts ViewModel has no Hilt
            // interop at all (calendar/requests shape).
            implementation(project(":shared:core:data"))
            implementation(project(":shared:core:ui"))
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped: no shortcuts file imports it — the navigation entry
            // uses entry<Route> from the nav3 runtime/ui artifacts only,
            // syncplay/calendar precedent. The legacy build also carried
            // core:datastore + :core:model + coil + coil-okhttp; none of the
            // three files import anything from them — the catalog has no
            // images, so even coil is unused.)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.shortcuts.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
