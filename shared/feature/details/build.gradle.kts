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
        namespace = "com.raulshma.jellyplay.shared.feature.details"
        compileSdk = 37
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // No wasmJs target yet (same as every shared/feature/* module): the web
    // shell lands in plan §Phase W; android+jvm covers V3/Phase X consumers.
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
            // Preference/state stores + projections (DetailStores bundle) and
            // the per-feature slices (Seerr, Downloads, Library, HomeDiscovery,
            // Experimental, PlayerEngine, AppRuntime).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
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
            // Koin owns every details ViewModel (V3/Phase X feature conveyor:
            // one framework per type — the Hilt annotations were stripped at
            // the move).
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
            // The trailer-host actual delegates to legacy core:ui's WebView
            // InlineTrailerPlayer (library/livetv/admin/calendar messenger
            // precedent — documented shared→legacy androidMain edge; dies at
            // Phase X). The AudioPlaybackManager/ThemeMusicPlayer adapters
            // stay APP-side (HiltInteropModule) because those singletons are
            // Hilt-owned in legacy :core:data — a shared-module androidMain
            // actual would have to construct second instances. The share +
            // StatFs storage-probe actuals need no legacy types.
            implementation(project(":core:ui"))
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy
// :feature:details so migrated files keep their
// `com.raulshma.jellyplay.feature.details` imports; generated accessors land
// in `...feature.details.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.details.generated.resources"
