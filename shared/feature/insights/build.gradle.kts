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
        namespace = "com.raulshma.jellyplay.shared.feature.insights"
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
    // PlaybackRepository, WatchHistoryRepository) that resolve only from the
    // android+jvm DI graph. The missing target also keeps java.time.* legal
    // in commonMain — LocalDate / DayOfWeek / DateTimeFormatter / ChronoUnit
    // drive the heatmap grid math — which a wasm target forbids.
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
            implementation(project(":shared:core:ui"))
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.animation)
            implementation(libs.jb.compose.material3)
            // Compose-resources runtime (stringResource/pluralStringResource
            // + the Res object the generated accessors hang off).
            implementation(compose.components.resources)
            implementation(libs.tabler.icons.outline)
            implementation(libs.tabler.icons.filled)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped: no insights file imports it — the navigation entry
            // uses entry<Route> from the nav3 runtime/ui artifacts only,
            // syncplay/calendar/requests precedent.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screen.
            implementation(libs.lifecycle.runtime.compose)
            // Coil for the screen's direct `coil3.size.Size` request-sizing
            // argument to MediaImage (admin PluginCatalogCard / editor
            // ImagesTab precedent — shared/core:ui's own coil dep is not
            // api-exported). The legacy build additionally carried
            // coil-network-okhttp; the day-detail rows' images load through
            // MediaImage, whose image loading already rides the app-level
            // network fetcher.
            implementation(libs.coil.compose)
            // Koin owns the insights ViewModel (V3 feature conveyor: one
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
        // Heatmap share actual (admin StatisticsExport precedent): the
        // FileProvider/drawToBitmap bodies need only the Android framework
        // plus androidx.core, which rides the compose-ui transitive edge —
        // androidMain needs no new dependency.
        getByName("androidMain").dependencies {
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy
// :feature:insights so migrated files keep their
// `com.raulshma.jellyplay.feature.insights` imports; generated accessors land
// in `...feature.insights.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.insights.generated.resources"
