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
            implementation(libs.coil.compose)
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

// The compose-resources `packageOfResClass`
// (`...feature.admin.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
