import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.home"
    }

    sourceSets {
        // The JVM-side bindings (the seam adapters over the core:data
        // jvmShared singles, plus the SyncStatusStateHolderFactory single
        // whose deps are jvmShared) — the player-audio jvmShared
        // platform-module pattern. (The jvmShared middle source set comes
        // from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // Cancellation-safe suspend wrapper for the offline gate's
            // cached-layout read.
            implementation(project(":shared:core:concurrency"))
            //  ripple: ArrRepository's calendar window now takes
            // kotlinx.datetime.LocalDate — HomeRefresher converts java.time at the boundary.
            implementation(libs.kotlinx.datetime)
            implementation(project(":shared:core:datastore"))
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
            implementation(libs.tabler.icons.filled)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped with the syncplay move: navigation entries use
            // entry<Route> from the nav3 runtime/ui artifacts only.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle + LocalLifecycleOwner (hero rotation
            // RESUMED gate) in the screens.
            implementation(libs.lifecycle.runtime.compose)
            // coil3.Size in HomeHero/HomeMediaRows image requests — coil3 is
            // not api-exported by shared/core:ui (insights precedent).
            implementation(libs.coil.compose)
            // Koin owns the home ViewModel (V3 feature conveyor: one
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
            // The shared FakeUserDataMutator double, replacing the private
            // twin this suite used to carry (test-scoped only — see the
            // fixtures module's house rules).
            implementation(project(":shared:core:test-fixtures"))
        }
        getByName("androidMain").dependencies {
            // The process-lifecycle actual (refresher start/stop on app
            // foreground/background) registers against
            // ProcessLifecycleOwner — Android-only API, same androidMain dep
            // as shared/core:data's AndroidOfflineModeManager.
            implementation(libs.lifecycle.process)
            // The report-fully-drawn actual reads LocalActivity (TTFD metric).
            implementation(libs.androidx.activity.compose)
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:home
// so migrated files keep their `com.raulshma.jellyplay.feature.home` imports;
// generated accessors land in `...feature.home.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.home.generated.resources"
