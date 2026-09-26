import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.details"
    }

    sourceSets {
        // The MediaDetail-cluster screens (MediaDetailScreen + its body/
        // sections/sheets and the ManageSeries + navigation entryProvider)
        // share android + desktop verbatim: they carry the java.io/java.time/
        // java.text bodies and reach Room-backed data through commonMain
        // seams. SeerrDetail's files stay in commonMain (purified).
        // (The jvmShared middle source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // runCatchingRethrowingCancellation in resolveTargetItemIds (the
            // sanctioned wrapper — a bare runCatching there masked cancellation
            // of the canonicalEpisodeIds fetch).
            implementation(project(":shared:core:concurrency"))
            // Preference/state stores + projections (DetailStores bundle) and
            // the per-feature slices (Seerr, Downloads, Library, HomeDiscovery,
            // Experimental, PlayerEngine, AppRuntime).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // SeerrDetailUtils' purified date formatting: the java.time
            // "yyyy-MM-dd" parse moved onto kotlinx-datetime's LocalDate.parse.
            implementation(libs.kotlinx.datetime)
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.animation)
            implementation(libs.jb.compose.material3)
            // Compose-resources runtime (stringResource/StringResource API +
            // the suspend getString resolver the DetailStrings seam uses).
            implementation(compose.components.resources)
            implementation(libs.tabler.icons.outline)
            implementation(libs.tabler.icons.filled)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // The navigation entry uses entry<Route> from the nav3 runtime/ui
            // artifacts only (syncplay/arrqueue precedent: the legacy
            // lifecycle-viewmodel-navigation3 edge is gone).
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.coil.compose)
            // Koin owns every details ViewModel (V3/ feature conveyor:
            // one framework per type — the Hilt annotations were stripped at
            // the move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
            // The shared FakeUserDataMutator double (test-scoped only — see
            // the fixtures module's house rules).
            implementation(project(":shared:core:test-fixtures"))
        }
        getByName("androidMain").dependencies {
            // The trailer-host actual delegates to InlineTrailerPlayer
            // (:shared:core:ui androidMain since the cutover). The
            // AudioPlaybackManager/ThemeMusicPlayer adapters
            // stay APP-side (AppKoinModule interop adapters; formerly the
            // HiltInteropModule singles) — a shared-module androidMain
            // actual would have to construct second instances. The share +
            // StatFs storage-probe actuals need no legacy types.
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy
// feature:details so migrated files keep their
// `com.raulshma.jellyplay.feature.details` imports; generated accessors land
// in `...feature.details.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.details.generated.resources"
