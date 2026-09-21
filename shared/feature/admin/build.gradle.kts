import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.admin"
    }

    sourceSets {
        // The whole admin surface is jvmShared (screens, ViewModels,
        // components, navigation, Koin module — commonMain keeps only
        // composeResources): it binds core:data's jvmShared admin
        // repositories and keeps the java.text/java.util renderers, both
        // JVM-only APIs. androidMain (WebView quartet, StatisticsExport/
        // AdminMessenger actuals) and jvmMain (desktop actuals) sit on top of
        // it exactly as they sat on commonMain. (The jvmShared middle source
        // set comes from the convention plugin.)

        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // runCatchingRethrowingCancellation in the AdminLoad fetch
            // variants (Dashboard/Logs preserve their historical catch
            // semantics without masking cancellation).
            implementation(project(":shared:core:concurrency"))
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
            // Nav3 ships KMP variants from google maven directly — no mirror.
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.coil.compose)
            // Koin owns the admin ViewModels (V3 feature conveyor: one
            // framework per type — the Hilt annotations were stripped at the
            // move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            // Stale-media / watched-removal scan JSON payloads.
            implementation(libs.kotlinx.serialization.json)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // Second documented shared→legacy edge (library/livetv/settings
        // precedent): PluginConfigScreen's message bus and the WebView quartet
        // stay Android-only in this source set; the quartet also needs OkHttp
        // (same-origin WebView request interception), declared explicitly
        // below.
        getByName("androidMain").dependencies {
            // WebView quartet: interceptAuthedRequest re-issues same-origin
            // GETs through the plugin WebView session's OkHttpClient.
            implementation(libs.okhttp)
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:admin
// so migrated files keep their `com.raulshma.jellyplay.feature.admin` imports;
// generated accessors land in `...feature.admin.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.admin.generated.resources"
