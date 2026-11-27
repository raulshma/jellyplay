plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.admin"

        // Robolectric lane (UserDetailTabSwitchHostTest) — the Android-runtime
        // link check for the user-detail tab switch. Flags mirrored verbatim
        // from shared/feature/player-video: real resource serving for
        // compose-resources string lookups and unstubbed-Context tolerance.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
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
            // The shared MainDispatcherRule fixture, replacing this lane's
            // hand-inlined setMain/resetMain boilerplate (test-scoped only —
            // see the fixtures module's house rules).
            implementation(project(":shared:core:test-fixtures"))
            implementation(libs.mockk)
            // Desktop compose UI test for the user-detail tab switch
            // (UserDetailTabSwitchUiTest) — runComposeUiTest is the
            // framework-agnostic ComposeUiTest entry (no JUnit4 runner; the
            // suite stays on kotlin-test like the rest of jvmTest).
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            // The UI test needs a real skia scene: currentOs pulls the
            // skiko-awt runtime (native lib) the ui-test scene renders with.
            implementation(compose.desktop.currentOs)
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

// Robolectric lane for the user-detail tab-switch regression
// (UserDetailTabSwitchHostTest). AGP 9.4's withHostTest names the lane's
// source set androidHostTest (src/androidHostTest/kotlin) and materializes it
// only in afterEvaluate, so the dependency wiring rides a configureEach — an
// eager lookup would run before the source set exists (same pattern as
// shared/feature/player-video and shared/core/ui).
kotlin.sourceSets.configureEach {
    if (name == "androidHostTest") {
        dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.mockk)
            // Shared MainDispatcherRule fixture for the ViewModel's
            // Dispatchers.Main coroutines (test-scoped only).
            implementation(project(":shared:core:test-fixtures"))
            implementation(libs.coroutines.test)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.manifest)
            // material3 needs no pin here: KmpLibraryComposePlugin's
            // alignment constraint drags this lane's graph to the catalog
            // composeMaterial3 too — the same version :app ships (see the
            // plugin KDoc for the compile-vs-runtime drift this closes).
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.admin.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
