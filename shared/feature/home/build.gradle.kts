import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.home"
        compileSdk = 37
        minSdk = 28
        // Compose-resources packaging (wave-21 device-pass finding): with the
        // AGP-9 KMP library plugin, android resources are OFF by default, so
        // copyAndroidMainComposeResourcesToAndroidAssets never runs and the
        // app APK ships this module's Res accessors with NO backing .cvr
        // assets — runtime MissingResourceException on the first string read.
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // No wasmJs target: not in the web v1 slice (requests/calendar/details),
    // and its ViewModels bind core:data seams (MediaRepository,
    // OfflineRepository, DownloadRepository, MediaSearchEngine) that resolve
    // only from the android+jvm DI graph (impls jvmShared; the web stack
    // registers no bindings). The missing target also keeps java.* types
    // (java.time.LocalDate in HomeViewModel, java.util.PriorityQueue in
    // HomeOfflineContent) legal in commonMain, which a wasm target forbids.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // Wave 15B ripple: ArrRepository's calendar window now takes
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
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
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
