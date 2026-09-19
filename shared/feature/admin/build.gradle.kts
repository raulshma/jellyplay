import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.admin"
        compileSdk = 37
        minSdk = 28
        // Compose-resources packaging (device-pass finding): with the
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

    // web breadth: target-only (the insights precedent). The whole
    // Kotlin surface (screens, ViewModels, components, navigation, Koin
    // module) moved to the jvmShared source set — commonMain keeps only
    // composeResources — because every ViewModel binds core:data's
    // jvmShared AdminRepository/AdminStatisticsRepository. The
    // promote-or-gate verdict is GATE: the repositories are
    // orchestrator-owned (core:data; the impls are OkHttp/network-backed,
    // the statistics one Room-backed, with no wasm client), so it may
    // not change core:data, and a wasm stub seam would fake an empty admin
    // dashboard/empty user list — fake server data, the exact thing the
    // insights review rejected ("a fake empty heatmap"). The web graph
    // therefore compiles an (intentionally) empty commonMain — the
    // orchestrator has nothing to route on web until the repository promotion lands
    // the admin repositories (interfaces + impls) — while the
    // android/desktop graphs keep everything, byte-identical. Promotion
    // scope for that future pass: AdminRepository + AdminStatisticsRepository
    // interfaces and impls (OkHttp api surface + Room statistics queries)
    // plus the java.text renderers' locale split (SimpleDateFormat
    // audit/statistics stamps; localized on JVM, fixed-English on wasm).
    // The karma/Chrome browser run stays off like core:ui/core:network.
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
        // The whole admin surface is jvmShared (see the target-only comment):
        // it binds core:data's jvmShared admin repositories and keeps the
        // java.text/java.util renderers, both of which a wasm target
        // forbids in commonMain. androidMain (WebView quartet, StatisticsExport/
        // AdminMessenger actuals) and jvmMain (desktop actuals) sit on top of
        // it exactly as they sat on commonMain.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

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
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
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

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface. Scoped
// to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before (the
// identical block lives in shared/core/ui, shared/feature/requests and the
// other web modules).
configurations.configureEach {
    if (name.lowercase().contains("wasmjs")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.navigation3:navigation3-ui"))
                .using(module(libs.jb.navigation3.ui.get().toString()))
                .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
        }
    }
}
