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

    // Wave 16C: second shared/feature module with the web target — the
    // SeerrDetail slice renders in the ComposeViewport web shell (the
    // Requests→SeerrDetail navigation stub becomes real). The old blocker
    // (java.time/java.text in commonMain) is gone two ways: SeerrDetailScreen/
    // SeerrDetailUtils were purified onto integer-math + kotlinx.datetime
    // seams, and the MediaDetail cluster (Room-blocked, OFF web) moved to
    // jvmShared with its java.* bodies verbatim. The karma/Chrome browser run
    // stays off like core:ui/core:network/requests — jvmTest pins semantics.
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // The MediaDetail-cluster screens (MediaDetailScreen + its body/
        // sections/sheets and the ManageSeries + navigation entryProvider)
        // share android + desktop verbatim: they carry the java.io/java.time/
        // java.text bodies and reach Room-backed data through commonMain
        // seams, and the cluster stays off web this wave. SeerrDetail's files
        // stay in commonMain (purified).
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // Preference/state stores + projections (DetailStores bundle) and
            // the per-feature slices (Seerr, Downloads, Library, HomeDiscovery,
            // Experimental, PlayerEngine, AppRuntime).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // SeerrDetailUtils' purified date formatting (wave 16C): the java.time
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
            // stay APP-side (AppKoinModule interop adapters; formerly the
            // HiltInteropModule singles) — a shared-module androidMain
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

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface. Scoped
// to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before (spike w-10C S1/R2; the
// identical block lives in shared/core/ui + shared/feature/requests).
configurations.configureEach {
    if (name.lowercase().contains("wasmjs")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.navigation3:navigation3-ui"))
                .using(module(libs.jb.navigation3.ui.get().toString()))
                .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
        }
    }
}
